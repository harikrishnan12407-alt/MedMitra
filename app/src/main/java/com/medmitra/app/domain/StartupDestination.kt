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

    fun afterSplash(showOnboarding: Boolean): String = afterSplash(
        showPermissionSetup = false,
        showOnboarding = showOnboarding
    )

    fun afterSplash(showPermissionSetup: Boolean, showOnboarding: Boolean): String = when {
        showPermissionSetup -> "permissions"
        showOnboarding -> "profile/setup"
        else -> "home"
    }
}
