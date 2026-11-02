package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.JointRotation
import com.example.gemtronicring.p4e7a91c3.domain.model.TiltStep
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Turns finger roll into page changes. A roll past [triggerDeg] moves one page, and the finger must
 * come back within [resetDeg] of neutral before it can move another.
 */
class TiltPageUseCase(
    private val triggerDeg: Float = TRIGGER_DEG,
    private val resetDeg: Float = RESET_DEG,
) {
    /** Roll of [current] about the finger's own axis (joint Z), measured from [neutral]. */
    fun rollDegrees(neutral: JointRotation, current: JointRotation): Float {
        // neutral⁻¹ · current, keeping only the z and w parts that the twist needs.
        val z =
            neutral.w * current.z - neutral.z * current.w - neutral.x * current.y +
                neutral.y * current.x
        val w =
            neutral.w * current.w + neutral.x * current.x + neutral.y * current.y +
                neutral.z * current.z
        val degrees = Math.toDegrees(2.0 * atan2(z, w).toDouble()).toFloat()
        return when {
            degrees > 180f -> degrees - 360f
            degrees < -180f -> degrees + 360f
            else -> degrees
        }
    }

    fun step(rollDeg: Float, armed: Boolean): TiltStep =
        when {
            armed && rollDeg >= triggerDeg -> TiltStep(armed = false, pageDelta = 1)
            armed && rollDeg <= -triggerDeg -> TiltStep(armed = false, pageDelta = -1)
            !armed && abs(rollDeg) <= resetDeg -> TiltStep(armed = true, pageDelta = 0)
            else -> TiltStep(armed = armed, pageDelta = 0)
        }

    /** Wraps around at both ends so the ring keeps cycling. */
    fun movedPage(index: Int, delta: Int, pageCount: Int): Int =
        if (pageCount == 0) 0 else (index + delta + pageCount) % pageCount

    private companion object {
        const val TRIGGER_DEG = 25f
        const val RESET_DEG = 10f
    }
}
