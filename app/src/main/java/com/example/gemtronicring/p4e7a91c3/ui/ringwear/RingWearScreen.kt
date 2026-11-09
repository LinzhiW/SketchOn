package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.components.RingOnHand
import com.example.gemtronicring.p4e7a91c3.data.local.PreferencesSessionStore
import java.io.File

@Composable
fun RingWearScreen() {
    val context = LocalContext.current
    val modelsDir = File(context.filesDir, "models")
    val vm: RingWearViewModel =
        viewModel(factory = RingWearViewModelFactory(modelsDir, PreferencesSessionStore(context)))
    val state by vm.state.collectAsStateWithLifecycle()
    RingWearContent(state = state, onEvent = vm::onEvent)
}

@Composable
internal fun RingWearContent(state: RingWearUiState, onEvent: (RingWearEvent) -> Unit) {
    RingOnHand(state = state, onEvent = onEvent)
}
