package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.RingSample
import com.example.gemtronicring.p4e7a91c3.domain.model.WornFinger
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearEvent
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearUiState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.LocalStageUpperLimbRenderModelManager
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.ui.platform.LengthUnit
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.ui.platform.ability.UpperLimbRenderMode
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private const val TAG = "SketchOn"
private const val HAND_LOST_DELAY_MS = 300L

private val RING_RIGHT_AXIS = Vector3(1f, 0f, 0f)

/** The first segment of a finger, where a ring sits. */
private fun fingerSegment(finger: WornFinger): Pair<HandJoint.Index, HandJoint.Index> =
    when (finger) {
        WornFinger.Thumb -> HandJoint.Index.THUMB_PROXIMAL to HandJoint.Index.THUMB_DISTAL
        WornFinger.Index -> HandJoint.Index.INDEX_PROXIMAL to HandJoint.Index.INDEX_INTERMEDIATE
        WornFinger.Middle -> HandJoint.Index.MIDDLE_PROXIMAL to HandJoint.Index.MIDDLE_INTERMEDIATE
        WornFinger.Ring -> HandJoint.Index.RING_PROXIMAL to HandJoint.Index.RING_INTERMEDIATE
        WornFinger.Little -> HandJoint.Index.LITTLE_PROXIMAL to HandJoint.Index.LITTLE_INTERMEDIATE
    }

