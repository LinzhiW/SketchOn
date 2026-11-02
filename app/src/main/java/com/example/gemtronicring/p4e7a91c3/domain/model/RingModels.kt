package com.example.gemtronicring.p4e7a91c3.domain.model

/** One firmware screen shown on the ring. */
data class RingPage(val id: String, val assetPath: String)

enum class RingPhase {
    HandSearching,
    RingWorn,
    TrackingUnavailable,
}

/** A joint rotation as a plain quaternion, so the domain layer stays free of SDK types. */
data class JointRotation(val x: Float, val y: Float, val z: Float, val w: Float)

/** What one tilt reading did: whether the gesture is ready again, and how far the page moved. */
data class TiltStep(val armed: Boolean, val pageDelta: Int)
