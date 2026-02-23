package com.studiocamera.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.SessionError
import com.studiocamera.core.domain.model.SessionEvent
import com.studiocamera.core.domain.session.SessionManager
import kotlinx.coroutines.flow.StateFlow

data class SessionErrorState(
    val lastError: SessionError? = null,
    val reconnectAttempt: Int = 0
)

@Composable
fun rememberSessionErrorState(
    sessionManager: SessionManager,
    connectionState: ConnectionState
): State<SessionErrorState> {
    val errorState = remember { mutableStateOf(SessionErrorState()) }
    val attemptCounter = remember { mutableIntStateOf(0) }

    // Collect error events from SessionManager
    LaunchedEffect(sessionManager) {
        sessionManager.events.collect { event ->
            when (event) {
                is SessionEvent.Error -> {
                    errorState.value = errorState.value.copy(lastError = event.error)
                }
                is SessionEvent.StateChanged -> {
                    if (event.state == ConnectionState.Reconnecting) {
                        attemptCounter.intValue++
                        errorState.value = errorState.value.copy(
                            reconnectAttempt = attemptCounter.intValue
                        )
                    }
                }
                else -> {}
            }
        }
    }

    // Clear error when reconnected
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.Connected) {
            errorState.value = SessionErrorState()
            attemptCounter.intValue = 0
        }
    }

    return errorState
}
