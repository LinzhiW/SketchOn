package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.ui.theme.GemtronicColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

private val PanelShape = RoundedCornerShape(24.dp)
private val MessageMaxWidth = 480.dp

/** The only 2D UI in the app, and only while the ring is not on the hand. */
@Composable
internal fun HintPanel(phase: RingPhase) {
    when (phase) {
        RingPhase.HandSearching -> HandHint()
        RingPhase.TrackingUnavailable -> TrackingMessage()
        RingPhase.RingWorn -> Unit
    }
}

@Composable
private fun HandHint() {
    Text(
        text = stringResource(R.string.hand_hint),
        color = PicoTheme.colorScheme.labelPrimaryLight,
        style = PicoTheme.typography.titleLarge,
        modifier =
            Modifier
                // design-style: design-surface hand-hint
                .background(GemtronicColors.ringScreenBase, PanelShape)
                .padding(24.dp),
    )
}

@Composable
private fun TrackingMessage() {
    Text(
        text = stringResource(R.string.tracking_message),
        color = PicoTheme.colorScheme.labelPrimaryLight,
        style = PicoTheme.typography.bodyLarge,
        modifier =
            Modifier.widthIn(max = MessageMaxWidth)
                // design-style: design-surface tracking-message
                .background(GemtronicColors.ringScreenBase, PanelShape)
                .padding(24.dp),
    )
}
