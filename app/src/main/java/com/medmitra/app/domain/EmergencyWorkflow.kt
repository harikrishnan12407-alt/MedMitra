package com.medmitra.app.domain

enum class EmergencyStage { IDLE, COUNTDOWN, LOCATION_LOOKUP, SMS_REVIEW, CALL_REVIEW, COMPLETE, CANCELLED }
enum class HandoffStatus { NOT_ATTEMPTED, OUTCOME_UNKNOWN, USER_ACTION_REQUIRED, UNAVAILABLE }

data class EmergencyWorkflowSnapshot(
    val stage: EmergencyStage = EmergencyStage.IDLE,
    val countdownRemainingSeconds: Int = 0,
    val locationStatus: String = "NOT_REQUESTED",
    val smsStatus: HandoffStatus = HandoffStatus.NOT_ATTEMPTED,
    val callStatus: HandoffStatus = HandoffStatus.NOT_ATTEMPTED
)

/** Pure state model. Handoff states intentionally cannot imply SMS delivery or a connected call. */
class EmergencyWorkflow(private val countdownSeconds: Int = 5) {
    init { require(countdownSeconds > 0) }

    var snapshot: EmergencyWorkflowSnapshot = EmergencyWorkflowSnapshot()
        private set

    private var startedAtMillis: Long = 0

    fun start(nowMillis: Long): EmergencyWorkflowSnapshot {
        startedAtMillis = nowMillis
        snapshot = EmergencyWorkflowSnapshot(stage = EmergencyStage.COUNTDOWN, countdownRemainingSeconds = countdownSeconds)
        return snapshot
    }

    fun remainingSeconds(nowMillis: Long): Int {
        if (snapshot.stage != EmergencyStage.COUNTDOWN) return snapshot.countdownRemainingSeconds
        val elapsed = ((nowMillis - startedAtMillis).coerceAtLeast(0) / 1_000L).toInt()
        return (countdownSeconds - elapsed).coerceAtLeast(0)
    }

    fun tick(nowMillis: Long): EmergencyWorkflowSnapshot {
        if (snapshot.stage != EmergencyStage.COUNTDOWN) return snapshot
        val remaining = remainingSeconds(nowMillis)
        snapshot = snapshot.copy(
            stage = if (remaining == 0) EmergencyStage.LOCATION_LOOKUP else EmergencyStage.COUNTDOWN,
            countdownRemainingSeconds = remaining
        )
        return snapshot
    }

    fun cancel(): Boolean {
        if (snapshot.stage != EmergencyStage.COUNTDOWN) return false
        snapshot = snapshot.copy(stage = EmergencyStage.CANCELLED, countdownRemainingSeconds = 0)
        return true
    }

    fun locationResolved(status: String): EmergencyWorkflowSnapshot {
        check(snapshot.stage == EmergencyStage.LOCATION_LOOKUP)
        snapshot = snapshot.copy(stage = EmergencyStage.SMS_REVIEW, locationStatus = status)
        return snapshot
    }

    fun smsComposerOpened(available: Boolean): EmergencyWorkflowSnapshot {
        check(snapshot.stage == EmergencyStage.SMS_REVIEW)
        snapshot = snapshot.copy(
            stage = EmergencyStage.CALL_REVIEW,
            smsStatus = if (available) HandoffStatus.OUTCOME_UNKNOWN else HandoffStatus.UNAVAILABLE
        )
        return snapshot
    }

    fun skipSms(): EmergencyWorkflowSnapshot {
        check(snapshot.stage == EmergencyStage.SMS_REVIEW)
        snapshot = snapshot.copy(stage = EmergencyStage.CALL_REVIEW, smsStatus = HandoffStatus.NOT_ATTEMPTED)
        return snapshot
    }

    fun dialerOpened(available: Boolean): EmergencyWorkflowSnapshot {
        check(snapshot.stage == EmergencyStage.CALL_REVIEW)
        snapshot = snapshot.copy(
            stage = EmergencyStage.COMPLETE,
            callStatus = if (available) HandoffStatus.USER_ACTION_REQUIRED else HandoffStatus.UNAVAILABLE
        )
        return snapshot
    }

    fun callRequested(available: Boolean): EmergencyWorkflowSnapshot {
        check(snapshot.stage == EmergencyStage.CALL_REVIEW)
        snapshot = snapshot.copy(
            stage = EmergencyStage.COMPLETE,
            callStatus = if (available) HandoffStatus.OUTCOME_UNKNOWN else HandoffStatus.UNAVAILABLE
        )
        return snapshot
    }

    fun skipCall(): EmergencyWorkflowSnapshot {
        check(snapshot.stage == EmergencyStage.CALL_REVIEW)
        snapshot = snapshot.copy(stage = EmergencyStage.COMPLETE)
        return snapshot
    }
}
