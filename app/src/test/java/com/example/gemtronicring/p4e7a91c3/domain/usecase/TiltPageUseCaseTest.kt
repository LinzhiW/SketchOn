package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.JointRotation
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class TiltPageUseCaseTest {
    private val useCase = TiltPageUseCase()

    private fun rollAboutZ(degrees: Float): JointRotation {
        val half = Math.toRadians(degrees / 2.0)
        return JointRotation(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
    }

    @Test
    fun `roll is measured from the neutral pose`() {
        val roll = useCase.rollDegrees(rollAboutZ(10f), rollAboutZ(40f))
        assertEquals(30f, roll, 0.01f)
    }

    @Test
    fun `roll takes the short way round`() {
        val roll = useCase.rollDegrees(rollAboutZ(170f), rollAboutZ(-170f))
        assertEquals(20f, roll, 0.01f)
    }

    @Test
    fun `tilting past the trigger moves one page and disarms`() {
        val forward = useCase.step(30f, armed = true)
        assertEquals(1, forward.pageDelta)
        assertEquals(false, forward.armed)
        assertEquals(-1, useCase.step(-30f, armed = true).pageDelta)
    }

    @Test
    fun `no page moves until the finger comes back near neutral`() {
        assertEquals(0, useCase.step(40f, armed = false).pageDelta)
        assertEquals(false, useCase.step(15f, armed = false).armed)
        assertEquals(true, useCase.step(5f, armed = false).armed)
    }

    @Test
    fun `pages wrap at both ends`() {
        assertEquals(0, useCase.movedPage(2, 1, 3))
        assertEquals(2, useCase.movedPage(0, -1, 3))
        assertEquals(0, useCase.movedPage(0, 1, 0))
    }
}
