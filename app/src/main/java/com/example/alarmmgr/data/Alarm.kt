package com.example.alarmmgr.data

import org.json.JSONObject
import java.util.Calendar

/** An alarm. [days] holds Calendar weekday constants (SUNDAY=1..SATURDAY=7). Empty [days] = fires once. */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: Set<Int>,
    val enabled: Boolean,
    val soundUri: String = "",
)

val WEEKDAY_CHIPS = listOf(
    Calendar.SUNDAY to "S",
    Calendar.MONDAY to "M",
    Calendar.TUESDAY to "T",
    Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "T",
    Calendar.FRIDAY to "F",
    Calendar.SATURDAY to "S",
)

private val WEEKDAY_SHORT = mapOf(
    Calendar.SUNDAY to "Sun",
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
)

fun Alarm.clockParts(): Triple<String, String, String> {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return Triple(
        h12.toString().padStart(2, '0'),
        minute.toString().padStart(2, '0'),
        if (hour < 12) "AM" else "PM",
    )
}

fun Alarm.repeatLabel(): String {
    if (days.isEmpty()) return "Once"
    if (days.size == 7) return "Every day"
    if (days == setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)) return "Weekdays"
    if (days == setOf(Calendar.SUNDAY, Calendar.SATURDAY)) return "Weekends"
    return days.sorted().joinToString(", ") { WEEKDAY_SHORT.getValue(it) }
}

private fun atMillis(dayOffset: Int, hour: Int, minute: Int, now: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun Alarm.nextFireMillis(now: Long = System.currentTimeMillis()): Long? {
    if (!enabled) return null
    if (days.isEmpty()) {
        val today = atMillis(0, hour, minute, now)
        return if (today > now) today else atMillis(1, hour, minute, now)
    }
    var best: Long? = null
    val todayDow = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
    for (day in days) {
        var offset = (day - todayDow + 7) % 7
        if (offset == 0 && atMillis(0, hour, minute, now) <= now) offset = 7
        val candidate = atMillis(offset, hour, minute, now)
        if (best == null || candidate < best) best = candidate
    }
    return best
}

fun nextUpcoming(alarms: List<Alarm>, now: Long = System.currentTimeMillis()): Pair<Alarm, Long>? {
    var best: Pair<Alarm, Long>? = null
    for (alarm in alarms) {
        val at = alarm.nextFireMillis(now) ?: continue
        if (best == null || at < best.second) best = alarm to at
    }
    return best
}

fun describeCountdown(fireAt: Long, now: Long = System.currentTimeMillis()): String {
    val totalMinutes = ((fireAt - now) / 60_000).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    if (hours == 0) return "in ${minutes}m"
    if (minutes == 0) return "in ${hours}h"
    return "in ${hours}h ${minutes}m"
}

fun Alarm.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("hour", hour)
    .put("minute", minute)
    .put("label", label)
    .put("days", days.sorted().joinToString(","))
    .put("enabled", enabled)
    .put("soundUri", soundUri)

fun JSONObject.toAlarm(): Alarm = Alarm(
    id = getLong("id"),
    hour = getInt("hour"),
    minute = getInt("minute"),
    label = optString("label", "Alarm"),
    days = optString("days", "")
        .split(",")
        .mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 1..7 } }
        .toSet(),
    enabled = optBoolean("enabled", true),
    soundUri = optString("soundUri", ""),
)
