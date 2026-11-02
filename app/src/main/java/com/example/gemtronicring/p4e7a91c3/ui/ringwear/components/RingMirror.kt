package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R

private val MirrorSize = 200.dp

/** A large copy of the ring's screen, because the real one is too small to read at arm's length. */
@Composable
internal fun RingMirror(frame: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image =
        remember(frame) {
            frame?.let { name ->
                context.assets.open("pages/$name.png").use { BitmapFactory.decodeStream(it) }
                    ?.asImageBitmap()
            }
        } ?: return
    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.ring_screen_description),
        filterQuality = FilterQuality.None,
        modifier = modifier.size(MirrorSize).clip(CircleShape),
    )
}
