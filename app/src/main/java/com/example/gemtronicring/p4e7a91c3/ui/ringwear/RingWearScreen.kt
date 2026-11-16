package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemtronicring.p4e7a91c3.data.local.PreferencesSessionStore
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.LaunchIntro
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.LaunchIntroState
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.RingOnHand
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.TextDrafts
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.WindowPanels
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import java.io.File

const val MAIN_WINDOW_ID = "MainWindow"
private const val MAIN_WINDOW_TAG = "main"

/**
 * One view model for the whole app. The stage and the system window are separate containers,
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

/** The stage: the hand and the ring. Opens the window that holds the panels. */
@Composable
fun RingWearScreen() {
    val vm = sharedViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    // Opened at every step, and whenever the app comes back to the front (tapping its icon),
    // so a window the user closed comes back. The tag keeps it to one.
    // Not until the opening animation has finished: the window would float in front of it.
    val introDone = LaunchIntroState.done
    LaunchedEffect(state.phase, introDone) {
        if (introDone) navigator.openWindowContainer(MAIN_WINDOW_ID, MAIN_WINDOW_TAG)
    }
    LifecycleResumeEffect(introDone) {
        if (introDone) navigator.openWindowContainer(MAIN_WINDOW_ID, MAIN_WINDOW_TAG)
        onPauseOrDispose {}
    }
    DisposableEffect(Unit) {
        StageLife.open = true
        onDispose { StageLife.open = false }
    }
    Box(Modifier.fillMaxSize()) {
        RingWearContent(state = state, onEvent = vm::onEvent)
        if (!introDone) LaunchIntro(onDone = { LaunchIntroState.done = true })
    }
}

/** Whether the stage (hand and ring) is showing, for the window to reopen it when it is not. */
private object StageLife {
    var open by mutableStateOf(false)
}

/**
 * The app's one system window: the user moves it by its handle, and typing works in it
 * (it did not in panels hung in the stage).
 */
@Composable
fun MainWindow() {
    val vm = sharedViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val drafts = remember { TextDrafts() }
    val navigator = LocalSpatialNavigator.current
    // A new drawing gets a new name; a password is not kept once signed in.
    LaunchedEffect(state.phase) {
        if (state.phase == RingPhase.Sketching) drafts.pieceName = ""
        if (state.user != null) drafts.loginPassword = ""
    }
    // The hand and the ring live in the stage. Going to the PICO home screen (to start a screen
    // recording, say) can close it while this window stays, and then no hand is tracked. So the
    // steps that need the ring open it again.
    // Only when the stage is really gone: opening it moves focus away from this window and back,
    // so reopening on every return to the front looped until the system killed the app.
    val needsStage = state.phase in STAGE_PHASES
    val stageOpen = StageLife.open
    LaunchedEffect(needsStage, stageOpen) { if (needsStage && !stageOpen) reopenStage(navigator) }
    // A fixed window, big enough for the largest panel, with the panel in the middle. The window
    // has no backing, so the space around a small panel is see-through. (Resizing it to each
    // panel left the lower buttons outside the part of the window that takes clicks.)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WindowPanels(state, drafts, vm::onEvent)
    }
}

private const val STAGE_ID = "RingStage"
private val STAGE_PHASES =
    setOf(RingPhase.Painting, RingPhase.FingerChoosing, RingPhase.HandSearching, RingPhase.RingWorn)

private suspend fun reopenStage(navigator: SpatialNavigator) {
    val result = navigator.openStage(STAGE_ID, StageStyle.Mixed)
    Log.i("SketchOn", "stage opened for the ring: $result")
}

@Composable
internal fun RingWearContent(state: RingWearUiState, onEvent: (RingWearEvent) -> Unit) {
    RingOnHand(state = state, onEvent = onEvent)
}
