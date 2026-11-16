package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import com.example.gemtronicring.p4e7a91c3.domain.model.Colourway
import com.example.gemtronicring.p4e7a91c3.domain.model.DesignStyle
import com.example.gemtronicring.p4e7a91c3.domain.model.MyLibrary
import com.example.gemtronicring.p4e7a91c3.domain.model.Notice
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPage
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
    /** A 3D ring being made while the user is elsewhere, or null. */
    val madeRing: MadeRing? = null,
    /** Painting with the fingertip (true), or colouring the whole ring at once (false). */
    val brush: Boolean = true,
    /** True when the ring has a texture the brush can paint on. */
    val brushAvailable: Boolean = false,
    /** True once the controllers have been seen while painting. */
    val controllersSeen: Boolean = false,
    /** The brush's colour, as ARGB. */
    val brushColor: Int = 0xFFE5484D.toInt(),
    /** The brush's size on the panel's slider, 0 (finest) to 1 (broadest). */
    val brushSize: Float = DEFAULT_BRUSH_SIZE,
    /** True while the brush wipes paint off instead of adding it. */
    val erasing: Boolean = false,
    /** Bumped to wipe the brush strokes off. */
    val brushClear: Int = 0,
    /** How far the user has moved the ring from where it is held for painting, in metres. */
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val moveZ: Float = 0f,
    /** The server id of the generated ring on show (its 3D job id), or null for the Gemtronic ring. */
    val pieceId: String? = null,
    /** Bumped to ask the stage for the painted texture, to save it with the colourway. */
    val textureRequest: Int = 0,
    /** A colourway's painted texture (PNG bytes) for the stage to put on the ring, or null. */
    val textureToLoad: ByteArray? = null,
    /** Bumped each time [textureToLoad] changes, so the same bytes can be loaded again. */
    val textureLoad: Int = 0,
    val saveColour: SaveColourUiState = SaveColourUiState(),
    val ringPage: RingPageUiState = RingPageUiState(),
    val mine: MineUiState = MineUiState(),
)

/** Saving the colours on the ring being painted. */
data class SaveColourUiState(
    val saving: Boolean = false,
    /** True once these colours are saved; any change to them clears it. */
    val saved: Boolean = false,
    /** Saving needs an account: shown with a log in button. */
    val needsLogin: Boolean = false,
    val error: String? = null,
)

data class RingPageUiState(
    val page: RingPage? = null,
    /** The ring's render, as image file bytes. */
    val picture: ByteArray? = null,
    val loading: Boolean = false,
    /** Asking before the author deletes the ring. */
    val confirmDelete: Boolean = false,
    val error: String? = null,
)

enum class MineTab {
    Creations,
    Saved,
    Worn,
    Notices,
}

data class MineUiState(
    val tab: MineTab = MineTab.Creations,
    val library: MyLibrary? = null,
    val notices: List<Notice> = emptyList(),
    /** Notifications not yet seen, for the dot on the home panel. */
    val unseen: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

/** A 3D ring made in the background, for the notice shown above every panel. */
sealed interface MadeRing {
    data object Making : MadeRing

    data object Ready : MadeRing

    data class Failed(val reason: String) : MadeRing
}

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

    /** Back to the step before this one (home when there is none). */
    data object Back : RingWearEvent

    /** Back to the waiting screen of the ring being made. */
    data object WatchMaking : RingWearEvent

    /** Puts on the ring that was made in the background. */
    data object WearMade : RingWearEvent

    /** Closes the notice about a ring that could not be made. */
    data object DismissMade : RingWearEvent

    /** Paint with the fingertip (true) or colour the whole ring (false). */
    data class SetBrush(val on: Boolean) : RingWearEvent

    /** Wipes the brush strokes off. */
    data object ClearBrush : RingWearEvent

    /** Saves the colours on the ring being painted as a colourway. */
    data object SaveColourway : RingWearEvent

    /** The stage's answer to a texture request: the painted texture as PNG, or null if unpainted. */
    data class TextureExported(val png: ByteArray?) : RingWearEvent

    /** Opens a ring's page. */
    data class OpenRing(val pieceId: String) : RingWearEvent

    /** Puts on the ring on the open page, as made (null) or in a colourway. */
    data class WearColourway(val colourway: Colourway?) : RingWearEvent

    /** Colours the ring on the open page anew, to save as one's own colourway. */
    data object Recolour : RingWearEvent

    data class LikeRing(val on: Boolean) : RingWearEvent

    data class SaveRing(val on: Boolean) : RingWearEvent

    data class LikeColourway(val id: String, val on: Boolean) : RingWearEvent

    /** Author only: the cover is one of their own colourways. */
    data class SetCover(val id: String) : RingWearEvent

    data class FeatureColourway(val id: String, val on: Boolean) : RingWearEvent

    data class HideColourway(val id: String, val on: Boolean) : RingWearEvent

    data class DeleteColourway(val id: String) : RingWearEvent

    /** Author only. The first press asks; the second ([sure]) deletes. */
    data class DeleteRing(val sure: Boolean) : RingWearEvent

    data object OpenMine : RingWearEvent

    data class ChooseMineTab(val tab: MineTab) : RingWearEvent

    /** The brush size from the panel's slider, 0 to 1. */
    data class SetBrushSize(val size: Float) : RingWearEvent

    /** Paint (false) or wipe paint off (true) with the brush. */
    data class SetErasing(val on: Boolean) : RingWearEvent

    /** The controllers were seen while painting, so the panel explains them. */
    data class ControllersSeen(val seen: Boolean) : RingWearEvent

    /** Whether the loaded ring can be painted with the brush. */
    data class BrushAvailable(val available: Boolean) : RingWearEvent

    /** Moves the ring held for painting, in metres. */
    data class MoveRing(val dx: Float, val dy: Float, val dz: Float) : RingWearEvent

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

/** Where the brush size slider starts: about a 1 cm brush. */
const val DEFAULT_BRUSH_SIZE = 0.3f
