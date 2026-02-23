package com.studiocamera.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.studiocamera.core.designsystem.component.ConnectionChip
import com.studiocamera.core.designsystem.component.PaywallGate
import com.studiocamera.core.designsystem.theme.StudioCameraTheme
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.core.ui.navigation.Tab
import com.studiocamera.feature.camera.presentation.CameraScreen
import com.studiocamera.feature.discovery.presentation.DiscoveryScreen
import com.studiocamera.feature.media.presentation.MediaScreen
import com.studiocamera.feature.mock.presentation.MockScreen
import com.studiocamera.feature.pair.presentation.PairScreen
import com.studiocamera.feature.pair.presentation.PairViewModel
import com.studiocamera.feature.settings.presentation.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import platform.UIKit.UIViewController

/**
 * iOS entry point — called from Swift via `MainViewControllerKt.mainViewController()`.
 *
 * Creates Decompose lifecycle, RootComponent, and wraps in ComposeUIViewController.
 */
fun mainViewController(): UIViewController {
    val lifecycle = LifecycleRegistry()
    val componentContext = DefaultComponentContext(lifecycle = lifecycle)
    val rootComponent = RootComponent(componentContext)

    return ComposeUIViewController {
        IosRootContent(component = rootComponent)
    }
}

/**
 * iOS-specific root content — simplified version of Android's RootContent.
 * No NFC, no Android permissions, no landscape NavigationRail (iOS handles rotation differently).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosRootContent(component: RootComponent) {
    val settingsRepository: SettingsRepository = koinInject()
    val appSettings by settingsRepository.settings.collectAsState()

    StudioCameraTheme(themeMode = appSettings.themeMode) {
        val childStack by component.childStack.subscribeAsState()
        val connectionState by component.connectionState.collectAsState()
        val connectedDevice by component.connectedDevice.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val sessionManager: SessionManager = koinInject()
        val billingRepository: BillingRepository = koinInject()
        val subscriptionState by billingRepository.subscriptionState.collectAsState()

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

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                Children(stack = childStack) { child ->
                    when (child.instance) {
                        is RootComponent.Child.Home -> {
                            val pairViewModel: PairViewModel = koinInject()
                            PairScreen(
                                onNavigateToCamera = { component.navigateToTab(Tab.Camera) },
                                onNavigateToMedia = { component.navigateToTab(Tab.Media) },
                                onOpenWifiSettings = { /* iOS handles via Settings app */ },
                                onOpenAppSettings = { /* iOS handles via Settings app */ },
                                onRequestCameraPermission = { onGranted -> onGranted() },
                                onRequestNearbyWifiPermission = { onResult -> onResult(true) },
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
                            onSendFeedbackEmail = { /* iOS: handled via MFMailComposeViewController in future */ },
                            onShareLogs = { /* iOS: handled via UIActivityViewController in future */ },
                            subscriptionState = subscriptionState,
                            onSubscribeMonthly = { scope.launch { billingRepository.launchMonthlyPurchase() } },
                            onSubscribeLifetime = { scope.launch { billingRepository.launchLifetimePurchase() } },
                            onRestorePurchases = { scope.launch { billingRepository.restorePurchases() } }
                        )
                        is RootComponent.Child.Mock -> MockScreen()
                    }
                }
            }
        }
    }
}
