package com.example.gemtronicring.p4e7a91c3.data.repository

import com.example.gemtronicring.p4e7a91c3.domain.model.MenuItem
import com.example.gemtronicring.p4e7a91c3.domain.model.RingMenu

interface RingMenuRepository {
    fun menu(): RingMenu
}

/** The ring's menu, as mapped by the website's ring simulator, from frames the firmware renders. */
class BundledRingMenuRepository : RingMenuRepository {
    override fun menu(): RingMenu =
        RingMenu(
            root = "home",
            levels =
                mapOf(
                    "home" to
                        listOf(
                            MenuItem("idle", "ambient"),
                            MenuItem("home-pet", "pet"),
                            MenuItem("home-game", "game"),
                            MenuItem("home-exercise", "exercise"),
                            MenuItem("home-rhythm", "rhythm"),
                            MenuItem("home-settings", "settings"),
                        ),
                    "ambient" to
                        listOf(
                            MenuItem("idle-gem", "face-gem"),
                            MenuItem("idle-clock", "face-clock"),
                            MenuItem("idle-rest", "face-rest"),
                        ),
                    "face-gem" to listOf(MenuItem("face-gem-dragon")),
                    "face-clock" to listOf(MenuItem("face-clock-network")),
                    "face-rest" to listOf(MenuItem("face-rest")),
                    "pet" to listOf(MenuItem("pet-egg")),
                    "game" to
                        listOf(
                            MenuItem("game-menu", "starship"),
                            MenuItem("game-menu-dice", "dice"),
                        ),
                    "starship" to listOf(MenuItem("game-play")),
                    "dice" to listOf(MenuItem("dice-ready")),
                    "exercise" to listOf(MenuItem("exercise-walking")),
                    "rhythm" to
                        listOf(
                            MenuItem("rhythm-menu", "stars"),
                            MenuItem("rhythm-menu-wave", "wave"),
                            MenuItem("rhythm-menu-circuit", "circuit"),
                        ),
                    "stars" to listOf(MenuItem("rhythm-strike")),
                    "wave" to listOf(MenuItem("wave-beat")),
                    "circuit" to listOf(MenuItem("circuit-beat")),
                    "settings" to listOf(MenuItem("settings")),
                ),
        )
}
