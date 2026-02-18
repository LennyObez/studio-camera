package com.studiocamera.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.SubscriptionState

@Composable
fun PaywallGate(
    subscriptionState: SubscriptionState,
    onSubscribeMonthly: () -> Unit,
    onSubscribeLifetime: () -> Unit,
    onRestorePurchases: () -> Unit,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    monthlyPriceDisplay: String = "Monthly",
    lifetimePriceDisplay: String = "Lifetime",
    content: @Composable () -> Unit
) {
    when (subscriptionState) {
        is SubscriptionState.Trial -> {
            // Show trial banner then content
            Column(modifier = modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "${subscriptionState.daysRemaining} days remaining \u2014 try all features before subscribing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                content()
            }
        }
        is SubscriptionState.TrialExpired -> {
            // Block content, show paywall
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Subscribe to continue",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Your 7-day trial has ended. Subscribe to keep using Camera and Media features.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onSubscribeMonthly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(monthlyPriceDisplay)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSubscribeLifetime,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(lifetimePriceDisplay)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Only subscribe if all features work for your camera. If something doesn't work, use the Feedback button to let us know.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onFeedback) {
                    Text("Send feedback")
                }

                TextButton(onClick = onRestorePurchases) {
                    Text("Restore purchases")
                }
            }
        }
        is SubscriptionState.Monthly,
        is SubscriptionState.Lifetime -> {
            // Active subscription — show content
            content()
        }
        is SubscriptionState.Unknown -> {
            // Loading — show centered spinner while billing state resolves
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}
