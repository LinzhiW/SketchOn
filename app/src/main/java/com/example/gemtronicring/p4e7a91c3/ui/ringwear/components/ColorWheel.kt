package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val WheelSize = 150.dp
private val BarWidth = 150.dp
private val BarHeight = 28.dp
private val PreviewSize = 56.dp

/**
 * A colour wheel, as in VR painting apps: around the wheel is the hue, towards the centre the
 * colour fades to white, and the bar below darkens it. [onPicked] fires when a tap or drag ends,
 * so a new render is asked for once per choice, not on every move.
 */
@Composable
internal fun ColorWheel(colour: Int, onPicked: (Int) -> Unit) {
    val start = remember(colour) { FloatArray(3).also { android.graphics.Color.colorToHSV(colour, it) } }
    var hue by remember(colour) { mutableStateOf(start[0]) }
    var saturation by remember(colour) { mutableStateOf(start[1]) }
    var value by remember(colour) { mutableStateOf(start[2]) }
    val picked by rememberUpdatedState(onPicked)

    fun current() = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    fun pointOnWheel(p: Offset, size: Float) {
        val r = size / 2f
        val dx = p.x - r
        val dy = p.y - r
        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
        saturation = min(1f, hypot(dx, dy) / r)
    }
    fun pointOnBar(p: Offset, width: Float) {
        value = (p.x / width).coerceIn(0.05f, 1f)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier =
                    Modifier.size(WheelSize)
                        .pointerInput(Unit) {
                            detectTapGestures { pointOnWheel(it, size.width.toFloat()); picked(current()) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, _ -> pointOnWheel(change.position, size.width.toFloat()) },
                                onDragEnd = { picked(current()) },
                            )
                        }
            ) {
                val r = size.minDimension / 2f
                // Hue around the wheel (screen angles run clockwise, as hue does here).
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                        center,
                    ),
                    radius = r,
                )
                // Fading to white towards the centre.
                drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center, r), radius = r)
                // Darkening by the bar's value.
                drawCircle(color = Color.Black.copy(alpha = 1f - value), radius = r)
                val a = Math.toRadians(hue.toDouble())
                val marker = Offset(center.x + (saturation * r * cos(a)).toFloat(), center.y + (saturation * r * sin(a)).toFloat())
                drawCircle(Color.White, radius = 9f, center = marker, style = Stroke(width = 4f))
                drawCircle(Color.Black, radius = 12f, center = marker, style = Stroke(width = 2f))
            }
            Canvas(
                modifier =
                    Modifier.width(BarWidth)
                        .height(BarHeight)
                        .clip(RoundedCornerShape(BarHeight / 2))
                        .pointerInput(Unit) {
                            detectTapGestures { pointOnBar(it, size.width.toFloat()); picked(current()) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, _ -> pointOnBar(change.position, size.width.toFloat()) },
                                onDragEnd = { picked(current()) },
                            )
                        }
            ) {
                val bright = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                drawRect(Brush.horizontalGradient(listOf(Color.Black, bright)))
                val x = value * size.width
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 5f)
            }
        }
        Box(modifier = Modifier.size(PreviewSize).clip(CircleShape).background(Color(current())))
    }
}
