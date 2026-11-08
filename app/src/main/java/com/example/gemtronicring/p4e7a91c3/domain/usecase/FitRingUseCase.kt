package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.ModelBounds
import com.example.gemtronicring.p4e7a91c3.domain.model.RingFit

/**
 * Fits a generated ring to a finger without finding the hole in the mesh.
 *
 * Tripo keeps +Y up, so a stone drawn on top stays at +Y. The hole is taken to run along the
 * thinner of X and Z: the first real model (2026-11-07) came back with its hole along X, though
 * it was drawn facing the viewer. The wider of X and Z is the band's outer diameter, and the
 * hole centre is half that above the bottom of the band, so a stone on top does not move it.
 * Finding the hole in the mesh is the later fix.
 */
class FitRingUseCase(
    /** Outer diameter on the finger: a 17 mm hole plus a 2 mm band, shown at the app's scale. */
    private val outerDiameterM: Float = 0.021f * DISPLAY_SCALE,
) {
    fun fit(bounds: ModelBounds): RingFit? {
        val sizeX = bounds.maxX - bounds.minX
        val sizeZ = bounds.maxZ - bounds.minZ
        val holeAlongX = sizeX < sizeZ
        val band = maxOf(sizeX, sizeZ)
        if (!(band > 0f) || band.isInfinite()) return null
        val scale = outerDiameterM / band
        val holeX = (bounds.minX + bounds.maxX) / 2f
        val holeY = bounds.minY + band / 2f
        val holeZ = (bounds.minZ + bounds.maxZ) / 2f
        return RingFit(scale, -holeX * scale, -holeY * scale, -holeZ * scale, holeAlongX)
    }

    companion object {
        /** The Gemtronic ring is shown at 3x, because the emulator holds the hand far away. */
        const val DISPLAY_SCALE = 3f
    }
}