private fun segmentMiddle(hand: HandPose, finger: WornFinger): Vector3 {
    val (base, next) = fingerSegment(finger)
    val a = hand[base].position
    val b = hand[next].position
    return Vector3((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f)
}

/**
 * The hand only if the headset is really tracking it. A hand it is not tracking can still come
 * as a pose with every joint at (0, 0, 0) (seen on a headset); that is no hand.
 */
internal fun HandPose?.tracked(): HandPose? = this?.takeIf { it[HandJoint.Index.WRIST].position != Vector3.ZERO }

/** Keeps the ring on the hand that was raised first, until that hand goes away. */
private class RaisedHand {
    private var usingRight = true

    fun pick(data: HandTrackingData): HandPose? {
        val right = data.right.tracked()
        val left = data.left.tracked()
        if (usingRight && right == null && left != null) usingRight = false
        if (!usingRight && left == null && right != null) usingRight = true
        return if (usingRight) right else left
    }
}

/** Stage root of the app: reads the hand, wears the ring on it, and hosts the panels. */
@Composable
internal fun RingOnHand(state: RingWearUiState, onEvent: (RingWearEvent) -> Unit) {
    val provider = remember { HandTrackingProvider() }
    val data by provider.dataFlow.collectAsState(initial = HandTrackingData(null, null, 0L))
    val currentOnEvent by rememberUpdatedState(onEvent)

    DisposableEffect(provider) {
        provider.start()
        if (provider.supportState == DataProvider.SupportState.WITHOUT_PERMISSION) {
            currentOnEvent(RingWearEvent.TrackingDenied)
        }
        onDispose { provider.stop() }
    }

    val raisedHand = remember { RaisedHand() }
    val hand = raisedHand.pick(data)

    LaunchedEffect(hand) {
        val pose = hand ?: return@LaunchedEffect
        // Gestures read the worn finger, as the sensor in the real ring does.
        val (base, _) = fingerSegment(state.finger)
        currentOnEvent(
            RingWearEvent.HandMoved(
                RingSample(
                    timeMs = SystemClock.elapsedRealtime(),
                    rightAxisUp = pose[base].rotation.rotateVector(RING_RIGHT_AXIS).y,
                    heightM = segmentMiddle(pose, state.finger).y,
                )
            )
        )
    }
    val handVisible = hand != null
    LaunchedEffect(handVisible) {
        if (!handVisible) {
            // A short gap in tracking should not make the ring blink.
            delay(HAND_LOST_DELAY_MS)
            currentOnEvent(RingWearEvent.HandLost)
        }
    }

    // The headset draws the real hand, cut out of the camera image, over everything in the stage
    // (Visible mode). A worn ring then sat behind the finger, faint at the cut's soft edge. While
    // the ring is on, the real hand is not drawn over it; elsewhere the system's choice stands.
    val limbs = LocalStageUpperLimbRenderModelManager.current
    val ringOnHand = state.phase == RingPhase.RingWorn || state.phase == RingPhase.HandSearching
    LaunchedEffect(ringOnHand) {
        val mode = if (ringOnHand) UpperLimbRenderMode.Hidden else UpperLimbRenderMode.Default
        Log.i(TAG, "real hand drawing: $mode -> ${limbs.setUpperLimbRenderMode(mode)}")
    }
    DisposableEffect(Unit) { onDispose { limbs.setUpperLimbRenderMode(UpperLimbRenderMode.Default) } }

    var rig by remember { mutableStateOf<WornRing?>(null) }
    val stageRoot = remember { Entity() }
    // The panels live in a system window now; this frame, placed in front of the user at each
    // step, is where the ring is held up for painting.
    val panelsRoot = remember { Entity().also { stageRoot.addChild(it) } }
    var panelsYaw by remember { mutableStateOf(Quat(Vector3(0f, 1f, 0f), 0f)) }
    // The headset's own pose, in world space (only reported in full space).
    val hmdProvider = remember { HMDTrackingProvider() }
    DisposableEffect(hmdProvider) {
        hmdProvider.start()
        onDispose { hmdProvider.stop() }
    }
    // Every new panel, and every press of "Bring panels here", comes up in front of the user.
    val currentData by rememberUpdatedState(data)
    LaunchedEffect(state.phase, state.recentre) {
        // In front of the headset as soon as it reports; failing that, in front of a hand.
        var waited = 0L
        while (waited < HAND_WAIT_MS) {
            val hmd = hmdProvider.latestData
            val turn =
                if (hmd.hmdPose.position != Vector3.ZERO) placeByHead(panelsRoot, stageRoot, hmd.hmdPose, waited)
                else if (waited >= HEAD_WAIT_MS) placeByHand(panelsRoot, stageRoot, currentData, waited)
                else null
            if (turn != null) {
                panelsYaw = turn
                return@LaunchedEffect
            }
            delay(HAND_POLL_MS)
            waited += HAND_POLL_MS
        }
        Log.i(TAG, "neither head nor hand seen; panels left where they are")
    }
    // Both hands pinched for a second brings the panels in front of the hands: works even when
    // no panel is in sight to press "Bring panels here" on.
    LaunchedEffect(Unit) {
        var heldMs = 0L
        while (true) {
            delay(HAND_POLL_MS)
            val d = currentData
            val bothPinched =
                d.leftPinch?.let { it.isReady && it.pinchValue > PINCH_ON } == true &&
                    d.rightPinch?.let { it.isReady && it.pinchValue > PINCH_ON } == true
            heldMs = if (bothPinched) heldMs + HAND_POLL_MS else 0L
            if (heldMs >= SUMMON_HOLD_MS) {
                heldMs = Long.MIN_VALUE / 2 // once per hold
                placeByBothHands(panelsRoot, stageRoot, d)?.let { panelsYaw = it }
            }
            if (!bothPinched) heldMs = 0L
        }
    }
    // Where the hands are, every two seconds, to see where the stage's origin is on a headset.
    LaunchedEffect(Unit) {
        while (true) {
            delay(HAND_LOG_MS)
            val d = currentData
            Log.i(TAG, "hands: right=${d.right?.get(HandJoint.Index.WRIST)?.position} " +
                "left=${d.left?.get(HandJoint.Index.WRIST)?.position} " +
                "rightAim=${d.rightAim?.takeIf { it.isReady }?.aimPose?.position}")
        }
    }
    // Load whichever ring is chosen, replacing the one before.
    LaunchedEffect(state.piece, state.frames) {
        rig?.destroy()
        rig = null
        val piece = state.piece
        runCatching {
                when (piece) {
                    RingPiece.Gemtronic ->
                        if (state.frames.isEmpty()) null
                        else RingRig.create(state.frames).also { stageRoot.addChild(it.root) }
                    is RingPiece.Generated -> GeneratedRingRig.create(piece.path, stageRoot)
                }
            }
            .onSuccess {
                Log.i(TAG, "ring loaded: ${piece::class.simpleName}, ${it?.parts() ?: 0} parts")
                rig = it
                if (it != null) currentOnEvent(RingWearEvent.RingLoaded(it.parts()))
            }
            .onFailure { Log.e(TAG, "Could not load the ring", it) }
    }
    // While painting, nothing moves the ring through the view's update (that runs on hand
    // data), so it is placed here whenever the ring, the phase or the turn changes.
    LaunchedEffect(
        rig, state.phase, state.turnYaw, state.turnPitch, panelsYaw,
        state.moveX, state.moveY, state.moveZ, state.brush, state.brushAvailable,
    ) {
        val ring = rig ?: return@LaunchedEffect
        // Takes the pointer only while being coloured; worn, it must not catch the finger's taps.
        // While brushing it still takes it, so the system's ray stops on the ring instead of
        // passing through; the drag below then leaves it alone (painting must not turn it).
        (ring as? GeneratedRingRig)?.setGrabbable(state.phase == RingPhase.Painting)
        if (state.phase == RingPhase.Painting) {
            ring.root.enabled = true
            holdForPainting(
                ring.root, stageRoot, panelsRoot, panelsYaw, state.turnYaw, state.turnPitch,
                Vector3(state.moveX, state.moveY, state.moveZ),
            )
        } else if (state.phase != RingPhase.RingWorn) {
            ring.root.enabled = false
            ring.root.components[TransformComponent::class.java]?.setScaleVector(Vector3(1f, 1f, 1f))
        }
    }
    LaunchedEffect(rig, state.paints) {
        val ring = rig as? GeneratedRingRig ?: return@LaunchedEffect
        state.paints.forEach { (part, colour) -> ring.paint(part, colour) }
    }

    // The brush: while colouring, the index fingertip paints wherever it touches the ring.
    var brushReady by remember { mutableStateOf(false) }
    LaunchedEffect(rig, state.paintable) {
        brushReady = false
        currentOnEvent(RingWearEvent.BrushAvailable(false))
        val ring = rig as? GeneratedRingRig ?: return@LaunchedEffect
        if (!state.paintable) return@LaunchedEffect
        ring.prepareBrush()
        brushReady = ring.brushable
        currentOnEvent(RingWearEvent.BrushAvailable(brushReady))
    }
    LaunchedEffect(rig, state.brushClear) {
        if (state.brushClear > 0) (rig as? GeneratedRingRig)?.clearBrush()
    }
    // Saving a colourway: the view model asks for the painted texture, and gets it (or null).
    LaunchedEffect(state.textureRequest) {
        if (state.textureRequest == 0) return@LaunchedEffect
        val png = (rig as? GeneratedRingRig)?.exportTexture()
        Log.i(TAG, "texture for the colourway: ${png?.size ?: 0} bytes")
        currentOnEvent(RingWearEvent.TextureExported(png))
    }
    // Wearing a colourway: its painted texture goes on once the ring's surface has been read.
    LaunchedEffect(rig, brushReady, state.textureLoad) {
        val ring = rig as? GeneratedRingRig ?: return@LaunchedEffect
        val png = state.textureToLoad ?: return@LaunchedEffect
        if (!brushReady) return@LaunchedEffect
        ring.loadTexture(png)
        Log.i(TAG, "colourway texture put on the ring: ${png.size} bytes")
    }
    val brushColor by rememberUpdatedState(state.brushColor)
    val brushRadiusM by rememberUpdatedState(brushRadius(state.brushSize))
    val erasing by rememberUpdatedState(state.erasing)
    // The controllers: the right one paints where it points while its trigger is held, the
    // left one turns the ring while its grip is held, and the right grip near the ring moves it.
    val controllers = remember { ControllerTrackingProvider() }
    val buttons = remember { LatestButtons() }
    DisposableEffect(controllers) {
        val listener = ControllerTrackingProvider.ControllerActionListener { buttons.latest = it }
        controllers.addControllerActionListener(listener)
        controllers.start()
        onDispose {
            controllers.removeControllerActionListener(listener)
            controllers.stop()
        }
    }
    val kit = remember { ControllerBrush(stageRoot) }
    LaunchedEffect(rig, brushReady, state.phase, state.brush) {
        val ring = rig as? GeneratedRingRig ?: return@LaunchedEffect
        if (!brushReady || !state.brush || state.phase != RingPhase.Painting) return@LaunchedEffect
        Log.i(TAG, "brush on: right hand paints, left hand turns")
        var dirty = false
        var lastShown = 0L
        var lastLog = 0L
        var painted = 0
        var shown = 0
        var grabbedAt: Vector3? = null
        // Where the left hand or controller was at the last step of a turn; null when not turning.
        var turningFrom: Vector3? = null
        var turns = 0
        var sawControllers = false
        try { while (true) {
            val d = currentData
            var gap = Float.NaN
            // Controllers put down keep reporting where they lie, so a tracked hand decides:
            // the headset only tracks hands once the controllers are set aside.
            val hands = listOfNotNull(d.right.tracked(), d.left.tracked())
            val pads = if (hands.isEmpty()) runCatching { controllers.latestData }.getOrNull() else null
            val left = pads?.left
            val right = pads?.right
            val pressed = buttons.latest?.right
            val mode = if (left != null || right != null) "controller" else "fingertip"
            // The left hand turns the ring: grip held on the controller, or a pinch without one.
            // Moving it sideways turns the ring round, up or down tips it.
            val leftPressed = buttons.latest?.left
            // Grip or trigger: on a headset the grip alone never turned it (0 turn steps), so
            // either one held turns the ring.
            val turnHand: Vector3? =
                if (left != null) left.position.takeIf {
                    (leftPressed?.gripValue ?: 0f) > GRIP_ON || (leftPressed?.triggerValue ?: 0f) > TRIGGER_ON
                }
                else d.left.tracked()?.takeIf { d.leftPinch?.let { p -> p.isReady && p.pinchValue > PINCH_ON } == true }
                    ?.get(HandJoint.Index.INDEX_TIP)?.position
            if (turnHand != null) {
                val from = turningFrom
                if (from != null) {
                    val dx = turnHand.x - from.x
                    val dy = turnHand.y - from.y
                    if (dx != 0f || dy != 0f) {
                        currentOnEvent(RingWearEvent.TurnRing(dx * TURN_PER_M, -dy * TURN_PER_M))
                        turns++
                    }
                }
                turningFrom = turnHand
            } else {
                turningFrom = null
            }
            if (left != null || right != null) {
                if (!sawControllers) {
                    sawControllers = true
                    currentOnEvent(RingWearEvent.ControllersSeen(true))
                }
                // Where the controller points on the ring: the brush finds the spot itself from
                // the ring's own triangles, so it lands on the surface, not on the grab box.
                val aimAt = right?.let { ring.rayHit(it.position, kit.forwardOf(it)) }
                kit.show(right, brushColor, erasing, brushRadiusM, aimAt)
                if (right != null && (pressed?.triggerValue ?: 0f) > TRIGGER_ON) {
                    if (ring.brushAt(aimAt ?: kit.tipOf(right), brushRadiusM, brushColor, erasing)) {
                        dirty = true
                        painted++
                    }
                    gap = ring.lastGapM
                }
                // Grip squeezed near the ring: the ring follows the controller until let go.
                if (right != null && (pressed?.gripValue ?: 0f) > GRIP_ON) {
                    val at = stageRoot.convertPositionFrom(right.position, null)
                    val from = grabbedAt
                    if (from != null) {
                        currentOnEvent(RingWearEvent.MoveRing(at.x - from.x, at.y - from.y, at.z - from.z))
                        grabbedAt = at
                    } else {
                        val centre = ring.root.components[TransformComponent::class.java]?.position
                        if (centre != null && distance(at, centre) < GRAB_REACH_M) grabbedAt = at
                    }
                } else {
                    grabbedAt = null
                }
            } else {
                // The right hand holds a pen while pinching, like a controller held in the hand:
                // the pen points where the hand aims, a dot shows where that meets the ring, and
                // the pinch paints there. (Touching the ring with a fingertip almost never
                // reached it: the ring hangs in the air.) The left hand is for turning.
                val aim = d.rightAim?.takeIf { it.isReady && d.right.tracked() != null }
                val pinched = d.rightPinch?.let { it.isReady && it.pinchValue > PINCH_ON } == true
                if (aim != null) {
                    val from = aim.aimPose.position
                    val dir = aim.aimPose.rotation.rotateVector(Vector3(0f, 0f, -1f))
                    val hit = ring.rayHit(from, dir)
                    kit.showPen(from, dir, hit, brushColor, erasing, brushRadiusM)
                    if (pinched && hit != null) {
                        if (ring.brushAt(hit, brushRadiusM, brushColor, erasing)) {
                            dirty = true
                            painted++
                        }
                        gap = ring.lastGapM
                    }
                } else {
                    kit.hide()
                }
                d.right.tracked()?.let { pose ->
                    val tip = pose[HandJoint.Index.INDEX_TIP].position
                    if (ring.brushAt(tip, brushRadiusM, brushColor, erasing)) {
                        dirty = true
                        painted++
                    }
                    if (!(ring.lastGapM >= gap)) gap = ring.lastGapM
                }
            }
            val now = SystemClock.elapsedRealtime()
            if (dirty && now - lastShown >= BRUSH_SHOW_MS) {
                ring.showBrush()
                shown++
                dirty = false
                lastShown = now
            }
            if (now - lastLog >= BRUSH_LOG_MS) {
                Log.i(
                    TAG,
                    "brush: $mode ${"%.3f".format(gap)} m from the ring, " +
                        "$painted dabs, $shown uploads, $turns turn steps, size ${"%.3f".format(brushRadiusM)} m, " +
                        "right trigger=${pressed?.triggerValue} grip=${pressed?.gripValue}, " +
                        "left trigger=${leftPressed?.triggerValue} grip=${leftPressed?.gripValue}, " +
                        "hand aim=${d.rightAim?.isReady} pinch=${d.rightPinch?.pinchValue}",
                )
                lastLog = now
            }
            delay(BRUSH_POLL_MS)
        } } finally {
            kit.hide()
        }
    }

    // A small dot under the ring held for painting: pinch it and drag to move the ring.
    val handle = remember { makeHandle().also { stageRoot.addChild(it) } }
    LaunchedEffect(rig, state.phase, state.turnYaw, state.turnPitch, panelsYaw, state.moveX, state.moveY, state.moveZ) {
        val ring = rig
        handle.enabled = ring != null && state.phase == RingPhase.Painting
        if (ring == null || state.phase != RingPhase.Painting) return@LaunchedEffect
        val at = ring.root.components[TransformComponent::class.java]?.position ?: return@LaunchedEffect
        handle.components[TransformComponent::class.java]?.setPosition(Vector3(at.x, at.y - HANDLE_BELOW_M, at.z))
    }
    val converter = LocalPhysicalLengthConverter.current
    val density = LocalDensity.current
    // Metres per pixel of drag in this view.
    val metresPerPx = remember(converter, density) { converter.dpToLength(1.dp, LengthUnit.Meters) / density.density }

    val context = LocalContext.current
    val currentPhase by rememberUpdatedState(state.phase)
    val currentRig by rememberUpdatedState(rig)
    val currentlyBrushing by rememberUpdatedState(state.brush && state.brushAvailable)
    SpatialView(
        modifier =
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // Dragging the dot moves the ring. The view's y runs down, the stage's up.
                    detectSpatialDragGesture(context = context, targetedToEntity = TargetEntity.any { it == handle }) {
                        if (currentPhase == RingPhase.Painting) {
                            val m = metresPerPx
                            currentOnEvent(
                                RingWearEvent.MoveRing(it.dragAmount.x * m, -it.dragAmount.y * m, it.dragAmount.z * m)
                            )
                        }
                    }
                }
                .pointerInput(Unit) {
                // Dragging the ring itself turns it while it is being coloured.
                // Only the ring itself: drags on the panels (the drawing canvas) must stay theirs.
                val onRing =
                    TargetEntity.any { hit ->
                        var e: Entity? = hit
                        val ringRoot = currentRig?.root
                        while (e != null && e != ringRoot) e = e.getParent()
                        e != null
                    }
                detectSpatialDragGesture(context = context, targetedToEntity = onRing) {
                    // While brushing, the right trigger on the ring paints; the left hand turns.
                    if (currentPhase == RingPhase.Painting && !currentlyBrushing) {
                        currentOnEvent(
                            RingWearEvent.TurnRing(it.dragAmount.x * TURN_PER_PX, it.dragAmount.y * TURN_PER_PX)
                        )
                    }
                }
            },
        update = { _, _ ->
            rig?.let { ring ->
                ring.showFrame(state.activeFrame)
                if (state.phase == RingPhase.Painting) {
                    // Placed by the effect below: nothing here changes while painting.
                } else {
                    ring.root.enabled = state.phase == RingPhase.RingWorn
                    // The rigs are built at 3x for the emulator; worn, the user sets the size.
                    val size = state.ringScale / RIG_SCALE
                    ring.root.components[TransformComponent::class.java]?.setScaleVector(Vector3(size, size, size))
                    if (hand != null) wearOnFinger(ring.root, stageRoot, hand, state.finger, flip = ring is GeneratedRingRig)
                }
            }
        },
    ) { content, _ ->
        content.addEntity(stageRoot)
    }
}

