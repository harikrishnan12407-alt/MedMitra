package com.medmitra.app.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAlarmPresentationPolicyTest {
    private val highImportance = 4

    @Test fun android14RequiresSpecialFullScreenAccess() {
        assertFalse(ReminderAlarmPresentationPolicy.allowFullScreen(34, false, true, highImportance, highImportance))
        assertTrue(ReminderAlarmPresentationPolicy.allowFullScreen(34, true, true, highImportance, highImportance))
    }

    @Test fun notificationPermissionAndHighImportanceChannelAreRequired() {
        assertFalse(ReminderAlarmPresentationPolicy.allowFullScreen(35, true, false, highImportance, highImportance))
        assertFalse(ReminderAlarmPresentationPolicy.allowFullScreen(35, true, true, 2, highImportance))
        assertTrue(ReminderAlarmPresentationPolicy.allowFullScreen(33, false, true, highImportance, highImportance))
    }
}
