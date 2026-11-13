package com.example.alarmmgr.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.alarmmgr.data.AlarmStore

/** Re-arm all alarms after a reboot. AlarmManager does not survive one. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        AlarmStore.init(context)
        AlarmScheduler.rescheduleAll(context, AlarmStore.current())
    }
}
