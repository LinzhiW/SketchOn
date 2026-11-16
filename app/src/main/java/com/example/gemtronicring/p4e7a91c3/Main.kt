package com.example.gemtronicring.p4e7a91c3

import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.MAIN_WINDOW_ID
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.MainWindow
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                RingWearScreen()
            }
        }
        // The app's panels, in a system window: moved by its handle, and typing works in it.
        WindowContainer(
            MAIN_WINDOW_ID,
            form = Form.Planar,
            // Fits the largest panel (drawing); smaller ones sit in the middle (see MainWindow).
            defaultSize = WindowContainerSize(width = 1160.dp, height = 780.dp),
            // The panel draws its own dark card; the frosted backing would show as a second box.
            enableMaterialBackground = false,
        ) {
            PicoTheme {
                MainWindow()
            }
        }
    }
