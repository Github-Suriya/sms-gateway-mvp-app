package com.example.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

data class DeviceTelemetry(
    val batteryPct: Int = 100,
    val isCharging: Boolean = false,
    val networkType: String = "wifi", // wifi, mobile, none, other
    val appVersion: String = "1.0.0",
    val androidVersion: String = "15"
)

class DeviceTelemetryHelper(private val context: Context) {

    fun getTelemetry(): DeviceTelemetry {
        return try {
            val batteryStatus = getBatteryStatus()
            val networkType = getNetworkType()
            val appVersion = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
            val androidVersion = Build.VERSION.RELEASE ?: "15"

            DeviceTelemetry(
                batteryPct = batteryStatus.first,
                isCharging = batteryStatus.second,
                networkType = networkType,
                appVersion = appVersion,
                androidVersion = androidVersion
            )
        } catch (e: Exception) {
            DeviceTelemetry()
        }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, filter)
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val pct = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                100
            }
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            Pair(pct, isCharging)
        } catch (e: Exception) {
            Pair(100, false)
        }
    }

    private fun getNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "other"
            val activeNetwork = cm.activeNetwork ?: return "other"
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "other"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "other"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "other"
                else -> "other"
            }
        } catch (e: Exception) {
            "other"
        }
    }
}
