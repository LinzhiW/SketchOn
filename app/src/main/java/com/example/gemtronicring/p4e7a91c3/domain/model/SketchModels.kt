package com.example.gemtronicring.p4e7a91c3.domain.model

/** What the user is trying on. */
sealed interface RingPiece {
    /** The Gemtronic smart ring, bundled with the app, with its screen and menu. */
    data object Gemtronic : RingPiece

    /** A ring the user drew, made into a GLB by the render server. */
    data class Generated(val path: String) : RingPiece
}

/** Material choices. The ids match the render server's STYLES. */
enum class RenderStyle(val id: String) {
    Gold("gold"),
    Silver("silver"),
    Rose("rose"),
    Platinum("platinum"),
    Vintage("vintage"),
}

/** Axis-aligned bounds of a model in its own units, free of SDK types. */
data class ModelBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
)

/**
 * Scale, and the offset to apply after scaling, so the ring hole sits on the joint. When
 * [holeAlongX] is true, the scaled model is then turned a quarter turn about Y so the hole
 * runs along Z, which is the finger's axis.
 */
data class RingFit(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float,
    val holeAlongX: Boolean,
)
