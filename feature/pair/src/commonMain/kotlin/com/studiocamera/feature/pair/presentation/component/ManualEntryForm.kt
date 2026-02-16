package com.studiocamera.feature.pair.presentation.component

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ManualEntryForm(
    endpoint: String,
    bindToken: String,
    isTokenVisible: Boolean,
    onEndpointChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Manual Entry",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
        )

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
                IconButton(onClick = onToggleVisibility) {
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
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = endpoint.isNotBlank() && bindToken.isNotBlank()
        ) {
            Text("Connect")
        }
    }
}
