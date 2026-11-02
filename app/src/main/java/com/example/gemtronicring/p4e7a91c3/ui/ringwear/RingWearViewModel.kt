package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.lifecycle.ViewModel
import com.example.gemtronicring.p4e7a91c3.domain.model.MenuCursor
import com.example.gemtronicring.p4e7a91c3.domain.model.RingMenu
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.usecase.NavigateMenuUseCase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.RingGestureUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RingWearViewModel(
    private val menu: RingMenu,
    private val gestures: RingGestureUseCase,
    private val navigate: NavigateMenuUseCase,
) : ViewModel() {
    private var cursor = MenuCursor(menu.root)

    private val _state =
        MutableStateFlow(
            RingWearUiState(frames = menu.frames, activeFrame = navigate.frame(menu, cursor))
        )
    val state: StateFlow<RingWearUiState> = _state.asStateFlow()

    fun onEvent(event: RingWearEvent) {
        when (event) {
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

    private fun enter(phase: RingPhase) {
        gestures.reset()
        _state.update { it.copy(phase = phase) }
    }

    private fun onHandMoved(sample: RingSample) {
        when (_state.value.phase) {
            RingPhase.HandSearching -> _state.update { it.copy(phase = RingPhase.RingWorn) }
            RingPhase.RingWorn -> Unit
            RingPhase.FingerChoosing,
            RingPhase.TrackingUnavailable -> return
        }
        gestures.update(sample)?.let(::moveMenu)
    }

    private fun onMenuInput(gesture: RingGesture) {
        val phase = _state.value.phase
        if (phase == RingPhase.RingWorn || phase == RingPhase.HandSearching) moveMenu(gesture)
    }

    private fun moveMenu(gesture: RingGesture) {
        cursor = navigate.move(menu, cursor, gesture)
        _state.update { it.copy(activeFrame = navigate.frame(menu, cursor)) }
    }
}
