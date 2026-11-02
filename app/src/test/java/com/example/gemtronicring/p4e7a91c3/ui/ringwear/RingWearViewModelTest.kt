package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingPageRepository
import com.example.gemtronicring.p4e7a91c3.domain.model.JointRotation
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.TiltPageUseCase
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class RingWearViewModelTest {
    private val viewModel = RingWearViewModel(BundledRingPageRepository().pages(), TiltPageUseCase())

    private fun hand(rollDeg: Float, isLeftHand: Boolean = false): RingWearEvent {
        val half = Math.toRadians(rollDeg / 2.0)
        val rotation = JointRotation(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
        return RingWearEvent.HandMoved(rotation, isLeftHand)
    }

    @Test
    fun `starts searching for a hand with all three pages`() {
        val state = viewModel.state.value
        assertEquals(RingPhase.HandSearching, state.phase)
        assertEquals(3, state.pages.size)
        assertEquals(0, state.activePageIndex)
    }

    @Test
    fun `a seen hand wears the ring`() {
        viewModel.onEvent(hand(0f))
        assertEquals(RingPhase.RingWorn, viewModel.state.value.phase)
    }

    @Test
    fun `one tilt moves one page until the finger returns`() {
        viewModel.onEvent(hand(0f))
        viewModel.onEvent(hand(30f))
        viewModel.onEvent(hand(45f))
        assertEquals(1, viewModel.state.value.activePageIndex)
        viewModel.onEvent(hand(0f))
        viewModel.onEvent(hand(30f))
        assertEquals(2, viewModel.state.value.activePageIndex)
    }

    @Test
    fun `the left hand mirrors the direction`() {
        viewModel.onEvent(hand(0f, isLeftHand = true))
        viewModel.onEvent(hand(-30f, isLeftHand = true))
        assertEquals(1, viewModel.state.value.activePageIndex)
    }

    @Test
    fun `losing the hand keeps the page and resets neutral`() {
        viewModel.onEvent(hand(0f))
        viewModel.onEvent(hand(30f))
        viewModel.onEvent(RingWearEvent.HandLost)
        assertEquals(RingPhase.HandSearching, viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.activePageIndex)
        viewModel.onEvent(hand(60f))
        assertEquals(1, viewModel.state.value.activePageIndex)
    }

    @Test
    fun `denied tracking stays denied`() {
        viewModel.onEvent(RingWearEvent.TrackingDenied)
        viewModel.onEvent(hand(0f))
        assertEquals(RingPhase.TrackingUnavailable, viewModel.state.value.phase)
    }
}
