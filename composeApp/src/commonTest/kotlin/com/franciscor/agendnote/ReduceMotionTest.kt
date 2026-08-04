package com.franciscor.agendnote

import com.franciscor.agendnote.core.ui.motion.glassImageFadeDurationMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class ReduceMotionTest {
    @Test
    fun `reduce motion collapses the background fade to instant`() {
        assertEquals(0, glassImageFadeDurationMillis(reduceMotion = true))
    }

    @Test
    fun `normal motion keeps the 700ms background fade`() {
        assertEquals(700, glassImageFadeDurationMillis(reduceMotion = false))
    }
}
