package com.studiocamera.android.ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.studiocamera.android.nfc.NfcBridge
import com.studiocamera.core.designsystem.component.ConnectionChip
import com.studiocamera.core.designsystem.component.PaywallGate
import com.studiocamera.core.designsystem.theme.StudioCameraTheme
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.SubscriptionState
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.core.ui.navigation.Tab
import org.koin.compose.koinInject
import com.studiocamera.feature.camera.presentation.CameraScreen
import com.studiocamera.feature.discovery.presentation.DiscoveryScreen
import com.studiocamera.feature.media.presentation.MediaScreen
import com.studiocamera.feature.mock.presentation.MockScreen
import com.studiocamera.feature.pair.presentation.PairEvent
import com.studiocamera.feature.pair.presentation.PairScreen
import com.studiocamera.feature.pair.presentation.PairSideEffect
import com.studiocamera.feature.pair.presentation.PairViewModel
import com.studiocamera.feature.pair.presentation.component.OnboardingOverlay
import com.studiocamera.feature.settings.presentation.SettingsScreen
import kotlinx.coroutines.launch

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

        // Camera permission launcher
        var pendingCameraPermissionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                pendingCameraPermissionCallback?.invoke()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Camera permission is required to scan QR codes",
                        duration = SnackbarDuration.Short
                    )
                }
            }
            pendingCameraPermissionCallback = null
        }

        // Nearby Wi-Fi devices permission launcher (Android 13+)
        var pendingNearbyWifiCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
        val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            pendingNearbyWifiCallback?.invoke(isGranted)
            pendingNearbyWifiCallback = null
        }

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

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val tabIcon: @Composable (Tab) -> Unit = { tab ->
            Icon(
                imageVector = when (tab) {
                    Tab.Home -> Icons.Default.Home
                    Tab.Discover -> Icons.Default.Radar
                    Tab.Camera -> Icons.Default.Videocam
                    Tab.Media -> Icons.Default.PhotoLibrary
                    Tab.Settings -> Icons.Default.Settings
                    Tab.Mock -> Icons.Default.BugReport
                },
                contentDescription = tab.contentDescription
            )
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

        val contentBlock: @Composable () -> Unit = {
            Children(stack = childStack) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Home -> {
                        val pairViewModel: PairViewModel = koinInject()

                        // Wire NFC tag reads to PairViewModel
                        nfcBridge?.let { bridge ->
                            bridge.onTagRead = { payload ->
                                pairViewModel.onEvent(PairEvent.NfcTagRead(payload))
                            }
                        }

                        // Collect side effects for NFC dispatch
                        LaunchedEffect(pairViewModel) {
                            pairViewModel.sideEffects.collect { effect ->
                                when (effect) {
                                    PairSideEffect.EnableNfcForegroundDispatch -> {
                                        nfcBridge?.enableDispatch()
                                    }
                                    PairSideEffect.DisableNfcForegroundDispatch -> {
                                        nfcBridge?.disableDispatch()
                                    }
                                    else -> {} // other effects handled inside PairScreen
                                }
                            }
                        }

                        PairScreen(
                            onNavigateToCamera = { component.navigateToTab(Tab.Camera) },
                            onNavigateToMedia = { component.navigateToTab(Tab.Media) },
                            onOpenWifiSettings = {
                                context.startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS))
                            },
                            onOpenAppSettings = {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                )
                            },
                            onRequestCameraPermission = { onGranted ->
                                pendingCameraPermissionCallback = onGranted
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onRequestNearbyWifiPermission = { onResult ->
                                pendingNearbyWifiCallback = onResult
                                nearbyWifiPermissionLauncher.launch(
                                    "android.permission.NEARBY_WIFI_DEVICES"
                                )
                            },
                            viewModel = pairViewModel
                        )
                    }
                    is RootComponent.Child.Discover -> DiscoveryScreen(
                        onDeviceSelected = { component.navigateToHome() }
                    )
                    is RootComponent.Child.Camera -> PaywallGate(
                        subscriptionState = subscriptionState,
                        onSubscribeMonthly = { scope.launch { billingRepository.launchMonthlyPurchase() } },
                        onSubscribeLifetime = { scope.launch { billingRepository.launchLifetimePurchase() } },
                        onRestorePurchases = { scope.launch { billingRepository.restorePurchases() } },
                        onFeedback = { component.navigateToTab(Tab.Settings) }
                    ) {
                        CameraScreen(
                            connectionState = connectionState,
                            onNavigateToHome = { component.navigateToHome() },
                            onNavigateToMedia = { component.navigateToTab(Tab.Media) }
                        )
                    }
                    is RootComponent.Child.Media -> PaywallGate(
                        subscriptionState = subscriptionState,
                        onSubscribeMonthly = { scope.launch { billingRepository.launchMonthlyPurchase() } },
                        onSubscribeLifetime = { scope.launch { billingRepository.launchLifetimePurchase() } },
                        onRestorePurchases = { scope.launch { billingRepository.restorePurchases() } },
                        onFeedback = { component.navigateToTab(Tab.Settings) }
                    ) {
                        MediaScreen(
                            connectionState = connectionState,
                            onNavigateToHome = { component.navigateToHome() }
                        )
                    }
                    is RootComponent.Child.Settings -> SettingsScreen(
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
                        },
                        subscriptionState = subscriptionState,
                        onSubscribeMonthly = { scope.launch { billingRepository.launchMonthlyPurchase() } },
                        onSubscribeLifetime = { scope.launch { billingRepository.launchLifetimePurchase() } },
                        onRestorePurchases = { scope.launch { billingRepository.restorePurchases() } }
                    )
                    is RootComponent.Child.Mock -> MockScreen()
                }
            }
        }

        // First-launch onboarding overlay
        val showOnboarding = !appSettings.onboardingCompleted

        if (isLandscape) {
            // Landscape: NavigationRail on the left, no top bar — maximize screen space
            Row(modifier = modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        ConnectionChip(
                            state = connectionState,
                            deviceName = connectedDevice?.displayName,
                            reconnectAttempt = reconnectAttempt,
                            onRetryClick = {
                                scope.launch { sessionManager.reconnect() }
                            },
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                ) {
                    visibleTabs.forEach { tab ->
                        val isGated = tab.requiresConnection &&
                                connectionState != ConnectionState.Connected
                        val isSelected = tab == activeTab

                        NavigationRailItem(
                            selected = isSelected,
                            enabled = !isGated,
                            onClick = { onTabClick(tab) },
                            icon = { tabIcon(tab) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            alwaysShowLabel = false
                        )
                    }
                }

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
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        visibleTabs.forEach { tab ->
                            val isGated = tab.requiresConnection &&
                                    connectionState != ConnectionState.Connected
                            val isSelected = tab == activeTab

                            NavigationBarItem(
                                selected = isSelected,
                                enabled = !isGated,
                                onClick = { onTabClick(tab) },
                                icon = { tabIcon(tab) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                ),
                                alwaysShowLabel = true
                            )
                        }
                    }
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
