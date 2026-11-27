package io.github.domicc.lara

import io.github.domicc.lara.core.version.BuildLabel

/**
 * Which build this is, in the two forms the app uses: the line at the top of the screen and
 * the fields of the journal lines that say which build wrote them. The values are fixed when
 * the app is built (app/build.gradle.kts).
 */
object BuildInfo {

    /** For example "Build 18 · 0.1.0-dev · 9ff1cad"; see [BuildLabel]. */
    val label: String = BuildLabel.line(
        BuildConfig.VERSION_CODE,
        BuildConfig.VERSION_NAME,
        BuildConfig.GIT_COMMIT,
        BuildConfig.GIT_CHANGED,
        BuildConfig.BUILD_NOTE,
    )

    /**
     * `versionCode` is the build number the screen shows. `uncommitted` means the build was
     * made from files that differed from `commit`.
     */
    val fields: Map<String, Any?> = buildMap {
        put("version", BuildConfig.VERSION_NAME)
        put("versionCode", BuildConfig.VERSION_CODE)
        put("commit", BuildConfig.GIT_COMMIT)
        put("uncommitted", BuildConfig.GIT_CHANGED)
        if (BuildConfig.BUILD_NOTE.isNotEmpty()) put("buildNote", BuildConfig.BUILD_NOTE)
    }
}
