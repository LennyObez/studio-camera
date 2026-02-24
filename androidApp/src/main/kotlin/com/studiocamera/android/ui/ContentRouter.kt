package com.studiocamera.android.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.router.stack.ChildStack
import com.studiocamera.android.nfc.NfcBridge
import com.studiocamera.core.designsystem.component.PaywallGate
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.SubscriptionState
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.core.ui.navigation.Tab
import com.studiocamera.feature.camera.presentation.CameraScreen
import com.studiocamera.feature.discovery.presentation.DiscoveryScreen
import com.studiocamera.feature.media.presentation.MediaScreen
import com.studiocamera.feature.mock.presentation.MockScreen
import com.studiocamera.feature.pair.presentation.PairEvent
import com.studiocamera.feature.pair.presentation.PairScreen
import com.studiocamera.feature.pair.presentation.PairSideEffect
import com.studiocamera.feature.pair.presentation.PairViewModel
import com.studiocamera.feature.settings.presentation.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Routes the Decompose child stack to the appropriate screen composable.
 * Keeps NFC bridge wiring and PaywallGate wrapping co-located with their
 * respective screens.
 */
@Composable
fun ContentRouter(
    childStack: ChildStack<RootComponent.Config, RootComponent.Child>,
    connectionState: ConnectionState,
    subscriptionState: SubscriptionState,
    nfcBridge: NfcBridge?,
    permissionLaunchers: PermissionLaunchers,
    scope: CoroutineScope,
    onNavigateToTab: (Tab) -> Unit,
    onNavigateToHome: () -> Unit,
    onLaunchMonthlyPurchase: suspend () -> Unit,
    onLaunchLifetimePurchase: suspend () -> Unit,
    onRestorePurchases: suspend () -> Unit,
    onSendFeedbackEmail: (String) -> Unit,
    onShareLogs: (String) -> Unit
) {
    val context = LocalContext.current

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
                    onNavigateToCamera = { onNavigateToTab(Tab.Camera) },
                    onNavigateToMedia = { onNavigateToTab(Tab.Media) },
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
                        permissionLaunchers.requestCameraPermission(onGranted)
                    },
                    onRequestNearbyWifiPermission = { onResult ->
                        permissionLaunchers.requestNearbyWifiPermission(onResult)
                    },
                    viewModel = pairViewModel
                )
            }
            is RootComponent.Child.Discover -> DiscoveryScreen(
                onDeviceSelected = { onNavigateToHome() }
            )
            is RootComponent.Child.Camera -> PaywallGate(
                subscriptionState = subscriptionState,
                onSubscribeMonthly = { scope.launch { onLaunchMonthlyPurchase() } },
                onSubscribeLifetime = { scope.launch { onLaunchLifetimePurchase() } },
                onRestorePurchases = { scope.launch { onRestorePurchases() } },
                onFeedback = { onNavigateToTab(Tab.Settings) }
            ) {
                CameraScreen(
                    connectionState = connectionState,
                    onNavigateToHome = { onNavigateToHome() },
                    onNavigateToMedia = { onNavigateToTab(Tab.Media) }
                )
            }
            is RootComponent.Child.Media -> PaywallGate(
                subscriptionState = subscriptionState,
                onSubscribeMonthly = { scope.launch { onLaunchMonthlyPurchase() } },
                onSubscribeLifetime = { scope.launch { onLaunchLifetimePurchase() } },
                onRestorePurchases = { scope.launch { onRestorePurchases() } },
                onFeedback = { onNavigateToTab(Tab.Settings) }
            ) {
                MediaScreen(
                    connectionState = connectionState,
                    onNavigateToHome = { onNavigateToHome() }
                )
            }
            is RootComponent.Child.Settings -> SettingsScreen(
                onSendFeedbackEmail = onSendFeedbackEmail,
                onShareLogs = onShareLogs,
                subscriptionState = subscriptionState,
                onSubscribeMonthly = { scope.launch { onLaunchMonthlyPurchase() } },
                onSubscribeLifetime = { scope.launch { onLaunchLifetimePurchase() } },
                onRestorePurchases = { scope.launch { onRestorePurchases() } }
            )
            is RootComponent.Child.Mock -> MockScreen()
        }
    }
}
