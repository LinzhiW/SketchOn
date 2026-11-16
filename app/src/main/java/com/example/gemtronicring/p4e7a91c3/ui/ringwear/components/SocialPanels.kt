package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R
import com.example.gemtronicring.p4e7a91c3.domain.model.Colourway
import com.example.gemtronicring.p4e7a91c3.domain.model.NoticeKind
import com.example.gemtronicring.p4e7a91c3.domain.model.RecordStatus
import com.example.gemtronicring.p4e7a91c3.domain.model.RingRecord
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.MineTab
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.MineUiState
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingPageUiState
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearEvent
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.SaveColourUiState
import com.example.gemtronicring.p4e7a91c3.ui.theme.GemtronicColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text

private val RingPageWidth = 760.dp
private val PictureSize = 220.dp
private val SwatchSize = 22.dp
/** How many colourways a section shows; the panel cannot scroll. */
private const val SECTION_ROWS = 4
/** How many entries a tab of the Me page shows. */
private const val LIST_ROWS = 6

/** "♥ 3" and "★ 1", as a post shows them in its bottom right corner. */
@Composable
internal fun LikesAndSaves(likes: Int, saves: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(SmallGap), verticalAlignment = Alignment.CenterVertically) {
        Label(stringResource(R.string.count_likes, likes))
        if (saves != null) Label(stringResource(R.string.count_saves, saves))
    }
}

/** A colourway's colours as a row of dots: band first, then the decoration. */
@Composable
internal fun Swatches(paints: Map<Int, Int>, painted: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        paints.toSortedMap().values.forEach { argb ->
            Box(Modifier.size(SwatchSize).clip(RoundedCornerShape(SwatchSize / 2)).background(Color(argb)))
        }
        if (painted) Label(stringResource(R.string.colourway_painted))
        if (paints.isEmpty() && !painted) Label(stringResource(R.string.colourway_plain))
    }
}

/** On the painting panel: save these colours as a colourway. */
@Composable
internal fun SaveColourRow(save: SaveColourUiState, canSave: Boolean, onEvent: (RingWearEvent) -> Unit) {
    if (!canSave) return
    Column(verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onEvent(RingWearEvent.SaveColourway) },
                enabled = !save.saving,
                colors = lightButtonColors(),
            ) {
                Text(stringResource(if (save.saving) R.string.colourway_saving else R.string.colourway_save))
            }
            if (save.saved) Label(stringResource(R.string.colourway_saved))
        }
        if (save.needsLogin) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
                Body(stringResource(R.string.colourway_needs_login))
                Button(onClick = { onEvent(RingWearEvent.OpenLogin) }, colors = darkButtonColors()) {
                    Text(stringResource(R.string.home_log_in))
                }
            }
        }
        save.error?.let { Body(it) }
    }
}

/**
 * One ring, like a post: its picture with likes and saves in the bottom right corner, Wear and
 * Recolour, then the author's colourways and everyone else's (featured first).
 */
