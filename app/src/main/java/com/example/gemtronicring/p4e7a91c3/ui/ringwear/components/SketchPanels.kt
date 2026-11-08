package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R
import com.example.gemtronicring.p4e7a91c3.domain.model.RenderStyle
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearEvent
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.SketchUiState
import com.example.gemtronicring.p4e7a91c3.ui.theme.GemtronicColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import java.io.ByteArrayOutputStream

private val PanelShape = RoundedCornerShape(24.dp)
private val SquareShape = RoundedCornerShape(16.dp)
private val SquareSize = 360.dp
private val Gap = 16.dp
private val Pad = 24.dp
private const val SEND_EVERY_MS = 300L
private const val SKETCH_PX = 512
private const val PEN_WIDTH_PX = 6f

/** First panel: draw a ring, or try the Gemtronic ring. */
@Composable
internal fun HomePanel(onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(440.dp).background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Text(stringResource(R.string.app_name), color = light(), style = PicoTheme.typography.titleLarge)
        Text(stringResource(R.string.home_subtitle), color = light(), style = PicoTheme.typography.bodyLarge)
        Button(onClick = { onEvent(RingWearEvent.StartSketch) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.home_draw))
        }
        Button(onClick = { onEvent(RingWearEvent.ChooseGemtronic) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.home_gemtronic))
        }
        Text(stringResource(R.string.home_community_soon), color = light(), style = PicoTheme.typography.bodyMedium)
    }
}

/** Drawing on the left, the live render on the right. */
@Composable
internal fun SketchPanel(sketch: SketchUiState, onEvent: (RingWearEvent) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var canvasPx by remember { mutableStateOf(1) }
    var lastSent by remember { mutableStateOf(0L) }

    fun send() {
        lastSent = System.currentTimeMillis()
        val all = strokes + listOf(current.toList()).filter { it.isNotEmpty() }
        onEvent(RingWearEvent.SketchChanged(if (all.isEmpty()) null else toDataUri(all, canvasPx)))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Text(stringResource(R.string.sketch_title), color = light(), style = PicoTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Canvas(
                modifier =
                    Modifier.size(SquareSize)
                        .clip(SquareShape)
                        .background(Color.White)
                        .onSizeChanged { canvasPx = it.width }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    current.clear()
                                    current.add(it)
                                },
                                onDrag = { change, _ ->
                                    current.add(change.position)
                                    if (System.currentTimeMillis() - lastSent >= SEND_EVERY_MS) send()
                                },
                                onDragEnd = {
                                    strokes.add(current.toList())
                                    current.clear()
                                    send()
                                },
                                onDragCancel = {
                                    strokes.add(current.toList())
                                    current.clear()
                                    send()
                                },
                            )
                        }
            ) {
                val pen = Stroke(width = PEN_WIDTH_PX * size.width / SKETCH_PX, cap = StrokeCap.Round, join = StrokeJoin.Round)
                (strokes + listOf(current.toList())).forEach { points ->
                    if (points.isEmpty()) return@forEach
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color.Black, style = pen)
                }
            }
            RenderView(sketch)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RenderStyle.entries.forEach { style ->
                val chosen = style == sketch.style
                Button(
                    onClick = { onEvent(RingWearEvent.ChooseStyle(style)) },
                    colors = if (chosen) lightButtonColors() else darkButtonColors(),
                ) {
                    Text(stringResource(styleLabel(style)))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onEvent(RingWearEvent.GoHome) }, colors = darkButtonColors()) {
                Text(stringResource(R.string.back_button))
            }
            Button(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.lastIndex)
                        send()
                    }
                },
                colors = darkButtonColors(),
            ) {
                Text(stringResource(R.string.sketch_undo))
            }
            Button(
                onClick = {
                    strokes.clear()
                    current.clear()
                    send()
                },
                colors = darkButtonColors(),
            ) {
                Text(stringResource(R.string.sketch_clear))
            }
            Button(
                onClick = { onEvent(RingWearEvent.Make3d) },
                enabled = sketch.render != null && !sketch.rendering,
                colors = lightButtonColors(),
            ) {
                Text(stringResource(R.string.sketch_make_3d))
            }
        }
        Text(statusText(sketch), color = light(), style = PicoTheme.typography.bodyMedium)
    }
}

