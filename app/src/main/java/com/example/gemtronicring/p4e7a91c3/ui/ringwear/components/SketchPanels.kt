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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R
import com.example.gemtronicring.p4e7a91c3.domain.model.DesignStyle
import com.example.gemtronicring.p4e7a91c3.domain.model.Material
import com.example.gemtronicring.p4e7a91c3.domain.model.SketchView
import com.example.gemtronicring.p4e7a91c3.domain.model.StrokePoint
import com.example.gemtronicring.p4e7a91c3.domain.usecase.ShapeStrokeUseCase
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.GalleryUiState
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearEvent
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearViewModel
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.SketchUiState
import com.example.gemtronicring.p4e7a91c3.ui.theme.GemtronicColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PanelShape = RoundedCornerShape(24.dp)
private val SquareShape = RoundedCornerShape(16.dp)
private val SquareSize = 300.dp
private val LeftWidth = 616.dp
private val RightWidth = 440.dp
private val SectionGap = 12.dp
private val CardSize = 150.dp
private val ViewPictureSize = 56.dp
private val ColourHeight = 150.dp
private val Gap = 16.dp
private val SmallGap = 8.dp
private val Pad = 24.dp
private val Dim = Color(0xFF2A2F2C)
private const val SEND_EVERY_MS = 300L
private const val SKETCH_PX = 512
private const val PEN_WIDTH_PX = 6f
private const val GUIDE_WIDTH_PX = 3f
private const val GUIDE_GREY = 0xFFC8C8C8.toInt()
private const val GALLERY_COLUMNS = 3
private const val NAME_LIMIT = 40

/** First panel: draw a ring, or browse what has been made. */
@Composable
internal fun HomePanel(onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(440.dp).background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Title(stringResource(R.string.app_name))
        Body(stringResource(R.string.home_subtitle))
        Button(onClick = { onEvent(RingWearEvent.StartSketch) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.home_draw))
        }
        Button(onClick = { onEvent(RingWearEvent.OpenGallery) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.home_gallery))
        }
    }
}

/** Everything there is to try on: the Gemtronic ring first, then pieces people have made. */
@Composable
internal fun GalleryPanel(gallery: GalleryUiState, onEvent: (RingWearEvent) -> Unit) {
    val context = LocalContext.current
    val gemtronicPicture = remember {
        runCatching {
                context.assets.open("gallery/gemtronic.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            }
            .getOrNull()
    }
    data class Card(val key: String, val label: String, val picture: ImageBitmap?, val onClick: () -> Unit)

    val cards =
        listOf(
            Card("gemtronic", stringResource(R.string.gallery_gemtronic), gemtronicPicture) {
                onEvent(RingWearEvent.ChooseGemtronic)
            }
        ) +
            gallery.pieces.take(RingWearViewModel.GALLERY_SIZE).mapIndexed { i, piece ->
                val bytes = gallery.pictures[piece.id]
                val picture = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                val label = piece.name ?: stringResource(R.string.gallery_piece, gallery.pieces.size - i)
                Card(piece.id, label, picture) {
                    onEvent(RingWearEvent.WearPiece(piece))
                }
            }

    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Title(stringResource(R.string.gallery_title))
        Body(stringResource(R.string.gallery_subtitle))
        cards.chunked(GALLERY_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                row.forEach { card ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SmallGap)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(CardSize).clip(SquareShape).background(Dim),
                        ) {
                            if (card.picture != null) {
                                Image(bitmap = card.picture, contentDescription = card.label, modifier = Modifier.fillMaxSize())
                            } else {
                                Body(stringResource(R.string.gallery_no_picture))
                            }
                        }
                        Button(
                            onClick = card.onClick,
                            enabled = gallery.opening == null,
                            colors = lightButtonColors(),
                        ) {
                            Text(
                                if (gallery.opening == card.key) stringResource(R.string.gallery_opening)
                                else card.label
                            )
                        }
                    }
                }
            }
        }
        when {
            gallery.loading -> Body(stringResource(R.string.gallery_loading))
            gallery.error != null -> Body(stringResource(R.string.sketch_error, gallery.error))
            gallery.pieces.isEmpty() -> Body(stringResource(R.string.gallery_empty))
        }
        Button(onClick = { onEvent(RingWearEvent.GoHome) }, colors = darkButtonColors()) {
            Text(stringResource(R.string.back_button))
        }
    }
}

