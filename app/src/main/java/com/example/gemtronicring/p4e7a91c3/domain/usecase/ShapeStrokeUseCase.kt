package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drawing help applied when a stroke ends, because a ring is hard to draw round with a mouse,
 * a controller or a hand in the air.
 *
 * - Smooth: averages each point with its neighbours, taking out the wobble.
 * - Shapes: a stroke that goes nearly all the way round and stays close to a circle becomes
 *   a true circle, like Procreate's QuickShape. Otherwise, a stroke whose ends nearly meet is
 *   closed. Procreate waits for the pen to hold still; the emulator sends too few points to
 *   tell a hold, so here it happens on release.
 */
class ShapeStrokeUseCase {
    fun shape(points: List<StrokePoint>, smooth: Boolean, shapes: Boolean): List<StrokePoint> {
        var result = points
        if (smooth) result = smooth(result)
        if (shapes) result = ellipse(result) ?: close(result)
        return result
    }

    fun smooth(points: List<StrokePoint>, radius: Int = 2): List<StrokePoint> {
        if (points.size < 2 * radius + 1) return points
        return points.indices.map { i ->
            if (i == 0 || i == points.lastIndex) return@map points[i]
            val from = maxOf(0, i - radius)
            val to = minOf(points.lastIndex, i + radius)
            val n = (to - from + 1).toFloat()
            StrokePoint(
                (from..to).sumOf { points[it].x.toDouble() }.toFloat() / n,
                (from..to).sumOf { points[it].y.toDouble() }.toFloat() / n,
            )
        }
    }

    /**
     * Returns a closed circle when the stroke is one, else null. Uses a least-squares circle
     * fit, which holds up with the few, unevenly spread points the emulator sends.
     */
    fun ellipse(points: List<StrokePoint>): List<StrokePoint>? {
        if (points.size < MIN_POINTS) return null
        val (cx, cy, r) = fitCircle(points) ?: return null
        if (r < MIN_RADIUS) return null
        val distances = points.map { hypot(it.x - cx, it.y - cy) / r }
        val mean = distances.average()
        val spread = sqrt(distances.sumOf { (it - mean) * (it - mean) } / distances.size)
        if (spread > MAX_SPREAD) return null
        if (turned(points, cx, cy) < MIN_TURN) return null
        return (0..SEGMENTS).map { k ->
            val a = 2.0 * PI * k / SEGMENTS
            StrokePoint((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())
        }
    }

    /** Kasa's fit: solves x^2 + y^2 + Dx + Ey + F = 0 in the least-squares sense. */
    private fun fitCircle(points: List<StrokePoint>): Triple<Float, Float, Float>? {
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0; var sx = 0.0; var sy = 0.0
        var sxz = 0.0; var syz = 0.0; var sz = 0.0
        val n = points.size.toDouble()
        for (p in points) {
            val x = p.x.toDouble()
            val y = p.y.toDouble()
            val z = x * x + y * y
            sxx += x * x; sxy += x * y; syy += y * y; sx += x; sy += y
            sxz += x * z; syz += y * z; sz += z
        }
        // Normal equations for [D, E, F].
        val a = arrayOf(doubleArrayOf(sxx, sxy, sx), doubleArrayOf(sxy, syy, sy), doubleArrayOf(sx, sy, n))
        val b = doubleArrayOf(-sxz, -syz, -sz)
        val det = det3(a)
        if (abs(det) < 1e-9) return null
        val sol = (0..2).map { col ->
            det3(Array(3) { r -> DoubleArray(3) { c -> if (c == col) b[r] else a[r][c] } }) / det
        }
        val cx = -sol[0] / 2
        val cy = -sol[1] / 2
        val r2 = cx * cx + cy * cy - sol[2]
        if (r2 <= 0) return null
        return Triple(cx.toFloat(), cy.toFloat(), sqrt(r2).toFloat())
    }

    private fun det3(m: Array<DoubleArray>) =
        m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
            m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
            m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])

    /** Joins the ends of a stroke that nearly meet. */
    fun close(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < MIN_POINTS) return points
        val span = hypot(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
        val gap = hypot(points.last().x - points.first().x, points.last().y - points.first().y)
        return if (gap > 0f && gap < span * CLOSE_FRACTION) points + points.first() else points
    }

    /** Total angle swept around the centre, in turns. */
    private fun turned(points: List<StrokePoint>, cx: Float, cy: Float): Double {
        var total = 0.0
        var last = atan2((points[0].y - cy).toDouble(), (points[0].x - cx).toDouble())
        for (p in points.drop(1)) {
            val a = atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())
            var d = a - last
            if (d > PI) d -= 2 * PI
            if (d < -PI) d += 2 * PI
            total += d
            last = a
        }
        return abs(total) / (2 * PI)
    }

    private companion object {
        const val MIN_POINTS = 4
        const val MIN_RADIUS = 8f
        const val MAX_SPREAD = 0.12
        const val MIN_TURN = 0.8
        const val CLOSE_FRACTION = 0.2f
        const val SEGMENTS = 72
    }
}
