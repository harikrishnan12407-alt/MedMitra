package com.medmitra.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationTest {
    @Test
    fun normalLaunchStartsInSplashState() {
        assertEquals(StartupDestination.SPLASH, StartupDestination.initial(false, false, false))
    }

    @Test
    fun notificationDestinationsBypassStartupSplash() {
        assertEquals("feature/reminder", StartupDestination.initial(true, true, true))
        assertEquals("feature/sos", StartupDestination.initial(false, true, true))
        assertEquals("feature/fall", StartupDestination.initial(false, false, true))
    }

    @Test
    fun splashRoutesToOnboardingOrHome() {
        assertEquals("profile/setup", StartupDestination.afterSplash(true))
        assertEquals("home", StartupDestination.afterSplash(false))
    }

    @Test
    fun firstLaunchPermissionChecklistPrecedesProfileOnboarding() {
        assertEquals("permissions", StartupDestination.afterSplash(showPermissionSetup = true, showOnboarding = true))
        assertEquals("profile/setup", StartupDestination.afterSplash(showPermissionSetup = false, showOnboarding = true))
        assertEquals("home", StartupDestination.afterSplash(showPermissionSetup = false, showOnboarding = false))
    }
}
