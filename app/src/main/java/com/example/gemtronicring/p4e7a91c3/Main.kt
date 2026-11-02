package com.example.gemtronicring.p4e7a91c3

import com.example.gemtronicring.p4e7a91c3.ui.ringwear.RingWearScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                RingWearScreen()
            }
        }
    }

