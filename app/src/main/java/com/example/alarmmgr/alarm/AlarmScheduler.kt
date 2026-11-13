package com.example.alarmmgr.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.alarmmgr.data.Alarm
import java.util.Calendar

/** Hands alarms to the OS. Fires with the app closed; re-armed on reboot by BootReceiver. */
object AlarmScheduler {
    const val ACTION_FIRE = "com.example.alarmmgr.action.FIRE"
    const val EXTRA_ALARM_ID = "alarm_id"

    private const val PREFS = "alarm_sched"
    private const val KEY_CODES = "codes"
    private const val SLOT_ONCE = 7
    private const val SLOT_SNOOZE = 8

    fun rescheduleAll(context: Context, alarms: List<Alarm>) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelTracked(context, manager)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val codes = mutableSetOf<Int>()
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            if (!alarm.enabled) continue
            val slots: List<Pair<Int, Long>> = if (alarm.days.isEmpty()) {
                val at = nextOnceMillis(alarm, now) ?: continue
                listOf(SLOT_ONCE to at)
            } else {
                alarm.days.sorted().mapIndexedNotNull { index, day ->
                    nextWeeklyMillis(alarm, day, now)?.let { index to it }
                }
            }
            for ((slot, at) in slots) {
                val code = requestCode(alarm.id, slot)
                codes += code
                if (exact) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(context, code, alarm.id))
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(context, code, alarm.id))
                }
            }
        }
        saveCodes(context, codes)
    }

    /** Re-arm [alarmId] to fire once, 9 minutes from now. */
    fun snooze(context: Context, alarmId: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val code = requestCode(alarmId, SLOT_SNOOZE)
        val at = System.currentTimeMillis() + 9 * 60_000L
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(context, code, alarmId))
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(context, code, alarmId))
        }
        saveCodes(context, loadCodes(context) + code)
    }

    private fun requestCode(id: Long, slot: Int): Int {
        val code = ((id % 100_000).toInt() * 10) + slot
        return if (code < 0) -code else code
    }

    private fun fireIntent(context: Context, code: Int, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextOnceMillis(alarm: Alarm, now: Long): Long? {
        if (!alarm.enabled) return null
        val today = atMillis(0, alarm, now)
        return if (today > now) today else atMillis(1, alarm, now)
    }

    private fun nextWeeklyMillis(alarm: Alarm, day: Int, now: Long): Long? {
        if (!alarm.enabled) return null
        val todayDow = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        var offset = (day - todayDow + 7) % 7
        if (offset == 0 && atMillis(0, alarm, now) <= now) offset = 7
        return atMillis(offset, alarm, now)
    }

    private fun atMillis(dayOffset: Int, alarm: Alarm, now: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun loadCodes(context: Context): Set<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CODES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    private fun saveCodes(context: Context, codes: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    private fun cancelTracked(context: Context, manager: AlarmManager) {
        for (code in loadCodes(context)) {
            val blank = PendingIntent.getBroadcast(
                context, code,
                Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            manager.cancel(blank)
            blank.cancel()
        }
        saveCodes(context, emptySet())
    }
}