private const val TURN_PER_PX = 0.4f
private const val HAND_POLL_MS = 100L
private const val HAND_WAIT_MS = 10_000L
/** How long to wait for the headset's pose before falling back to a hand. */
private const val HEAD_WAIT_MS = 2000L
private const val HAND_LOG_MS = 2000L
private const val SUMMON_HOLD_MS = 1000L
private const val PINCH_ON = 0.8f
/** A hand held up in front sits about this far below the eyes, and this far ahead of them. */
private const val HAND_BELOW_HEAD_M = 0.35f
private const val HAND_AHEAD_M = 0.3f
/** The panel positions above assume a head this high above the panels' frame. */
private const val HEAD_HEIGHT_M = 1.5f
/** RingRig and GeneratedRingRig build rings at this multiple of real size. */
private const val RIG_SCALE = 3f
// Within arm's reach, so the fingertip can paint it: a little right of and below the eyes.
// (It was 0.95 m away, beside the panel, out of reach.)
private val PAINT_POSITION = Vector3(0.22f, 1.25f, -0.45f)
private const val PAINT_SCALE = 3f
/** The brush's reach around the fingertip, in metres, on the ring held at [PAINT_SCALE]. */
/** The brush's reach at each end of the panel's slider, in metres. */
private const val BRUSH_MIN_M = 0.003f
private const val BRUSH_MAX_M = 0.03f
/** Degrees the ring turns per metre the left hand moves. */
private const val TURN_PER_M = 600f
private const val BRUSH_POLL_MS = 33L
private const val TRIGGER_ON = 0.3f
private const val GRIP_ON = 0.5f
/** The grip grabs the ring when the controller is this close to its centre. */
private const val GRAB_REACH_M = 0.2f

