package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.domain.model.DesignStyle
import com.example.gemtronicring.p4e7a91c3.domain.model.GalleryPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.Material
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.model.SketchView
import com.example.gemtronicring.p4e7a91c3.domain.model.WornFinger

data class RingWearUiState(
    val phase: RingPhase = RingPhase.Home,
    val piece: RingPiece = RingPiece.Gemtronic,
    val finger: WornFinger = WornFinger.Index,
    val frames: List<String> = emptyList(),
    val activeFrame: String? = null,
    val sketch: SketchUiState = SketchUiState(),
    val gallery: GalleryUiState = GalleryUiState(),
)

data class GalleryUiState(
    val pieces: List<GalleryPiece> = emptyList(),
    /** Each piece's render, by piece id, as image file bytes. Filled in as they arrive. */
    val pictures: Map<String, ByteArray> = emptyMap(),
    val loading: Boolean = false,
    /** The piece being fetched to wear. */
    val opening: String? = null,
    val error: String? = null,
)

/** A soft pink, so clay and resin start in a colour that suits them. */
const val DEFAULT_COLOR: Int = 0xFFF4B6C2.toInt()

data class SketchUiState(
    val material: Material = Material.Gold,
    /** ARGB colour from the colour wheel. Used for clay and resin only. */
    val color: Int = DEFAULT_COLOR,
    val style: DesignStyle = DesignStyle.Refined,
    val view: SketchView = SketchView.RingShape,
    /** Drawing help: average out wobble. */
    val smooth: Boolean = true,
    /** Drawing help: snap near-circles to ellipses and close near-closed strokes. */
    val shapes: Boolean = true,
    /** The latest render, as a data URI. */
    val render: String? = null,
    val rendering: Boolean = false,
    val lastRenderMs: Long? = null,
    val error: String? = null,
    /** Seconds since the 3D conversion started. */
    val making3dSeconds: Int = 0,
)

sealed interface RingWearEvent {
    data object StartSketch : RingWearEvent

    data object OpenGallery : RingWearEvent

    data object ChooseGemtronic : RingWearEvent

    data class WearPiece(val piece: GalleryPiece) : RingWearEvent

    data object GoHome : RingWearEvent

    /** The drawing changed. [sketchDataUri] is a PNG data URI; null when the drawing is empty. */
    data class SketchChanged(val sketchDataUri: String?) : RingWearEvent

    data class ChooseMaterial(val material: Material) : RingWearEvent

    /** [color] is ARGB, from the colour wheel. */
    data class ChooseColor(val color: Int) : RingWearEvent

    data class ChooseStyle(val style: DesignStyle) : RingWearEvent

    data class ChooseView(val view: SketchView) : RingWearEvent

    data object ToggleSmooth : RingWearEvent

    data object ToggleShapes : RingWearEvent

    /** Asks for a name first. */
    data object Make3d : RingWearEvent

    data class ConfirmMake3d(val name: String) : RingWearEvent

    data object Cancel3d : RingWearEvent

    data class SelectFinger(val finger: WornFinger) : RingWearEvent

    data object ConfirmFinger : RingWearEvent

    data object ChangeFinger : RingWearEvent

    data class HandMoved(val sample: RingSample) : RingWearEvent

    /** The buttons in the control panel, which do what the ring's gestures do. */
    data class MenuInput(val gesture: RingGesture) : RingWearEvent

    data object HandLost : RingWearEvent

    data object TrackingDenied : RingWearEvent
}
