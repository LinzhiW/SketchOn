package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.data.remote.JobStatus
import com.example.gemtronicring.p4e7a91c3.data.remote.SketchOnServer
import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingMenuRepository
import com.example.gemtronicring.p4e7a91c3.domain.model.GalleryPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.Material
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.model.WornFinger
import com.example.gemtronicring.p4e7a91c3.domain.usecase.NavigateMenuUseCase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.RingGestureUseCase
import java.io.File
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Answers instantly and records what it was asked. */
private class FakeServer : SketchOnServer {
    val renders = mutableListOf<String>()
    var jobPolls = 0

    override suspend fun render(
        sketchDataUri: String,
        material: String,
        style: String,
        view: String,
        seed: Int,
    ): String {
        renders += "$sketchDataUri/$material"
        return "data:image/jpeg;base64,render-of-$sketchDataUri"
    }

    override suspend fun library() =
        listOf(
            GalleryPiece("piece-2", "Cloud Ring", "clay", "/models/piece-2.glb", "/models/piece-2.jpg"),
            GalleryPiece("piece-1", null, null, "/models/piece-1.glb", null),
        )

    override suspend fun bytes(url: String) = url.toByteArray()

    val names = mutableListOf<String>()

    override suspend fun start3d(imageDataUri: String, name: String, material: String): String {
        names += "$name/$material"
        return "job-1"
    }

    override suspend fun job(jobId: String): JobStatus =
        if (++jobPolls < 2) JobStatus.Running else JobStatus.Done("/models/job-1.glb")

    override suspend fun download(modelUrl: String, target: File) = target.writeText("glb")
}

