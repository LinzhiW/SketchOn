package com.example.gemtronicring.p4e7a91c3.domain.usecase

import com.example.gemtronicring.p4e7a91c3.domain.model.ModelBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitRingUseCaseTest {
    private val fit = FitRingUseCase(outerDiameterM = 0.02f)

    @Test
    fun scalesTheBandWidthToTheOuterDiameter() {
        val result = fit.fit(ModelBounds(-0.5f, -0.5f, -0.1f, 0.5f, 0.5f, 0.1f))!!
        assertEquals(0.02f, result.scale, 1e-6f)
        assertFalse(result.holeAlongX)
    }

    @Test
    fun findsAHoleAlongXLikeTheFirstTripoModel() {
        // Measured from the first Tripo model: thin in X, a stone making it tall in Y.
        val result = fit.fit(ModelBounds(-0.092f, -0.5f, -0.41f, 0.092f, 0.5f, 0.41f))!!
        assertTrue(result.holeAlongX)
        assertEquals(0.02f / 0.82f, result.scale, 1e-6f)
        // Hole centre: half the band (0.41) above the bottom (-0.5), so y = -0.09.
        assertEquals(0.09f * result.scale, result.offsetY, 1e-6f)
    }

    @Test
    fun putsTheHoleCentreOnTheJointWhenAStoneMakesTheModelTaller() {
        val result = fit.fit(ModelBounds(-0.5f, -0.5f, -0.1f, 0.5f, 0.9f, 0.1f))!!
        assertEquals(0f, result.offsetY, 1e-6f)
        assertEquals(0f, result.offsetX, 1e-6f)
    }

    @Test
    fun movesAnOffCentreModelBack() {
        val result = fit.fit(ModelBounds(1f, 2f, 3f, 2f, 3f, 4f))!!
        assertEquals(-1.5f * 0.02f, result.offsetX, 1e-6f)
        assertEquals(-2.5f * 0.02f, result.offsetY, 1e-6f)
        assertEquals(-3.5f * 0.02f, result.offsetZ, 1e-6f)
    }

    @Test
    fun refusesAnEmptyModel() {
        assertNull(fit.fit(ModelBounds(0f, 0f, 0f, 0f, 0f, 0f)))
    }
}
