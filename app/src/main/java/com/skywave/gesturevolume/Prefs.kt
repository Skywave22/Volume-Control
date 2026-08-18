package com.skywave.gesturevolume

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "gesture_volume_prefs"

    private const val KEY_ENABLED = "gestures_enabled"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SHOW_UI = "show_system_ui"
    private const val KEY_LAST_VOL = "last_volume_before_mute"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var gesturesEnabledCache = true

    fun gesturesEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_ENABLED, true).also { gesturesEnabledCache = it }

    fun setGesturesEnabled(context: Context, value: Boolean) {
        gesturesEnabledCache = value
        sp(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** 1 = least sensitive (long swipe per step), 5 = most sensitive. Default 3. */
    fun sensitivity(context: Context): Int = sp(context).getInt(KEY_SENSITIVITY, 3).coerceIn(1, 5)

    fun setSensitivity(context: Context, value: Int) =
        sp(context).edit().putInt(KEY_SENSITIVITY, value.coerceIn(1, 5)).apply()

    fun hapticsEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_HAPTICS, true)

    fun setHaptics(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_HAPTICS, value).apply()

    fun showSystemUi(context: Context): Boolean = sp(context).getBoolean(KEY_SHOW_UI, true)

    fun setShowSystemUi(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_SHOW_UI, value).apply()

    fun lastVolumeBeforeMute(context: Context): Int = sp(context).getInt(KEY_LAST_VOL, 0)

    fun setLastVolumeBeforeMute(context: Context, value: Int) =
        sp(context).edit().putInt(KEY_LAST_VOL, value).apply()
}
