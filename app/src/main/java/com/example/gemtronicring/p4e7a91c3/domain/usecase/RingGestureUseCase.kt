package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import kotlin.math.abs
import kotlin.math.asin

/**
 * The real ring's gestures, read from hand tracking instead of an accelerometer.
 *
 * Ported from firmware/core/menu_gesture.h (signed 2026-09-27, tuned and signed 2026-10-04). The
 * tip rule and its timings are the firmware's own numbers. The click cannot be: the firmware
 * measures a spike in g, and hand tracking only gives positions. [DIP_SPEED_M_S] and
 * [STOP_RATIO] describe the same motion, a sharp dip that stops, and were not measured.
 */
class RingGestureUseCase {
    private var lastTimeMs = 0L
    private var lastHeightM = 0f
    private var speedMS = 0f

    private var tipRestDeg: Float? = null
    private var lastTipDeg = 0f
    private var armed = true
    private var holdSinceMs = 0L
    private var holdSide = 0
    private var firedAtMs = 0L
    private var tipQuietUntilMs = 0L

    private var peakDipMS = 0f
    private var dipStartMs = 0L
    private var waitingForDipToEnd = false
    private var clickQuietUntilMs = 0L
    private var clickPending = false
    private var clickAtMs = 0L

    /** Forget the resting pose and any click in flight, as the firmware does between uses. */
    fun reset() {
        lastTimeMs = 0L
        speedMS = 0f
        tipRestDeg = null
        armed = true
        holdSinceMs = 0L
        holdSide = 0
        tipQuietUntilMs = 0L
        peakDipMS = 0f
        waitingForDipToEnd = false
        clickQuietUntilMs = 0L
        clickPending = false
    }

    fun update(sample: RingSample): RingGesture? {
        val elapsedMs = sample.timeMs - lastTimeMs
        val first = lastTimeMs == 0L
        if (!first && elapsedMs <= 0L) return null
        val instant = if (first) 0f else (sample.heightM - lastHeightM) * MS_PER_S / elapsedMs
        lastTimeMs = sample.timeMs
        lastHeightM = sample.heightM
        if (abs(instant) > TRACKING_JUMP_M_S) {
            // No hand moves this fast: tracking lost the hand and found it somewhere else.
            peakDipMS = 0f
            speedMS = 0f
            return pendingConfirm(sample.timeMs)
        }
        speedMS += (instant - speedMS) * SPEED_SMOOTHING

        return updateClick(sample.timeMs) ?: updateTip(sample, elapsedMs) ?: pendingConfirm(sample.timeMs)
    }

    private fun pendingConfirm(nowMs: Long): RingGesture? {
        // A second dip already under way decides this click; wait for it to finish.
        if (!clickPending || peakDipMS > 0f || nowMs - clickAtMs < DOUBLE_CLICK_WAIT_MS) return null
        clickPending = false
        return RingGesture.Confirm
    }

    private fun updateClick(nowMs: Long): RingGesture? {
        val dip = -speedMS
        if (nowMs < clickQuietUntilMs) return null
        if (waitingForDipToEnd) {
            if (dip < DIP_SPEED_M_S * STOP_RATIO) waitingForDipToEnd = false
            return null
        }
        if (peakDipMS == 0f) {
            if (dip >= DIP_SPEED_M_S) {
                peakDipMS = dip
                dipStartMs = nowMs
            }
            return null
        }
        if (dip > peakDipMS) peakDipMS = dip
        if (nowMs - dipStartMs > DIP_WINDOW_MS) {
            // The hand is being lowered, not clicked. Nothing counts until it has stopped.
            peakDipMS = 0f
            waitingForDipToEnd = true
            return null
        }
        if (dip > peakDipMS * STOP_RATIO) return null

        peakDipMS = 0f
        clickQuietUntilMs = nowMs + NEXT_DIP_GAP_MS
        tipQuietUntilMs = nowMs + CLICK_QUIET_MS
        holdSinceMs = 0L
        if (clickPending) {
            clickPending = false
            return RingGesture.Back
        }
        clickPending = true
        clickAtMs = nowMs
        return null
    }

    private fun updateTip(sample: RingSample, elapsedMs: Long): RingGesture? {
        val nowMs = sample.timeMs
        // The ring reads tilt only while it is held still; a moving hand is not a tip.
        if (abs(speedMS) > MOVING_SPEED_M_S) {
            holdSinceMs = 0L
            return null
        }
        val degrees = Math.toDegrees(asin(sample.rightAxisUp.coerceIn(-1f, 1f).toDouble())).toFloat()
        val rest = tipRestDeg ?: degrees.also { tipRestDeg = it }
        // Right side down reads negative, and is the positive tip.
        val delta = rest - degrees
        lastTipDeg = delta
        val size = abs(delta)

        if (!armed) {
            if (size <= EXIT_DEG) {
                armed = true
            } else if (nowMs - firedAtMs >= RESETTLE_MS) {
                // Held tipped: the hand has settled, and this pose is the new rest.
                tipRestDeg = degrees
                armed = true
            }
            return null
        }
        if (size < ENTER_DEG) {
            holdSinceMs = 0L
            if (size <= EXIT_DEG) {
                val follow = (elapsedMs.toFloat() / REST_FOLLOW_MS).coerceIn(0f, 1f)
                tipRestDeg = rest + (degrees - rest) * follow
            }
            return null
        }

        val side = if (delta > 0f) 1 else -1
        if (holdSinceMs == 0L || holdSide != side) {
            holdSinceMs = nowMs
            holdSide = side
            return null
        }
        if (nowMs - holdSinceMs < HOLD_MS || nowMs < tipQuietUntilMs) return null

        armed = false
        firedAtMs = nowMs
        holdSinceMs = 0L
        return if (side > 0) RingGesture.Next else RingGesture.Previous
    }

    private companion object {
        const val MS_PER_S = 1000f

        // Firmware values: MenuGestureTuning and kResettleMs.
        const val ENTER_DEG = 25f
        const val EXIT_DEG = 10f
        const val HOLD_MS = 80L
        const val DOUBLE_CLICK_WAIT_MS = 450L
        const val CLICK_QUIET_MS = 300L
        const val RESETTLE_MS = 1500L
        const val REST_FOLLOW_MS = 2000f

        // Hand-tracking stand-ins for the firmware's click and motion gates. Not measured.
        // The firmware waits 250 ms for the sensor to stop ringing. Positions do not ring, and
        // that wait swallows the second click at the emulator's 20 samples a second.
        const val NEXT_DIP_GAP_MS = 120L
        const val DIP_SPEED_M_S = 0.35f
        const val STOP_RATIO = 0.35f
        const val DIP_WINDOW_MS = 350L
        const val MOVING_SPEED_M_S = 1.5f
        const val TRACKING_JUMP_M_S = 4f
        const val SPEED_SMOOTHING = 0.5f
    }
}
