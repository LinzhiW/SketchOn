package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.domain.model.JointRotation
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPage
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase

data class RingWearUiState(
    val phase: RingPhase = RingPhase.HandSearching,
    val pages: List<RingPage> = emptyList(),
    val activePageIndex: Int = 0,
)

sealed interface RingWearEvent {
    data class HandMoved(val rotation: JointRotation, val isLeftHand: Boolean) : RingWearEvent

    data object HandLost : RingWearEvent

    data object TrackingDenied : RingWearEvent
}
