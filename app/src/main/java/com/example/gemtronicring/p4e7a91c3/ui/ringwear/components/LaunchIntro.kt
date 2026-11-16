package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.pico.spatial.core.ecs.DrawOrderGroup
import com.pico.spatial.core.ecs.DrawOrderGroupComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private const val TAG = "SketchOn"

/** Whether the opening animation has played in this run of the app. Not again on return to the front. */
object LaunchIntroState {
    var done by mutableStateOf(false)
}

/**
 * The opening animation. It fills the whole view for about seven seconds: a soft mint-to-lilac
 * sky closes the room off, a glowing pen draws a ring, the ring turns solid, its stone lights up
 * and the name appears. Then the sky fades away and the room shows again.
 *
 * Everything is made in code (no files to load), so it shows at once. The view is only covered
 * briefly at launch, which the store allows for MR apps (PXRC-MR-M2003); the system's own safety
 * checks keep running underneath.
 */
@Composable
fun LaunchIntro(onDone: () -> Unit) {
    val currentOnDone by rememberUpdatedState(onDone)
    val scene = remember { IntroScene() }
    val hmd = remember { HMDTrackingProvider() }
    DisposableEffect(hmd) {
        hmd.start()
        onDispose { hmd.stop() }
    }
    LaunchedEffect(scene) {
        // Placed around the head as soon as the headset reports where it is.
        var waited = 0L
        while (waited < HEAD_WAIT_MS && hmd.latestData.hmdPose.position == Vector3.ZERO) {
            delay(POLL_MS)
            waited += POLL_MS
        }
        scene.placeAroundHead(hmd.latestData.hmdPose.let { if (it.position == Vector3.ZERO) null else it })
        scene.root.enabled = true
        val start = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { (it - start) / 1_000_000L }
            scene.show(t)
            if (t >= T_END) break
        }
        scene.root.enabled = false
        Log.i(TAG, "intro played (head ${if (waited < HEAD_WAIT_MS) "found" else "not found"} after ${waited}ms)")
        currentOnDone()
    }
    SpatialView(modifier = Modifier.fillMaxSize()) { content, _ -> content.addEntity(scene.stageRoot) }
}

// Timeline, in milliseconds from the first frame.
// About twice the first cut (4 s): on a headset that went by before the eye could follow it.
private const val T_DRAW_START = 400L
private const val T_DRAW_END = 2900L
private const val T_SOLID_END = 3700L
private const val T_GEM_START = 3400L
private const val T_GEM_END = 4300L
private const val T_NAME_START = 4000L
private const val T_NAME_END = 5000L
private const val T_FADE_START = 6500L
private const val T_END = 7500L
private const val SPARKLE_STEP_MS = 250L
private const val SPARKLE_MS = 1000L

private const val HEAD_WAIT_MS = 1000L
private const val POLL_MS = 50L

// Sizes and places, in metres. The scene's origin is the head; -Z is straight ahead.
private const val SKY_RADIUS = 5f
private const val RING_DISTANCE = 1.6f
private const val RING_OUTER = 0.30f
private const val RING_INNER = 0.25f
private const val RING_MID = (RING_OUTER + RING_INNER) / 2f
private const val BEADS = 96
private const val BEAD_RADIUS = 0.013f
private const val PEN_RADIUS = 0.022f
private const val GEM_RADIUS = 0.075f
private const val SPARKLE_RADIUS = 0.012f
private const val NAME_WIDTH = 0.9f
private const val NAME_HEIGHT = 0.225f
private const val NAME_BELOW = 0.5f
/** With no head pose, the head is taken to be this high above the stage's origin. */
private const val FALLBACK_HEAD_HEIGHT = 1.5f

// The icon's colours.
private val SKY_TOP = intArrayOf(178, 240, 218)
private val SKY_BOTTOM = intArrayOf(230, 200, 248)
private val STROKE = Color4(0.47f, 0.31f, 0.78f, 1f)
private val PEN = Color4(1f, 1f, 1f, 1f)
private val BAND = Color4(0.47f, 0.84f, 0.74f, 1f)
private val GEM = Color4(0.75f, 0.58f, 0.96f, 1f)
private val GEM_GLOW = Color4(0.35f, 0.22f, 0.55f, 1f)
private const val NAME_COLOR = 0xFF5A3C96.toInt()

private class IntroScene {
    val stageRoot = Entity()
    /** At the head, turned the way the head faces. */
    val root = Entity().apply { enabled = false }.also { stageRoot.addChild(it) }

