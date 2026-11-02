package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R
import com.example.gemtronicring.p4e7a91c3.domain.model.WornFinger
import com.example.gemtronicring.p4e7a91c3.ui.theme.GemtronicColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

private val PickerWidth = 392.dp
private val PickerHeight = 420.dp
private val PalmShape = RoundedCornerShape(32.dp)
private val PalmX = 100.dp
private val PalmY = 210.dp
private val PalmWidth = 216.dp
private val PalmHeight = 200.dp
private val FingerShape = RoundedCornerShape(28.dp)

private const val RING_MARK_POSITION = 0.72f
private const val RING_MARK_HEIGHT = 14f
private const val RING_GEM_RADIUS = 11f

/** Where one finger sits on the drawing, which shows the back of a right hand. */
private class FingerSpot(
    val finger: WornFinger,
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val tiltDeg: Float,
    val labelRes: Int,
)

private val FingerSpots =
    listOf(
        FingerSpot(WornFinger.Thumb, 36.dp, 224.dp, 58.dp, 172.dp, -38f, R.string.finger_thumb),
        FingerSpot(WornFinger.Index, 104.dp, 62.dp, 46.dp, 180.dp, -8f, R.string.finger_index),
        FingerSpot(WornFinger.Middle, 160.dp, 20.dp, 46.dp, 216.dp, -2f, R.string.finger_middle),
        FingerSpot(WornFinger.Ring, 216.dp, 44.dp, 46.dp, 196.dp, 5f, R.string.finger_ring),
        FingerSpot(WornFinger.Little, 272.dp, 104.dp, 42.dp, 150.dp, 14f, R.string.finger_little),
    )

/** A hand to tap. The chosen finger lights up and shows where the ring sits on it. */
@Composable
internal fun HandPicker(selected: WornFinger, onSelect: (WornFinger) -> Unit) {
    val description = stringResource(R.string.hand_picker_description)
    Box(Modifier.size(PickerWidth, PickerHeight).semantics { contentDescription = description }) {
        Canvas(Modifier.offset(PalmX, PalmY).size(PalmWidth, PalmHeight)) {
            drawRoundRect(
                color = GemtronicColors.handSkin,
                cornerRadius = CornerRadius(PalmShape.topStart.toPx(size, this)),
            )
        }
        FingerSpots.forEach { spot ->
            HandFinger(spot, selected = spot.finger == selected) { onSelect(spot.finger) }
        }
    }
}

@Composable
private fun HandFinger(spot: FingerSpot, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) PicoTheme.colorScheme.labelPrimaryLight else GemtronicColors.handSkin
    val label = stringResource(spot.labelRes)
    Box(
        Modifier.offset(spot.x, spot.y)
            .size(spot.width, spot.height)
            .rotate(spot.tiltDeg)
            .clip(FingerShape)
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(color = fill, cornerRadius = CornerRadius(size.width / 2f))
            if (selected) drawRingMark(this.size, GemtronicColors.ringScreenBase)
        }
    }
}

private fun DrawScope.drawRingMark(area: Size, color: Color) {
    val y = area.height * RING_MARK_POSITION
    val band = RING_MARK_HEIGHT.dp.toPx()
    drawRect(color, topLeft = Offset(0f, y - band / 2f), size = Size(area.width, band))
    drawCircle(color, radius = RING_GEM_RADIUS.dp.toPx(), center = Offset(area.width / 2f, y))
}
