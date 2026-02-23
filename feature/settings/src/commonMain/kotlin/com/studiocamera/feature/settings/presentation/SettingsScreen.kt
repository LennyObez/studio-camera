package com.studiocamera.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.SubscriptionState
import com.studiocamera.core.domain.model.ThemeMode
import com.studiocamera.feature.settings.presentation.component.FeedbackSheet

import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    onSendFeedbackEmail: ((String) -> Unit)? = null,
    onShareLogs: ((String) -> Unit)? = null,
    subscriptionState: SubscriptionState = SubscriptionState.Unknown,
    onSubscribeMonthly: () -> Unit = {},
    onSubscribeLifetime: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinInject()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Developer mode snackbar
    LaunchedEffect(state.showDeveloperModeSnackbar) {
        if (state.showDeveloperModeSnackbar) {
            snackbarHostState.showSnackbar("You are now a developer!")
            viewModel.onEvent(SettingsEvent.DismissDeveloperSnackbar)
        }
    }

    // Forget device dialog
    state.showForgetDeviceDialog?.let { deviceId ->
        val device = state.pairedDevices.find { it.deviceId == deviceId }
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DismissForgetDevice) },
            title = { Text("Forget device?") },
            text = { Text("Remove \"${device?.displayName ?: deviceId}\" from paired devices?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.ConfirmForgetDevice) }) {
                    Text("Forget", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.DismissForgetDevice) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear data dialog
    if (state.showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DismissClearData) },
            title = { Text("Clear all data?") },
            text = { Text("This will remove all paired devices, settings, and cached data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.ConfirmClearData) }) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.DismissClearData) }) {
                    Text("Cancel")
                }
            }
        )
    }


    // Feedback sheet
    if (state.showFeedbackSheet) {
        FeedbackSheet(
            onDismiss = { viewModel.onEvent(SettingsEvent.DismissFeedback) },
            onSend = { data ->
                viewModel.onEvent(SettingsEvent.DismissFeedback)
                val body = buildString {
                    appendLine("Camera Brand: ${data.cameraBrand}")
                    appendLine("Camera Model: ${data.cameraModel}")
                    if (data.worksChecks.isNotEmpty()) appendLine("Works: ${data.worksChecks.joinToString()}")
                    if (data.issueChecks.isNotEmpty()) appendLine("Issues: ${data.issueChecks.joinToString()}")
                    if (data.comment.isNotBlank()) appendLine("Comments: ${data.comment}")
                    if (data.email.isNotBlank()) appendLine("Email: ${data.email}")
                }
                onSendFeedbackEmail?.invoke(body)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Appearance ---
        SectionHeader("Appearance")

        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onEvent(SettingsEvent.UpdateTheme(mode)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.settings.themeMode == mode,
                    onClick = { viewModel.onEvent(SettingsEvent.UpdateTheme(mode)) }
                )
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Live View Defaults ---
        SectionHeader("Live view defaults")

        SettingsSwitch(
            label = "Show grid overlay",
            checked = state.settings.defaultGrid,
            onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateDefaultGrid(it)) }
        )
        SettingsSwitch(
            label = "Show safe zone overlay",
            checked = state.settings.defaultSafeZone,
            onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateDefaultSafeZone(it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Connection ---
        SectionHeader("Connection")

        SettingsSwitch(
            label = "Auto-reconnect",
            checked = state.settings.autoReconnect,
            onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateAutoReconnect(it)) }
        )
        SettingsSwitch(
            label = "Keep screen on",
            checked = state.settings.keepScreenOn,
            onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateKeepScreenOn(it)) }
        )
        SettingsSwitch(
            label = "Battery warnings",
            checked = state.settings.showBatteryWarnings,
            onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateShowBatteryWarnings(it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Paired Devices ---
        SectionHeader("Paired devices")

        if (state.pairedDevices.isEmpty()) {
            Text(
                text = "No paired devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            state.pairedDevices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (device.wifiSsid != null) "Wi-Fi: ${device.wifiSsid}" else device.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { viewModel.onEvent(SettingsEvent.ForgetDevice(device.deviceId)) }
                    ) {
                        Text("Forget", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Feedback & Support ---
        SectionHeader("Support")

        OutlinedButton(
            onClick = { viewModel.onEvent(SettingsEvent.ShowFeedback) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Feedback & support")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Developer Mode ---
        if (state.settings.developerMode) {
            SectionHeader("Developer")

            SettingsSwitch(
                label = "Developer mode",
                checked = state.settings.developerMode,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateDeveloperMode(it)) }
            )


            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // --- Subscription ---
        SectionHeader("Subscription")

        val planText = when (subscriptionState) {
            is SubscriptionState.Trial -> "Free Trial — ${subscriptionState.daysRemaining} days remaining"
            is SubscriptionState.TrialExpired -> "Trial expired"
            is SubscriptionState.Monthly -> "Monthly subscription (active)"
            is SubscriptionState.Lifetime -> "Lifetime (purchased)"
            is SubscriptionState.Unknown -> "Loading..."
        }
        Text(
            text = planText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )

        if (subscriptionState is SubscriptionState.Trial || subscriptionState is SubscriptionState.TrialExpired) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSubscribeMonthly,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Monthly")
                }
                OutlinedButton(
                    onClick = onSubscribeLifetime,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lifetime")
                }
            }
        }

        TextButton(
            onClick = onRestorePurchases,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text("Restore purchases")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Data ---
        SectionHeader("Data")

        OutlinedButton(
            onClick = { viewModel.onEvent(SettingsEvent.ShowClearDataDialog) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear all data", color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- About ---
        SectionHeader("About")

        Text(
            text = "Studio Camera v${state.appVersion}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { viewModel.onEvent(SettingsEvent.TapVersionString) }
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Snackbar host for developer mode toast
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
