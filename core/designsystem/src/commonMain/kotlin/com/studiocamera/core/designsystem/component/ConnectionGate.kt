package com.studiocamera.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.SessionError

@Composable
fun ConnectionGate(
    connectionState: ConnectionState,
    featureName: String,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    lastError: SessionError? = null,
    reconnectAttempt: Int = 0,
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
    content: @Composable () -> Unit
) {
    when (connectionState) {
        ConnectionState.Connected -> {
            content()
        }
        ConnectionState.Reconnecting -> {
            // Show content with error banner overlay
            Box(modifier = modifier.fillMaxSize()) {
                content()
                ConnectionErrorBanner(
                    connectionState = connectionState,
                    reconnectAttempt = reconnectAttempt,
                    lastError = lastError,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
        ConnectionState.Failed -> {
            // Failed: show error banner with retry, no content underneath
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ConnectionErrorBanner(
                    connectionState = connectionState,
                    reconnectAttempt = reconnectAttempt,
                    lastError = lastError,
                    onRetry = onRetry,
                    onDismiss = onNavigateToHome,
                    modifier = Modifier
                )

                // Show a centered message below the banner
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Connection lost",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$featureName is unavailable while disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = onNavigateToHome) {
                        Text("Go to Home")
                    }
                }
            }
        }
        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = "Not connected",
                    modifier = Modifier.height(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Connect a device to access $featureName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = onNavigateToHome) {
                    Text("Go to Home")
                }
            }
        }
    }
}