/** The controllers' buttons, written from the tracking thread and read by the brush loop. */
private class LatestButtons {
    @Volatile var latest: ControllerActionData? = null
}

private fun distance(a: Vector3, b: Vector3): Float {
    val x = a.x - b.x; val y = a.y - b.y; val z = a.z - b.z
    return sqrt(x * x + y * y + z * z)
}
private const val BRUSH_LOG_MS = 1000L
/** How often the painted texture is put back on the ring while painting. */
private const val BRUSH_SHOW_MS = 120L
/** The drag dot sits this far below the centre of the ring held for painting. */
private const val HANDLE_BELOW_M = 0.14f
private const val HANDLE_RADIUS_M = 0.012f
private const val HANDLE_GRAB_RADIUS_M = 0.03f

/** The dot under the ring held for painting, which moves it when dragged. */
private fun makeHandle(): Entity =
    Entity().apply {
        enabled = false
        components.set(
            ModelComponent(
                MeshResource.createSphere(HANDLE_RADIUS_M),
                UnlitMaterial.create().apply { setBaseColor(Color4(1f, 1f, 1f, 1f)) },
            )
        )
        components.set(InteractableComponent())
        components.set(
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createSphere(HANDLE_GRAB_RADIUS_M)),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
    }

/**
 * Holds the ring still for painting, facing the user (looking through its hole, stone up).
 * [PAINT_POSITION] is in the panels' frame, which follows the user; [moved] is how far the
 * user has dragged it from there, in the stage's space.
 */
