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
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.sqrt

private const val TAG = "SketchOn"
private const val CENTER_PANEL_ID = "center-panel"
private const val GUIDE_PANEL_ID = "gesture-guide"
private const val CONTROL_PANEL_ID = "control-panel"
private const val HAND_LOST_DELAY_MS = 300L

private val CENTER_PANEL_POSITION = Vector3(0f, 1.5f, -0.8f)
private val GUIDE_PANEL_POSITION = Vector3(0.34f, 1.5f, -0.85f)
private val GUIDE_PANEL_ROTATION = Quat(Vector3(0f, 1f, 0f), -0.38f)
private val CONTROL_PANEL_POSITION = Vector3(-0.34f, 1.5f, -0.85f)
private val CONTROL_PANEL_ROTATION = Quat(Vector3(0f, 1f, 0f), 0.38f)
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

/** Keeps the ring on the hand that was raised first, until that hand goes away. */
private class RaisedHand {
    private var usingRight = true

    fun pick(data: HandTrackingData): HandPose? {
        if (usingRight && data.right == null && data.left != null) usingRight = false
        if (!usingRight && data.left == null && data.right != null) usingRight = true
        return if (usingRight) data.right else data.left
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

    val drafts = remember { TextDrafts() }
    // A new drawing gets a new name; a password is not kept once signed in.
    LaunchedEffect(state.phase) {
        if (state.phase == RingPhase.Sketching) drafts.pieceName = ""
        if (state.user != null) drafts.loginPassword = ""
    }
    var rig by remember { mutableStateOf<WornRing?>(null) }
    val stageRoot = remember { Entity() }
    // The panels hang from this, placed in front of the user (see recentre below). On a headset
    // the stage's origin is not where the user's head is, so fixed positions put panels out of
    // sight; the emulator happened to match.
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
    val isGemtronic = state.piece == RingPiece.Gemtronic
    // While painting, nothing moves the ring through the view's update (that runs on hand
    // data), so it is placed here whenever the ring, the phase or the turn changes.
    LaunchedEffect(rig, state.phase, state.turnYaw, state.turnPitch, panelsYaw) {
        val ring = rig ?: return@LaunchedEffect
        if (state.phase == RingPhase.Painting) {
            ring.root.enabled = true
            holdForPainting(ring.root, stageRoot, panelsRoot, panelsYaw, state.turnYaw, state.turnPitch)
        } else if (state.phase != RingPhase.RingWorn) {
            ring.root.enabled = false
            ring.root.components[TransformComponent::class.java]?.setScaleVector(Vector3(1f, 1f, 1f))
        }
    }
    LaunchedEffect(rig, state.paints) {
        val ring = rig as? GeneratedRingRig ?: return@LaunchedEffect
        state.paints.forEach { (part, colour) -> ring.paint(part, colour) }
    }

    val context = LocalContext.current
    val currentPhase by rememberUpdatedState(state.phase)
    val currentRig by rememberUpdatedState(rig)
    SpatialView(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
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
                    if (currentPhase == RingPhase.Painting) {
                        currentOnEvent(
                            RingWearEvent.TurnRing(it.dragAmount.x * TURN_PER_PX, it.dragAmount.y * TURN_PER_PX)
                        )
                    }
                }
            },
        attachments = {
            AttachmentPanel(CENTER_PANEL_ID) { CenterPanel(state, drafts, onEvent) }
            AttachmentPanel(GUIDE_PANEL_ID) {
                if (isGemtronic) GestureGuide(state.activeFrame, state.ringScale, onEvent)
                else GeneratedGuide(state.paintable, state.ringScale, onEvent)
            }
            AttachmentPanel(CONTROL_PANEL_ID) { ControlPanel(onEvent) }
        },
        update = { _, attachments ->
            // Signing in has its own system window.
            attachments.entity(CENTER_PANEL_ID)?.enabled =
                state.phase != RingPhase.RingWorn && state.phase != RingPhase.Login
            val ringInUse =
                state.phase == RingPhase.HandSearching || state.phase == RingPhase.RingWorn
            attachments.entity(GUIDE_PANEL_ID)?.enabled = ringInUse
            attachments.entity(CONTROL_PANEL_ID)?.enabled = ringInUse && isGemtronic
            rig?.let { ring ->
                ring.showFrame(state.activeFrame)
                if (state.phase == RingPhase.Painting) {
                    // Placed by the effect below: nothing here changes while painting.
                } else {
                    ring.root.enabled = state.phase == RingPhase.RingWorn
                    // The rigs are built at 3x for the emulator; worn, the user sets the size.
                    val size = state.ringScale / RIG_SCALE
                    ring.root.components[TransformComponent::class.java]?.setScaleVector(Vector3(size, size, size))
                    if (hand != null) wearOnFinger(ring.root, stageRoot, hand, state.finger)
                }
            }
        },
    ) { content, attachments ->
        content.addEntity(stageRoot)
        attachments.entity(CENTER_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.setPosition(CENTER_PANEL_POSITION)
            panelsRoot.addChild(this)
        }
        attachments.entity(GUIDE_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.apply {
                setPosition(GUIDE_PANEL_POSITION)
                setQuaternion(GUIDE_PANEL_ROTATION)
            }
            panelsRoot.addChild(this)
        }
        attachments.entity(CONTROL_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.apply {
                setPosition(CONTROL_PANEL_POSITION)
                setQuaternion(CONTROL_PANEL_ROTATION)
            }
            panelsRoot.addChild(this)
        }
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
private val PAINT_POSITION = Vector3(0.36f, 1.45f, -0.95f)
private const val PAINT_SCALE = 3f

/**
 * Holds the ring still for painting beside the panel, facing the user (looking through its
 * hole, stone up). [PAINT_POSITION] is in the panels' frame, which follows the user.
 */
private fun holdForPainting(ring: Entity, stageRoot: Entity, panelsRoot: Entity, panelsYaw: Quat, yawDeg: Float, pitchDeg: Float) {
    val yaw = Quat(Vector3(0f, 1f, 0f), Math.toRadians(yawDeg.toDouble()).toFloat())
    val pitch = Quat(Vector3(1f, 0f, 0f), Math.toRadians(pitchDeg.toDouble()).toFloat())
    ring.components[TransformComponent::class.java]?.apply {
        setPosition(stageRoot.convertPositionFrom(PAINT_POSITION, panelsRoot))
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
    val wrist = (data.right ?: data.left)?.get(HandJoint.Index.WRIST)?.position
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
    val l = data.left?.get(HandJoint.Index.WRIST)?.position ?: return null
    val r = data.right?.get(HandJoint.Index.WRIST)?.position ?: return null
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
private fun wearOnFinger(ring: Entity, parent: Entity, hand: HandPose, finger: WornFinger) {
    val (base, _) = fingerSegment(finger)
    ring.components[TransformComponent::class.java]?.apply {
        setPosition(parent.convertPositionFrom(segmentMiddle(hand, finger), null))
        setQuaternion(parent.convertRotationFrom(hand[base].rotation, null))
    }
}
