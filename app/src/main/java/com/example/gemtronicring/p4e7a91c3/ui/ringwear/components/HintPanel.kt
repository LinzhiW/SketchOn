package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.R
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPhase
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearEvent
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearUiState
import com.example.gemtronicring.p4e7a91c3.ui.theme.GemtronicColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonColors
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

private val PanelShape = RoundedCornerShape(24.dp)
private val PanelWidth = 440.dp
private val GuideWidth = 380.dp
private val ControlWidth = 300.dp
private val ArrowButtonSize = 80.dp
private val MessageMaxWidth = 480.dp
private val PanelGap = 16.dp
private val PanelPadding = 24.dp

/** The panel in front of the user: finger choice first, then whatever the ring needs next. */
@Composable
internal fun CenterPanel(state: RingWearUiState, onEvent: (RingWearEvent) -> Unit) {
    when (state.phase) {
        RingPhase.Home -> HomePanel(onEvent)
        RingPhase.Gallery -> GalleryPanel(state.gallery, onEvent)
        RingPhase.Sketching -> SketchPanel(state.sketch, onEvent)
        RingPhase.Naming -> NamingPanel(state.sketch, onEvent)
        RingPhase.Making3D -> Making3dPanel(state.sketch, onEvent)
        RingPhase.FingerChoosing -> FingerPanel(state, onEvent)
        RingPhase.HandSearching -> HandHint()
        RingPhase.TrackingUnavailable -> TrackingMessage()
        RingPhase.RingWorn -> Unit
    }
}

@Composable
private fun FingerPanel(state: RingWearUiState, onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PanelGap),
        modifier =
            Modifier.width(PanelWidth)
                // design-style: design-surface finger-panel
                .background(GemtronicColors.ringScreenBase, PanelShape)
                .padding(PanelPadding),
    ) {
        PanelTitle(stringResource(R.string.finger_title))
        HandPicker(selected = state.finger) { onEvent(RingWearEvent.SelectFinger(it)) }
        Button(onClick = { onEvent(RingWearEvent.ConfirmFinger) }, colors = lightButton()) {
            Text(stringResource(R.string.wear_button))
        }
    }
}

/** The left panel: buttons for the same four inputs, for when a gesture is not picked up. */
@Composable
internal fun ControlPanel(onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PanelGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.width(ControlWidth)
                // design-style: design-surface control-panel
                .background(GemtronicColors.ringScreenBase, PanelShape)
                .padding(PanelPadding),
    ) {
        PanelTitle(stringResource(R.string.control_title))
        Row(horizontalArrangement = Arrangement.spacedBy(PanelGap)) {
            ArrowButton(R.drawable.ic_arrow_left, R.string.previous_page) {
                onEvent(RingWearEvent.MenuInput(RingGesture.Previous))
            }
            ArrowButton(R.drawable.ic_arrow_right, R.string.next_page) {
                onEvent(RingWearEvent.MenuInput(RingGesture.Next))
            }
        }
        Button(
            onClick = { onEvent(RingWearEvent.MenuInput(RingGesture.Confirm)) },
            colors = lightButton(),
        ) {
            Text(stringResource(R.string.confirm_button))
        }
        Button(
            onClick = { onEvent(RingWearEvent.MenuInput(RingGesture.Back)) },
            colors = lightButton(),
        ) {
            Text(stringResource(R.string.back_button))
        }
    }
}

@Composable
private fun ArrowButton(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        size = IconButtonDefaults.iconButtonSize(ArrowButtonSize),
        colors = lightButton(),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = stringResource(labelRes))
    }
}

/** The right panel: the real ring's four gestures. */
@Composable
internal fun GestureGuide(frame: String?, onEvent: (RingWearEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PanelGap),
        modifier =
            Modifier.width(GuideWidth)
                // design-style: design-surface gesture-guide
                .background(GemtronicColors.ringScreenBase, PanelShape)
                .padding(PanelPadding),
    ) {
        RingMirror(frame, Modifier.align(Alignment.CenterHorizontally))
        PanelTitle(stringResource(R.string.guide_title))
        GuideLine(R.string.guide_next)
        GuideLine(R.string.guide_previous)
        GuideLine(R.string.guide_open)
        GuideLine(R.string.guide_back)
        GuideLine(R.string.guide_click)
        Button(onClick = { onEvent(RingWearEvent.ChangeFinger) }, colors = lightButton()) {
            Text(stringResource(R.string.change_finger))
        }
    }
}

// The panels are dark so they read in any room, and SpatialUI's default button is dark there.
@Composable
private fun lightButton(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = PicoTheme.colorScheme.labelPrimaryLight,
        contentColor = GemtronicColors.ringScreenBase,
    )

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        color = PicoTheme.colorScheme.labelPrimaryLight,
        style = PicoTheme.typography.titleLarge,
    )
}

@Composable
private fun GuideLine(textRes: Int) {
    Text(
        text = stringResource(textRes),
        color = PicoTheme.colorScheme.labelPrimaryLight,
        style = PicoTheme.typography.bodyLarge,
    )
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
                .padding(PanelPadding),
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
                .padding(PanelPadding),
    )
}
