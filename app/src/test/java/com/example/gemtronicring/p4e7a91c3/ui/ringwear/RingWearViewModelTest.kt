package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingMenuRepository
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.model.WornFinger
import com.example.gemtronicring.p4e7a91c3.domain.usecase.NavigateMenuUseCase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.RingGestureUseCase
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingWearViewModelTest {
    private val viewModel =
        RingWearViewModel(
            BundledRingMenuRepository().menu(),
            RingGestureUseCase(),
            NavigateMenuUseCase(),
        )
    private var timeMs = 1000L

    private fun hand(tipDeg: Float, ms: Long) {
        repeat((ms / 16L).toInt()) {
            timeMs += 16L
            val up = sin(Math.toRadians(-tipDeg.toDouble())).toFloat()
            viewModel.onEvent(RingWearEvent.HandMoved(RingSample(timeMs, up, 1.2f)))
        }
    }

    private fun wearRing() {
        viewModel.onEvent(RingWearEvent.ConfirmFinger)
        hand(0f, 100)
    }

    @Test
    fun `starts on the finger choice with the home frame ready`() {
        val state = viewModel.state.value
        assertEquals(RingPhase.FingerChoosing, state.phase)
        assertEquals(WornFinger.Index, state.finger)
        assertEquals("idle", state.activeFrame)
        assertTrue(state.frames.size == 25)
    }

    @Test
    fun `a hand is ignored until the finger is chosen`() {
        hand(0f, 100)
        assertEquals(RingPhase.FingerChoosing, viewModel.state.value.phase)
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
