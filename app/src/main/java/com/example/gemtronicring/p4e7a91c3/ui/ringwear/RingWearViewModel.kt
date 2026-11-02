package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.lifecycle.ViewModel
import com.example.gemtronicring.p4e7a91c3.domain.model.JointRotation
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPage
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.TiltPageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RingWearViewModel(pages: List<RingPage>, private val tiltPage: TiltPageUseCase) :
    ViewModel() {
    private val _state = MutableStateFlow(RingWearUiState(pages = pages))
    val state: StateFlow<RingWearUiState> = _state.asStateFlow()

    private var neutral: JointRotation? = null
    private var armed = true

    fun onEvent(event: RingWearEvent) {
        when (event) {
            is RingWearEvent.HandMoved -> onHandMoved(event)
            RingWearEvent.HandLost -> onHandLost()
            RingWearEvent.TrackingDenied ->
                _state.update { it.copy(phase = RingPhase.TrackingUnavailable) }
        }
    }

    private fun onHandMoved(event: RingWearEvent.HandMoved) {
        if (_state.value.phase == RingPhase.TrackingUnavailable) return
        val start = neutral
        if (start == null) {
            // The pose the hand arrives in counts as neutral.
            neutral = event.rotation
            armed = true
            _state.update { it.copy(phase = RingPhase.RingWorn) }
            return
        }
        val roll = tiltPage.rollDegrees(start, event.rotation)
        // Mirror the left hand so the same physical tip means the same direction.
        val step = tiltPage.step(if (event.isLeftHand) -roll else roll, armed)
        armed = step.armed
        if (step.pageDelta != 0) {
            _state.update {
                it.copy(
                    activePageIndex =
                        tiltPage.movedPage(it.activePageIndex, step.pageDelta, it.pages.size)
                )
            }
        }
    }

    private fun onHandLost() {
        if (_state.value.phase == RingPhase.TrackingUnavailable) return
        neutral = null
        _state.update { it.copy(phase = RingPhase.HandSearching) }
    }
}
