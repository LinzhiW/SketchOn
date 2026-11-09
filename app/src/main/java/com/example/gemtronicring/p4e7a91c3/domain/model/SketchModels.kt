package com.example.gemtronicring.p4e7a91c3.domain.model

/** What the user is trying on. */
sealed interface RingPiece {
    /** The Gemtronic smart ring, bundled with the app, with its screen and menu. */
    data object Gemtronic : RingPiece

    /** A ring made by the render server, saved on the headset at [path]. */
    data class Generated(val path: String) : RingPiece
}

/** What the ring is made of. Ids match the render server's MATERIALS. */
enum class Material(val id: String, val metal: Boolean) {
    Clay("clay", metal = false),
    Resin("resin", metal = false),
    Gold("gold", metal = true),
    Silver("silver", metal = true),
    RoseGold("rose", metal = true),
    Platinum("platinum", metal = true);

    companion object {
        fun byId(id: String?): Material? = entries.firstOrNull { it.id == id }
    }
}

/** How the ring is designed, separate from its material. Ids match the server's STYLES. */
enum class DesignStyle(val id: String) {
    Refined("refined"),
    Cute("cute"),
    Vintage("vintage"),
    Minimal("minimal"),
    Bold("bold"),
}

/**
 * Which side of the ring the drawing shows. Ids match the server's VIEWS.
 * - RingShape: looking through the hole. The band is a circle with any stone on top. It is
 *   also how a ring lying flat looks from above, and the view Tripo turns into 3D best.
 * - FromAbove: looking down at the stone, as it looks on a hand.
 * - Profile: from the side, showing the band's thickness and the stone's height.
 */
enum class SketchView(val id: String) {
    RingShape("front"),
    FromAbove("top"),
    Profile("side"),
}

/** A finished piece on the render server, shown in the gallery. */
data class GalleryPiece(
    val id: String,
    val name: String?,
    /** Who made it; null for none recorded. */
    val author: String?,
    /** The material id it was made in (see [Material]), or null for older pieces. */
    val material: String?,
    val modelUrl: String,
    val pictureUrl: String?,
)

/** A point of a stroke, in the drawing's own pixels. */
data class StrokePoint(val x: Float, val y: Float)
