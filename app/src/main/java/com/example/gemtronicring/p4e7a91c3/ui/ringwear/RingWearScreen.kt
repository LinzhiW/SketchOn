package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.pico.spatial.core.container.SpaceState
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
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
    // Logged to read a headset session afterwards; the window decides by the space state.
    LifecycleStartEffect(Unit) {
        Log.i("SketchOn", "stage started")
        onStopOrDispose { Log.i("SketchOn", "stage stopped") }
    }
    Box(Modifier.fillMaxSize()) {
        RingWearContent(state = state, onEvent = vm::onEvent)
        if (!introDone) LaunchIntro(onDone = { LaunchIntroState.done = true })
    }
}

private object StageLife {
    /** When the window last asked for the stage, so it never asks in a tight loop. */
    var askedAtMs = 0L
}

private const val STAGE_CHECK_MS = 1_000L
/** The stage and the window come back to the front one after the other; wait for both. */
private const val STAGE_SETTLE_MS = 2_000L
/** Opening the stage moves focus away and back; asking more often looped until the app was killed. */
private const val STAGE_REOPEN_GAP_MS = 5_000L

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
    // Only while this window is itself in front: with the whole app in the back, nothing is opened.
    val windowLife by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val windowInFront = windowLife.isAtLeast(Lifecycle.State.STARTED)
    val context = LocalContext.current
    LaunchedEffect(needsStage, windowInFront) {
        if (!needsStage || !windowInFront) return@LaunchedEffect
        // The system can send the stage to the back while this window stays (seen on a headset):
        // the app is then in shared space, where no hand is tracked and no ring is drawn. The
        // stage is still alive, so only the space state tells. Asked of the system each second.
        var missingMs = 0L
        while (true) {
            delay(STAGE_CHECK_MS)
            // Only a clear "shared space" counts; "unknown" (headset taken off, say) does not.
            val shared = context.getSelfSpaceState() == SpaceState.SHARED_SPACE
            missingMs = if (shared) missingMs + STAGE_CHECK_MS else 0L
            val sinceAsked = SystemClock.elapsedRealtime() - StageLife.askedAtMs
            if (missingMs >= STAGE_SETTLE_MS && sinceAsked >= STAGE_REOPEN_GAP_MS) {
                StageLife.askedAtMs = SystemClock.elapsedRealtime()
                missingMs = 0L
                reopenStage(navigator)
            }
        }
    }
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
