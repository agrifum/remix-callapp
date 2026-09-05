package com.example.system.overlay

internal enum class OverlayPresentation {
    COLLAPSED,
    EXPANDED
}

internal class OverlayInteractionState {
    var presentation: OverlayPresentation = OverlayPresentation.COLLAPSED
        private set

    private var pendingImeRequest: Boolean = false

    fun expand() {
        presentation = OverlayPresentation.EXPANDED
    }

    fun collapse() {
        presentation = OverlayPresentation.COLLAPSED
        pendingImeRequest = false
    }

    fun requestNoteInput(): Boolean {
        if (presentation != OverlayPresentation.EXPANDED) return false
        pendingImeRequest = true
        return true
    }

    fun onWindowFocusChanged(hasFocus: Boolean): Boolean {
        if (!hasFocus || !pendingImeRequest) return false
        pendingImeRequest = false
        return true
    }

    fun cancelNoteInput() {
        pendingImeRequest = false
    }
}

internal object BubblePosition {
    fun clamp(value: Int, min: Int, max: Int): Int {
        if (max < min) return min
        return value.coerceIn(min, max)
    }

    fun snapX(currentX: Int, bubbleWidth: Int, screenWidth: Int, edgeInset: Int): Int {
        val left = edgeInset
        val right = (screenWidth - bubbleWidth - edgeInset).coerceAtLeast(left)
        val bubbleCenter = currentX + bubbleWidth / 2
        return if (bubbleCenter < screenWidth / 2) left else right
    }
}
