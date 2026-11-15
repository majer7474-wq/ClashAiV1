package com.clashai.bot.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCoordinateMapperTest {
    @Test fun mapsArenaAcrossFullLandscapeDisplay() {
        val point = ScreenCoordinateMapper.normalizedToPixel(0.70f, 0.515f, 2880, 1800)
        assertEquals(2015, point.x)
        assertEquals(926, point.y)
    }

    @Test fun clampsOutOfRangeCoordinates() {
        val point = ScreenCoordinateMapper.normalizedToPixel(1.4f, -0.2f, 2880, 1800)
        assertEquals(2879, point.x)
        assertEquals(0, point.y)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDisplaySize() {
        ScreenCoordinateMapper.normalizedToPixel(0.5f, 0.5f, 0, 1800)
    }
}
