package com.studiocamera.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.android.ui.RootContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rootComponent = RootComponent(
            componentContext = defaultComponentContext()
        )

        setContent {
            RootContent(component = rootComponent)
        }
    }
}
