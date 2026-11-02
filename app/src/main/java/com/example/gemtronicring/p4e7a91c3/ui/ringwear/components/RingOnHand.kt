package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

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
import com.example.gemtronicring.p4e7a91c3.domain.model.JointRotation
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearEvent
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearUiState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay

private const val TAG = "GemtronicRing"
private const val HINT_PANEL_ID = "hint-panel"
private const val HAND_LOST_DELAY_MS = 300L

// The first segment of the ring finger, where a ring sits.
private val BASE_JOINT = HandJoint.Index.RING_PROXIMAL
private val NEXT_JOINT = HandJoint.Index.RING_INTERMEDIATE

private val HINT_PANEL_POSITION = Vector3(0f, 1.4f, -0.8f)

/** Stage root of the app: reads the hand, wears the ring on it, and shows the hint when needed. */
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

    val hand = data.right ?: data.left
    val isLeftHand = data.right == null

    LaunchedEffect(hand) {
        val pose = hand ?: return@LaunchedEffect
        val rotation = pose[BASE_JOINT].rotation
        currentOnEvent(
            RingWearEvent.HandMoved(
                JointRotation(rotation.x, rotation.y, rotation.z, rotation.w),
                isLeftHand,
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

    var rig by remember { mutableStateOf<RingRig?>(null) }
    val stageRoot = remember { Entity() }
    LaunchedEffect(state.pages) {
        if (state.pages.isEmpty() || rig != null) return@LaunchedEffect
        runCatching { RingRig.create(state.pages) }
            .onSuccess {
                stageRoot.addChild(it.root)
                rig = it
            }
            .onFailure { Log.e(TAG, "Could not load the ring", it) }
    }

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = { AttachmentPanel(HINT_PANEL_ID) { HintPanel(phase = state.phase) } },
        update = { _, attachments ->
            attachments.entity(HINT_PANEL_ID)?.enabled = state.phase != RingPhase.RingWorn
            rig?.let { ring ->
                ring.showPage(state.activePageIndex)
                ring.root.enabled = state.phase == RingPhase.RingWorn
                if (hand != null) wearOnFinger(ring.root, stageRoot, hand)
            }
        },
    ) { content, attachments ->
        content.addEntity(stageRoot)
        attachments.entity(HINT_PANEL_ID)?.apply {
            components[TransformComponent::class.java]?.setPosition(HINT_PANEL_POSITION)
            stageRoot.addChild(this)
        }
    }
}

/** Centres the ring on the finger segment; the joint's own axes line up with the ring's. */
private fun wearOnFinger(ring: Entity, parent: Entity, hand: HandPose) {
    val base = hand[BASE_JOINT]
    val next = hand[NEXT_JOINT]
    val middle =
        Vector3(
            (base.position.x + next.position.x) / 2f,
            (base.position.y + next.position.y) / 2f,
            (base.position.z + next.position.z) / 2f,
        )
    ring.components[TransformComponent::class.java]?.apply {
        setPosition(parent.convertPositionFrom(middle, null))
        setQuaternion(parent.convertRotationFrom(base.rotation, null))
    }
}
