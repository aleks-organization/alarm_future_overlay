package com.example.alarm_future_overlay

import android.content.Context

object AlarmStore {

    private const val PREFS_NAME = "alarm_future_overlay_alarms"
    private const val KEY_IDS = "alarm_ids"

    data class StoredAlarm(
        val id: Int,
        val timeMillis: Long,
        val label: String,
        val sound: String?
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, id: Int, timeMillis: Long, label: String, sound: String?) {
        val p = prefs(context)
        val ids = p.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(id.toString())
        p.edit()
            .putStringSet(KEY_IDS, ids)
            .putLong("time_$id", timeMillis)
            .putString("label_$id", label)
            .putString("sound_$id", sound)
            .apply()
    }

    fun remove(context: Context, id: Int) {
        val p = prefs(context)
        val ids = p.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(id.toString())
        p.edit()
            .putStringSet(KEY_IDS, ids)
            .remove("time_$id")
            .remove("label_$id")
            .remove("sound_$id")
            .apply()
    }

    fun get(context: Context, id: Int): StoredAlarm? {
        val p = prefs(context)
        if (!p.contains("time_$id")) return null
        return StoredAlarm(
            id = id,
            timeMillis = p.getLong("time_$id", 0L),
            label = p.getString("label_$id", "Alarm") ?: "Alarm",
            sound = p.getString("sound_$id", null)
        )
    }

    fun getAll(context: Context): List<StoredAlarm> {
        val p = prefs(context)
        val ids = p.getStringSet(KEY_IDS, emptySet()) ?: return emptyList()
        return ids.mapNotNull { idStr ->
            val id = idStr.toIntOrNull() ?: return@mapNotNull null
            get(context, id)
        }
    }
}
