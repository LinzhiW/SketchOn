package com.example.gemtronicring.p4e7a91c3.domain.model

enum class RingPhase {
    FingerChoosing,
    HandSearching,
    RingWorn,
    TrackingUnavailable,
}

enum class WornFinger {
    Thumb,
    Index,
    Middle,
    Ring,
    Little,
}

/** The four inputs of the real ring: firmware/core/menu_gesture.h in the Aspirium repo. */
enum class RingGesture {
    Next,
    Previous,
    Confirm,
    Back,
}

/** One hand reading, free of SDK types. */
data class RingSample(
    val timeMs: Long,
    /** World-up component of the ring's left-to-right axis; negative when the right side dips. */
    val rightAxisUp: Float,
    val heightM: Float,
)

/** One entry at a menu level: the firmware frame it shows, and the level it opens, if any. */
data class MenuItem(val frame: String, val opens: String? = null)

data class RingMenu(val root: String, val levels: Map<String, List<MenuItem>>) {
    val frames: List<String>
        get() = levels.values.flatten().map { it.frame }.distinct()
}

/** Where the ring is in its menu. [trail] holds the positions that Back returns to. */
data class MenuCursor(
    val level: String,
    val index: Int = 0,
    val trail: List<Pair<String, Int>> = emptyList(),
)
