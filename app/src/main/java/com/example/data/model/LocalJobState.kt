package com.example.data.model

enum class LocalJobState {
    /** Job leased from server and durably persisted in local Room database before start */
    RECEIVED,

    /** Start request sent to server with stable UUID */
    START_REQUESTED,

    /** Server acknowledged start with 200 { status: sending, accepted: true } */
    START_CONFIRMED,

    /** Intent to execute SMS recorded before SmsManager invocation */
    SEND_INTENT_PERSISTED,

    /** SmsManager called once for all parts */
    SEND_INVOKED,

    /** Waiting for sent PendingIntent callbacks across all parts */
    CALLBACK_PROCESSING,

    /** All parts returned RESULT_OK sent callbacks; authoritative sms_sent reported */
    SENT_TO_CARRIER,

    /** At least one part returned a failed sent callback; authoritative sms_send_failed reported */
    SEND_FAILED,

    /** All parts returned successful delivery callback */
    DELIVERED,

    /** Delivery callback reported failure */
    DELIVERY_FAILED,

    /** Ambiguous execution state (e.g. process crash, restart, lease stale). MUST NEVER re-execute. Requires manual review. */
    NEEDS_REVIEW,

    /** Job was cancelled or rejected by server due to stale lease or invalid transition */
    CANCELLED_STALE
}

enum class EventUploadState {
    PENDING,
    UPLOADING,
    ACKNOWLEDGED,
    REJECTED_CONFLICT
}
