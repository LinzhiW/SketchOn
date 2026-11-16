package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Paints a ring's texture where a brush touches its surface.
 *
 * A dab colours every texture pixel whose point on the ring lies within the brush's reach.
 * That point is found from the triangle the pixel belongs to, so a dab across a seam in the
 * texture (where the ring's surface is cut apart to lie flat) still paints both sides.
 */
internal class SurfacePainter(private val surface: RingSurface) {
    val width = surface.texture.width
    val height = surface.texture.height
    private val original = IntArray(width * height).also { surface.texture.getPixels(it, 0, width, 0, 0, width, height) }
    private val pixels = original.copyOf()
    private val triangles = surface.indices.size / 3

    // Each triangle's centre and reach, to skip the ones the brush cannot touch.
    private val centres = FloatArray(triangles * 3)
    private val reach = FloatArray(triangles)

    init {
        val p = surface.positions
        for (t in 0 until triangles) {
            var cx = 0f
            var cy = 0f
            var cz = 0f
            for (k in 0 until 3) {
                val v = surface.indices[t * 3 + k] * 3
                cx += p[v]; cy += p[v + 1]; cz += p[v + 2]
            }
            cx /= 3f; cy /= 3f; cz /= 3f
            var r = 0f
            for (k in 0 until 3) {
                val v = surface.indices[t * 3 + k] * 3
                r = max(r, dist(p[v] - cx, p[v + 1] - cy, p[v + 2] - cz))
            }
            centres[t * 3] = cx; centres[t * 3 + 1] = cy; centres[t * 3 + 2] = cz
            reach[t] = r
        }
    }

    /**
     * Paints [argb] around the point ([x], [y], [z]) in the model's own space, out to
     * [radius] (same units). The edge is soft. Returns true if any pixel changed.
     */
    fun dab(x: Float, y: Float, z: Float, radius: Float, argb: Int, erase: Boolean = false): Boolean {
        var changed = false
        val p = surface.positions
        val uv = surface.uvs
        val ix = surface.indices
        for (t in 0 until triangles) {
            if (dist(centres[t * 3] - x, centres[t * 3 + 1] - y, centres[t * 3 + 2] - z) > radius + reach[t]) continue
            val a = ix[t * 3]; val b = ix[t * 3 + 1]; val c = ix[t * 3 + 2]
            // Corners on the texture, in pixels.
            val ax = uv[a * 2] * width; val ay = uv[a * 2 + 1] * height
            val bx = uv[b * 2] * width; val by = uv[b * 2 + 1] * height
            val cx = uv[c * 2] * width; val cy = uv[c * 2 + 1] * height
            val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if (area == 0f) continue
            // A pixel just outside the triangle is still painted, so no gap shows along seams.
            val slack = SEAM_PX / sqrt(kotlin.math.abs(area))
            val x0 = max(0, floor(min(ax, min(bx, cx)) - SEAM_PX).toInt())
            val x1 = min(width - 1, floor(max(ax, max(bx, cx)) + SEAM_PX).toInt())
            val y0 = max(0, floor(min(ay, min(by, cy)) - SEAM_PX).toInt())
            val y1 = min(height - 1, floor(max(ay, max(by, cy)) + SEAM_PX).toInt())
            for (py in y0..y1) for (px in x0..x1) {
                val sx = px + 0.5f
                val sy = py + 0.5f
                var wa = ((bx - sx) * (cy - sy) - (cx - sx) * (by - sy)) / area
                var wb = ((cx - sx) * (ay - sy) - (ax - sx) * (cy - sy)) / area
                var wc = 1f - wa - wb
                if (wa < -slack || wb < -slack || wc < -slack) continue
                wa = max(0f, wa); wb = max(0f, wb); wc = max(0f, wc)
                val sum = wa + wb + wc
                val qx = (wa * p[a * 3] + wb * p[b * 3] + wc * p[c * 3]) / sum
                val qy = (wa * p[a * 3 + 1] + wb * p[b * 3 + 1] + wc * p[c * 3 + 1]) / sum
                val qz = (wa * p[a * 3 + 2] + wb * p[b * 3 + 2] + wc * p[c * 3 + 2]) / sum
                val d = dist(qx - x, qy - y, qz - z)
                if (d > radius) continue
                // Full colour in the middle, fading over the outer part of the brush.
                val strength = min(1f, (radius - d) / (radius * SOFT_EDGE))
                val i = py * width + px
                // The eraser paints the ring's own texture back.
                val mixed = mix(pixels[i], if (erase) original[i] else argb, strength)
                if (mixed != pixels[i]) {
                    pixels[i] = mixed
                    changed = true
                }
            }
        }
        return changed
    }

    /** Distance from a point to the nearest corner of the surface, in the model's units. */
    fun nearest(x: Float, y: Float, z: Float): Float {
        val p = surface.positions
        var best = Float.MAX_VALUE
        var i = 0
        while (i < p.size) {
            best = min(best, dist(p[i] - x, p[i + 1] - y, p[i + 2] - z))
            i += 3
        }
        return best
    }

    /** Back to the texture the ring was made with. */
    fun clear() = original.copyInto(pixels)

    /** The painted texture, written into [into] (same size as the ring's texture). */
    fun writeTo(into: Bitmap) = into.setPixels(pixels, 0, width, 0, 0, width, height)

    private fun dist(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    private fun mix(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = (((from shr shift) and 0xFF) * (1f - t) + ((to shr shift) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private companion object {
        const val SEAM_PX = 1.5f
        const val SOFT_EDGE = 0.35f
    }
}