/** Drawing on the left, the live render on the right, choices underneath. */
@Composable
internal fun SketchPanel(sketch: SketchUiState, onEvent: (RingWearEvent) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var canvasPx by remember { mutableStateOf(1) }
    var lastSent by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val shaper = remember { ShapeStrokeUseCase() }
    // The drag handler is set up once, so it reads the latest settings through these.
    val options by rememberUpdatedState(sketch)
    // Encodings can finish out of order; only the newest drawing may reach the server.
    var sent by remember { mutableStateOf(0) }
    var delivered by remember { mutableStateOf(0) }

    // Turning the drawing into a PNG is slow on the emulator, so it runs off the main thread
    // and the pen keeps up with the pointer.
    fun send() {
        lastSent = System.currentTimeMillis()
        val all = strokes.toList() + listOf(current.toList()).filter { it.isNotEmpty() }
        val px = canvasPx
        val view = options.view
        val number = ++sent
        scope.launch {
            val uri = if (all.isEmpty()) null else withContext(Dispatchers.Default) { toDataUri(all, view, px) }
            if (number < delivered) return@launch
            delivered = number
            onEvent(RingWearEvent.SketchChanged(uri))
        }
    }

    fun finishStroke() {
        if (current.isEmpty()) return
        val shaped =
            shaper.shape(current.map { StrokePoint(it.x, it.y) }, smooth = options.smooth, shapes = options.shapes)
        strokes.add(shaped.map { Offset(it.x, it.y) })
        current.clear()
        send()
    }

    // Two columns of fixed width. The panel grows to fit its content but is cut off past
    // about 700 dp high and about 1150 dp wide, so the choices sit beside the drawing, and
    // neither column may take the other's room (a long hint once squeezed the choices).
    Row(
        horizontalArrangement = Arrangement.spacedBy(Pad),
        modifier = Modifier.background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Gap), modifier = Modifier.width(LeftWidth)) {
            Title(stringResource(R.string.sketch_title))
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
                                    onDragEnd = { finishStroke() },
                                    onDragCancel = { finishStroke() },
                                )
                            }
                ) {
                    val guide = Stroke(width = GUIDE_WIDTH_PX * size.width / SKETCH_PX, cap = StrokeCap.Round)
                    guideShapes(sketch.view).forEach { line ->
                        drawPath(smoothPath(line.map { Offset(it.x * size.width, it.y * size.height) }), Color(GUIDE_GREY), style = guide)
                    }
                    val pen = Stroke(width = PEN_WIDTH_PX * size.width / SKETCH_PX, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    (strokes + listOf(current.toList())).forEach { points ->
                        if (points.isEmpty()) return@forEach
                        drawPath(smoothPath(points), Color.Black, style = pen)
                    }
                }
                RenderView(sketch)
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
            Body(statusText(sketch))
        }
        Column(verticalArrangement = Arrangement.spacedBy(SectionGap), modifier = Modifier.width(RightWidth)) {
            // Material is clay, resin or metal. The metals (gold, silver, rose gold, platinum)
            // are metal's colours, so they sit where clay and resin show the colour wheel.
            var lastMetal by remember { mutableStateOf(Material.Gold) }
            if (sketch.material.metal) lastMetal = sketch.material
            Section(R.string.sketch_material) {
                ButtonRow(
                    listOf(
                        Triple(R.string.material_clay, sketch.material == Material.Clay) { onEvent(RingWearEvent.ChooseMaterial(Material.Clay)) },
                        Triple(R.string.material_resin, sketch.material == Material.Resin) { onEvent(RingWearEvent.ChooseMaterial(Material.Resin)) },
                        Triple(R.string.material_metal, sketch.material.metal) {
                            if (!sketch.material.metal) onEvent(RingWearEvent.ChooseMaterial(lastMetal))
                        },
                    )
                )
            }
            // A fixed height, so the choices below do not move when the material changes.
            Section(R.string.sketch_color, Modifier.height(ColourHeight)) {
                if (sketch.material.metal) {
                    Material.entries.filter { it.metal }.chunked(2).forEach { pair ->
                        ButtonRow(
                            pair.map { metal ->
                                Triple(materialLabel(metal), metal == sketch.material) { onEvent(RingWearEvent.ChooseMaterial(metal)) }
                            }
                        )
                    }
                } else {
                    ColorWheel(sketch.color) { onEvent(RingWearEvent.ChooseColor(it)) }
                }
            }
            Section(R.string.sketch_style) {
                DesignStyle.entries.chunked(3).forEach { row ->
                    ButtonRow(row.map { style -> Triple(styleLabel(style), style == sketch.style) { onEvent(RingWearEvent.ChooseStyle(style)) } })
                }
            }
            Section(R.string.sketch_view) { ViewPicker(sketch.view) { onEvent(RingWearEvent.ChooseView(it)) } }
            Section(R.string.sketch_help) {
                ButtonRow(
                    listOf(
                        Triple(R.string.sketch_smooth, sketch.smooth) { onEvent(RingWearEvent.ToggleSmooth) },
                        Triple(R.string.sketch_shapes, sketch.shapes) { onEvent(RingWearEvent.ToggleShapes) },
                    )
                )
            }
        }
    }
}

