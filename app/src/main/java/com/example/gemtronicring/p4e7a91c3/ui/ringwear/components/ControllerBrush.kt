package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.controller.ControllerPose
import kotlin.math.sqrt

/** What a palette swatch does when the brush tip touches it. */
internal sealed interface Swatch {
    data class Colour(val argb: Int) : Swatch

    data object Eraser : Swatch

    data class Size(val index: Int) : Swatch
}

/**
 * Painting with the controllers: the right one is the brush (its tip shows the colour), the
 * left one carries a palette of colours, an eraser and three brush sizes, picked by touching
 * them with the brush tip.
 */
internal class ControllerBrush(private val stageRoot: Entity) {
    private val unit = MeshResource.createSphere(1f)
    private val tipMaterial = UnlitMaterial.create()
    private val tip = Entity().apply {
        enabled = false
        components.set(ModelComponent(unit, tipMaterial))
        stageRoot.addChild(this)
    }
    private val palette = Entity().apply {
        enabled = false
        stageRoot.addChild(this)
    }
    private val swatches = mutableListOf<Pair<Swatch, Entity>>()
    private var touching: Swatch? = null

    init {
        // Colours in two rows above the controller, then the eraser and sizes below them.
        COLOURS.forEachIndexed { i, argb ->
            val row = i / PER_ROW
            val col = i % PER_ROW
            add(Swatch.Colour(argb), argb, Vector3(colX(col), ROW1_Y - row * ROW_GAP, 0f), SWATCH_R)
        }
        add(Swatch.Eraser, ERASER_GREY, Vector3(colX(0), SIZE_ROW_Y, 0f), SWATCH_R * 1.2f, flat = true)
        SIZES_M.forEachIndexed { i, size ->
            add(Swatch.Size(i), WHITE, Vector3(colX(2 + i), SIZE_ROW_Y, 0f), (size * 0.8f).coerceAtLeast(0.004f))
        }
    }

    private fun colX(col: Int) = (col - (PER_ROW - 1) / 2f) * COL_GAP

    private fun add(swatch: Swatch, argb: Int, at: Vector3, radius: Float, flat: Boolean = false) {
        val e = Entity().apply {
            components.set(ModelComponent(unit, UnlitMaterial.create().apply { setBaseColor(colour(argb)) }))
            components[TransformComponent::class.java]?.apply {
                setPosition(at)
                setScaleVector(Vector3(radius, if (flat) radius * 0.4f else radius, radius))
            }
        }
        palette.addChild(e)
        swatches += swatch to e
    }

    /** Where the brush paints: a little ahead of the controller, in the container's space. */
    fun tipOf(right: ControllerPose): Vector3 {
        val ahead = right.rotation.rotateVector(Vector3(0f, 0f, -TIP_AHEAD_M))
        return Vector3(right.position.x + ahead.x, right.position.y + ahead.y, right.position.z + ahead.z)
    }

    /**
     * Shows the brush tip and the palette where the controllers are (null hides them), and
     * marks the chosen colour, eraser and size.
     */
    fun show(left: ControllerPose?, right: ControllerPose?, argb: Int, erasing: Boolean, size: Int) {
        tip.enabled = right != null
        if (right != null) {
            val r = SIZES_M[size]
            tip.components[TransformComponent::class.java]?.apply {
                setPosition(stageRoot.convertPositionFrom(tipOf(right), null))
                setScaleVector(Vector3(r, r, r))
            }
            tipMaterial.setBaseColor(colour(if (erasing) ERASER_GREY else argb))
        }
        palette.enabled = left != null
        if (left != null) {
            palette.components[TransformComponent::class.java]?.apply {
                setPosition(stageRoot.convertPositionFrom(left.position, null))
                setQuaternion(stageRoot.convertRotationFrom(left.rotation, null))
            }
        }
        swatches.forEach { (swatch, e) ->
            val chosen =
                when (swatch) {
                    is Swatch.Colour -> !erasing && swatch.argb == argb
                    Swatch.Eraser -> erasing
                    is Swatch.Size -> swatch.index == size
                }
            // A chosen swatch sits a little forward, so it stands out.
            val base = e.components[TransformComponent::class.java]?.position ?: return@forEach
            e.components[TransformComponent::class.java]?.setPosition(Vector3(base.x, base.y, if (chosen) CHOSEN_Z else 0f))
        }
    }

    /** The swatch the brush tip has just touched, once per touch; null otherwise. */
    fun touched(right: ControllerPose?): Swatch? {
        if (right == null || !palette.enabled) {
            touching = null
            return null
        }
        val at = stageRoot.convertPositionFrom(tipOf(right), null)
        val hit = swatches.firstOrNull { (_, e) ->
            val c = stageRoot.convertPositionFrom(Vector3(0f, 0f, 0f), e)
            dist(at, c) < PICK_M
        }?.first
        val fresh = hit != null && hit != touching
        touching = hit
        return if (fresh) hit else null
    }

    fun hide() {
        tip.enabled = false
        palette.enabled = false
        touching = null
    }

    private fun dist(a: Vector3, b: Vector3): Float {
        val x = a.x - b.x; val y = a.y - b.y; val z = a.z - b.z
        return sqrt(x * x + y * y + z * z)
    }

    companion object {
        /** Brush reach for each size, in metres, on the ring held for painting. */
        val SIZES_M = listOf(0.006f, 0.012f, 0.024f)
        private const val TIP_AHEAD_M = 0.07f
        private const val PICK_M = 0.022f
        private const val SWATCH_R = 0.011f
        private const val PER_ROW = 5
        private const val COL_GAP = 0.03f
        private const val ROW1_Y = 0.14f
        private const val ROW_GAP = 0.03f
        private const val SIZE_ROW_Y = 0.075f
        private const val CHOSEN_Z = -0.012f
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val ERASER_GREY = 0xFF8A8F8C.toInt()
        val COLOURS =
            listOf(
                0xFFE5484D, 0xFFF76B15, 0xFFFFC53D, 0xFF46A758, 0xFF0090FF,
                0xFF6E56CF, 0xFFE93D82, 0xFF8D5A3B, 0xFF1C2024, 0xFFFFFFFF,
            ).map { it.toInt() }

        fun colour(argb: Int) =
            Color4(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, 1f)
    }
}
