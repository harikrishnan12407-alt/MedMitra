package com.medmitra.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPromptPolicyTest {
    @Test fun asksOnceOnSupportedAndroidForFirstTimeUnaskedPermission() {
        assertTrue(NotificationPermissionPromptPolicy.shouldRequest(33, false, false, false))
        assertTrue(NotificationPermissionPromptPolicy.shouldRequest(35, false, false, false))
    }

    @Test fun skipsIfPermissionWasAlreadyHandledOrIsAlreadyGranted() {
        assertFalse(NotificationPermissionPromptPolicy.shouldRequest(35, true, false, false))
        assertFalse(NotificationPermissionPromptPolicy.shouldRequest(35, false, true, false))
        assertFalse(NotificationPermissionPromptPolicy.shouldRequest(35, false, false, true))
    }

    @Test fun olderAndroidVersionsDoNotReceiveNotificationRuntimePrompt() {
        assertFalse(NotificationPermissionPromptPolicy.shouldRequest(32, false, false, false))
    }
}