@OptIn(ExperimentalCoroutinesApi::class)
class RingWearViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val server = FakeServer()
    private lateinit var viewModel: RingWearViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel =
            RingWearViewModel(
                BundledRingMenuRepository().menu(),
                RingGestureUseCase(),
                NavigateMenuUseCase(),
                server,
                folder.root,
                pollIntervalMs = 10,
            )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()
    private var timeMs = 1000L

    private fun hand(tipDeg: Float, ms: Long) {
        repeat((ms / 16L).toInt()) {
            timeMs += 16L
            val up = sin(Math.toRadians(-tipDeg.toDouble())).toFloat()
            viewModel.onEvent(RingWearEvent.HandMoved(RingSample(timeMs, up, 1.2f)))
        }
    }

    private fun wearRing() {
        viewModel.onEvent(RingWearEvent.ChooseGemtronic)
        viewModel.onEvent(RingWearEvent.ConfirmFinger)
        hand(0f, 100)
    }

    @Test
    fun `starts at home with the Gemtronic ring's home frame ready`() {
        val state = viewModel.state.value
        assertEquals(RingPhase.Home, state.phase)
        assertEquals(WornFinger.Index, state.finger)
        assertEquals("idle", state.activeFrame)
        assertTrue(state.frames.size == 25)
    }

    @Test
    fun `a hand is ignored until the finger is chosen`() {
        hand(0f, 100)
        assertEquals(RingPhase.Home, viewModel.state.value.phase)
        viewModel.onEvent(RingWearEvent.ChooseGemtronic)
        hand(0f, 100)
        assertEquals(RingPhase.FingerChoosing, viewModel.state.value.phase)
    }

    @Test
    fun `a drawing is rendered, and only the newest drawing waits while one renders`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.StartSketch)
        viewModel.onEvent(RingWearEvent.SketchChanged("a"))
        viewModel.onEvent(RingWearEvent.SketchChanged("b"))
        viewModel.onEvent(RingWearEvent.SketchChanged("c"))
        advanceUntilIdle()
        assertEquals(listOf("a/gold", "c/gold"), server.renders)
        assertEquals("data:image/jpeg;base64,render-of-c", viewModel.state.value.sketch.render)
    }

    @Test
    fun `a new material renders the drawing again`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.StartSketch)
        viewModel.onEvent(RingWearEvent.SketchChanged("a"))
        advanceUntilIdle()
        viewModel.onEvent(RingWearEvent.ChooseMaterial(Material.Clay))
        advanceUntilIdle()
        assertEquals(listOf("a/gold", "a/clay"), server.renders)
    }

    @Test
    fun `the gallery lists pieces with their pictures, and wearing one downloads it`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.OpenGallery)
        advanceUntilIdle()
        val gallery = viewModel.state.value.gallery
        assertEquals(listOf("piece-2", "piece-1"), gallery.pieces.map { it.id })
        assertEquals(setOf("piece-2"), gallery.pictures.keys)
        viewModel.onEvent(RingWearEvent.WearPiece(gallery.pieces[1]))
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(RingPhase.FingerChoosing, state.phase)
        assertTrue(File((state.piece as RingPiece.Generated).path).exists())
    }

    @Test
    fun `a clay piece from the gallery can be painted, an old piece cannot`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.OpenGallery)
        advanceUntilIdle()
        val pieces = viewModel.state.value.gallery.pieces
        viewModel.onEvent(RingWearEvent.WearPiece(pieces[0]))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.paintable)
        viewModel.onEvent(RingWearEvent.PaintRing(0xFF00FF00.toInt()))
        assertEquals(0xFF00FF00.toInt(), viewModel.state.value.paint)
        viewModel.onEvent(RingWearEvent.OpenGallery)
        advanceUntilIdle()
        viewModel.onEvent(RingWearEvent.WearPiece(pieces[1]))
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.paintable)
        assertNull(viewModel.state.value.paint)
        viewModel.onEvent(RingWearEvent.PaintRing(0xFF00FF00.toInt()))
        assertNull(viewModel.state.value.paint)
    }

    @Test
    fun `clearing the drawing clears the render`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.StartSketch)
        viewModel.onEvent(RingWearEvent.SketchChanged("a"))
        advanceUntilIdle()
        viewModel.onEvent(RingWearEvent.SketchChanged(null))
        assertNull(viewModel.state.value.sketch.render)
    }

    @Test
    fun `making 3D downloads the model and goes to the finger choice`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.StartSketch)
        viewModel.onEvent(RingWearEvent.SketchChanged("a"))
        advanceUntilIdle()
        viewModel.onEvent(RingWearEvent.Make3d)
        assertEquals(RingPhase.Naming, viewModel.state.value.phase)
        viewModel.onEvent(RingWearEvent.ConfirmMake3d("  Cloud Ring "))
        assertEquals(RingPhase.Making3D, viewModel.state.value.phase)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(RingPhase.FingerChoosing, state.phase)
        val piece = state.piece as RingPiece.Generated
        assertTrue(File(piece.path).exists())
        assertEquals(listOf("Cloud Ring/gold"), server.names)
        // Gold is not painted; only clay and resin are.
        assertEquals(false, state.paintable)
    }

    @Test
    fun `naming can go back to drawing without making anything`() = runTest(dispatcher) {
        viewModel.onEvent(RingWearEvent.StartSketch)
        viewModel.onEvent(RingWearEvent.SketchChanged("a"))
        advanceUntilIdle()
        viewModel.onEvent(RingWearEvent.Make3d)
        viewModel.onEvent(RingWearEvent.Cancel3d)
        advanceUntilIdle()
        assertEquals(RingPhase.Sketching, viewModel.state.value.phase)
        assertTrue(server.names.isEmpty())
    }

    @Test
    fun `the chosen finger is kept and the ring is worn once a hand is seen`() {
        viewModel.onEvent(RingWearEvent.SelectFinger(WornFinger.Thumb))
        viewModel.onEvent(RingWearEvent.ConfirmFinger)
        assertEquals(RingPhase.HandSearching, viewModel.state.value.phase)
        hand(0f, 100)
        val state = viewModel.state.value
        assertEquals(RingPhase.RingWorn, state.phase)
        assertEquals(WornFinger.Thumb, state.finger)
    }

    @Test
    fun `a tip moves the menu one step`() {
        wearRing()
        hand(30f, 300)
        assertEquals("home-pet", viewModel.state.value.activeFrame)
    }

    @Test
    fun `losing the hand keeps the page`() {
        wearRing()
        hand(30f, 300)
        viewModel.onEvent(RingWearEvent.HandLost)
        assertEquals(RingPhase.HandSearching, viewModel.state.value.phase)
        assertEquals("home-pet", viewModel.state.value.activeFrame)
    }

    @Test
    fun `changing finger goes back to the choice and keeps the page`() {
        wearRing()
        hand(30f, 300)
        viewModel.onEvent(RingWearEvent.ChangeFinger)
        assertEquals(RingPhase.FingerChoosing, viewModel.state.value.phase)
        assertEquals("home-pet", viewModel.state.value.activeFrame)
    }

    @Test
    fun `the buttons do what the gestures do`() {
        wearRing()
        viewModel.onEvent(RingWearEvent.MenuInput(RingGesture.Next))
        assertEquals("home-pet", viewModel.state.value.activeFrame)
        viewModel.onEvent(RingWearEvent.MenuInput(RingGesture.Confirm))
        assertEquals("pet-egg", viewModel.state.value.activeFrame)
        viewModel.onEvent(RingWearEvent.MenuInput(RingGesture.Back))
        assertEquals("home-pet", viewModel.state.value.activeFrame)
        viewModel.onEvent(RingWearEvent.MenuInput(RingGesture.Previous))
        assertEquals("idle", viewModel.state.value.activeFrame)
    }

    @Test
    fun `the buttons work while no hand is seen`() {
        viewModel.onEvent(RingWearEvent.ChooseGemtronic)
        viewModel.onEvent(RingWearEvent.ConfirmFinger)
        viewModel.onEvent(RingWearEvent.MenuInput(RingGesture.Next))
        assertEquals("home-pet", viewModel.state.value.activeFrame)
    }

    @Test
    fun `the buttons do nothing during the finger choice`() {
        viewModel.onEvent(RingWearEvent.MenuInput(RingGesture.Next))
        assertEquals("idle", viewModel.state.value.activeFrame)
    }

    @Test
    fun `denied tracking stays denied`() {
        viewModel.onEvent(RingWearEvent.TrackingDenied)
        viewModel.onEvent(RingWearEvent.ConfirmFinger)
        viewModel.onEvent(RingWearEvent.TrackingDenied)
        hand(0f, 100)
        assertEquals(RingPhase.TrackingUnavailable, viewModel.state.value.phase)
    }
}
