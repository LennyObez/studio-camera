package com.studiocamera.android.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.studiocamera.android.nfc.NfcBridge
import com.studiocamera.core.designsystem.component.ConnectionChip
import com.studiocamera.core.designsystem.theme.StudioCameraTheme
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.core.ui.navigation.Tab
import com.studiocamera.feature.pair.presentation.component.OnboardingOverlay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootContent(
    component: RootComponent,
    nfcBridge: NfcBridge? = null,
    modifier: Modifier = Modifier
) {
    val settingsRepository: SettingsRepository = koinInject()
    val appSettings by settingsRepository.settings.collectAsState()

    StudioCameraTheme(themeMode = appSettings.themeMode) {
        val childStack by component.childStack.subscribeAsState()
        val connectionState by component.connectionState.collectAsState()
        val connectedDevice by component.connectedDevice.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val sessionManager: SessionManager = koinInject()
        val billingRepository: BillingRepository = koinInject()
        val subscriptionState by billingRepository.subscriptionState.collectAsState()
        val connectionStateManager: ConnectionStateManager = koinInject()
        val reconnectAttempt by connectionStateManager.reconnectAttempt.collectAsState()

        val permissionLaunchers = rememberPermissionLaunchers(snackbarHostState, scope)

        val activeTab = when (childStack.active.instance) {
            is RootComponent.Child.Home -> Tab.Home
            is RootComponent.Child.Discover -> Tab.Discover
            is RootComponent.Child.Camera -> Tab.Camera
            is RootComponent.Child.Media -> Tab.Media
            is RootComponent.Child.Settings -> Tab.Settings
            is RootComponent.Child.Mock -> Tab.Mock
        }

        val visibleTabs = if (appSettings.developerMode) {
            Tab.entries
        } else {
            Tab.entries.filter { it != Tab.Mock }
        }

        val onTabClick: (Tab) -> Unit = { tab ->
            val isGated = tab.requiresConnection &&
                    connectionState != ConnectionState.Connected
            if (isGated) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Connect a device first",
                        duration = SnackbarDuration.Short
                    )
                }
            } else {
                component.navigateToTab(tab)
            }
        }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val contentBlock: @Composable () -> Unit = {
            ContentRouter(
                childStack = childStack,
                connectionState = connectionState,
                subscriptionState = subscriptionState,
                nfcBridge = nfcBridge,
                permissionLaunchers = permissionLaunchers,
                scope = scope,
                onNavigateToTab = { component.navigateToTab(it) },
                onNavigateToHome = { component.navigateToHome() },
                onLaunchMonthlyPurchase = { billingRepository.launchMonthlyPurchase() },
                onLaunchLifetimePurchase = { billingRepository.launchLifetimePurchase() },
                onRestorePurchases = { billingRepository.restorePurchases() },
                onSendFeedbackEmail = { body ->
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:support@studio-camera.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Studio Camera Feedback")
                        putExtra(Intent.EXTRA_TEXT, body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No email app found")
                        }
                    }
                },
                onShareLogs = { logText ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, logText)
                        putExtra(Intent.EXTRA_SUBJECT, "Studio Camera debug logs")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share logs"))
                }
            )
        }

        // First-launch onboarding overlay
        val showOnboarding = !appSettings.onboardingCompleted

        if (isLandscape) {
            // Landscape: NavigationRail on the left, no top bar — maximize screen space
            Row(modifier = modifier.fillMaxSize()) {
                LandscapeNavigationRail(
                    visibleTabs = visibleTabs,
                    activeTab = activeTab,
                    connectionState = connectionState,
                    deviceName = connectedDevice?.displayName,
                    reconnectAttempt = reconnectAttempt,
                    onRetryClick = { scope.launch { sessionManager.reconnect() } },
                    onTabClick = onTabClick
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    contentBlock()
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    OnboardingOverlay(
                        visible = showOnboarding,
                        onComplete = {
                            scope.launch {
                                settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
                            }
                        }
                    )
                }
            }
        } else {
            // Portrait: TopAppBar + bottom NavigationBar
            Scaffold(
                modifier = modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text("Studio Camera") },
                        actions = {
                            ConnectionChip(
                                state = connectionState,
                                deviceName = connectedDevice?.displayName,
                                onRetryClick = {
                                    scope.launch { sessionManager.reconnect() }
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    PortraitBottomBar(
                        visibleTabs = visibleTabs,
                        activeTab = activeTab,
                        connectionState = connectionState,
                        onTabClick = onTabClick
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    contentBlock()
                    OnboardingOverlay(
                        visible = showOnboarding,
                        onComplete = {
                            scope.launch {
                                settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
                            }
                        }
                    )
                }
            }
        }
    }
}
