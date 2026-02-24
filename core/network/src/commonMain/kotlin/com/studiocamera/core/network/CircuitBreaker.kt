package com.studiocamera.core.network

import co.touchlab.kermit.Logger
import com.studiocamera.core.common.currentTimeMillis

/**
 * Simple circuit breaker for network calls.
 *
 * States:
 * - **Closed**: requests pass through normally. After [failureThreshold] consecutive
 *   failures the breaker trips to Open.
 * - **Open**: all requests fail fast with [isOpen] = true. After [cooldownMs] the
 *   breaker transitions to HalfOpen.
 * - **HalfOpen**: a single probe request is allowed. If it succeeds the breaker
 *   closes; if it fails it reopens.
 *
 * Thread-safety: all mutable state is guarded by [lock]. No java.util.concurrent
 * imports — this is KMP commonMain safe.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val cooldownMs: Long = 30_000L
) {

    enum class State { Closed, Open, HalfOpen }

    // -- guarded state --
    private val lock = Any()
    private var state: State = State.Closed
    private var consecutiveFailures: Int = 0
    private var openedAt: Long = 0L

    /** Current state (snapshot). */
    val currentState: State
        get() = synchronized(lock) {
            maybeTransitionToHalfOpen()
            state
        }

    /**
     * Returns `true` when the breaker is open **and** the cooldown has not
     * yet elapsed — i.e. requests should fail fast.
     */
    val isOpen: Boolean
        get() = synchronized(lock) {
            maybeTransitionToHalfOpen()
            state == State.Open
        }

    /**
     * Attempt to acquire permission to execute a request.
     *
     * @return `true` if the call is allowed (Closed, or HalfOpen probe).
     *         `false` if the circuit is Open and calls should fail fast.
     */
    fun allowRequest(): Boolean = synchronized(lock) {
        maybeTransitionToHalfOpen()
        when (state) {
            State.Closed -> true
            State.HalfOpen -> true   // one probe allowed
            State.Open -> false
        }
    }

    /**
     * Record a successful call. Resets consecutive failures and closes
     * the breaker if it was half-open.
     */
    fun recordSuccess(): Unit = synchronized(lock) {
        consecutiveFailures = 0
        if (state == State.HalfOpen) {
            state = State.Closed
            Logger.i("CircuitBreaker") { "Probe succeeded — circuit CLOSED" }
        }
    }

    /**
     * Record a failed call. Increments consecutive failures and may
     * trip the breaker open.
     */
    fun recordFailure(): Unit = synchronized(lock) {
        consecutiveFailures++
        when (state) {
            State.Closed -> {
                if (consecutiveFailures >= failureThreshold) {
                    tripOpen()
                }
            }
            State.HalfOpen -> {
                // Probe failed — reopen
                tripOpen()
            }
            State.Open -> {
                // Already open, nothing to do
            }
        }
    }

    /** Force-reset the breaker to Closed. Useful for manual recovery. */
    fun reset(): Unit = synchronized(lock) {
        state = State.Closed
        consecutiveFailures = 0
        openedAt = 0L
        Logger.d("CircuitBreaker") { "Manually reset — circuit CLOSED" }
    }

    // -- internals --

    private fun tripOpen() {
        state = State.Open
        openedAt = currentTimeMillis()
        Logger.w("CircuitBreaker") {
            "Circuit OPEN after $consecutiveFailures consecutive failures"
        }
    }

    /**
     * If currently Open and the cooldown has elapsed, transition to HalfOpen.
     * Must be called while holding [lock].
     */
    private fun maybeTransitionToHalfOpen() {
        if (state == State.Open && currentTimeMillis() - openedAt >= cooldownMs) {
            state = State.HalfOpen
            Logger.i("CircuitBreaker") {
                "Cooldown elapsed — circuit HALF-OPEN, allowing probe"
            }
        }
    }
}
