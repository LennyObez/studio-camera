package com.studiocamera.feature.pair.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ManualEntryForm(
    ssid: String,
    wifiPassword: String,
    isPasswordVisible: Boolean,
    onSsidChanged: (String) -> Unit,
    onWifiPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onConnectWifiDirect: () -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    endpoint: String,
    bindToken: String,
    isTokenVisible: Boolean,
    onEndpointChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onConnectStudioBox: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Manual connection",
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = "Enter your camera's Wi-Fi network name and password",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChanged,
            label = { Text("Wi-Fi Network Name (SSID)") },
            placeholder = { Text("DIRECT-xxxx:ILCE-7M3") },
            supportingText = { Text("Found on your camera's screen or in Wi-Fi settings") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = wifiPassword,
            onValueChange = onWifiPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onConnectWifiDirect() }),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = onConnectWifiDirect,
            modifier = Modifier.fillMaxWidth(),
            enabled = ssid.isNotBlank()
        ) {
            Text("Connect to camera Wi-Fi")
        }

        // Advanced: manual endpoint connection
        TextButton(
            onClick = onToggleAdvanced,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (showAdvanced) "Hide advanced"
                else "Advanced"
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpointChanged,
                    label = { Text("Device Endpoint") },
                    placeholder = { Text("https://192.168.4.1:8443") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bindToken,
                    onValueChange = onTokenChanged,
                    label = { Text("Bind Token") },
                    singleLine = true,
                    visualTransformation = if (isTokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleTokenVisibility) {
                            Icon(
                                imageVector = if (isTokenVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (isTokenVisible) "Hide token" else "Show token"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnectStudioBox() }),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = onConnectStudioBox,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = endpoint.isNotBlank() && bindToken.isNotBlank()
                ) {
                    Text("Connect via endpoint")
                }
            }
        }
    }
}
