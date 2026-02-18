package com.studiocamera.feature.pair.domain

import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.repository.PairRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

data class PairStep(
    val name: String,
    val status: StepStatus = StepStatus.Pending,
    val elapsedMs: Long = 0L,
    val errorMessage: String? = null
)

enum class StepStatus {
    Pending,
    InProgress,
    Completed,
    Failed
}

data class PairProgress(
    val steps: List<PairStep> = defaultSteps(),
    val currentStepIndex: Int = -1,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val device: PairedDevice? = null,
    val capabilities: DeviceCapabilities? = null,
    val requiresTrustConfirmation: Boolean = false,
    val fingerprint: String? = null
) {
    companion object {
        fun defaultSteps() = listOf(
            PairStep("Resolving"),
            PairStep("TLS Handshake"),
            PairStep("Trust Prompt"),
            PairStep("Authenticating"),
            PairStep("Binding"),
            PairStep("Negotiating Capabilities")
        )
    }
}

class PairStateMachine(
    private val pairRepository: PairRepository
) {
    private val _progress = MutableStateFlow(PairProgress())
    val progress: StateFlow<PairProgress> = _progress.asStateFlow()

    private var pairJob: Job? = null

    suspend fun startPairing(
        endpoint: String,
        bindToken: String,
        fingerprint: String,
        deviceId: String,
        deviceName: String,
        onTrustConfirmation: suspend (String) -> Boolean
    ): Result<PairedDevice> = coroutineScope {
        // Capture the Job so cancel() can stop this coroutine
        pairJob = coroutineContext[Job]
        _progress.value = PairProgress()
        try {
            withTimeout(30_000L) {
                // Step 0: Resolving
                executeStep(0) {
                    withTimeout(10_000L) {
                        pairRepository.resolveEndpoint(endpoint)
                    }
                }

                // Step 1: TLS Handshake
                val serverFingerprint = executeStep(1) {
                    withTimeout(10_000L) {
                        pairRepository.performTlsHandshake(endpoint)
                    }
                }

                // Step 2: Trust Prompt (TOFU)
                executeStep(2) {
                    val existingFingerprint = pairRepository.getTrustedFingerprint(deviceId)
                    if (existingFingerprint != null) {
                        // Known device - verify fingerprint
                        if (existingFingerprint != serverFingerprint) {
                            throw SecurityException(
                                "Device identity changed. Re-pair required."
                            )
                        }
                    } else {
                        // New device - ask user to trust
                        _progress.value = _progress.value.copy(
                            requiresTrustConfirmation = true,
                            fingerprint = serverFingerprint
                        )
                        val trusted = onTrustConfirmation(serverFingerprint)
                        _progress.value = _progress.value.copy(
                            requiresTrustConfirmation = false
                        )
                        if (!trusted) {
                            throw CancellationException("User rejected device trust")
                        }
                        pairRepository.saveTrustedFingerprint(deviceId, serverFingerprint)
                    }
                }

                // Step 3: Authenticating
                val sessionTokens = executeStep(3) {
                    withTimeout(10_000L) {
                        pairRepository.authenticate(endpoint, bindToken)
                    }
                }

                // Step 4: Binding
                val device = executeStep(4) {
                    withTimeout(10_000L) {
                        pairRepository.bind(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            endpoint = endpoint,
                            fingerprint = serverFingerprint,
                            accessToken = sessionTokens.first,
                            refreshToken = sessionTokens.second
                        )
                    }
                }

                // Step 5: Negotiating Capabilities
                val capabilities = executeStep(5) {
                    withTimeout(10_000L) {
                        pairRepository.negotiateCapabilities(endpoint, sessionTokens.first)
                    }
                }

                val pairedDevice = device.copy(capabilities = capabilities)
                _progress.value = _progress.value.copy(
                    isComplete = true,
                    device = pairedDevice,
                    capabilities = capabilities
                )

                Result.success(pairedDevice)
            }
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(isCancelled = true)
            Result.failure(e)
        } catch (e: Exception) {
            val currentStep = _progress.value.currentStepIndex
            if (currentStep >= 0) {
                updateStep(currentStep) {
                    it.copy(
                        status = StepStatus.Failed,
                        errorMessage = e.message
                    )
                }
            }
            Result.failure(e)
        }
    }

    fun cancel() {
        pairJob?.cancel()
        _progress.value = _progress.value.copy(isCancelled = true)
    }

    fun reset() {
        _progress.value = PairProgress()
    }

    private suspend fun <T> executeStep(index: Int, block: suspend () -> T): T {
        _progress.value = _progress.value.copy(currentStepIndex = index)
        updateStep(index) { it.copy(status = StepStatus.InProgress) }

        val startTime = currentTimeMs()
        try {
            val result = block()
            val elapsed = currentTimeMs() - startTime
            updateStep(index) {
                it.copy(status = StepStatus.Completed, elapsedMs = elapsed)
            }
            return result
        } catch (e: Exception) {
            val elapsed = currentTimeMs() - startTime
            updateStep(index) {
                it.copy(
                    status = StepStatus.Failed,
                    elapsedMs = elapsed,
                    errorMessage = e.message
                )
            }
            throw e
        }
    }

    private fun updateStep(index: Int, update: (PairStep) -> PairStep) {
        val steps = _progress.value.steps.toMutableList()
        steps[index] = update(steps[index])
        _progress.value = _progress.value.copy(steps = steps)
    }

    private fun currentTimeMs(): Long {
        // Using kotlin.system for KMP compatibility
        return com.studiocamera.core.common.currentTimeMillis()
    }
}

