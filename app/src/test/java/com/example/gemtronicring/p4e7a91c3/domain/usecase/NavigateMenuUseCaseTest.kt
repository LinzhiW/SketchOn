package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingMenuRepository
import com.example.gemtronicring.p4e7a91c3.domain.model.MenuCursor
import com.example.gemtronicring.p4e7a91c3.domain.model.RingGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateMenuUseCaseTest {
    private val menu = BundledRingMenuRepository().menu()
    private val useCase = NavigateMenuUseCase()
    private val home = MenuCursor(menu.root)

    @Test
    fun `next and previous wrap at both ends`() {
        val last = useCase.move(menu, home, RingGesture.Previous)
        assertEquals("home-settings", useCase.frame(menu, last))
        assertEquals("idle", useCase.frame(menu, useCase.move(menu, last, RingGesture.Next)))
    }

    @Test
    fun `confirm opens the item and back returns to the same place`() {
        val onGame =
            useCase.move(menu, useCase.move(menu, home, RingGesture.Next), RingGesture.Next)
        val inside = useCase.move(menu, onGame, RingGesture.Confirm)
        assertEquals("game-menu", useCase.frame(menu, inside))
        assertEquals(onGame, useCase.move(menu, inside, RingGesture.Back))
    }

    @Test
    fun `confirm on a leaf and back at the top do nothing`() {
        val pet = MenuCursor("pet")
        assertEquals(pet, useCase.move(menu, pet, RingGesture.Confirm))
        assertEquals(home, useCase.move(menu, home, RingGesture.Back))
    }

    @Test
    fun `every opened level exists`() {
        val targets = menu.levels.values.flatten().mapNotNull { it.opens }
        assertTrue(targets.all { it in menu.levels })
    }
}
