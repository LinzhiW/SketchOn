package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.Bitmap
import android.util.Log
import com.pico.spatial.core.ecs.resource.TextureResource
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
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
internal class GeneratedRingRig private constructor(
    override val root: Entity,
    /** The loaded GLB node: its own space is the mesh's, where the brush paints. */
    private val model: Entity,
    private val path: String,
) : WornRing {
    private var painter: SurfacePainter? = null
    private var canvas: Bitmap? = null
    private var shownTexture: TextureResource? = null

    /** True once the ring has a texture the brush can paint on. */
    val brushable: Boolean
        get() = painter != null

    /** Reads the ring's surface for the brush. Off the main thread: it reads the whole file. */
    suspend fun prepareBrush() {
        if (painter != null) return
        val surface = withContext(Dispatchers.IO) { RingSurface.read(File(path), BRUSH_TEXTURE_MAX) }
        if (surface == null) {
            Log.i("SketchOn", "brush: this ring has no texture to paint on")
            return
        }
        painter = withContext(Dispatchers.Default) { SurfacePainter(surface) }
        canvas = surface.texture
        Log.i("SketchOn", "brush ready: ${surface.indices.size / 3} triangles, texture ${surface.texture.width}px")
    }

    /** How far the last brush point was from the ring's surface, in metres, for the log. */
    var lastGapM = Float.NaN
        private set

    /**
     * Where a ray from [origin] along [direction] (stage container space, like [brushAt])
     * first meets the ring, or null. The model's space is the container's moved, turned and
     * scaled, so a point a fraction along the ray there is the same fraction along it here.
     */
    fun rayHit(origin: Vector3, direction: Vector3): Vector3? {
        val p = painter ?: return null
        val o = model.convertPositionFrom(origin, null)
        val e = model.convertPositionFrom(Vector3(origin.x + direction.x, origin.y + direction.y, origin.z + direction.z), null)
        val t = p.raycast(o.x, o.y, o.z, e.x - o.x, e.y - o.y, e.z - o.z) ?: return null
        return Vector3(origin.x + direction.x * t, origin.y + direction.y * t, origin.z + direction.z * t)
    }

    /**
     * Paints around [point] out to [radiusM] metres. The point is in the stage container's own
     * space, where hand joints are reported (not the stage root's: the first try read it there
     * and never touched the ring). Returns true if the ring's look changed; call [showBrush] to
     * put it on the ring.
     */
    fun brushAt(point: Vector3, radiusM: Float, argb: Int, erase: Boolean = false): Boolean {
        val p = painter ?: return false
        val at = model.convertPositionFrom(point, null)
        val edge = model.convertPositionFrom(Vector3(point.x + radiusM, point.y, point.z), null)
        val r = Vector3(edge.x - at.x, edge.y - at.y, edge.z - at.z).let { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }
        lastGapM = if (r > 0f) p.nearest(at.x, at.y, at.z) * radiusM / r else Float.NaN
        return p.dab(at.x, at.y, at.z, r, argb, erase).also { if (it) painted = true }
    }

    /** Puts the painted texture on the ring. */
    fun showBrush() {
        val p = painter ?: return
        val bitmap = canvas ?: return
        p.writeTo(bitmap)
        val texture = TextureResource.create(bitmap)
        materials.forEach {
            it.setBaseColor(Color4(1f, 1f, 1f, 1f))
            it.setBaseColorTexture(texture)
        }
        shownTexture?.close()
        shownTexture = texture
    }

    /** Wipes the brush strokes off. */
    fun clearBrush() {
        painter?.clear() ?: return
        painted = false
        showBrush()
    }

    /** True once the brush (or a loaded colourway) has changed the ring's texture. */
    private var painted = false

    /** The painted texture as PNG, to save with a colourway; null when nothing was painted. */
    suspend fun exportTexture(): ByteArray? {
        val p = painter ?: return null
        if (!painted) return null
        val bitmap = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
        p.writeTo(bitmap)
        return withContext(Dispatchers.Default) {
            java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }.also { bitmap.recycle() }
    }

    /** Puts a colourway's painted texture (PNG bytes) on the ring. */
    suspend fun loadTexture(png: ByteArray) {
        val p = painter ?: return
        val bitmap = withContext(Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size) } ?: return
        p.load(bitmap)
        bitmap.recycle()
        painted = true
        showBrush()
    }

    override fun destroy(): Boolean {
        val done = root.destroy()
        shownTexture?.close()
        shownTexture = null
        return done
    }
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

    /** What takes the pointer: a box hugging the ring once its surface is read, else a sphere. */
    private var hitBox: Entity? = null
    private var hitBoxFitted = false

    /**
     * Grabbable only while it is being coloured. Worn, the ring sits on the finger that taps,
     * so its grab sphere took every tap and no window button could be pressed.
     *
     * A ring that can be brushed gets a box just around its surface, so the system's ray stops
     * on the ring. (A sphere well beyond the ring made the ray stop in the air; no shape at all
     * let it pass straight through, which looked as if the ring could not be hit.)
     */
    fun setGrabbable(grabbable: Boolean) {
        val bounds = if (grabbable) boundsInRoot() else null
        // Kept as it is, unless a sphere can now become the fitted box (the surface is read
        // after the ring first shows).
        if (grabbable && hitBox != null && (hitBoxFitted || bounds == null)) return
        if (!grabbable && hitBox == null) return
        hitBox?.destroy()
        hitBox = null
        hitBoxFitted = false
        if (!grabbable) return
        val box = Entity().apply {
            components.set(InteractableComponent())
            if (bounds != null) {
                val (lo, hi) = bounds
                components[TransformComponent::class.java]?.setPosition(
                    Vector3((lo.x + hi.x) / 2f, (lo.y + hi.y) / 2f, (lo.z + hi.z) / 2f)
                )
                components.set(
                    CollisionComponent(
                        collisionShape = listOf(ShapeResource.createBox(Vector3(hi.x - lo.x, hi.y - lo.y, hi.z - lo.z))),
                        physicsMaterial = PhysicsMaterialResource(),
                    )
                )
            } else {
                components.set(
                    CollisionComponent(
                        collisionShape = listOf(ShapeResource.createSphere(GRAB_RADIUS_M)),
                        physicsMaterial = PhysicsMaterialResource(),
                    )
                )
            }
        }
        root.addChild(box)
        hitBox = box
        hitBoxFitted = bounds != null
        Log.i(
            "SketchOn",
            if (bounds != null) "hit box fitted: ${bounds.first} to ${bounds.second}" else "hit box: sphere until the surface is read",
        )
    }

    /**
     * The ring's surface as a box in the root's space (lowest and highest corner), or null
     * before the brush has read the surface. The mesh's space is the root's moved, turned and
     * scaled, so four points are enough to carry every corner over.
     */
    private fun boundsInRoot(): Pair<Vector3, Vector3>? {
        val p = painter?.surface?.positions ?: return null
        if (p.isEmpty()) return null
        val o = root.convertPositionFrom(Vector3(0f, 0f, 0f), model)
        val ex = root.convertPositionFrom(Vector3(1f, 0f, 0f), model).let { Vector3(it.x - o.x, it.y - o.y, it.z - o.z) }
        val ey = root.convertPositionFrom(Vector3(0f, 1f, 0f), model).let { Vector3(it.x - o.x, it.y - o.y, it.z - o.z) }
        val ez = root.convertPositionFrom(Vector3(0f, 0f, 1f), model).let { Vector3(it.x - o.x, it.y - o.y, it.z - o.z) }
        val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        var i = 0
        while (i < p.size) {
            val x = p[i]; val y = p[i + 1]; val z = p[i + 2]
            val q = floatArrayOf(
                o.x + ex.x * x + ey.x * y + ez.x * z,
                o.y + ex.y * x + ey.y * y + ez.y * z,
                o.z + ex.z * x + ey.z * y + ez.z * z,
            )
            for (k in 0..2) {
                if (q[k] < lo[k]) lo[k] = q[k]
                if (q[k] > hi[k]) hi[k] = q[k]
            }
            i += 3
        }
        return Vector3(lo[0], lo[1], lo[2]) to Vector3(hi[0], hi[1], hi[2])
    }

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
        /** The painted texture's largest side, in pixels: sharp enough, quick to repaint. */
        private const val BRUSH_TEXTURE_MAX = 1024
        /** A little more than the ring's outer radius, so the whole ring can be grabbed. */
        private const val GRAB_RADIUS_M = OUTER_DIAMETER_M * 0.7f

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
            // Made grabbable when colouring starts (see setGrabbable).
            return GeneratedRingRig(root, model, path)
        }
    }
}