private fun holdForPainting(
    ring: Entity,
    stageRoot: Entity,
    panelsRoot: Entity,
    panelsYaw: Quat,
    yawDeg: Float,
    pitchDeg: Float,
    moved: Vector3,
) {
    val yaw = Quat(Vector3(0f, 1f, 0f), Math.toRadians(yawDeg.toDouble()).toFloat())
    val pitch = Quat(Vector3(1f, 0f, 0f), Math.toRadians(pitchDeg.toDouble()).toFloat())
    ring.components[TransformComponent::class.java]?.apply {
        val home = stageRoot.convertPositionFrom(PAINT_POSITION, panelsRoot)
        setPosition(Vector3(home.x + moved.x, home.y + moved.y, home.z + moved.z))
        setQuaternion(panelsYaw * pitch * yaw)
        setScaleVector(Vector3(PAINT_SCALE, PAINT_SCALE, PAINT_SCALE))
    }
}

/**
 * Puts the panels' frame in front of a tracked hand, turned the way the hand points. The head
 * is taken to be [HAND_BELOW_HEAD_M] above the hand and [HAND_AHEAD_M] behind it; the panel
 * positions are written for a head at [HEAD_HEIGHT_M] above the frame. Returns the turn, or
 * null when no hand is tracked.
 */
