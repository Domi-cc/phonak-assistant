package io.github.domicc.lara.core.version

/**
 * The line the app shows about itself, at the top of its screen: which build is on the phone.
 * The owner installs builds by hand and tests them without a computer next to him, so the
 * number he reads there is the only way to tell which build a test was made with.
 *
 * The build number is the versionCode, the number of commits. A build made from files that
 * were changed after the last commit carries the number of that commit although it is not that
 * commit; it gets a "+" after the number. A build that differs from the usual one in another
 * way (another target SDK) says so in a note, because it can share number and commit with the
 * usual build.
 */
object BuildLabel {

    /**
     * @param code the versionCode
     * @param name the versionName
     * @param commit the short hash of the commit the build was made from; empty if unknown
     * @param changed files differed from that commit when the build was made
     * @param note what else sets this build apart from the usual one of that commit; empty if nothing
     * @return for example "Build 18 · 0.1.0-dev · 9ff1cad", or "Build 18+ · 0.1.0-dev · 9ff1cad, geändert"
     */
    fun line(code: Int, name: String, commit: String, changed: Boolean, note: String = ""): String = buildString {
        append("Build ").append(code)
        if (changed) append('+')
        append(" · ").append(name)
        // a branch build from GitHub has the commit in its version name already
        if (commit.isNotEmpty() && !name.contains(commit)) append(" · ").append(commit)
        if (note.isNotEmpty()) append(" · ").append(note)
        if (changed) append(", geändert")
    }
}
