package com.studiocamera.feature.pair.domain

import com.studiocamera.core.domain.model.DeviceCapabilities
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.model.SessionInfo
import com.studiocamera.core.domain.repository.PairRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Configurable fake for testing PairStateMachine without real network. */
private class ConfigurableFakePairRepository(
    var resolveResult: Result<Unit> = Result.success(Unit),
    var handshakeFingerprint: String = "AA:BB:CC:DD",
    var authenticateResult: Pair<String, String> = "access-tok" to "refresh-tok",
    var bindResult: PairedDevice = PairedDevice(
        deviceId = "dev-1",
        deviceName = "Test Camera",
        endpoint = "https://10.0.0.1:8443",
        fingerprint = "AA:BB:CC:DD"
    ),
    var capabilities: DeviceCapabilities = DeviceCapabilities(),
    var trustedFingerprints: MutableMap<String, String> = mutableMapOf()
) : PairRepository {

    override suspend fun resolveEndpoint(endpoint: String) {
        resolveResult.getOrThrow()
    }

    override suspend fun performTlsHandshake(endpoint: String): String = handshakeFingerprint

    override suspend fun authenticate(endpoint: String, bindToken: String): Pair<String, String> =
        authenticateResult

    override suspend fun bind(
        deviceId: String,
        deviceName: String,
        endpoint: String,
        fingerprint: String,
        accessToken: String,
        refreshToken: String
    ): PairedDevice = bindResult

    override suspend fun negotiateCapabilities(endpoint: String, accessToken: String) = capabilities

    override suspend fun saveTrustedFingerprint(deviceId: String, fingerprint: String) {
        trustedFingerprints[deviceId] = fingerprint
    }

    override suspend fun getTrustedFingerprint(deviceId: String): String? =
        trustedFingerprints[deviceId]
}

@OptIn(ExperimentalCoroutinesApi::class)
class PairStateMachineTest {

    private fun createMachine(
        repo: ConfigurableFakePairRepository = ConfigurableFakePairRepository()
    ) = PairStateMachine(repo) to repo

    // --- Happy path ---

    @Test
    fun successfulPairing_completesAllSteps() = runTest {
        val (machine, _) = createMachine()

        val result = machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { true }
        )

        assertTrue(result.isSuccess)
        val progress = machine.progress.value
        assertTrue(progress.isComplete)
        assertFalse(progress.isCancelled)
        assertEquals("dev-1", progress.device?.deviceId)
        progress.steps.forEach { step ->
            assertEquals(StepStatus.Completed, step.status, "Step '${step.name}' should be completed")
            assertTrue(step.elapsedMs >= 0, "Step '${step.name}' should have non-negative elapsed time")
        }
    }

    @Test
    fun successfulPairing_savesFingerprint() = runTest {
        val repo = ConfigurableFakePairRepository()
        val machine = PairStateMachine(repo)

        machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { true }
        )

        assertEquals("AA:BB:CC:DD", repo.trustedFingerprints["dev-1"])
    }

    // --- Step failures ---

    @Test
    fun resolveFailure_failsAtStep0() = runTest {
        val repo = ConfigurableFakePairRepository(
            resolveResult = Result.failure(Exception("DNS failed"))
        )
        val machine = PairStateMachine(repo)

        val result = machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { true }
        )

        assertTrue(result.isFailure)
        val progress = machine.progress.value
        assertFalse(progress.isComplete)
        assertEquals(StepStatus.Failed, progress.steps[0].status)
        assertEquals("DNS failed", progress.steps[0].errorMessage)
        // Steps after failure remain pending
        assertEquals(StepStatus.Pending, progress.steps[1].status)
    }

    // --- Trust / TOFU ---

    @Test
    fun knownDevice_matchingFingerprint_skipsPrompt() = runTest {
        val repo = ConfigurableFakePairRepository(
            trustedFingerprints = mutableMapOf("dev-1" to "AA:BB:CC:DD")
        )
        val machine = PairStateMachine(repo)
        var promptCalled = false

        val result = machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { promptCalled = true; true }
        )

        assertTrue(result.isSuccess)
        assertFalse(promptCalled, "Should not prompt for already-trusted device")
    }

    @Test
    fun knownDevice_mismatchedFingerprint_failsWithSecurityException() = runTest {
        val repo = ConfigurableFakePairRepository(
            trustedFingerprints = mutableMapOf("dev-1" to "XX:YY:ZZ"),
            handshakeFingerprint = "AA:BB:CC:DD"
        )
        val machine = PairStateMachine(repo)

        val result = machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { true }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals(StepStatus.Failed, machine.progress.value.steps[2].status)
    }

    @Test
    fun userRejectsTrust_cancels() = runTest {
        val (machine, _) = createMachine()

        val result = machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { false }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(machine.progress.value.isCancelled)
    }

    // --- Cancel / Reset ---

    @Test
    fun reset_restoresInitialState() = runTest {
        val (machine, _) = createMachine()

        machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { true }
        )
        assertTrue(machine.progress.value.isComplete)

        machine.reset()

        val progress = machine.progress.value
        assertFalse(progress.isComplete)
        assertFalse(progress.isCancelled)
        assertNull(progress.device)
        assertEquals(-1, progress.currentStepIndex)
        progress.steps.forEach { step ->
            assertEquals(StepStatus.Pending, step.status)
        }
    }

    // --- Progress tracking ---

    @Test
    fun progress_tracksCurrentStepIndex() = runTest {
        val (machine, _) = createMachine()

        machine.startPairing(
            endpoint = "https://10.0.0.1:8443",
            bindToken = "token",
            fingerprint = "AA:BB:CC:DD",
            deviceId = "dev-1",
            deviceName = "Test Camera",
            onTrustConfirmation = { true }
        )

        // After completion, all 6 steps (0..5) should have been visited
        val progress = machine.progress.value
        assertEquals(6, progress.steps.size)
        assertTrue(progress.isComplete)
    }

    @Test
    fun defaultSteps_hasExpectedNames() {
        val steps = PairProgress.defaultSteps()
        assertEquals(6, steps.size)
        assertEquals("Resolving", steps[0].name)
        assertEquals("TLS Handshake", steps[1].name)
        assertEquals("Trust Prompt", steps[2].name)
        assertEquals("Authenticating", steps[3].name)
        assertEquals("Binding", steps[4].name)
        assertEquals("Negotiating Capabilities", steps[5].name)
    }
}