    private val skyMaterial =
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColorTexture(TextureResource.create(skyBitmap()))
            // Seen from inside: the outward faces are skipped.
            setCullingMode(MaterialCullingMode.FRONT)
            setDepthWrite(false)
        }
    /** Transparent things draw in this order (higher first, so behind): the sky, then the ring and name. */
    private val drawOrder = DrawOrderGroup.create()
    private val sky =
        model(MeshResource.createSphere(SKY_RADIUS), skyMaterial).also {
            root.addChild(it)
            it.components.set(DrawOrderGroupComponent(drawOrder, 2))
            it.components.set(OpacityControllerComponent(1f))
        }

    /** The ring's own frame: its hole faces the user, tipped back a little so it reads as 3D. */
    private val pivot =
        Entity().also {
            root.addChild(it)
            it.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, 0.05f, -RING_DISTANCE))
                setQuaternion(pivotTurn(0.35f))
            }
        }

    /** The torus lies flat (hole up the Y axis): stood up by a quarter turn about X, less a tip back, then turned [yaw]. */
    private fun pivotTurn(yaw: Float) =
        Quat(Vector3(0f, 1f, 0f), yaw) * Quat(Vector3(1f, 0f, 0f), (PI / 2 - 0.35).toFloat())

    private val strokeMaterial = UnlitMaterial.create().apply { setBaseColor(STROKE) }
    private val beadMesh = MeshResource.createSphere(BEAD_RADIUS)
    /** The drawn line: beads round the ring, starting at the top where the stone goes. */
    private val beads =
        List(BEADS) { i ->
            val a = -PI / 2 + 2 * PI * i / BEADS
            model(beadMesh, strokeMaterial).also {
                it.enabled = false
                pivot.addChild(it)
                it.place(Vector3((RING_MID * cos(a)).toFloat(), 0f, (RING_MID * sin(a)).toFloat()))
            }
        }
    private val pen =
        model(MeshResource.createSphere(PEN_RADIUS), UnlitMaterial.create().apply { setBaseColor(PEN) })
            .also { pivot.addChild(it) }

    private val bandMaterial =
        PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(BAND)
            setMetallic(0.35f)
            setRoughness(0.3f)
        }
    private val band =
        model(MeshResource.createTorus(RING_OUTER, RING_INNER), bandMaterial).also {
            pivot.addChild(it)
            it.components.set(DrawOrderGroupComponent(drawOrder, 1))
            it.components.set(OpacityControllerComponent(0f))
        }

    private val gem =
        model(
            MeshResource.createSphere(GEM_RADIUS),
            PhysicallyBasedMaterial.create().apply {
                setBaseColor(GEM)
                setEmissiveColor(GEM_GLOW)
                setMetallic(0.1f)
                setRoughness(0.12f)
            },
        ).also {
            pivot.addChild(it)
            it.place(Vector3(0f, 0f, -(RING_OUTER + GEM_RADIUS * 0.55f)))
        }

    private val sparkles =
        listOf(Vector3(0.13f, 0.02f, -0.42f), Vector3(-0.12f, 0.03f, -0.45f), Vector3(0.07f, 0.04f, -0.52f))
            .map { at ->
                model(MeshResource.createSphere(SPARKLE_RADIUS), UnlitMaterial.create().apply { setBaseColor(PEN) })
                    .also {
                        pivot.addChild(it)
                        it.place(at)
                    }
            }

    private val nameMaterial =
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColorTexture(TextureResource.create(nameBitmap()))
            setDepthWrite(false)
        }
    private val name =
        model(MeshResource.createPlane(NAME_WIDTH, NAME_HEIGHT, 0f), nameMaterial).also {
            root.addChild(it)
            it.components.set(DrawOrderGroupComponent(drawOrder, 1))
            it.components.set(OpacityControllerComponent(0f))
            it.place(Vector3(0f, -NAME_BELOW, -RING_DISTANCE))
        }

    fun placeAroundHead(pose: com.pico.spatial.tracking.hmd.HMDPose?) {
        val transform = root.components[TransformComponent::class.java] ?: return
        if (pose == null) {
            transform.setPosition(Vector3(0f, FALLBACK_HEAD_HEIGHT, 0f))
            return
        }
        val f = pose.rotation.rotateVector(Vector3(0f, 0f, -1f))
        val len = sqrt(f.x * f.x + f.z * f.z)
        val (fx, fz) = if (len > 0.1f) f.x / len to f.z / len else 0f to -1f
        // Head pose is in world space; the scene hangs from the stage root.
        transform.setPosition(stageRoot.convertPositionFrom(pose.position, null))
        transform.setQuaternion(stageRoot.convertRotationFrom(Quat(Vector3(0f, 1f, 0f), atan2(-fx, -fz)), null))
    }

    /** Shows the frame [t] milliseconds in. */
    fun show(t: Long) {
        // The line is drawn round the ring, the pen leading.
        val drawn = progress(t, T_DRAW_START, T_DRAW_END).ease()
        val shown = (drawn * BEADS).toInt()
        val solid = progress(t, T_DRAW_END, T_SOLID_END)
        beads.forEachIndexed { i, b -> b.enabled = i < shown && solid < 1f }
        pen.enabled = t in T_DRAW_START until T_DRAW_END
        if (pen.enabled) {
            val a = -PI / 2 + 2 * PI * drawn
            pen.place(Vector3((RING_MID * cos(a)).toFloat(), 0f, (RING_MID * sin(a)).toFloat()))
        }
        // Then the band fills in under the line.
        band.opacity(solid.ease())
        band.scale(0.92f + 0.08f * solid.ease())
        // The stone pops up, a touch too big, and settles.
        val g = progress(t, T_GEM_START, T_GEM_END)
        gem.scale(if (g < 0.7f) 1.15f * (g / 0.7f).ease() else 1.15f - 0.15f * ((g - 0.7f) / 0.3f))
        sparkles.forEachIndexed { i, s ->
            val p = progress(t, T_GEM_START + SPARKLE_STEP_MS * i, T_GEM_START + SPARKLE_STEP_MS * i + SPARKLE_MS)
            s.scale(sin(p * PI).toFloat())
        }
        name.opacity(progress(t, T_NAME_START, T_NAME_END).ease())
        // The ring swings slowly left and right the whole time.
        pivot.components[TransformComponent::class.java]?.setQuaternion(pivotTurn(0.35f - 0.5f * sin(t / 1400.0).toFloat()))
        // At the end, everything fades and the room shows again.
        val out = progress(t, T_FADE_START, T_END).ease()
        // (Opacity multiplies down the tree, so fading the root fades everything.)
        root.opacity(1f - out)
        if (out > 0f) pivot.scale(1f - 0.4f * out)
    }
}