@Composable
internal fun RingPagePanel(ui: RingPageUiState, opening: Boolean, onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.width(RingPageWidth).background(GemtronicColors.ringScreenBase, CardPanelShape).padding(Pad),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onEvent)
            Title(ui.page?.piece?.name ?: stringResource(R.string.ring_page_untitled))
        }
        val page = ui.page
        if (page == null) {
            Body(ui.error ?: stringResource(R.string.gallery_loading))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Pad), verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(SmallGap)) {
                val picture = remember(ui.picture) { ui.picture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                Box(Modifier.size(PictureSize).clip(SquareShape).background(Dim), contentAlignment = Alignment.Center) {
                    if (picture != null) Image(bitmap = picture, contentDescription = page.piece.name, modifier = Modifier.fillMaxSize())
                }
                // The post's corner: likes and saves, each a button that toggles.
                Row(horizontalArrangement = Arrangement.spacedBy(SmallGap), verticalAlignment = Alignment.CenterVertically) {
                    Label(page.piece.author?.let { stringResource(R.string.gallery_by, it) } ?: stringResource(R.string.gallery_by_unknown))
                    Button(
                        onClick = { onEvent(RingWearEvent.LikeRing(!page.likedByMe)) },
                        colors = if (page.likedByMe) chosenButtonColors() else darkButtonColors(),
                    ) { Text(stringResource(R.string.count_likes, page.likes)) }
                    Button(
                        onClick = { onEvent(RingWearEvent.SaveRing(!page.savedByMe)) },
                        colors = if (page.savedByMe) chosenButtonColors() else darkButtonColors(),
                    ) { Text(stringResource(R.string.count_saves, page.saves)) }
                }
                Label(stringResource(R.string.ring_page_wearers, page.wearers))
            }
            Column(verticalArrangement = Arrangement.spacedBy(SmallGap), modifier = Modifier.width(RingPageWidth - PictureSize - Pad * 3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
                    Button(onClick = { onEvent(RingWearEvent.WearColourway(null)) }, enabled = !opening, colors = lightButtonColors()) {
                        Text(stringResource(if (opening) R.string.gallery_opening else R.string.ring_page_wear))
                    }
                    Button(onClick = { onEvent(RingWearEvent.Recolour) }, enabled = !opening, colors = darkButtonColors()) {
                        Text(stringResource(R.string.ring_page_recolour))
                    }
                }
                Label(stringResource(R.string.ring_page_author_colours))
                ColourwayList(page.authorColourways, own = true, page.isAuthor, page.coverId, opening, onEvent)
                Label(stringResource(R.string.ring_page_other_colours))
                ColourwayList(page.otherColourways, own = false, page.isAuthor, page.coverId, opening, onEvent)
            }
        }
        ui.error?.let { Body(it) }
        if (page.isAuthor) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
                if (ui.confirmDelete) {
                    Body(stringResource(R.string.ring_page_delete_ask))
                    Button(onClick = { onEvent(RingWearEvent.DeleteRing(true)) }, colors = darkButtonColors()) {
                        Text(stringResource(R.string.ring_page_delete_yes))
                    }
                } else {
                    Button(onClick = { onEvent(RingWearEvent.DeleteRing(false)) }, colors = darkButtonColors()) {
                        Text(stringResource(R.string.ring_page_delete))
                    }
                }
            }
        }
    }
}

/**
 * A section of colourways. Each can be worn and liked. The author also sees: Cover on their
 * own, Feature and Hide on other people's. A colourway's maker can delete it.
 */
@Composable
private fun ColourwayList(
    colourways: List<Colourway>,
    own: Boolean,
    isAuthor: Boolean,
    coverId: String?,
    opening: Boolean,
    onEvent: (RingWearEvent) -> Unit,
) {
    if (colourways.isEmpty()) {
        Body(stringResource(R.string.ring_page_no_colours))
        return
    }
    colourways.take(SECTION_ROWS).forEach { c ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(SmallGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(SquareShape).background(Dim).padding(SmallGap),
        ) {
            Swatches(c.paints, c.textureUrl != null)
            Label(
                when {
                    c.featured -> stringResource(R.string.colourway_featured_by, c.author)
                    c.hidden -> stringResource(R.string.colourway_hidden_by, c.author)
                    else -> stringResource(R.string.gallery_by, c.author)
                }
            )
            Button(onClick = { onEvent(RingWearEvent.WearColourway(c)) }, enabled = !opening, colors = lightButtonColors()) {
                Text(stringResource(R.string.ring_page_wear))
            }
            Button(
                onClick = { onEvent(RingWearEvent.LikeColourway(c.id, !c.likedByMe)) },
                colors = if (c.likedByMe) chosenButtonColors() else darkButtonColors(),
            ) { Text(stringResource(R.string.count_likes, c.likes)) }
            if (isAuthor && own) {
                Button(
                    onClick = { onEvent(RingWearEvent.SetCover(c.id)) },
                    enabled = c.id != coverId,
                    colors = if (c.id == coverId) chosenButtonColors() else darkButtonColors(),
                ) { Text(stringResource(if (c.id == coverId) R.string.colourway_is_cover else R.string.colourway_make_cover)) }
            }
            if (isAuthor && !own) {
                Button(
                    onClick = { onEvent(RingWearEvent.FeatureColourway(c.id, !c.featured)) },
                    colors = if (c.featured) chosenButtonColors() else darkButtonColors(),
                ) { Text(stringResource(R.string.colourway_feature)) }
                Button(onClick = { onEvent(RingWearEvent.HideColourway(c.id, !c.hidden)) }, colors = darkButtonColors()) {
                    Text(stringResource(if (c.hidden) R.string.colourway_unhide else R.string.colourway_hide))
                }
            }
            if (isAuthor && own) {
                Button(onClick = { onEvent(RingWearEvent.DeleteColourway(c.id)) }, colors = darkButtonColors()) {
                    Text(stringResource(R.string.colourway_delete))
                }
            }
        }
    }
    if (colourways.size > SECTION_ROWS) Label(stringResource(R.string.ring_page_more, colourways.size - SECTION_ROWS))
}

