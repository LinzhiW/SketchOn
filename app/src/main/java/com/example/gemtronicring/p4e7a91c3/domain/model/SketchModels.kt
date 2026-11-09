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