/** A small heading with its choices underneath. */
@Composable
private fun Section(labelRes: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SmallGap), modifier = modifier) {
        Label(stringResource(labelRes))
        content()
    }
}

/** Buttons as (label, chosen, action); the chosen ones are light. */
@Composable
private fun ButtonRow(buttons: List<Triple<Int, Boolean, () -> Unit>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
        buttons.forEach { (label, chosen, onClick) ->
            Button(onClick = onClick, colors = if (chosen) lightButtonColors() else darkButtonColors()) {
                Text(stringResource(label))
            }
        }
    }
}

/**
 * The three views as pictures of a ring: gold marks the part of the ring a drawing in that
 * view shapes, and the arrow shows where the drawing is seen from.
 */
@Composable
private fun ViewPicker(chosen: SketchView, onPick: (SketchView) -> Unit) {
    val context = LocalContext.current
    val pictures = remember {
        SketchView.entries.associateWith { view ->
            runCatching {
                    context.assets.open(viewPicture(view)).use { BitmapFactory.decodeStream(it).asImageBitmap() }
                }
                .getOrNull()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SmallGap), verticalAlignment = Alignment.CenterVertically) {
        SketchView.entries.forEach { view ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SmallGap)) {
                pictures[view]?.let {
                    Image(
                        bitmap = it,
                        contentDescription = stringResource(viewLabel(view)),
                        modifier = Modifier.size(ViewPictureSize).clip(SquareShape),
                    )
                }
                Button(
                    onClick = { onPick(view) },
                    colors = if (view == chosen) lightButtonColors() else darkButtonColors(),
                ) {
                    Text(stringResource(viewLabel(view)))
                }
            }
        }
    }
}

private fun viewPicture(v: SketchView): String =
    when (v) {
        SketchView.RingShape -> "views/ring_shape.png"
        SketchView.FromAbove -> "views/top.png"
        SketchView.Profile -> "views/profile.png"
    }

@Composable
private fun RenderView(sketch: SketchUiState) {
    val image = remember(sketch.render) { sketch.render?.let(::decodeDataUri) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(SquareSize).clip(SquareShape).background(Dim),
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = stringResource(R.string.sketch_render_description), modifier = Modifier.fillMaxSize())
        } else {
            Body(stringResource(R.string.sketch_render_empty))
        }
    }
}

