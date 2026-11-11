package com.example.gemtronicring.p4e7a91c3

import androidx.compose.ui.unit.dp
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.LOGIN_WINDOW_ID
import com.example.gemtronicring.p4e7a91c3.ui.ringwear.LoginWindow
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
        // A system window, so the user can move it by its handle and type into it.
        WindowContainer(
            LOGIN_WINDOW_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 520.dp, height = 640.dp),
            // The panel draws its own dark card; the frosted backing would show as a second box.
            enableMaterialBackground = false,
        ) {
            PicoTheme {
                LoginWindow()
            }
        }
    }
