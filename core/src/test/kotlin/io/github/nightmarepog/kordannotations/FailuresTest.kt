package io.github.nightmarepog.kordannotations

import kotlin.test.Test
import kotlin.test.assertEquals

class FailuresTest {
    @Test
    fun `formats singular durations`() {
        assertEquals("Try again in 1 second.", CommandOnCooldownException(1).message)
        assertEquals("The command timed out after 1 second.", CommandTimedOutException(1).message)
    }

    @Test
    fun `formats plural durations`() {
        assertEquals("Try again in 2 seconds.", CommandOnCooldownException(2).message)
        assertEquals("The command timed out after 2 seconds.", CommandTimedOutException(2).message)
    }
}
