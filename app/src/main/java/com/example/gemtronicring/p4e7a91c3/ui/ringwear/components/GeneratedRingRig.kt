package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

/** Something worn on the finger. Only the Gemtronic ring has a screen to change. */
internal interface WornRing {
    val root: Entity

    fun showFrame(frame: String?) = Unit

    /** Colourable parts: one per material, in the order the server wrote them. */
    fun parts(): Int = 1

    fun destroy() = root.destroy()
}

/**
 * A ring the user drew, loaded from the GLB the render server made.
 *
 * The server already put the ring in a standard pose (server/src/ringfit.js): hole along Z,
 * hole centre at the origin, stone towards +Y, outer diameter 1. That matches the finger
 * joint's axes, so the ring only needs scaling.
 */
internal class GeneratedRingRig private constructor(override val root: Entity) : WornRing {
    /**
     * Colours the whole ring. Clay and resin are made white, and glTF multiplies the base
     * colour with the texture, so the white texture takes this colour and keeps its shading.
     * Materials are changed in place, never swapped (swapping releases them, see RingRig).
     */
    private val materials: List<PhysicallyBasedMaterial> by lazy {
        val found = mutableListOf<PhysicallyBasedMaterial>()
        fun visit(entity: Entity) {
            entity.components[ModelComponent::class.java]?.materials?.forEach {
                (it as? PhysicallyBasedMaterial)?.let(found::add)
            }
            entity.getChildren().forEach(::visit)
        }
        visit(root)
        found
    }

    override fun parts(): Int = materials.size.coerceAtLeast(1)

    /** Colours one part (0 band, 1 decoration) of the ring. */
    fun paint(part: Int, argb: Int) {
        val material = materials.getOrNull(part) ?: return
        material.setBaseColor(toColour(argb))
    }

    private fun toColour(argb: Int) =
        Color4(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            1f,
        )

    /** Colours the whole ring. */
    fun paint(argb: Int) {
        val colour =
            Color4(
                ((argb shr 16) and 0xFF) / 255f,
                ((argb shr 8) and 0xFF) / 255f,
                (argb and 0xFF) / 255f,
                1f,
            )
        fun visit(entity: Entity) {
            entity.components[ModelComponent::class.java]?.materials?.forEach {
                (it as? PhysicallyBasedMaterial)?.setBaseColor(colour)
            }
            entity.getChildren().forEach(::visit)
        }
        visit(root)
    }

    companion object {
        /** A 17 mm hole plus a 2 mm band, at the 3x the Gemtronic ring is shown at. */
        private const val OUTER_DIAMETER_M = 0.021f * 3f

        suspend fun create(path: String, stageRoot: Entity): GeneratedRingRig {
            // The loaded entity is the GLB's node itself and carries the server's pose
            // (translation, rotation, scale). Setting its scale replaced the pose's scale and
            // left its translation unscaled, so the ring sat 20 cm off the finger. The size is
            // set on a holder around it instead.
            val model = Entity.loadSuspend("file://$path")
            val size = Entity().apply {
                components[TransformComponent::class.java]?.setScaleVector(
                    Vector3(OUTER_DIAMETER_M, OUTER_DIAMETER_M, OUTER_DIAMETER_M)
                )
                addChild(model)
            }
            val root = Entity().apply {
                enabled = false
                addChild(size)
            }
            stageRoot.addChild(root)
            return GeneratedRingRig(root)
        }
    }
}
