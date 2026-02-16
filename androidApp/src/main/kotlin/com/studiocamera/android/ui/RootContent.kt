package com.studiocamera.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.studiocamera.core.designsystem.component.ConnectionChip
import com.studiocamera.core.designsystem.theme.StudioCameraTheme
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.core.ui.navigation.Tab
import com.studiocamera.feature.camera.presentation.CameraScreen
import com.studiocamera.feature.discovery.presentation.DiscoveryScreen
import com.studiocamera.feature.media.presentation.MediaScreen
import com.studiocamera.feature.mock.presentation.MockScreen
import com.studiocamera.feature.pair.presentation.PairScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier
) {
    StudioCameraTheme {
        val childStack by component.childStack.subscribeAsState()
        val connectionState by component.connectionState.collectAsState()
        val connectedDevice by component.connectedDevice.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

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

        val activeTab = when (childStack.active.instance) {
            is RootComponent.Child.Pair -> Tab.Pair
            is RootComponent.Child.Discover -> Tab.Discover
            is RootComponent.Child.Camera -> Tab.Camera
            is RootComponent.Child.Media -> Tab.Media
            is RootComponent.Child.Mock -> Tab.Mock
        }

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
                            deviceName = connectedDevice?.deviceName,
                            onRetryClick = {},
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
                    Tab.entries.forEach { tab ->
                        val isGated = tab.requiresConnection &&
                                connectionState != ConnectionState.Connected
                        val isSelected = tab == activeTab

                        NavigationBarItem(
                            selected = isSelected,
                            enabled = !isGated,
                            onClick = {
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
                            },
                            icon = {
                                Text(
                                    text = tab.label.first().toString(),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                Children(stack = childStack) { child ->
                    when (val instance = child.instance) {
                        is RootComponent.Child.Pair -> PairScreen(
                            onNavigateToCamera = { component.navigateToTab(Tab.Camera) },
                            onNavigateToMedia = { component.navigateToTab(Tab.Media) },
                            onOpenWifiSettings = {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            },
                            onOpenAppSettings = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                )
                            },
                            onRequestCameraPermission = { onGranted ->
                                pendingCameraPermissionCallback = onGranted
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                        is RootComponent.Child.Discover -> DiscoveryScreen(
                            onDeviceSelected = { component.navigateToPair() }
                        )
                        is RootComponent.Child.Camera -> CameraScreen(
                            connectionState = connectionState,
                            onNavigateToPair = { component.navigateToPair() }
                        )
                        is RootComponent.Child.Media -> MediaScreen(
                            connectionState = connectionState,
                            onNavigateToPair = { component.navigateToPair() }
                        )
                        is RootComponent.Child.Mock -> MockScreen()
                    }
                }
            }
        }
    }
}
