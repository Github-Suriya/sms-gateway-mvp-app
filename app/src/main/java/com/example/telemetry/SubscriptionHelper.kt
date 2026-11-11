package com.example.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSubscriptionInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
) {
    val userLabel: String
        get() {
            val carrier = if (carrierName.isNotBlank() && carrierName != displayName) " ($carrierName)" else ""
            return "SIM ${slotIndex + 1}: $displayName$carrier"
        }
}

class SubscriptionHelper(private val context: Context) {

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getActiveSubscriptions(): List<SimSubscriptionInfo> {
        if (!hasPhoneStatePermission()) {
            return emptyList()
        }

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return emptyList()

            val infoList: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            infoList?.map { info ->
                SimSubscriptionInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: ""
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isSubscriptionActive(subId: Int): Boolean {
        if (subId == -1) return false
        val active = getActiveSubscriptions()
        return if (active.isNotEmpty()) {
            active.any { it.subscriptionId == subId }
        } else {
            // When permission is absent or info is unavailable, check default SMS sub
            val defaultSub = getDefaultSmsSubscriptionId()
            defaultSub == subId || subId >= 0
        }
    }

    fun getDefaultSmsSubscriptionId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefaultSmsSubscriptionId()
        }
    }

    fun getSmsManagerForSubscription(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val baseManager = context.getSystemService(SmsManager::class.java)
            if (subId >= 0) {
                baseManager.createForSubscriptionId(subId)
            } else {
                baseManager
            }
        } else {
            if (subId >= 0) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }
}
