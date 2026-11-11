package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemtronicring.p4e7a91c3.data.local.PreferencesSessionStore
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.LoginPanel
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.RingOnHand
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.TextDrafts
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import java.io.File

const val LOGIN_WINDOW_ID = "LoginWindow"
private const val LOGIN_WINDOW_TAG = "login"

/**
 * One view model for the whole app. The stage and the system windows are separate containers,
 * each with its own owner, so they share it through this.
 */
private object AppViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
private fun sharedViewModel(): RingWearViewModel {
    val context = LocalContext.current.applicationContext
    val modelsDir = File(context.filesDir, "models")
    return viewModel(
        viewModelStoreOwner = AppViewModels,
        factory = RingWearViewModelFactory(modelsDir, PreferencesSessionStore(context)),
    )
}

@Composable
fun RingWearScreen() {
    val vm = sharedViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    // Signing in happens in a system window: it can be moved by its handle, and typing works.
    LaunchedEffect(state.phase == RingPhase.Login) {
        if (state.phase == RingPhase.Login) navigator.openWindowContainer(LOGIN_WINDOW_ID, LOGIN_WINDOW_TAG)
    }
    RingWearContent(state = state, onEvent = vm::onEvent)
}

/** The sign-in window. Closes itself once the user has moved on. */
@Composable
fun LoginWindow() {
    val vm = sharedViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    val drafts = remember { TextDrafts() }
    LaunchedEffect(state.phase) {
        if (state.phase != RingPhase.Login) navigator.closeWindowContainer()
    }
    // The panel is the window: no system backing behind it (see Main.kt).
    LoginPanel(state.login, drafts, vm::onEvent, Modifier.fillMaxSize())
}

@Composable
internal fun RingWearContent(state: RingWearUiState, onEvent: (RingWearEvent) -> Unit) {
    RingOnHand(state = state, onEvent = onEvent)
}
