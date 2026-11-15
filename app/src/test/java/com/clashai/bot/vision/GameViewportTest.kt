package com.clashai.bot.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameViewportTest {
    @Test fun mapsPortraitTabletWithHorizontalLetterboxing() {
        val viewport = GameViewport.fromFrame(1800, 2880)!!
        assertEquals(660, viewport.mapX(340f))
        assertEquals(900, viewport.mapX(540f))
        assertEquals(2604, viewport.mapY(2170f))
    }

    @Test fun mapsLandscapeTabletWithCenteredPortraitGame() {
        val viewport = GameViewport.fromFrame(2880, 1800)!!
        assertEquals(1290, viewport.mapX(340f))
        assertEquals(1440, viewport.mapX(540f))
        assertEquals(1628, viewport.mapY(2170f))
    }

    @Test fun rejectsInvalidFramesAndCoordinatesOutsideReferenceViewport() {
        assertNull(GameViewport.fromFrame(0, 1800))
        val viewport = GameViewport.fromFrame(2880, 1800)!!
        assertNull(viewport.mapX(1081f))
        assertNull(viewport.mapY(-1f))
        assertNotNull(viewport.mapPoint(540f, 1200f))
    }
}