private fun placeByHand(panelsRoot: Entity, stageRoot: Entity, data: HandTrackingData, waitedMs: Long): Quat? {
    val aim = data.rightAim?.takeIf { it.isReady } ?: data.leftAim?.takeIf { it.isReady }
    val wrist = (data.right.tracked() ?: data.left.tracked())?.get(HandJoint.Index.WRIST)?.position
    val hand = aim?.aimPose?.position ?: wrist ?: return null
    // Which way the user faces: where the aim ray points, flattened. Without one, stage forward.
    var fx = 0f
    var fz = -1f
    aim?.aimPose?.rotation?.rotateVector(Vector3(0f, 0f, -1f))?.let {
        val len = sqrt(it.x * it.x + it.z * it.z)
        if (len > 0.2f) {
            fx = it.x / len
            fz = it.z / len
        }
    }
    return placeFrame(panelsRoot, stageRoot, hand, fx, fz, "hand (aim=${aim != null}) after ${waitedMs}ms")
}

/** Both hands held up and pinched: panels go in front of the point between them. */
private fun placeByBothHands(panelsRoot: Entity, stageRoot: Entity, data: HandTrackingData): Quat? {
    val l = data.left.tracked()?.get(HandJoint.Index.WRIST)?.position ?: return null
    val r = data.right.tracked()?.get(HandJoint.Index.WRIST)?.position ?: return null
    val mid = Vector3((l.x + r.x) / 2f, (l.y + r.y) / 2f, (l.z + r.z) / 2f)
    // Facing is square to the line between the wrists; of its two senses, the one the aim rays
    // agree with (or stage forward without them).
    var fx = -(r.z - l.z)
    var fz = r.x - l.x
    val len = sqrt(fx * fx + fz * fz)
    if (len < 0.05f) return null
    fx /= len
    fz /= len
    var ax = 0f
    var az = -1f
    listOfNotNull(data.leftAim, data.rightAim).filter { it.isReady }.forEach {
        val v = it.aimPose.rotation.rotateVector(Vector3(0f, 0f, -1f))
        ax += v.x
        az += v.z
    }
    if (fx * ax + fz * az < 0f) {
        fx = -fx
        fz = -fz
    }
    return placeFrame(panelsRoot, stageRoot, mid, fx, fz, "both hands pinched")
}

