package com.medmitra.app.domain

object NotificationPermissionPromptPolicy {
    fun shouldRequest(
        sdkInt: Int,
        alreadyAsked: Boolean,
        alreadyGranted: Boolean,
        previouslyReviewedInLegacySetup: Boolean
    ): Boolean = sdkInt >= 33 &&
        !alreadyAsked &&
        !alreadyGranted &&
        !previouslyReviewedInLegacySetup
}
