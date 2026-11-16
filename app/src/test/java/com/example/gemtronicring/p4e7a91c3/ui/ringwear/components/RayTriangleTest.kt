package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The controller brush paints where its ray meets the ring; this is that test, per triangle. */
class RayTriangleTest {
    // One triangle in the z = 0 plane, around the origin.
    private val tri = floatArrayOf(
        -1f, -1f, 0f,
        1f, -1f, 0f,
        0f, 1f, 0f,
    )

    private fun cast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float) =
        rayTriangle(ox, oy, oz, dx, dy, dz, tri, 0, 1, 2)

    @Test
    fun `a ray pointed at the triangle hits it at the right distance`() {
        val t = cast(0f, 0f, 2f, 0f, 0f, -1f)
        assertNotNull(t)
        assertEquals(2f, t!!, 1e-5f)
    }

    @Test
    fun `the parameter scales with the direction's length`() {
        assertEquals(0.5f, cast(0f, 0f, 2f, 0f, 0f, -4f)!!, 1e-5f)
    }

    @Test
    fun `the back of the triangle counts too`() {
        assertEquals(3f, cast(0f, 0f, -3f, 0f, 0f, 1f)!!, 1e-5f)
    }

    @Test
    fun `a ray beside the triangle misses`() {
        assertNull(cast(3f, 0f, 2f, 0f, 0f, -1f))
    }

    @Test
    fun `a ray pointing away misses`() {
        assertNull(cast(0f, 0f, 2f, 0f, 0f, 1f))
    }

    @Test
    fun `a ray along the triangle's plane misses`() {
        assertNull(cast(-5f, 0f, 0f, 1f, 0f, 0f))
    }
}
