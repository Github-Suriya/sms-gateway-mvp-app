package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SentCallbackReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val leaseId = intent.getStringExtra(EXTRA_LEASE_ID) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val partsTotal = intent.getIntExtra(EXTRA_PARTS_TOTAL, -1)
        val resultCode = resultCode

        val pendingResult = goAsync()
        scope.launch {
            try {
                SmsDispatchManager.getInstance(context).onSentCallback(
                    jobId = jobId,
                    leaseId = leaseId,
                    partIndex = partIndex,
                    partsTotal = partsTotal,
                    rawResultCode = resultCode
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.example.relaygateway.SMS_SENT_ACTION"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_LEASE_ID = "extra_lease_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_PARTS_TOTAL = "extra_parts_total"
    }
}

class DeliveryCallbackReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val leaseId = intent.getStringExtra(EXTRA_LEASE_ID) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val partsTotal = intent.getIntExtra(EXTRA_PARTS_TOTAL, -1)
        val resultCode = resultCode

        val pendingResult = goAsync()
        scope.launch {
            try {
                SmsDispatchManager.getInstance(context).onDeliveryCallback(
                    jobId = jobId,
                    leaseId = leaseId,
                    partIndex = partIndex,
                    partsTotal = partsTotal,
                    rawResultCode = resultCode
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.example.relaygateway.SMS_DELIVERY_ACTION"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_LEASE_ID = "extra_lease_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_PARTS_TOTAL = "extra_parts_total"
    }
}
