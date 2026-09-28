package com.musaraj.forumindex

import org.junit.Assert.*
import org.junit.Test

class TopicTitleVisibilityTest {
    @Test fun deliberateDirectionChangesRevealAndHideTitle() {
        val title = TopicTitleVisibility()
        title.scroll(100f, true)
        assertFalse(title.isVisible)
        title.scroll(-6f, true)
        assertFalse(title.isVisible)
        title.scroll(-6f, true)
        assertTrue(title.isVisible)
        title.scroll(0f, true) // Overscroll consumes no distance.
        assertTrue(title.isVisible)
        title.scroll(11f, true)
        assertTrue(title.isVisible)
        title.scroll(1f, true)
        assertFalse(title.isVisible)
    }

    @Test fun topAndNewPagesResetVisibilityAndTravel() {
        val title = TopicTitleVisibility()
        title.scroll(-20f, true)
        title.scroll(-20f, false)
        assertFalse(title.isVisible)
        title.scroll(-6f, true)
        assertFalse(title.isVisible)
        title.scroll(-6f, true)
        assertTrue(title.isVisible)
        title.reset()
        assertFalse(title.isVisible)
    }
}
