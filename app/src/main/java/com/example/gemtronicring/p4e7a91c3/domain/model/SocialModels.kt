package com.example.gemtronicring.p4e7a91c3.domain.model

/**
 * One set of colours on a ring (docs/colourways-plan.md): a colour per part, and optionally a
 * painted texture. Anyone signed in can colour any ring; the ring's author picks its cover
 * from their own colourways.
 */
data class Colourway(
    val id: String,
    val pieceId: String,
    val author: String,
    /** Colour per part (0 band, 1 decoration), as ARGB. */
    val paints: Map<Int, Int>,
    val textureUrl: String? = null,
    val pictureUrl: String? = null,
    val featured: Boolean = false,
    val hidden: Boolean = false,
    val likes: Int = 0,
    val likedByMe: Boolean = false,
    val wearers: Int = 0,
)

/** A ring's page: the ring, its counts, the author's colourways and everyone else's. */
data class RingPage(
    val piece: GalleryPiece,
    val isAuthor: Boolean,
    val coverId: String?,
    val likes: Int,
    val saves: Int,
    val wearers: Int,
    val likedByMe: Boolean,
    val savedByMe: Boolean,
    val authorColourways: List<Colourway>,
    /** Featured first. Hidden ones are only here for the author. */
    val otherColourways: List<Colourway>,
)

/** Whether something in my records can still be worn. */
enum class RecordStatus {
    Live,
    /** The ring's author hid this colourway; only its maker still sees it. */
    Hidden,
    /** The ring (or the colourway) was deleted: it stays listed but cannot be worn. */
    Removed;

    companion object {
        fun of(id: String?): RecordStatus =
            when (id) {
                "hidden" -> Hidden
                "removed" -> Removed
                else -> Live
            }
    }
}

/** A colourway I made, on any ring. */
data class MyColourway(val colourway: Colourway, val pieceName: String?, val pieceAuthor: String?, val onMyRing: Boolean, val status: RecordStatus)

/** A ring I saved or wore, as my records list it. */
data class RingRecord(
    val pieceId: String,
    val name: String?,
    val author: String?,
    val status: RecordStatus,
    /** The colourway worn, or null for the ring as its author left it. */
    val colourwayId: String? = null,
    val paints: Map<Int, Int>? = null,
)

/** Everything of mine: rings I drew, colourways I made, rings I saved, what I wore. */
data class MyLibrary(
    val rings: List<GalleryPiece> = emptyList(),
    val colourways: List<MyColourway> = emptyList(),
    val saved: List<RingRecord> = emptyList(),
    val worn: List<RingRecord> = emptyList(),
)

/** Someone recoloured my ring, or the author featured my colourway. */
data class Notice(
    val id: Long,
    val kind: NoticeKind,
    val pieceId: String,
    val pieceName: String?,
    val actor: String,
    val seen: Boolean,
)

enum class NoticeKind {
    Recoloured,
    Featured,
}
