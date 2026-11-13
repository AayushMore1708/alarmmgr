package com.example.alarmmgr.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.example.alarmmgr.data.Alarm
import com.example.alarmmgr.data.AlarmStore

/** Fired by AlarmManager. Re-queues the schedule, then rings. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        AlarmStore.init(context)
        val alarm = AlarmStore.get(id) ?: return
        AlarmScheduler.rescheduleAll(context, AlarmStore.current())
        ring(context, alarm)
    }

    companion object {
        const val CHANNEL_ID = "alarm_channel"

        fun ring(context: Context, alarm: Alarm) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channelId = CHANNEL_ID + "_" + alarm.id
            val effective = AlarmStore.effectiveSoundUri(alarm)
            val sound: Uri = if (effective.startsWith("/")) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                effective.ifBlank { null }?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 500, 500)
                    setSound(
                        sound,
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                }
            )
            val open = PendingIntent.getActivity(
                context, alarm.id.hashCode(),
                Intent(context, RingActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, channelId)
                .setContentTitle("Alarm")
                .setContentText(alarm.label)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build()
            manager.notify(("alarm-" + alarm.id).hashCode(), notification)
        }

        fun silence(context: Context, alarmId: Long) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(("alarm-" + alarmId).hashCode())
        }
    }
}
