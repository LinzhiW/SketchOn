package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

/** Something worn on the finger. Only the Gemtronic ring has a screen to change. */
internal interface WornRing {
    val root: Entity

    fun showFrame(frame: String?) = Unit

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
    companion object {
        /** A 17 mm hole plus a 2 mm band, at the 3x the Gemtronic ring is shown at. */
        private const val OUTER_DIAMETER_M = 0.021f * 3f

        suspend fun create(path: String, stageRoot: Entity): GeneratedRingRig {
            val model = Entity.loadSuspend("file://$path")
            model.components[TransformComponent::class.java]?.setScaleVector(
                Vector3(OUTER_DIAMETER_M, OUTER_DIAMETER_M, OUTER_DIAMETER_M)
            )
            val root = Entity().apply {
                enabled = false
                addChild(model)
            }
            stageRoot.addChild(root)
            return GeneratedRingRig(root)
        }
    }
}
