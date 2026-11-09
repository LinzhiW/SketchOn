package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.StrokePoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeStrokeUseCaseTest {
    private val shaper = ShapeStrokeUseCase()

    /** A wobbly circle like a mouse makes: 6 points, radius 100 +- 8, not quite closed. */
    private fun wobblyCircle(turn: Double = 0.95, n: Int = 6): List<StrokePoint> =
        (0..n).map { k ->
            val a = 2 * Math.PI * turn * k / n
            val r = 100 + if (k % 2 == 0) 8 else -8
            StrokePoint((200 + r * cos(a)).toFloat(), (200 + r * sin(a)).toFloat())
        }

    @Test
    fun aRoughCircleBecomesAClosedCircleOfTheSameSize() {
        val result = shaper.ellipse(wobblyCircle())!!
        assertEquals(result.first().x, result.last().x, 0.01f)
        assertEquals(result.first().y, result.last().y, 0.01f)
        // Every point is on one circle close to the one drawn (radius 100, centre 200,200).
        result.forEach { assertEquals(100.0, hypot(it.x - 200.0, it.y - 200.0), 8.0) }
    }

    @Test
    fun anOpenArcIsNotTurnedIntoACircle() {
        assertNull(shaper.ellipse(wobblyCircle(turn = 0.5)))
    }

    @Test
    fun aStraightLineIsLeftAlone() {
        val line = (0..10).map { StrokePoint(it * 20f, it * 5f) }
        assertEquals(line, shaper.shape(line, smooth = false, shapes = true))
    }

    @Test
    fun aShapeWhoseEndsNearlyMeetIsClosed() {
        // A square drawn by hand, ending just short of where it began.
        val square = listOf(
            StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 100f),
            StrokePoint(0f, 100f), StrokePoint(0f, 12f),
        )
        val closed = shaper.close(square)
        assertEquals(square.size + 1, closed.size)
        assertEquals(square.first(), closed.last())
    }

    @Test
    fun aStrokeOfFewPointsIsNotSmoothed() {
        val few = (0..6).map { StrokePoint(it * 10f, if (it % 2 == 0) 0f else 10f) }
        assertEquals(few, shaper.smooth(few))
    }

    @Test
    fun smoothingKeepsTheEndsAndCalmsTheMiddle() {
        val zigzag = (0..14).map { StrokePoint(it * 10f, if (it % 2 == 0) 0f else 10f) }
        val smooth = shaper.smooth(zigzag)
        assertEquals(zigzag.first(), smooth.first())
        assertEquals(zigzag.last(), smooth.last())
        assertTrue(smooth.drop(2).dropLast(2).all { it.y in 3f..7f })
    }
}
