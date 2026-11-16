package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt
import com.pico.spatial.tracking.controller.ControllerPose

/**
 * The right controller's brush: a dot in the brush's colour and size, on the ring where the
 * controller points, or just ahead of it when it points past the ring. Colour, size and the
 * eraser are chosen on the panel. (A palette hung on the left controller was dropped: it was
 * fiddly to pick from, and the left hand now turns the ring.)
 */
internal class ControllerBrush(private val stageRoot: Entity) {
    private val tipMaterial = UnlitMaterial.create()
    private val tip = Entity().apply {
        enabled = false
        components.set(ModelComponent(MeshResource.createSphere(1f), tipMaterial))
        stageRoot.addChild(this)
    }

    /** Which way the controller points (its ray), in the container's space. */
    fun forwardOf(pose: ControllerPose): Vector3 = pose.rotation.rotateVector(Vector3(0f, 0f, -1f))

    /** A point a little ahead of the controller, in the container's space: paints by touch. */
    fun tipOf(right: ControllerPose): Vector3 {
        val ahead = forwardOf(right)
        return Vector3(
            right.position.x + ahead.x * TIP_AHEAD_M,
            right.position.y + ahead.y * TIP_AHEAD_M,
            right.position.z + ahead.z * TIP_AHEAD_M,
        )
    }

    /** Shows the dot at [aimAt] (or ahead of [right]); hides it when there is no controller. */
    fun show(right: ControllerPose?, argb: Int, erasing: Boolean, radiusM: Float, aimAt: Vector3?) {
        tip.enabled = right != null
        if (right == null) return
        tip.components[TransformComponent::class.java]?.apply {
            setPosition(stageRoot.convertPositionFrom(aimAt ?: tipOf(right), null))
            setScaleVector(Vector3(radiusM, radiusM, radiusM))
        }
        tipMaterial.setBaseColor(colour(if (erasing) ERASER_GREY else argb))
    }

    /** A thin pen held in the pinching hand, shown along the hand's aim. */
    private val penMaterial = UnlitMaterial.create()
    private val pen = Entity().apply {
        enabled = false
        components.set(ModelComponent(MeshResource.createCylinder(PEN_LENGTH_M, PEN_RADIUS_M), penMaterial))
        stageRoot.addChild(this)
    }

    /**
     * The hand's pen: a stick from [from] along [dir] (container space), and the dot at [hit]
     * on the ring, or at the pen's tip when it points past the ring.
     */
    fun showPen(from: Vector3, dir: Vector3, hit: Vector3?, argb: Int, erasing: Boolean, radiusM: Float) {
        val len = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z)
        if (len == 0f) return hide()
        val d = Vector3(dir.x / len, dir.y / len, dir.z / len)
        val mid = Vector3(from.x + d.x * PEN_LENGTH_M / 2f, from.y + d.y * PEN_LENGTH_M / 2f, from.z + d.z * PEN_LENGTH_M / 2f)
        pen.enabled = true
        pen.components[TransformComponent::class.java]?.apply {
            setPosition(stageRoot.convertPositionFrom(mid, null))
            setQuaternion(stageRoot.convertRotationFrom(alongY(d), null))
        }
        penMaterial.setBaseColor(colour(if (erasing) ERASER_GREY else argb))
        val end = hit ?: Vector3(from.x + d.x * PEN_LENGTH_M, from.y + d.y * PEN_LENGTH_M, from.z + d.z * PEN_LENGTH_M)
        tip.enabled = true
        tip.components[TransformComponent::class.java]?.apply {
            setPosition(stageRoot.convertPositionFrom(end, null))
            setScaleVector(Vector3(radiusM, radiusM, radiusM))
        }
        tipMaterial.setBaseColor(colour(if (erasing) ERASER_GREY else argb))
    }

    fun hide() {
        tip.enabled = false
        pen.enabled = false
    }

    /** The turn that points a cylinder's length (its Y axis) along the unit vector [d]. */
    private fun alongY(d: Vector3): Quat {
        val axis = Vector3(d.z, 0f, -d.x) // (0, 1, 0) x d
        val s = sqrt(axis.x * axis.x + axis.z * axis.z)
        if (s < 1e-6f) return if (d.y >= 0f) Quat() else Quat(Vector3(1f, 0f, 0f), PI.toFloat())
        return Quat(Vector3(axis.x / s, 0f, axis.z / s), acos(d.y.coerceIn(-1f, 1f)))
    }

    private companion object {
        const val TIP_AHEAD_M = 0.07f
        const val PEN_LENGTH_M = 0.12f
        const val PEN_RADIUS_M = 0.004f
        const val ERASER_GREY = 0xFF8A8F8C.toInt()

        fun colour(argb: Int) =
            Color4(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, 1f)
    }
}
