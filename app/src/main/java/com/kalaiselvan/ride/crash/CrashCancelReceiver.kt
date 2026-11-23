package com.kalaiselvan.ride.crash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CrashCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CrashAlertManager.cancelCrash(context)
    }
}
