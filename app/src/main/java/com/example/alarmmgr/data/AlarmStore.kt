package com.example.alarmmgr.data

import android.content.Context
import com.example.alarmmgr.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Single source of truth. Mutations persist, re-sync the OS schedule, then push to observers. */
object AlarmStore {
    private const val PREFS = "alarmmgr"
    private const val KEY_ALARMS = "alarms/v1"

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _defaultSound = MutableStateFlow("" to "Default alarm sound")
    val defaultSound: StateFlow<Pair<String, String>> = _defaultSound.asStateFlow()

    @Volatile private var initialized = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        _alarms.value = load()
        _defaultSound.value = loadDefaultSound()
    }

    fun current(): List<Alarm> = _alarms.value

    fun get(id: Long): Alarm? = _alarms.value.find { it.id == id }

    fun add(hour: Int, minute: Int, label: String, days: Set<Int>, soundUri: String = "") {
        val alarm = Alarm(
            id = System.currentTimeMillis(),
            hour = hour,
            minute = minute,
            label = label.ifBlank { "Alarm" },
            days = days,
            enabled = true,
            soundUri = soundUri,
        )
        commit(listOf(alarm) + _alarms.value)
    }

    fun update(id: Long, hour: Int, minute: Int, label: String, days: Set<Int>) {
        commit(_alarms.value.map {
            if (it.id == id) it.copy(hour = hour, minute = minute, label = label.ifBlank { "Alarm" }, days = days)
            else it
        })
    }

    fun setDefaultSound(uri: String, title: String) {
        _defaultSound.value = uri to title
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("default/sound_uri", uri)
            .putString("default/sound_title", title)
            .apply()
    }

    /** Sound actually used for [alarm]: its own tone, else the global default. */
    fun defaultSoundUri(): String = _defaultSound.value.first

    fun effectiveSoundUri(alarm: Alarm): String =
        alarm.soundUri.ifBlank { _defaultSound.value.first }

    fun toggle(id: Long) {
        commit(_alarms.value.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun remove(id: Long) {
        commit(_alarms.value.filterNot { it.id == id })
    }

    private fun commit(next: List<Alarm>) {
        _alarms.value = next
        persist(next)
        AlarmScheduler.rescheduleAll(appContext, next)
    }

    private fun load(): List<Alarm> {
        val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i -> array.getJSONObject(i).toAlarm() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadDefaultSound(): Pair<String, String> {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (prefs.getString("default/sound_uri", "") ?: "") to
            (prefs.getString("default/sound_title", "Default alarm sound") ?: "Default alarm sound")
    }

    private fun persist(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, array.toString())
            .apply()
    }
}