/** Mine: rings I drew and colourways I made, saved rings, what I wore, and notifications. */
@Composable
internal fun MinePanel(ui: MineUiState, onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.width(RingPageWidth).background(GemtronicColors.ringScreenBase, CardPanelShape).padding(Pad),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onEvent)
            Title(stringResource(R.string.mine_title))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
            listOf(
                MineTab.Creations to stringResource(R.string.mine_creations),
                MineTab.Saved to stringResource(R.string.mine_saved),
                MineTab.Worn to stringResource(R.string.mine_worn),
                MineTab.Notices to
                    if (ui.unseen > 0) stringResource(R.string.mine_notices_new, ui.unseen) else stringResource(R.string.mine_notices),
            ).forEach { (tab, label) ->
                Button(
                    onClick = { onEvent(RingWearEvent.ChooseMineTab(tab)) },
                    colors = if (tab == ui.tab) chosenButtonColors() else darkButtonColors(),
                ) { Text(label) }
            }
        }
        val library = ui.library
        if (library == null) {
            Body(ui.error ?: stringResource(R.string.gallery_loading))
            return@Column
        }
        when (ui.tab) {
            MineTab.Creations -> {
                Label(stringResource(R.string.mine_my_rings))
                if (library.rings.isEmpty()) Body(stringResource(R.string.mine_none))
                library.rings.take(LIST_ROWS).forEach { ring ->
                    EntryRow(ring.name ?: stringResource(R.string.ring_page_untitled), null) { onEvent(RingWearEvent.OpenRing(ring.id)) }
                }
                Label(stringResource(R.string.mine_my_colours))
                if (library.colourways.isEmpty()) Body(stringResource(R.string.mine_none))
                library.colourways.take(LIST_ROWS).forEach { mc ->
                    val ring = mc.pieceName ?: stringResource(R.string.ring_page_untitled)
                    val title = if (mc.onMyRing) ring else stringResource(R.string.mine_on_ring_by, ring, mc.pieceAuthor ?: "?")
                    Row(horizontalArrangement = Arrangement.spacedBy(SmallGap), verticalAlignment = Alignment.CenterVertically) {
                        Swatches(mc.colourway.paints, mc.colourway.textureUrl != null)
                        EntryRow(title, mc.status) { onEvent(RingWearEvent.OpenRing(mc.colourway.pieceId)) }
                    }
                }
            }
            MineTab.Saved -> {
                if (library.saved.isEmpty()) Body(stringResource(R.string.mine_none))
                library.saved.take(LIST_ROWS).forEach { RecordRow(it, onEvent) }
            }
            MineTab.Worn -> {
                if (library.worn.isEmpty()) Body(stringResource(R.string.mine_none))
                library.worn.take(LIST_ROWS).forEach { RecordRow(it, onEvent) }
            }
            MineTab.Notices -> {
                if (ui.notices.isEmpty()) Body(stringResource(R.string.mine_no_notices))
                ui.notices.take(LIST_ROWS).forEach { n ->
                    val ring = n.pieceName ?: stringResource(R.string.ring_page_untitled)
                    val text =
                        when (n.kind) {
                            NoticeKind.Recoloured -> stringResource(R.string.notice_recoloured, n.actor, ring)
                            NoticeKind.Featured -> stringResource(R.string.notice_featured, n.actor, ring)
                        }
                    EntryRow(text, null) { onEvent(RingWearEvent.OpenRing(n.pieceId)) }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: RingRecord, onEvent: (RingWearEvent) -> Unit) {
    val name = record.name ?: stringResource(R.string.ring_page_untitled)
    val title = record.author?.let { stringResource(R.string.mine_ring_by, name, it) } ?: name
    Row(horizontalArrangement = Arrangement.spacedBy(SmallGap), verticalAlignment = Alignment.CenterVertically) {
        record.paints?.let { Swatches(it, false) }
        EntryRow(title, record.status) { onEvent(RingWearEvent.OpenRing(record.pieceId)) }
    }
}

/** A line in a list: its text, a status when it is not live, and Open (not for removed rings). */
@Composable
private fun EntryRow(text: String, status: RecordStatus?, onOpen: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SmallGap), verticalAlignment = Alignment.CenterVertically) {
        Body(text)
        when (status) {
            RecordStatus.Removed -> Label(stringResource(R.string.record_removed))
            RecordStatus.Hidden -> Label(stringResource(R.string.record_hidden))
            else -> Unit
        }
        if (status != RecordStatus.Removed) {
            Button(onClick = onOpen, colors = darkButtonColors()) { Text(stringResource(R.string.mine_open)) }
        }
    }
}
