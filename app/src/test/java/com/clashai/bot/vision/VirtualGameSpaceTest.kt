package com.clashai.bot.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualGameSpaceTest {
    @Test
    fun nativeVirtualCanvasMapsOneToOne() {
        val point = VirtualGameSpace.toScreen(540f, 1200f, 1080, 2400)
        assertEquals(540, point.x)
        assertEquals(1200, point.y)
    }

    @Test
    fun widerTabletCentersVirtualCanvasWithoutStretching() {
        val point = VirtualGameSpace.toScreen(540f, 1200f, 1600, 2560)
        assertEquals(800, point.x)
        assertEquals(1280, point.y)
    }

    @Test
    fun narrowerDisplayAddsVerticalLetterbox() {
        val point = VirtualGameSpace.toScreen(540f, 1200f, 1000, 2500)
        assertEquals(500, point.x)
        assertEquals(1250, point.y)
    }

    @Test
    fun handSlotsUseFixedVirtualCoordinates() {
        assertEquals(VirtualGameSpace.Point(180, 2160), VirtualGameSpace.handSlot(0))
        assertEquals(VirtualGameSpace.Point(900, 2160), VirtualGameSpace.handSlot(3))
    }
}
