package com.example

import android.app.Activity
import android.telephony.SmsManager
import com.example.data.model.AllowedCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedCodesTest {

    @Test
    fun testResultCodeAllowlistMapping() {
        assertEquals(AllowedCodes.RESULT_OK, AllowedCodes.mapAndroidSentResultCode(Activity.RESULT_OK))
        assertEquals(
            AllowedCodes.RESULT_ERROR_GENERIC_FAILURE,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_GENERIC_FAILURE)
        )
        assertEquals(
            AllowedCodes.RESULT_ERROR_RADIO_OFF,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_RADIO_OFF)
        )
        assertEquals(
            AllowedCodes.RESULT_ERROR_NULL_PDU,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_NULL_PDU)
        )
        assertEquals(
            AllowedCodes.RESULT_ERROR_NO_SERVICE,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_NO_SERVICE)
        )
        assertEquals(
            AllowedCodes.RESULT_ERROR_LIMIT_EXCEEDED,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_LIMIT_EXCEEDED)
        )
        assertEquals(
            AllowedCodes.RESULT_ERROR_FDN_CHECK_FAILURE,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE)
        )
        assertEquals(
            AllowedCodes.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            AllowedCodes.mapAndroidSentResultCode(SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED)
        )
        assertEquals(
            AllowedCodes.RESULT_CANCELED,
            AllowedCodes.mapAndroidSentResultCode(Activity.RESULT_CANCELED)
        )

        // Any unlisted / proprietary failure code must map to RESULT_ERROR_UNKNOWN
        assertEquals(
            AllowedCodes.RESULT_ERROR_UNKNOWN,
            AllowedCodes.mapAndroidSentResultCode(99999)
        )
    }

    @Test
    fun testAllowlistIntegrity() {
        assertTrue(AllowedCodes.ALLOWED_RESULT_CODES.contains(AllowedCodes.RESULT_OK))
        assertTrue(AllowedCodes.ALLOWED_RESULT_CODES.contains(AllowedCodes.RESULT_ERROR_UNKNOWN))
        assertTrue(AllowedCodes.ALLOWED_FAILURE_CODES.contains(AllowedCodes.FAILURE_NO_SERVICE))
        assertTrue(AllowedCodes.ALLOWED_FAILURE_CODES.contains(AllowedCodes.FAILURE_RADIO_OFF))
        assertTrue(AllowedCodes.ALLOWED_FAILURE_CODES.contains(AllowedCodes.FAILURE_GENERIC_FAILURE))

        assertEquals(
            AllowedCodes.FAILURE_NO_SERVICE,
            AllowedCodes.inferFailureCode(AllowedCodes.RESULT_ERROR_NO_SERVICE)
        )
        assertEquals(
            AllowedCodes.FAILURE_RADIO_OFF,
            AllowedCodes.inferFailureCode(AllowedCodes.RESULT_ERROR_RADIO_OFF)
        )
        assertNull(AllowedCodes.inferFailureCode(AllowedCodes.RESULT_OK))
    }
}
