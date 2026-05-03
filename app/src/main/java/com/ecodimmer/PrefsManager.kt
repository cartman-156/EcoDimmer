package com.ecodimmer

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ecodimmer_prefs", Context.MODE_PRIVATE)

    var isDimmingActive: Boolean
        get() = prefs.getBoolean("is_dimming_active", false)
        set(value) = prefs.edit().putBoolean("is_dimming_active", value).apply()

    var dimmingLevel: Float
        get() = prefs.getFloat("dimming_level", 0.5f)
        set(value) = prefs.edit().putFloat("dimming_level", value).apply()

    var isScheduleEnabled: Boolean
        get() = prefs.getBoolean("is_schedule_enabled", false)
        set(value) = prefs.edit().putBoolean("is_schedule_enabled", value).apply()

    var scheduleStartHour: Int
        get() = prefs.getInt("schedule_start_hour", 22) // Default 10 PM
        set(value) = prefs.edit().putInt("schedule_start_hour", value).apply()

    var scheduleStartMinute: Int
        get() = prefs.getInt("schedule_start_minute", 0)
        set(value) = prefs.edit().putInt("schedule_start_minute", value).apply()

    var scheduleEndHour: Int
        get() = prefs.getInt("schedule_end_hour", 7) // Default 7 AM
        set(value) = prefs.edit().putInt("schedule_end_hour", value).apply()

    var scheduleEndMinute: Int
        get() = prefs.getInt("schedule_end_minute", 0)
        set(value) = prefs.edit().putInt("schedule_end_minute", value).apply()
}
