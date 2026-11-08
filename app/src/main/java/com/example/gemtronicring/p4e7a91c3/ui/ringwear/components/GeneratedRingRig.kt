package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import com.example.gemtronicring.p4e7a91c3.domain.model.ModelBounds
import com.example.gemtronicring.p4e7a91c3.domain.usecase.FitRingUseCase
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI

/** Something worn on the finger. Only the Gemtronic ring has a screen to change. */
internal interface WornRing {
    val root: Entity

    fun showFrame(frame: String?) = Unit

    fun destroy() = root.destroy()
}

/** A ring the user drew, loaded from the GLB the render server made. */
internal class GeneratedRingRig private constructor(override val root: Entity) : WornRing {
    companion object {
        private val QUARTER_TURN = Quat(Vector3(0f, 1f, 0f), (PI / 2).toFloat())

        /**
         * Bounds need the entity in a scene, so [stageRoot] is where the ring will live.
         * Returns null when the model has no size to fit.
         */
        suspend fun create(path: String, stageRoot: Entity): GeneratedRingRig? {
            val model = Entity.loadSuspend("file://$path")
            val turn = Entity().apply { addChild(model) }
            val root = Entity().apply {
                enabled = false
                addChild(turn)
            }
            stageRoot.addChild(root)

            val box = model.getVisualBounds(model)
            val fit =
                FitRingUseCase()
                    .fit(ModelBounds(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z))
            if (fit == null) {
                root.destroy()
                return null
            }
            model.components[TransformComponent::class.java]?.apply {
                setScaleVector(Vector3(fit.scale, fit.scale, fit.scale))
                setPosition(Vector3(fit.offsetX, fit.offsetY, fit.offsetZ))
            }
            if (fit.holeAlongX) {
                turn.components[TransformComponent::class.java]?.setQuaternion(QUARTER_TURN)
            }
            return GeneratedRingRig(root)
        }
    }
}
