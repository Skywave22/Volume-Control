package com.skywave.gesturevolume

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Single place where every volume change happens, so gestures and the widget
 * always behave identically.
 */
object VolumeController {

    private const val STREAM = AudioManager.STREAM_MUSIC

    private fun am(context: Context): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun maxVolume(context: Context): Int = am(context).getStreamMaxVolume(STREAM)

    fun currentVolume(context: Context): Int = am(context).getStreamVolume(STREAM)

    fun isMuted(context: Context): Boolean {
        val a = am(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            a.isStreamMute(STREAM)
        } else {
            a.getStreamVolume(STREAM) == 0
        }
    }

    /** Raise/lower by [steps] volume notches. Positive raises, negative lowers. */
    fun adjust(context: Context, steps: Int, showUi: Boolean) {
        if (steps == 0) return
        val a = am(context)
        val direction = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND else 0
        repeat(kotlin.math.abs(steps)) {
            a.adjustStreamVolume(STREAM, direction, flags)
        }
    }

    /** Jump straight to an absolute level (used by the widget slider). */
    fun setLevel(context: Context, level: Int, showUi: Boolean) {
        val a = am(context)
        val clamped = level.coerceIn(0, maxVolume(context))
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && a.isStreamMute(STREAM) && clamped > 0) {
            a.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0)
        }
        a.setStreamVolume(STREAM, clamped, flags)
    }

    /** Mute <-> unmute. Restores the previous level when unmuting. */
    fun toggleMute(context: Context, showUi: Boolean) {
        val a = am(context)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val muted = a.isStreamMute(STREAM)
            if (muted) {
                a.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, flags)
                if (a.getStreamVolume(STREAM) == 0) {
                    val restore = Prefs.lastVolumeBeforeMute(context)
                        .takeIf { it > 0 } ?: (maxVolume(context) / 2)
                    a.setStreamVolume(STREAM, restore, flags)
                }
            } else {
                Prefs.setLastVolumeBeforeMute(context, a.getStreamVolume(STREAM))
                a.adjustStreamVolume(STREAM, AudioManager.ADJUST_MUTE, flags)
            }
        } else {
            @Suppress("DEPRECATION")
            if (a.getStreamVolume(STREAM) == 0) {
                val restore = Prefs.lastVolumeBeforeMute(context)
                    .takeIf { it > 0 } ?: (maxVolume(context) / 2)
                a.setStreamVolume(STREAM, restore, flags)
            } else {
                Prefs.setLastVolumeBeforeMute(context, a.getStreamVolume(STREAM))
                a.setStreamVolume(STREAM, 0, flags)
            }
        }
    }

    fun buzz(context: Context) {
        if (!Prefs.hapticsEnabled(context)) return
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(18, 60))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(18)
        }
    }
}
