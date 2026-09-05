package com.example.system.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayInteractionStateTest {

    @Test
    fun `overlay starts collapsed and can expand then collapse`() {
        val state = OverlayInteractionState()

        assertEquals(OverlayPresentation.COLLAPSED, state.presentation)

        state.expand()
        assertEquals(OverlayPresentation.EXPANDED, state.presentation)

        state.collapse()
        assertEquals(OverlayPresentation.COLLAPSED, state.presentation)
    }

    @Test
    fun `IME is requested only after expanded overlay receives window focus`() {
        val state = OverlayInteractionState()
        state.expand()

        assertTrue(state.requestNoteInput())
        assertFalse(state.onWindowFocusChanged(false))
        assertTrue(state.onWindowFocusChanged(true))
        assertFalse(state.onWindowFocusChanged(true))
    }

    @Test
    fun `collapsed overlay cannot request note input`() {
        val state = OverlayInteractionState()

        assertFalse(state.requestNoteInput())
        assertFalse(state.onWindowFocusChanged(true))
    }

    @Test
    fun `bubble position is clamped and snaps to nearest horizontal edge`() {
        assertEquals(8, BubblePosition.clamp(2, min = 8, max = 300))
        assertEquals(300, BubblePosition.clamp(340, min = 8, max = 300))
        assertEquals(120, BubblePosition.clamp(120, min = 8, max = 300))

        assertEquals(8, BubblePosition.snapX(currentX = 70, bubbleWidth = 56, screenWidth = 360, edgeInset = 8))
        assertEquals(296, BubblePosition.snapX(currentX = 220, bubbleWidth = 56, screenWidth = 360, edgeInset = 8))
    }
}