/** Before making 3D: name the piece, a few tips, and a note that it goes to the gallery. */
@Composable
internal fun NamingPanel(sketch: SketchUiState, onEvent: (RingWearEvent) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.width(520.dp).background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Title(stringResource(R.string.naming_title))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            val image = remember(sketch.render) { sketch.render?.let(::decodeDataUri) }
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.sketch_render_description),
                    modifier = Modifier.size(CardSize).clip(SquareShape),
                )
            }
            TextField(
                value = name,
                onValueChange = { name = it.take(NAME_LIMIT) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.naming_placeholder)) },
            )
        }
        Body(stringResource(R.string.naming_shared))
        Label(stringResource(R.string.naming_tips_title))
        Body(stringResource(R.string.naming_tip_time))
        Body(stringResource(R.string.naming_tip_view))
        Body(stringResource(R.string.naming_tip_fit))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Button(onClick = { onEvent(RingWearEvent.Cancel3d) }, colors = darkButtonColors()) {
                Text(stringResource(R.string.naming_back))
            }
            Button(
                onClick = { onEvent(RingWearEvent.ConfirmMake3d(name)) },
                enabled = name.isNotBlank(),
                colors = lightButtonColors(),
            ) {
                Text(stringResource(R.string.sketch_make_3d))
            }
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
        Title(stringResource(R.string.making_title))
        RenderView(sketch)
        Body(stringResource(R.string.making_progress, sketch.making3dSeconds))
        Button(onClick = { onEvent(RingWearEvent.Cancel3d) }, colors = darkButtonColors()) {
            Text(stringResource(R.string.making_cancel))
        }
    }
}

/** The right panel when wearing a made ring: it has no screen or gestures. */
@Composable
internal fun GeneratedGuide(onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.width(320.dp).background(GemtronicColors.ringScreenBase, PanelShape).padding(Pad),
    ) {
        Title(stringResource(R.string.generated_title))
        Body(stringResource(R.string.generated_body))
        Button(onClick = { onEvent(RingWearEvent.ChangeFinger) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.change_finger))
        }
        Button(onClick = { onEvent(RingWearEvent.StartSketch) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.generated_draw_again))
        }
        Button(onClick = { onEvent(RingWearEvent.OpenGallery) }, colors = lightButtonColors()) {
            Text(stringResource(R.string.home_gallery))
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
        else -> stringResource(viewHint(sketch.view))
    }

private fun materialLabel(m: Material): Int =
    when (m) {
        Material.Clay -> R.string.material_clay
        Material.Resin -> R.string.material_resin
        Material.Gold -> R.string.material_gold
        Material.Silver -> R.string.material_silver
        Material.RoseGold -> R.string.material_rose
        Material.Platinum -> R.string.material_platinum
    }

private fun styleLabel(s: DesignStyle): Int =
    when (s) {
        DesignStyle.Refined -> R.string.style_refined
        DesignStyle.Cute -> R.string.style_cute
        DesignStyle.Vintage -> R.string.style_vintage
        DesignStyle.Minimal -> R.string.style_minimal
        DesignStyle.Bold -> R.string.style_bold
    }

private fun viewLabel(v: SketchView): Int =
    when (v) {
        SketchView.RingShape -> R.string.view_ring_shape
        SketchView.FromAbove -> R.string.view_from_above
        SketchView.Profile -> R.string.view_profile
    }

private fun viewHint(v: SketchView): Int =
    when (v) {
        SketchView.RingShape -> R.string.hint_ring_shape
        SketchView.FromAbove -> R.string.hint_from_above
        SketchView.Profile -> R.string.hint_profile
    }

@Composable
private fun Title(text: String) = Text(text, color = light(), style = PicoTheme.typography.titleLarge)

@Composable
private fun Body(text: String) = Text(text, color = light(), style = PicoTheme.typography.bodyLarge)

@Composable
private fun Label(text: String) = Text(text, color = light(), style = PicoTheme.typography.bodyMedium)

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
    ButtonDefaults.buttonColors(containerColor = Dim, contentColor = PicoTheme.colorScheme.labelPrimaryLight)

