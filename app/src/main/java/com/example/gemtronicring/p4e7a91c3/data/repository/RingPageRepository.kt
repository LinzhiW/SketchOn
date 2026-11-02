package com.example.gemtronicring.p4e7a91c3.data.repository

import com.example.gemtronicring.p4e7a91c3.domain.model.RingPage

interface RingPageRepository {
    fun pages(): List<RingPage>
}

/** The firmware screens packed into the app's assets. */
class BundledRingPageRepository : RingPageRepository {
    override fun pages(): List<RingPage> =
        listOf(
            RingPage(id = "gem", assetPath = "pages/page_gem.png"),
            RingPage(id = "clock", assetPath = "pages/page_clock.png"),
            RingPage(id = "pet", assetPath = "pages/page_pet.png"),
        )
}
