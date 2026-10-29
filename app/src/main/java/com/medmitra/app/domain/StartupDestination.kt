package com.medmitra.app.domain

/** Routes opened directly from notifications, or the first destination after startup. */
object StartupDestination {
    const val SPLASH = "splash"

    fun initial(
        openReminders: Boolean,
        openSos: Boolean,
        openFall: Boolean
    ): String = when {
        openReminders -> "feature/reminder"
        openSos -> "feature/sos"
        openFall -> "feature/fall"
        else -> SPLASH
    }

    fun afterSplash(showOnboarding: Boolean): String =
        if (showOnboarding) "profile/setup" else "home"
}
