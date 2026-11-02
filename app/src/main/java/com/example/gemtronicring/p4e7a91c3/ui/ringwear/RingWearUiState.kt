package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.model.WornFinger

data class RingWearUiState(
    val phase: RingPhase = RingPhase.FingerChoosing,
    val finger: WornFinger = WornFinger.Index,
    val frames: List<String> = emptyList(),
    val activeFrame: String? = null,
)

sealed interface RingWearEvent {
    data class SelectFinger(val finger: WornFinger) : RingWearEvent

    data object ConfirmFinger : RingWearEvent

    data object ChangeFinger : RingWearEvent

    data class HandMoved(val sample: RingSample) : RingWearEvent

    /** The buttons in the control panel, which do what the ring's gestures do. */
    data class MenuInput(val gesture: RingGesture) : RingWearEvent

    data object HandLost : RingWearEvent

    data object TrackingDenied : RingWearEvent
}
