package com.callscribe.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            isIncoming = false
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (!number.isNullOrEmpty()) {
                savedNumber = number
            }

            var state = TelephonyManager.CALL_STATE_IDLE
            when (stateStr) {
                TelephonyManager.EXTRA_STATE_IDLE -> state = TelephonyManager.CALL_STATE_IDLE
                TelephonyManager.EXTRA_STATE_OFFHOOK -> state = TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_RINGING -> state = TelephonyManager.CALL_STATE_RINGING
            }

            onCustomCallStateChanged(context, state, savedNumber ?: "Unknown")
        }
    }

    private fun onCustomCallStateChanged(context: Context, state: Int, number: String) {
        if (lastState == state) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                savedNumber = number
                Log.d(TAG, "Incoming call ringing from: $number")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered or outgoing call connected
                Log.d(TAG, "Call started with $number (incoming: $isIncoming). Starting recorder service...")
                startRecordingService(context, number, isIncoming)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended
                Log.d(TAG, "Call ended. Stopping recorder service...")
                stopRecordingService(context)
            }
        }
        lastState = state
    }

    private fun startRecordingService(context: Context, number: String, incoming: Boolean) {
        val serviceIntent = Intent(context, CallRecordService::class.java).apply {
            action = CallRecordService.ACTION_START_RECORDING
            putExtra(CallRecordService.EXTRA_PHONE_NUMBER, number)
            putExtra(CallRecordService.EXTRA_IS_INCOMING, incoming)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopRecordingService(context: Context) {
        val serviceIntent = Intent(context, CallRecordService::class.java).apply {
            action = CallRecordService.ACTION_STOP_RECORDING
        }
        context.startService(serviceIntent)
    }
}