/**
 * A plain ring band to draw on, as it looks from each view, in 0..1 drawing coordinates.
 * It is drawn light grey and sent with the drawing, so the AI has a band even when the user
 * only draws the stone; the prompt tells it the grey lines are only a guide.
 */
private fun guideShapes(view: SketchView): List<List<StrokePoint>> {
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
        (0..72).map { k ->
            val a = 2.0 * Math.PI * k / 72
            StrokePoint((cx + rx * Math.cos(a)).toFloat(), (cy + ry * Math.sin(a)).toFloat())
        }
    fun box(x0: Float, y0: Float, x1: Float, y1: Float) =
        listOf(StrokePoint(x0, y0), StrokePoint(x1, y0), StrokePoint(x1, y1), StrokePoint(x0, y1), StrokePoint(x0, y0))
    // Matches a real ring seen from each side (Design 1, posed): the band, and a small stone
    // marker where a stone would sit.
    return when (view) {
        // Through the hole: the band is two circles, the stone sits on top.
        SketchView.RingShape ->
            listOf(
                ellipse(0.5f, 0.6f, 0.3f, 0.3f),
                ellipse(0.5f, 0.6f, 0.23f, 0.23f),
                ellipse(0.5f, 0.25f, 0.06f, 0.05f),
            )
        // Looking down at the stone: the band crosses as a wide strip, the stone in the middle.
        SketchView.FromAbove -> listOf(box(0.2f, 0.45f, 0.8f, 0.55f), ellipse(0.5f, 0.5f, 0.08f, 0.08f))
        // From the side: the band is a tall narrow strip, the stone on its top end.
        SketchView.Profile -> listOf(box(0.46f, 0.3f, 0.54f, 0.9f), ellipse(0.5f, 0.25f, 0.06f, 0.05f))
    }
}

/** Draws the guide and the strokes at 512 px, the size the render server expects. */
private fun toDataUri(strokes: List<List<Offset>>, view: SketchView, canvasPx: Int): String {
    val bitmap = Bitmap.createBitmap(SKETCH_PX, SKETCH_PX, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    fun paint(colour: Int, width: Float) =
        Paint().apply {
            color = colour
            strokeWidth = width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    fun draw(points: List<Offset>, paint: Paint) {
        if (points.isEmpty()) return
        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) path.lineTo(points[0].x + 0.1f, points[0].y)
        forEachCurve(points) { c, end -> path.quadTo(c.x, c.y, end.x, end.y) }
        canvas.drawPath(path, paint)
    }
    val guide = paint(GUIDE_GREY, GUIDE_WIDTH_PX)
    guideShapes(view).forEach { line -> draw(line.map { Offset(it.x * SKETCH_PX, it.y * SKETCH_PX) }, guide) }
    val k = SKETCH_PX.toFloat() / canvasPx.coerceAtLeast(1)
    val pen = paint(android.graphics.Color.BLACK, PEN_WIDTH_PX)
    strokes.forEach { points -> draw(points.map { Offset(it.x * k, it.y * k) }, pen) }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/**
 * Joins the points with curves through their midpoints, so a stroke made of few points (the
 * emulator sends only a handful per drag) still looks drawn by hand rather than as straight
 * segments.
 */
private fun forEachCurve(points: List<Offset>, curve: (control: Offset, end: Offset) -> Unit) {
    if (points.size < 2) return
    for (i in 1 until points.lastIndex) {
        val mid = Offset((points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
        curve(points[i], mid)
    }
    curve(points.last(), points.last())
}

private fun smoothPath(points: List<Offset>): Path =
    Path().apply {
        moveTo(points[0].x, points[0].y)
        if (points.size == 1) lineTo(points[0].x + 0.1f, points[0].y)
        forEachCurve(points) { c, end -> quadraticBezierTo(c.x, c.y, end.x, end.y) }
    }

private fun decodeDataUri(uri: String): ImageBitmap? =
    runCatching {
            val bytes = Base64.decode(uri.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        .getOrNull()
