package com.mick.zynaddsubfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnobDragScaleTest {
    @Test fun twoChoicesCanSwitchWithinSixteenDpOnATallScreen() {
        val density = 3f
        val travel = knobDragRangePx(1f, 2400f, 32f * density)
        assertEquals(32f, travel / density, .001f)
        assertTrue(16.1f * density / travel > .5f)
    }

    @Test fun threeChoicesNeedAtMostThirtyTwoDpPerStep() {
        val density = 2f
        val travel = knobDragRangePx(2f, 2000f, 32f * density)
        assertEquals(1f, 32f * density / travel * 2f, .001f)
    }

    @Test fun shortRangeTravelIsIndependentOfPixelDensity() {
        for (density in listOf(1f, 2f, 3f, 4f)) {
            assertEquals(32f, knobDragRangePx(3f, 1000f * density, 32f * density) / 3f / density, .001f)
        }
    }

    @Test fun wideRangeRetainsItsExistingSensitivity() {
        assertEquals(600f, knobDragRangePx(16383f, 600f, 96f), .001f)
        assertEquals(2400f, knobDragRangePx(127f, 2400f, 96f), .001f)
    }

    @Test fun degenerateBoundsDoNotProduceZeroTravel() {
        assertEquals(1f, knobDragRangePx(0f, 0f, 0f), .001f)
    }
}
