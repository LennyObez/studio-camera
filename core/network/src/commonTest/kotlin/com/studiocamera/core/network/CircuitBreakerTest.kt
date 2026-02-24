package com.studiocamera.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CircuitBreakerTest {

    @Test
    fun startsInClosedState() {
        val breaker = CircuitBreaker(failureThreshold = 3, cooldownMs = 1_000L)

        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
        assertTrue(breaker.allowRequest())
        assertFalse(breaker.isOpen)
    }

    @Test
    fun opensAfterConsecutiveFailuresReachThreshold() {
        // Use a long cooldown so Open state doesn't immediately transition to HalfOpen
        val breaker = CircuitBreaker(failureThreshold = 3, cooldownMs = 60_000L)

        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)

        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)

        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        assertTrue(breaker.isOpen)
    }

    @Test
    fun rejectsRequestsWhenOpen() {
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 60_000L)

        breaker.recordFailure()
        breaker.recordFailure()

        assertFalse(breaker.allowRequest())
        assertTrue(breaker.isOpen)
    }

    @Test
    fun transitionsToHalfOpenAfterCooldown() {
        // Use 0ms cooldown so the transition happens on the next state access
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 0L)

        breaker.recordFailure()
        breaker.recordFailure()

        // With 0ms cooldown, the next access already finds cooldown elapsed -> HalfOpen
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun closesAfterSuccessfulProbeInHalfOpen() {
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 0L)

        // Trip open
        breaker.recordFailure()
        breaker.recordFailure()

        // 0ms cooldown -> already HalfOpen on next access
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)

        // Successful probe closes the circuit
        breaker.recordSuccess()
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun reopensAfterFailedProbeInHalfOpen() {
        // With 0ms cooldown, Open immediately transitions to HalfOpen on access.
        // A failed probe trips it open again, and 0ms cooldown means it's HalfOpen
        // again on the next access. We verify the cycle works correctly.
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 0L)

        // Trip open
        breaker.recordFailure()
        breaker.recordFailure()

        // Cooldown = 0 -> transitions to HalfOpen on access
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)

        // Failed probe -> trips open -> 0ms cooldown -> HalfOpen again
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)

        // Only a success can break the cycle and close the circuit
        breaker.recordSuccess()
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun failedProbeKeepsCyclingUntilSuccess() {
        // Verify that repeated failed probes in HalfOpen keep the circuit
        // from closing — it only closes after a successful probe.
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 0L)

        // Trip to Open -> HalfOpen (0ms cooldown)
        breaker.recordFailure()
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)

        // Multiple failed probes
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)

        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.currentState)

        // Success finally closes
        breaker.recordSuccess()
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun resetReturnsToClosed() {
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 60_000L)

        // Trip open (with long cooldown so it stays Open)
        breaker.recordFailure()
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        breaker.reset()

        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
        assertTrue(breaker.allowRequest())
        assertFalse(breaker.isOpen)
    }

    @Test
    fun successResetsConsecutiveFailureCounter() {
        val breaker = CircuitBreaker(failureThreshold = 3, cooldownMs = 60_000L)

        breaker.recordFailure()
        breaker.recordFailure()
        // 2 consecutive failures, not yet at threshold of 3

        breaker.recordSuccess()
        // Counter resets to 0

        breaker.recordFailure()
        breaker.recordFailure()
        // Only 2 failures since the reset, still below threshold

        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun staysOpenWithLongCooldown() {
        val breaker = CircuitBreaker(failureThreshold = 1, cooldownMs = 60_000L)

        breaker.recordFailure()

        // With 60s cooldown, it should stay Open
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        assertFalse(breaker.allowRequest())
        assertTrue(breaker.isOpen)
    }
}