private fun model(mesh: MeshResource, material: com.pico.spatial.core.ecs.resource.Material) =
    Entity().apply { components.set(ModelComponent(mesh, material)) }

private fun Entity.place(at: Vector3) {
    components[TransformComponent::class.java]?.setPosition(at)
}

private fun Entity.opacity(o: Float) {
    val c = components[OpacityControllerComponent::class.java]
    if (c != null) c.opacity = o.coerceIn(0f, 1f) else components.set(OpacityControllerComponent(o.coerceIn(0f, 1f)))
}

private fun Entity.scale(s: Float) {
    val v = s.coerceAtLeast(0.0001f)
    components[TransformComponent::class.java]?.setScaleVector(Vector3(v, v, v))
}

private fun progress(t: Long, from: Long, to: Long): Float = ((t - from).toFloat() / (to - from)).coerceIn(0f, 1f)

/** Starts fast, lands softly. */
private fun Float.ease(): Float = 1f - (1f - this) * (1f - this) * (1f - this)

/** Mint at the top to lilac at the bottom, like the icon. A sphere's texture runs top to bottom. */
private fun skyBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(64, 256, Bitmap.Config.ARGB_8888)
    val top = android.graphics.Color.rgb(SKY_TOP[0], SKY_TOP[1], SKY_TOP[2])
    val bottom = android.graphics.Color.rgb(SKY_BOTTOM[0], SKY_BOTTOM[1], SKY_BOTTOM[2])
    Canvas(bmp).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 256f, top, bottom, Shader.TileMode.CLAMP) })
    return bmp
}

/** "SketchOn" in the deep purple of the stroke, on a clear background. */
private fun nameBitmap(): Bitmap {
    val w = 1024
    val h = (w * NAME_HEIGHT / NAME_WIDTH).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_COLOR
            textSize = h * 0.62f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.02f
        }
    val y = h / 2f - (paint.descent() + paint.ascent()) / 2f
    Canvas(bmp).drawText("SketchOn", w / 2f, y, paint)
    return bmp
}
