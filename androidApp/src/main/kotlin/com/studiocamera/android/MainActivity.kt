package com.studiocamera.android

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.arkivanov.decompose.defaultComponentContext
import com.studiocamera.android.billing.GoogleBillingRepository
import com.studiocamera.android.nfc.NfcBridge
import com.studiocamera.core.domain.repository.SettingsRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.ui.navigation.RootComponent
import com.studiocamera.android.ui.RootContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.getKoin

class MainActivity : ComponentActivity() {

    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private lateinit var nfcBridge: NfcBridge
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcBridge = NfcBridge(this)

        // Initialize billing
        val billingRepository = getKoin().get<GoogleBillingRepository>()
        billingRepository.setActivity(this)
        billingRepository.initialize()

        // Wire keep-screen-on setting
        val settingsRepository = getKoin().get<SettingsRepository>()
        settingsRepository.settings
            .map { it.keepScreenOn }
            .onEach { keepOn ->
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            .launchIn(activityScope)

        val rootComponent = RootComponent(
            componentContext = defaultComponentContext()
        )

        setupLifecycleObserver()

        setContent {
            RootContent(
                component = rootComponent,
                nfcBridge = nfcBridge
            )
        }

        // Handle NFC intent that launched the activity
        if (intent != null) {
            nfcBridge.handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable NFC foreground dispatch so our app takes priority over
        // any AAR (Android Application Record) targeting other apps like
        // Sony's Imaging Edge Mobile
        nfcBridge.enableDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcBridge.disableDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nfcBridge.handleIntent(intent)
    }

    private fun setupLifecycleObserver() {
        lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // App came to foreground
                try {
                    val sessionManager = getKoin().get<SessionManager>()
                    sessionManager.onForeground()
                } catch (_: Exception) {
                    // SessionManager not yet initialized
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                // App went to background
                try {
                    val sessionManager = getKoin().get<SessionManager>()
                    sessionManager.onBackground()
                } catch (_: Exception) {
                    // SessionManager not yet initialized
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver!!)
    }

    override fun onDestroy() {
        activityScope.cancel()
        try {
            getKoin().get<GoogleBillingRepository>().destroy()
        } catch (_: Exception) {}
        lifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
        }
        super.onDestroy()
    }
}
