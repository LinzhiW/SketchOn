package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class RingGestureUseCaseTest {
    private val useCase = RingGestureUseCase()
    private var timeMs = 1000L
    private var heightM = 1.2f

    /** Feeds [ms] of 60 Hz samples at a tip angle, and returns the gestures that came out. */
    private fun hold(tipDeg: Float, ms: Long, heightSpeed: Float = 0f): List<RingGesture> {
        val out = mutableListOf<RingGesture>()
        val steps = (ms / STEP_MS).toInt()
        repeat(steps) {
            timeMs += STEP_MS
            heightM += heightSpeed * STEP_MS / 1000f
            // Right side down is a positive tip and a negative up component.
            val up = sin(Math.toRadians(-tipDeg.toDouble())).toFloat()
            useCase.update(RingSample(timeMs, up, heightM))?.let(out::add)
        }
        return out
    }

    private fun clickInAir(): List<RingGesture> = hold(0f, 70, heightSpeed = -0.9f) + hold(0f, 90)

    @Test
    fun `tipping right past the threshold is next`() {
        hold(0f, 100)
        assertEquals(listOf(RingGesture.Next), hold(30f, 200))
    }

    @Test
    fun `tipping left past the threshold is previous`() {
        hold(0f, 100)
        assertEquals(listOf(RingGesture.Previous), hold(-30f, 200))
    }

    @Test
    fun `a tip shorter than the hold time does nothing`() {
        hold(0f, 100)
        assertEquals(emptyList<RingGesture>(), hold(30f, 50))
    }

    @Test
    fun `a tip below the threshold does nothing`() {
        hold(0f, 100)
        assertEquals(emptyList<RingGesture>(), hold(20f, 500))
    }

    @Test
    fun `no second tip until the ring comes back near rest`() {
        hold(0f, 100)
        hold(30f, 200)
        assertEquals(emptyList<RingGesture>(), hold(30f, 500))
        hold(5f, 100)
        assertEquals(listOf(RingGesture.Next), hold(30f, 200))
    }

    @Test
    fun `a pose held for a long time becomes the new rest`() {
        hold(0f, 100)
        hold(30f, 1700)
        hold(30f, 200)
        assertEquals(emptyList<RingGesture>(), hold(30f, 300))
    }

    @Test
    fun `one click in the air confirms after the double click wait`() {
        hold(0f, 100)
        assertEquals(emptyList<RingGesture>(), clickInAir())
        assertEquals(listOf(RingGesture.Confirm), hold(0f, 500))
    }

    @Test
    fun `two clicks in the air go back and do not confirm`() {
        hold(0f, 100)
        clickInAir()
        // Past the 250 ms the firmware ignores after a click, and inside its 450 ms wait.
        hold(0f, 180)
        assertEquals(listOf(RingGesture.Back), clickInAir())
        assertEquals(emptyList<RingGesture>(), hold(0f, 600))
    }

    @Test
    fun `slowly lowering the hand is not a click`() {
        hold(0f, 100)
        assertEquals(emptyList<RingGesture>(), hold(0f, 1000, heightSpeed = -0.15f))
        assertEquals(emptyList<RingGesture>(), hold(0f, 600))
    }

    @Test
    fun `a long fall of the hand is not a click`() {
        hold(0f, 100)
        assertEquals(emptyList<RingGesture>(), hold(0f, 600, heightSpeed = -0.9f))
        assertEquals(emptyList<RingGesture>(), hold(0f, 600))
    }

    @Test
    fun `a click does not read as a tip`() {
        hold(0f, 100)
        assertEquals(emptyList<RingGesture>(), hold(30f, 70, heightSpeed = -0.9f))
    }

    @Test
    fun `a tracking jump is not a click`() {
        hold(0f, 100)
        hold(0f, 16, heightSpeed = -12f)
        assertEquals(emptyList<RingGesture>(), hold(0f, 700))
    }

    @Test
    fun `a tip still counts through tracking noise`() {
        hold(0f, 100)
        val out = mutableListOf<RingGesture>()
        repeat(12) { i ->
            out += hold(30f, 16, heightSpeed = if (i % 2 == 0) 0.5f else -0.5f)
        }
        assertEquals(listOf(RingGesture.Next), out)
    }

    @Test
    fun `a tip still counts while noise looks like the start of a dip`() {
        hold(0f, 100)
        val out = mutableListOf<RingGesture>()
        repeat(16) { i ->
            out += hold(30f, 16, heightSpeed = if (i % 2 == 0) 0.9f else -0.9f)
        }
        assertEquals(listOf(RingGesture.Next), out)
    }

    @Test
    fun `reset forgets a pending click`() {
        hold(0f, 100)
        clickInAir()
        useCase.reset()
        assertEquals(emptyList<RingGesture>(), hold(0f, 600))
    }

    private companion object {
        const val STEP_MS = 16L
    }
}
