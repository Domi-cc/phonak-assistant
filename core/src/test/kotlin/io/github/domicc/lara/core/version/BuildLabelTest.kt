package io.github.domicc.lara.core.version

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildLabelTest {

    @Test
    fun `a build from a commit shows number, version and commit`() {
        assertEquals("Build 18 · 0.1.0-dev · 9a1591f", BuildLabel.line(18, "0.1.0-dev", "9a1591f", changed = false))
    }

    @Test
    fun `a build from changed files is marked, because it shares its number with the commit`() {
        assertEquals(
            "Build 18+ · 0.1.0-dev · 9a1591f, geändert",
            BuildLabel.line(18, "0.1.0-dev", "9a1591f", changed = true),
        )
    }

    @Test
    fun `the commit is not said twice when the version name contains it`() {
        assertEquals(
            "Build 18 · 0.0.0-background-listener.9a1591f",
            BuildLabel.line(18, "0.0.0-background-listener.9a1591f", "9a1591f", changed = false),
        )
    }

    @Test
    fun `without git the line has no commit`() {
        assertEquals("Build 1 · 0.1.0-dev", BuildLabel.line(1, "0.1.0-dev", "", changed = false))
        assertEquals("Build 1+ · 0.1.0-dev, geändert", BuildLabel.line(1, "0.1.0-dev", "", changed = true))
    }
}
