package io.github.domicc.lara.core.version

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildLabelTest {

    @Test
    fun `a build from a commit shows number, version and commit`() {
        assertEquals("Build 18 · 0.1.0-dev · 9ff1cad", BuildLabel.line(18, "0.1.0-dev", "9ff1cad", changed = false))
    }

    @Test
    fun `a build from changed files is marked, because it shares its number with the commit`() {
        assertEquals(
            "Build 18+ · 0.1.0-dev · 9ff1cad, geändert",
            BuildLabel.line(18, "0.1.0-dev", "9ff1cad", changed = true),
        )
    }

    @Test
    fun `the commit is not said twice when the version name contains it`() {
        assertEquals(
            "Build 18 · 0.0.0-background-listener.9ff1cad",
            BuildLabel.line(18, "0.0.0-background-listener.9ff1cad", "9ff1cad", changed = false),
        )
    }

    @Test
    fun `without git the line has no commit`() {
        assertEquals("Build 1 · 0.1.0-dev", BuildLabel.line(1, "0.1.0-dev", "", changed = false))
        assertEquals("Build 1+ · 0.1.0-dev, geändert", BuildLabel.line(1, "0.1.0-dev", "", changed = true))
    }

    @Test
    fun `a build that differs from the usual one of its commit says how`() {
        assertEquals(
            "Build 18 · 0.1.0-dev · 9ff1cad · targetSdk 37",
            BuildLabel.line(18, "0.1.0-dev", "9ff1cad", changed = false, note = "targetSdk 37"),
        )
        assertEquals(
            "Build 18+ · 0.1.0-dev · 9ff1cad · targetSdk 37, geändert",
            BuildLabel.line(18, "0.1.0-dev", "9ff1cad", changed = true, note = "targetSdk 37"),
        )
    }

    @Test
    fun `a changed build whose version name contains the commit is still marked`() {
        assertEquals(
            "Build 18+ · 0.0.0-main.9ff1cad, geändert",
            BuildLabel.line(18, "0.0.0-main.9ff1cad", "9ff1cad", changed = true),
        )
    }
}
