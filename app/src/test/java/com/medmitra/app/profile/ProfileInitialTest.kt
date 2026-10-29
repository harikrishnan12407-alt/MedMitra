package com.medmitra.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileInitialTest {
    @Test fun firstNameLetterIsTrimmedAndUppercase() {
        assertEquals("H", profileInitial("Harikrishnan"))
        assertEquals("H", profileInitial("  hari krishnan  "))
    }

    @Test fun blankNameHasNoInitialSoUiCanShowGenericIcon() {
        assertNull(profileInitial("  "))
    }
}
