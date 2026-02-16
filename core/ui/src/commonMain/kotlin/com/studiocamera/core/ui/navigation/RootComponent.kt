package com.studiocamera.core.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RootComponent(
    componentContext: ComponentContext
) : ComponentContext by componentContext, KoinComponent {

    private val connectionStateManager: ConnectionStateManager by inject()

    val connectionState: StateFlow<ConnectionState> = connectionStateManager.state
    val connectedDevice = connectionStateManager.connectedDevice

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Pair,
        handleBackButton = true,
        childFactory = ::createChild
    )

    fun navigateToTab(tab: Tab) {
        // Connection gating: Camera and Media require connection
        if (tab.requiresConnection && !connectionStateManager.isConnected) {
            // Don't navigate - the UI will show a tooltip
            return
        }

        val config = when (tab) {
            Tab.Pair -> Config.Pair
            Tab.Discover -> Config.Discover
            Tab.Camera -> Config.Camera
            Tab.Media -> Config.Media
            Tab.Mock -> Config.Mock
        }
        navigation.bringToFront(config)
    }

    fun navigateToPair() {
        navigation.bringToFront(Config.Pair)
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child {
        return when (config) {
            Config.Pair -> Child.Pair
            Config.Discover -> Child.Discover
            Config.Camera -> Child.Camera
            Config.Media -> Child.Media
            Config.Mock -> Child.Mock
        }
    }

    @Serializable
    sealed class Config {
        @Serializable data object Pair : Config()
        @Serializable data object Discover : Config()
        @Serializable data object Camera : Config()
        @Serializable data object Media : Config()
        @Serializable data object Mock : Config()
    }

    sealed class Child {
        data object Pair : Child()
        data object Discover : Child()
        data object Camera : Child()
        data object Media : Child()
        data object Mock : Child()
    }
}
