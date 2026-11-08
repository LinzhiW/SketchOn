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
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay

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

    var rig by remember { mutableStateOf<WornRing?>(null) }
    val stageRoot = remember { Entity() }
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
            .onSuccess { rig = it }
            .onFailure { Log.e(TAG, "Could not load the ring", it) }
    }
    val isGemtronic = state.piece == RingPiece.Gemtronic

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            AttachmentPanel(CENTER_PANEL_ID) { CenterPanel(state, onEvent) }
            AttachmentPanel(GUIDE_PANEL_ID) {
                if (isGemtronic) GestureGuide(state.activeFrame, onEvent) else GeneratedGuide(onEvent)
            }
            AttachmentPanel(CONTROL_PANEL_ID) { ControlPanel(onEvent) }
        },
        update = { _, attachments ->
            attachments.entity(CENTER_PANEL_ID)?.enabled = state.phase != RingPhase.RingWorn
            val ringInUse =
                state.phase == RingPhase.HandSearching || state.phase == RingPhase.RingWorn
            attachments.entity(GUIDE_PANEL_ID)?.enabled = ringInUse
            attachments.entity(CONTROL_PANEL_ID)?.enabled = ringInUse && isGemtronic
            rig?.let { ring ->
                ring.showFrame(state.activeFrame)
                ring.root.enabled = state.phase == RingPhase.RingWorn
                if (hand != null) wearOnFinger(ring.root, stageRoot, hand, state.finger)
            }
        },
    ) { content, attachments ->
        content.addEntity(stageRoot)
        attachments.entity(CENTER_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.setPosition(CENTER_PANEL_POSITION)
            stageRoot.addChild(this)
        }
        attachments.entity(GUIDE_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.apply {
                setPosition(GUIDE_PANEL_POSITION)
                setQuaternion(GUIDE_PANEL_ROTATION)
            }
            stageRoot.addChild(this)
        }
        attachments.entity(CONTROL_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.apply {
                setPosition(CONTROL_PANEL_POSITION)
                setQuaternion(CONTROL_PANEL_ROTATION)
            }
            stageRoot.addChild(this)
        }
    }
}

/** Centres the ring on the finger segment; the joint's own axes line up with the ring's. */
private fun wearOnFinger(ring: Entity, parent: Entity, hand: HandPose, finger: WornFinger) {
    val (base, _) = fingerSegment(finger)
    ring.components[TransformComponent::class.java]?.apply {
        setPosition(parent.convertPositionFrom(segmentMiddle(hand, finger), null))
        setQuaternion(parent.convertRotationFrom(hand[base].rotation, null))
    }
}
