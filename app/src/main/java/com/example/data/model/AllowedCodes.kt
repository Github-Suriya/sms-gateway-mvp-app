package com.example.data.model

object AllowedCodes {
    // Authoritative event types per Version 1.1.0 contract
    const val EVENT_SMS_SENT = "sms_sent"
    const val EVENT_SMS_SEND_FAILED = "sms_send_failed"
    const val EVENT_SMS_DELIVERED = "sms_delivered"
    const val EVENT_SMS_DELIVERY_FAILED = "sms_delivery_failed"

    val ALLOWED_EVENT_TYPES = setOf(
        EVENT_SMS_SENT,
        EVENT_SMS_SEND_FAILED,
        EVENT_SMS_DELIVERED,
        EVENT_SMS_DELIVERY_FAILED
    )

    // Success result code
    const val RESULT_OK = "RESULT_OK"

    // Contract-allowed failure result codes
    const val RESULT_ERROR_GENERIC_FAILURE = "RESULT_ERROR_GENERIC_FAILURE"
    const val RESULT_ERROR_RADIO_OFF = "RESULT_ERROR_RADIO_OFF"
    const val RESULT_ERROR_NULL_PDU = "RESULT_ERROR_NULL_PDU"
    const val RESULT_ERROR_NO_SERVICE = "RESULT_ERROR_NO_SERVICE"
    const val RESULT_ERROR_LIMIT_EXCEEDED = "RESULT_ERROR_LIMIT_EXCEEDED"
    const val RESULT_ERROR_FDN_CHECK_FAILURE = "RESULT_ERROR_FDN_CHECK_FAILURE"
    const val RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED"
    const val RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED"
    const val RESULT_ERROR_UNKNOWN = "RESULT_ERROR_UNKNOWN"
    const val RESULT_CANCELED = "RESULT_CANCELED"

    val ALLOWED_RESULT_CODES = setOf(
        RESULT_OK,
        RESULT_ERROR_GENERIC_FAILURE,
        RESULT_ERROR_RADIO_OFF,
        RESULT_ERROR_NULL_PDU,
        RESULT_ERROR_NO_SERVICE,
        RESULT_ERROR_LIMIT_EXCEEDED,
        RESULT_ERROR_FDN_CHECK_FAILURE,
        RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
        RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
        RESULT_ERROR_UNKNOWN,
        RESULT_CANCELED
    )

    // Optional failure_code allowlist
    const val FAILURE_NO_SIM = "NO_SIM"
    const val FAILURE_NO_SERVICE = "NO_SERVICE"
    const val FAILURE_PERMISSION_DENIED = "PERMISSION_DENIED"
    const val FAILURE_RADIO_OFF = "RADIO_OFF"
    const val FAILURE_GENERIC_FAILURE = "GENERIC_FAILURE"
    const val FAILURE_LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
    const val FAILURE_DELIVERY_FAILED = "DELIVERY_FAILED"

    val ALLOWED_FAILURE_CODES = setOf(
        FAILURE_NO_SIM,
        FAILURE_NO_SERVICE,
        FAILURE_PERMISSION_DENIED,
        FAILURE_RADIO_OFF,
        FAILURE_GENERIC_FAILURE,
        FAILURE_LIMIT_EXCEEDED,
        FAILURE_DELIVERY_FAILED
    )

    /**
     * Map arbitrary Android resultCode int to contract string
     */
    fun mapAndroidSentResultCode(resultCode: Int): String {
        return when (resultCode) {
            android.app.Activity.RESULT_OK -> RESULT_OK
            android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> RESULT_ERROR_GENERIC_FAILURE
            android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> RESULT_ERROR_RADIO_OFF
            android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> RESULT_ERROR_NULL_PDU
            android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> RESULT_ERROR_NO_SERVICE
            android.telephony.SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> RESULT_ERROR_LIMIT_EXCEEDED
            android.telephony.SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> RESULT_ERROR_FDN_CHECK_FAILURE
            android.telephony.SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> RESULT_ERROR_SHORT_CODE_NOT_ALLOWED
            android.telephony.SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED
            android.app.Activity.RESULT_CANCELED -> RESULT_CANCELED
            else -> RESULT_ERROR_UNKNOWN
        }
    }

    /**
     * Infer failure_code from resultCode or context
     */
    fun inferFailureCode(resultCodeStr: String): String? {
        return when (resultCodeStr) {
            RESULT_ERROR_NO_SERVICE -> FAILURE_NO_SERVICE
            RESULT_ERROR_RADIO_OFF -> FAILURE_RADIO_OFF
            RESULT_ERROR_LIMIT_EXCEEDED -> FAILURE_LIMIT_EXCEEDED
            RESULT_ERROR_GENERIC_FAILURE -> FAILURE_GENERIC_FAILURE
            else -> null
        }
    }
}
