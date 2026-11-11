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
    /** Bumped to bring the panels in front of the user again. */
    val recentre: Int = 0,
    /** Ring size when worn, as a multiple of real size. */
    val ringScale: Float = 1f,
    /** The signed-in user's name, or null. */
    val user: String? = null,
    val login: LoginUiState = LoginUiState(),
    /** True when the worn piece is clay or resin, which render white and can be coloured. */
    val paintable: Boolean = false,
    /** Colour per part (0 band, 1 decoration), as ARGB. Parts not in the map keep their own. */
    val paints: Map<Int, Int> = emptyMap(),
    /** The part the colour wheel colours. */
    val paintPart: Int = 0,
    /** How many parts the loaded ring has: 1, or 2 when the server split off the decoration. */
    val parts: Int = 1,
    /** How the ring is turned while painting, in degrees. */
    val turnYaw: Float = 0f,
    val turnPitch: Float = 0f,
)

data class LoginUiState(val busy: Boolean = false, val error: String? = null)

data class GalleryUiState(
    val pieces: List<GalleryPiece> = emptyList(),
    /** Each piece's render, by piece id, as image file bytes. Filled in as they arrive. */
    val pictures: Map<String, ByteArray> = emptyMap(),
    val loading: Boolean = false,
    /** The piece being fetched to wear. */
    val opening: String? = null,
    val error: String? = null,
)

data class SketchUiState(
    val material: Material = Material.Gold,
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
    data class LogIn(val name: String, val password: String) : RingWearEvent

    data class SignUp(val name: String, val password: String) : RingWearEvent

    data object LogOut : RingWearEvent

    /** Puts the panels in front of the user, wherever they are looking. */
    data object RecentrePanels : RingWearEvent

    data object RingBigger : RingWearEvent

    data object RingSmaller : RingWearEvent

    data object RingRealSize : RingWearEvent

    /** From the home panel when no one is signed in. */
    data object OpenLogin : RingWearEvent

    data object StartSketch : RingWearEvent

    data object OpenGallery : RingWearEvent

    data object ChooseGemtronic : RingWearEvent

    data class WearPiece(val piece: GalleryPiece) : RingWearEvent

    data object GoHome : RingWearEvent

    /** The drawing changed. [sketchDataUri] is a PNG data URI; null when the drawing is empty. */
    data class SketchChanged(val sketchDataUri: String?) : RingWearEvent

    data class ChooseMaterial(val material: Material) : RingWearEvent

    /** Colours the chosen part of the clay or resin ring. [color] is ARGB, from the wheel. */
    data class PaintRing(val color: Int) : RingWearEvent

    data class ChoosePaintPart(val part: Int) : RingWearEvent

    /** Turns the ring held up for painting, in degrees. */
    data class TurnRing(val yaw: Float, val pitch: Float) : RingWearEvent

    /** Back to painting from wearing. */
    data object OpenPainting : RingWearEvent

    /** Painting done: choose a finger. */
    data object FinishPainting : RingWearEvent

    /** The 3D ring finished loading; [parts] is how many colourable parts it has. */
    data class RingLoaded(val parts: Int) : RingWearEvent

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