@Composable
private fun RenderView(sketch: SketchUiState) {
    val image = remember(sketch.render) { sketch.render?.let(::decodeDataUri) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(SquareSize).clip(SquareShape).background(Color(0xFF2A2F2C)),
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = stringResource(R.string.sketch_render_description), modifier = Modifier.fillMaxSize())
        } else {
            Text(stringResource(R.string.sketch_render_empty), color = light(), style = PicoTheme.typography.bodyLarge)
        }
    }
}

/** Shown while Tripo builds the model, which takes a minute or two. */
@Composable
internal fun Making3dPanel(sketch: SketchUiState, onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(440.dp).background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Text(stringResource(R.string.making_title), color = light(), style = PicoTheme.typography.titleLarge)
        RenderView(sketch)
        Text(
            stringResource(R.string.making_progress, sketch.making3dSeconds),
            color = light(),
            style = PicoTheme.typography.bodyLarge,
        )
        Button(onClick = { onEvent(RingWearEvent.Cancel3d) }, colors = darkButtonColors()) {
            Text(stringResource(R.string.making_cancel))
        }
    }
}

/** The right panel when wearing a drawn ring: it has no screen or gestures. */
@Composable
internal fun GeneratedGuide(onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.width(320.dp).background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Text(stringResource(R.string.generated_title), color = light(), style = PicoTheme.typography.titleLarge)
        Text(stringResource(R.string.generated_body), color = light(), style = PicoTheme.typography.bodyLarge)
        Button(onClick = { onEvent(RingWearEvent.ChangeFinger) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.change_finger))
        }
        Button(onClick = { onEvent(RingWearEvent.StartSketch) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.generated_draw_again))
        }
        Button(onClick = { onEvent(RingWearEvent.GoHome) }, colors = darkButtonColors()) {
            Text(stringResource(R.string.generated_home))
        }
    }
}

@Composable
private fun statusText(sketch: SketchUiState): String =
    when {
        sketch.error != null -> stringResource(R.string.sketch_error, sketch.error)
        sketch.rendering -> stringResource(R.string.sketch_rendering)
        sketch.lastRenderMs != null -> stringResource(R.string.sketch_rendered, sketch.lastRenderMs)
        else -> stringResource(R.string.sketch_hint)
    }

private fun styleLabel(style: RenderStyle): Int =
    when (style) {
        RenderStyle.Gold -> R.string.style_gold
        RenderStyle.Silver -> R.string.style_silver
        RenderStyle.Rose -> R.string.style_rose
        RenderStyle.Platinum -> R.string.style_platinum
        RenderStyle.Vintage -> R.string.style_vintage
    }

@Composable
private fun light() = PicoTheme.colorScheme.labelPrimaryLight

@Composable
private fun lightButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = PicoTheme.colorScheme.labelPrimaryLight,
        contentColor = GemtronicColors.ringScreenBase,
    )

@Composable
private fun darkButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = Color(0xFF2A2F2C),
        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
    )

/** Draws the strokes black on white at 512 px, the size the render server expects. */
private fun toDataUri(strokes: List<List<Offset>>, canvasPx: Int): String {
    val bitmap = Bitmap.createBitmap(SKETCH_PX, SKETCH_PX, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val k = SKETCH_PX.toFloat() / canvasPx.coerceAtLeast(1)
    val paint =
        Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = PEN_WIDTH_PX
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    strokes.forEach { points ->
        if (points.isEmpty()) return@forEach
        val path = android.graphics.Path()
        path.moveTo(points[0].x * k, points[0].y * k)
        if (points.size == 1) path.lineTo(points[0].x * k + 0.1f, points[0].y * k)
        points.drop(1).forEach { path.lineTo(it.x * k, it.y * k) }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodeDataUri(uri: String): ImageBitmap? =
    runCatching {
            val bytes = Base64.decode(uri.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        .getOrNull()
