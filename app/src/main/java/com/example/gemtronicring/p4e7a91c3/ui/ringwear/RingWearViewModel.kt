package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemtronicring.p4e7a91c3.data.local.SessionStore
import com.example.gemtronicring.p4e7a91c3.data.remote.JobStatus
import com.example.gemtronicring.p4e7a91c3.data.remote.NotLoggedInException
import com.example.gemtronicring.p4e7a91c3.data.remote.Session
import com.example.gemtronicring.p4e7a91c3.data.remote.SketchOnServer
import com.example.gemtronicring.p4e7a91c3.domain.model.MenuCursor
import com.example.gemtronicring.p4e7a91c3.domain.model.GalleryPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.Material
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingMenu
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.usecase.NavigateMenuUseCase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.RingGestureUseCase
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RingWearViewModel(
    private val menu: RingMenu,
    private val gestures: RingGestureUseCase,
    private val navigate: NavigateMenuUseCase,
    private val server: SketchOnServer,
    private val modelsDir: File,
    private val sessions: SessionStore,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
) : ViewModel() {
    private var cursor = MenuCursor(menu.root)

    private val _state =
        MutableStateFlow(
            RingWearUiState(frames = menu.frames, activeFrame = navigate.frame(menu, cursor))
        )
    val state: StateFlow<RingWearUiState> = _state.asStateFlow()

    // Live rendering: one request at a time; while one runs, only the newest drawing waits.
    private var latestSketch: String? = null
    private var waitingSketch: String? = null
    private var renderJob: Job? = null
    private var seed = Random.nextInt(0, Int.MAX_VALUE)
    private var making3d: Job? = null
    // The 3D job runs while the user names the piece; these join the two.
    private var jobId: String? = null
    private var pendingName: String? = null
    private var madePiece: Pair<RingPiece, Boolean>? = null
    private var named = false

    init {
        val saved = sessions.load()
        if (saved == null) {
            _state.update { it.copy(phase = RingPhase.Login) }
        } else {
            server.token = saved.token
            _state.update { it.copy(user = saved.name) }
            // Check the saved login is still good; keep it if the server cannot be reached.
            viewModelScope.launch {
                try {
                    server.me()
                } catch (e: NotLoggedInException) {
                    signedOut(e.message)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun onEvent(event: RingWearEvent) {
        when (event) {
            is RingWearEvent.LogIn -> authenticate { server.logIn(event.name, event.password) }
            is RingWearEvent.SignUp -> authenticate { server.signUp(event.name, event.password) }
            RingWearEvent.LogOut -> {
                viewModelScope.launch { runCatching { server.logOut() } }
                signedOut(null)
            }
            RingWearEvent.OpenLogin -> _state.update { it.copy(phase = RingPhase.Login, login = LoginUiState()) }
            RingWearEvent.StartSketch -> {
                seed = Random.nextInt(0, Int.MAX_VALUE)
                _state.update { it.copy(phase = RingPhase.Sketching) }
            }
            RingWearEvent.OpenGallery -> openGallery()
            is RingWearEvent.WearPiece -> wear(event.piece)
            RingWearEvent.ChooseGemtronic -> {
                _state.update { it.copy(piece = RingPiece.Gemtronic, paintable = false, paints = emptyMap()) }
                enter(RingPhase.FingerChoosing)
            }
            RingWearEvent.GoHome -> {
                making3d?.cancel()
                enter(RingPhase.Home)
            }
            is RingWearEvent.SketchChanged -> onSketchChanged(event.sketchDataUri)
            is RingWearEvent.ChooseMaterial -> rerender { it.copy(material = event.material) }
            // One colour for the whole ring for now; colouring by part comes later.
            is RingWearEvent.PaintRing ->
                _state.update {
                    if (it.paintable) it.copy(paints = (0 until it.parts).associateWith { event.color }) else it
                }
            is RingWearEvent.ChoosePaintPart ->
                _state.update { it.copy(paintPart = event.part.coerceIn(0, it.parts - 1)) }
            is RingWearEvent.TurnRing ->
                _state.update {
                    it.copy(
                        turnYaw = (it.turnYaw + event.yaw) % 360f,
                        turnPitch = (it.turnPitch + event.pitch).coerceIn(-80f, 80f),
                    )
                }
            RingWearEvent.OpenPainting -> if (_state.value.paintable) enter(RingPhase.Painting)
            RingWearEvent.FinishPainting -> enter(RingPhase.FingerChoosing)
            is RingWearEvent.RingLoaded ->
                _state.update { it.copy(parts = event.parts.coerceAtLeast(1), paintPart = 0) }
            is RingWearEvent.ChooseStyle -> rerender { it.copy(style = event.style) }
            is RingWearEvent.ChooseView -> rerender { it.copy(view = event.view) }
            RingWearEvent.ToggleSmooth ->
                _state.update { it.copy(sketch = it.sketch.copy(smooth = !it.sketch.smooth)) }
            RingWearEvent.ToggleShapes ->
                _state.update { it.copy(sketch = it.sketch.copy(shapes = !it.sketch.shapes)) }
            RingWearEvent.Make3d -> make3d()
            is RingWearEvent.ConfirmMake3d -> confirmName(event.name)
            RingWearEvent.Cancel3d -> {
                making3d?.cancel()
                _state.update { it.copy(phase = RingPhase.Sketching) }
            }
            is RingWearEvent.SelectFinger -> _state.update { it.copy(finger = event.finger) }
            RingWearEvent.ConfirmFinger -> enter(RingPhase.HandSearching)
            RingWearEvent.ChangeFinger -> enter(RingPhase.FingerChoosing)
            is RingWearEvent.HandMoved -> onHandMoved(event.sample)
            is RingWearEvent.MenuInput -> onMenuInput(event.gesture)
            RingWearEvent.HandLost ->
                if (_state.value.phase == RingPhase.RingWorn) enter(RingPhase.HandSearching)
            RingWearEvent.TrackingDenied ->
                _state.update { it.copy(phase = RingPhase.TrackingUnavailable) }
        }
    }

    /** A new material, style or view changes the render, so the drawing is rendered again. */
    private fun rerender(change: (SketchUiState) -> SketchUiState) {
        _state.update { it.copy(sketch = change(it.sketch)) }
        latestSketch?.let(::requestRender)
    }

    private fun openGallery() {
        _state.update {
            it.copy(phase = RingPhase.Gallery, gallery = it.gallery.copy(loading = true, error = null))
        }
        viewModelScope.launch {
            try {
                val pieces = server.library()
                _state.update { it.copy(gallery = it.gallery.copy(pieces = pieces, loading = false)) }
                pieces.take(GALLERY_SIZE).forEach { piece ->
                    val url = piece.pictureUrl ?: return@forEach
                    if (piece.id in _state.value.gallery.pictures) return@forEach
                    val bytes = runCatching { server.bytes(url) }.getOrNull() ?: return@forEach
                    _state.update {
                        it.copy(gallery = it.gallery.copy(pictures = it.gallery.pictures + (piece.id to bytes)))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(gallery = it.gallery.copy(loading = false, error = reasonOf(e))) }
            }
        }
    }

    private fun wear(piece: GalleryPiece) {
        if (_state.value.gallery.opening != null) return
        _state.update { it.copy(gallery = it.gallery.copy(opening = piece.id, error = null)) }
        viewModelScope.launch {
            try {
                val file = modelFile(piece.id)
                if (!file.exists()) server.download(piece.modelUrl, file)
                _state.update {
                    it.copy(
                        piece = RingPiece.Generated(file.absolutePath),
                        paintable = Material.byId(piece.material)?.metal == false,
                        paints = emptyMap(),
                        turnYaw = 0f,
                        turnPitch = 0f,
                        gallery = it.gallery.copy(opening = null),
                    )
                }
                enter(if (_state.value.paintable) RingPhase.Painting else RingPhase.FingerChoosing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(gallery = it.gallery.copy(opening = null, error = reasonOf(e))) }
            }
        }
    }

    // ".v2": models posed with a node matrix (which PICO ignores) are not reused.
    private fun modelFile(id: String): File {
        modelsDir.mkdirs()
        return File(modelsDir, "$id.v2.glb")
    }

    private fun authenticate(call: suspend () -> Session) {
        if (_state.value.login.busy) return
        _state.update { it.copy(login = LoginUiState(busy = true)) }
        viewModelScope.launch {
            try {
                val session = call()
                server.token = session.token
                sessions.save(session)
                _state.update { it.copy(user = session.name, login = LoginUiState(), phase = RingPhase.Home) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(login = LoginUiState(error = reasonOf(e))) }
            }
        }
    }

    /** Forgets the login and shows the login panel, with [reason] if there is one. */
    private fun signedOut(reason: String?) {
        server.token = null
        sessions.clear()
        making3d?.cancel()
        _state.update { it.copy(user = null, phase = RingPhase.Login, login = LoginUiState(error = reason)) }
    }

    private fun onSketchChanged(sketch: String?) {
        latestSketch = sketch
        if (sketch == null) {
            waitingSketch = null
            _state.update { it.copy(sketch = it.sketch.copy(render = null, error = null)) }
            return
        }
        requestRender(sketch)
    }

    private fun requestRender(sketch: String) {
        if (renderJob?.isActive == true) {
            waitingSketch = sketch
            return
        }
        renderJob =
            viewModelScope.launch {
                var next: String? = sketch
                while (next != null) {
                    renderOnce(next)
                    next = waitingSketch
                    waitingSketch = null
                }
            }
    }

    private suspend fun renderOnce(sketch: String) {
        _state.update { it.copy(sketch = it.sketch.copy(rendering = true)) }
        val started = System.currentTimeMillis()
        val options = _state.value.sketch
        try {
            val image = server.render(
                    sketch,
                    options.material.id,
                    options.style.id,
                    options.view.id,
                    seed,
                )
            // A cleared drawing should not get a late render back.
            if (latestSketch == null) return
            _state.update {
                it.copy(
                    sketch =
                        it.sketch.copy(
                            render = image,
                            lastRenderMs = System.currentTimeMillis() - started,
                            error = null,
                        )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotLoggedInException) {
            signedOut(e.message)
        } catch (e: Exception) {
            _state.update { it.copy(sketch = it.sketch.copy(error = reasonOf(e))) }
        } finally {
            _state.update { it.copy(sketch = it.sketch.copy(rendering = false)) }
        }
    }

    /**
     * Starts the 3D job at once and asks for a name while it runs, so naming fills the wait.
     * The ring is worn when both are done: the model is ready and the name is confirmed.
     */
    private fun make3d() {
        val render = _state.value.sketch.render ?: return
        making3d?.cancel()
        jobId = null
        madePiece = null
        named = false
        _state.update {
            it.copy(phase = RingPhase.Naming, sketch = it.sketch.copy(making3dSeconds = 0, error = null))
        }
        making3d =
            viewModelScope.launch {
                val started = System.currentTimeMillis()
                try {
                    val material = _state.value.sketch.material
                    val id = server.start3d(render, "", material.id)
                    jobId = id
                    pendingName?.let { server.nameJob(id, it) }
                    var status: JobStatus = JobStatus.Running
                    while (status == JobStatus.Running) {
                        delay(pollIntervalMs)
                        val seconds = ((System.currentTimeMillis() - started) / 1000).toInt()
                        _state.update { it.copy(sketch = it.sketch.copy(making3dSeconds = seconds)) }
                        status = server.job(id)
                    }
                    when (val result = status) {
                        is JobStatus.Done -> {
                            val file = modelFile(id)
                            if (!file.exists()) server.download(result.modelUrl, file)
                            madePiece = RingPiece.Generated(file.absolutePath) to !material.metal
                            wearIfReady()
                        }
                        is JobStatus.Failed -> fail3d(result.reason)
                        JobStatus.Running -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NotLoggedInException) {
                    signedOut(e.message)
                } catch (e: Exception) {
                    fail3d(reasonOf(e))
                }
            }
    }

    private fun confirmName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _state.value.phase != RingPhase.Naming) return
        named = true
        val id = jobId
        if (id == null) {
            // The job has not started yet; it sends the name once it has an id.
            pendingName = trimmed
        } else {
            viewModelScope.launch { runCatching { server.nameJob(id, trimmed) } }
        }
        if (!wearIfReady()) _state.update { it.copy(phase = RingPhase.Making3D) }
    }

    /** Wears the made ring once it exists and has a name. Returns true if it did. */
    private fun wearIfReady(): Boolean {
        val (piece, paintable) = madePiece ?: return false
        if (!named) return false
        pendingName = null
        _state.update {
            it.copy(piece = piece, paintable = paintable, paints = emptyMap(), turnYaw = 0f, turnPitch = 0f)
        }
        // Clay and resin are coloured first, held still in front; other rings go on at once.
        enter(if (paintable) RingPhase.Painting else RingPhase.FingerChoosing)
        return true
    }

    private fun fail3d(reason: String) {
        _state.update { it.copy(phase = RingPhase.Sketching, sketch = it.sketch.copy(error = reason)) }
    }

    private fun enter(phase: RingPhase) {
        gestures.reset()
        _state.update { it.copy(phase = phase) }
    }

    private fun onHandMoved(sample: RingSample) {
        when (_state.value.phase) {
            RingPhase.HandSearching -> _state.update { it.copy(phase = RingPhase.RingWorn) }
            RingPhase.RingWorn -> Unit
            else -> return
        }
        // Only the Gemtronic ring has a menu to drive.
        if (_state.value.piece == RingPiece.Gemtronic) gestures.update(sample)?.let(::moveMenu)
    }

    private fun onMenuInput(gesture: RingGesture) {
        val phase = _state.value.phase
        if (phase == RingPhase.RingWorn || phase == RingPhase.HandSearching) moveMenu(gesture)
    }

    private fun moveMenu(gesture: RingGesture) {
        cursor = navigate.move(menu, cursor, gesture)
        _state.update { it.copy(activeFrame = navigate.frame(menu, cursor)) }
    }

    private fun reasonOf(e: Exception): String =
        when (e) {
            is java.net.ConnectException,
            is java.net.SocketTimeoutException -> "Can't reach the render server."
            else -> e.message ?: "Something went wrong."
        }

    companion object {
        /** Pieces shown in the gallery, newest first. */
        const val GALLERY_SIZE = 8
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
