package com.musaraj.forumindex

/** Receives consumed scroll distance only, so layout changes and overscroll cannot reveal the title. */
internal class TopicTitleVisibility {
    var isVisible = false
        private set
    private var travel = 0f

    fun reset() { isVisible = false; travel = 0f }

    fun scroll(delta: Float, titleOffscreen: Boolean) {
        if (!titleOffscreen) { reset(); return }
        if (delta == 0f) return
        if ((delta > 0 && travel < 0) || (delta < 0 && travel > 0)) travel = 0f
        travel += delta
        if (travel <= -12f) { isVisible = true; travel = -12f }
        else if (travel >= 12f) { isVisible = false; travel = 12f }
    }
}