/** Puts the panels' frame in front of the headset, turned the way it faces (left/right only). */
private fun placeByHead(panelsRoot: Entity, stageRoot: Entity, pose: HMDPose, waitedMs: Long): Quat {
    val f = pose.rotation.rotateVector(Vector3(0f, 0f, -1f))
    var fx = 0f
    var fz = -1f
    val len = sqrt(f.x * f.x + f.z * f.z)
    if (len > 0.1f) {
        fx = f.x / len
        fz = f.z / len
    }
    val head = pose.position
    val worldTurn = Quat(Vector3(0f, 1f, 0f), atan2(-fx, -fz))
    val framePos = stageRoot.convertPositionFrom(Vector3(head.x, head.y - HEAD_HEIGHT_M, head.z), null)
    val turn = stageRoot.convertRotationFrom(worldTurn, null)
    panelsRoot.components[TransformComponent::class.java]?.apply {
        setPosition(framePos)
        setQuaternion(turn)
    }
    Log.i(TAG, "panels placed by head: head=$head facing=($fx, $fz) frame=$framePos after ${waitedMs}ms")
    return turn
}

/** Puts the frame for a head [HAND_BELOW_HEAD_M] above and [HAND_AHEAD_M] behind [hand]. */
private fun placeFrame(panelsRoot: Entity, stageRoot: Entity, hand: Vector3, fx: Float, fz: Float, why: String): Quat {
    val head = Vector3(hand.x - fx * HAND_AHEAD_M, hand.y + HAND_BELOW_HEAD_M, hand.z - fz * HAND_AHEAD_M)
    val worldTurn = Quat(Vector3(0f, 1f, 0f), atan2(-fx, -fz))
    // Hand data is in world space; the panels' frame hangs from the stage root, which need not
    // sit at the world origin.
    val framePos = stageRoot.convertPositionFrom(Vector3(head.x, head.y - HEAD_HEIGHT_M, head.z), null)
    val turn = stageRoot.convertRotationFrom(worldTurn, null)
    panelsRoot.components[TransformComponent::class.java]?.apply {
        setPosition(framePos)
        setQuaternion(turn)
    }
    Log.i(TAG, "panels placed by $why: hand=$hand facing=($fx, $fz) frame=$framePos")
    return turn
}

