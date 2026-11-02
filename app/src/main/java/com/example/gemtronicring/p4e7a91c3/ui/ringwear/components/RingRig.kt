package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import com.example.gemtronicring.p4e7a91c3.domain.model.RingPage
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI

/** The ring model plus one screen disc per page, under one [root] entity. */
internal class RingRig(val root: Entity, private val screens: List<Entity>) {
    // One disc per page: swapping a ModelComponent's material releases the one taken off.
    fun showPage(index: Int) {
        screens.forEachIndexed { i, screen -> screen.enabled = i == index }
    }

    companion object {
        // Measured from ring.glb, whose units are millimetres.
        private const val MM_TO_M = 0.001f
        private const val DISPLAY_SCALE = 1.5f
        private val HOLE_CENTRE_MM = Vector3(-0.21f, -8.02f, 0.78f)
        private val SCREEN_CENTRE_MM = Vector3(-0.24f, 10.05f, 0.78f)
        private const val SCREEN_DIAMETER_MM = 17.9f
        private const val WORDMARK_NODE = "screen_content"
        private const val DOME_NODE = "四面体球"
        private const val DOME_OPACITY = 0.14f
        private const val DOME_ROUGHNESS = 0.05f
        private const val RING_MODEL_URI = "asset://ring.glb"
        private val HALF_TURN = Quat(Vector3(0f, 1f, 0f), PI.toFloat())

        suspend fun create(pages: List<RingPage>): RingRig {
            val scale = MM_TO_M * DISPLAY_SCALE
            val model = Entity.loadSuspend(RING_MODEL_URI)
            model.findEntity(WORDMARK_NODE)?.enabled = false
            model.findEntity(DOME_NODE)?.let(::makeDomeClear)
            model.components[TransformComponent::class.java]?.apply {
                setScaleVector(Vector3(scale, scale, scale))
                // Half a turn about the screen's normal puts the charging port at the bottom.
                setQuaternion(HALF_TURN)
                setPosition(
                    Vector3(
                        HOLE_CENTRE_MM.x * scale,
                        -HOLE_CENTRE_MM.y * scale,
                        HOLE_CENTRE_MM.z * scale,
                    )
                )
            }

            val screens = pages.map { page -> createScreen(TextureResource.load(page.assetPath)) }
            screens.forEach(model::addChild)

            val root = Entity().apply { enabled = false }
            root.addChild(model)
            return RingRig(root, screens).apply { showPage(0) }
        }

        /** PICO has no glass transmission, so the dome would render solid and hide the screen. */
        private fun makeDomeClear(dome: Entity) {
            val glass =
                PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT).apply {
                    setOpacity(DOME_OPACITY)
                    setMetallic(0f)
                    setRoughness(DOME_ROUGHNESS)
                    setDepthWrite(false)
                }
            dome.components[ModelComponent::class.java]?.materials?.set(0, glass)
        }

        private fun createScreen(page: TextureResource): Entity {
            val material = UnlitMaterial.create().apply { setBaseColorTexture(page) }
            val disc =
                MeshResource.createPlane(
                    SCREEN_DIAMETER_MM,
                    SCREEN_DIAMETER_MM,
                    SCREEN_DIAMETER_MM / 2f,
                )
            return Entity().apply {
                components.set(ModelComponent(disc, material))
                components[TransformComponent::class.java]?.apply {
                    setPosition(SCREEN_CENTRE_MM)
                    // The plane faces +Z and the ring's screen faces +Y. The half turn keeps the
                    // page upright inside the turned model.
                    setQuaternion(HALF_TURN * Quat(Vector3(1f, 0f, 0f), (-PI / 2).toFloat()))
                }
            }
        }
    }
}
