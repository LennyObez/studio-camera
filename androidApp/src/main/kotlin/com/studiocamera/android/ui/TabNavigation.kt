package com.studiocamera.android.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.designsystem.component.ConnectionChip
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.ui.navigation.Tab

@Composable
internal fun TabIcon(tab: Tab) {
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

/**
 * Portrait bottom navigation bar with connection-gated tabs.
 */
@Composable
fun PortraitBottomBar(
    visibleTabs: List<Tab>,
    activeTab: Tab,
    connectionState: ConnectionState,
    onTabClick: (Tab) -> Unit
) {
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
                icon = { TabIcon(tab) },
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

/**
 * Landscape navigation rail with connection chip header and connection-gated tabs.
 */
@Composable
fun LandscapeNavigationRail(
    visibleTabs: List<Tab>,
    activeTab: Tab,
    connectionState: ConnectionState,
    deviceName: String?,
    reconnectAttempt: Int,
    onRetryClick: () -> Unit,
    onTabClick: (Tab) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            ConnectionChip(
                state = connectionState,
                deviceName = deviceName,
                reconnectAttempt = reconnectAttempt,
                onRetryClick = onRetryClick,
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
                icon = { TabIcon(tab) },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(
                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                ),
                alwaysShowLabel = false
            )
        }
    }
}