/** Centres the ring on the finger segment; the joint's own axes line up with the ring's. */
/**
 * Puts the ring on the finger. A generated ring has its top (where the drawing's decoration
 * is) along +Y, which on the finger joint points to the palm: worn, the decoration sat under
 * the finger. [flip] turns it half round the finger so the top faces the back of the hand.
 */
private fun wearOnFinger(ring: Entity, parent: Entity, hand: HandPose, finger: WornFinger, flip: Boolean = false) {
    val (base, _) = fingerSegment(finger)
    val joint = hand[base].rotation
    val turned = if (flip) joint * HALF_TURN_ROUND_FINGER else joint
    ring.components[TransformComponent::class.java]?.apply {
        setPosition(parent.convertPositionFrom(segmentMiddle(hand, finger), null))
        setQuaternion(parent.convertRotationFrom(turned, null))
    }
}

/** Half a turn round the finger (the ring's hole runs along Z). */
private val HALF_TURN_ROUND_FINGER = Quat(Vector3(0f, 0f, 1f), PI.toFloat())

/** The brush's reach in metres for the slider's [size] (0 to 1). */
internal fun brushRadius(size: Float): Float = BRUSH_MIN_M + (BRUSH_MAX_M - BRUSH_MIN_M) * size.coerceIn(0f, 1f)
