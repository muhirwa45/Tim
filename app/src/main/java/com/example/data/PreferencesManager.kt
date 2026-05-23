package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tim_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FOCUS_DURATION = "focus_duration"
        private const val KEY_SHORT_BREAK_DURATION = "short_break_duration"
        private const val KEY_LONG_BREAK_DURATION = "long_break_duration"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SELECTED_TASK_ID = "selected_task_id"
    }

    var focusDuration: Int
        get() = prefs.getInt(KEY_FOCUS_DURATION, 25)
        set(value) = prefs.edit().putInt(KEY_FOCUS_DURATION, value).apply()

    var shortBreakDuration: Int
        get() = prefs.getInt(KEY_SHORT_BREAK_DURATION, 5)
        set(value) = prefs.edit().putInt(KEY_SHORT_BREAK_DURATION, value).apply()

    var longBreakDuration: Int
        get() = prefs.getInt(KEY_LONG_BREAK_DURATION, 15)
        set(value) = prefs.edit().putInt(KEY_LONG_BREAK_DURATION, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var selectedTaskId: Int?
        get() {
            val id = prefs.getInt(KEY_SELECTED_TASK_ID, -1)
            return if (id == -1) null else id
        }
        set(value) = prefs.edit().putInt(KEY_SELECTED_TASK_ID, value ?: -1).apply()
}
