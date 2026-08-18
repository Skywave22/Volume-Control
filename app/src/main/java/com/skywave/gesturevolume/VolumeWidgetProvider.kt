package com.skywave.gesturevolume

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * All-in-one home screen widget:
 *   [ - ]  [ mute/unmute ]  [ + ]
 *   [========== slider ==========]
 *
 * The "slider" is a row of tap targets drawn as a progress bar; tapping a segment jumps
 * straight to that level. RemoteViews cannot host a real draggable SeekBar, so this gives
 * the same one-touch "set volume to X" behaviour that works on every launcher.
 */
class VolumeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_VOL_UP = "com.skywave.gesturevolume.ACTION_VOL_UP"
        const val ACTION_VOL_DOWN = "com.skywave.gesturevolume.ACTION_VOL_DOWN"
        const val ACTION_MUTE_TOGGLE = "com.skywave.gesturevolume.ACTION_MUTE_TOGGLE"
        const val ACTION_SET_LEVEL = "com.skywave.gesturevolume.ACTION_SET_LEVEL"
        const val EXTRA_LEVEL_PERCENT = "level_percent"

        /** Number of tap segments in the slider row. */
        const val SLIDER_SEGMENTS = 10

        private val SEGMENT_IDS = intArrayOf(
            R.id.seg_0, R.id.seg_1, R.id.seg_2, R.id.seg_3, R.id.seg_4,
            R.id.seg_5, R.id.seg_6, R.id.seg_7, R.id.seg_8, R.id.seg_9
        )

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VolumeWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) updateWidget(context, mgr, id)
        }

        private fun pi(context: Context, action: String, requestCode: Int, extraPercent: Int? = null): PendingIntent {
            val intent = Intent(context, VolumeWidgetProvider::class.java).apply {
                this.action = action
                extraPercent?.let { putExtra(EXTRA_LEVEL_PERCENT, it) }
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_volume)

            val max = VolumeController.maxVolume(context).coerceAtLeast(1)
            val cur = VolumeController.currentVolume(context)
            val muted = VolumeController.isMuted(context) || cur == 0
            val percent = (cur * 100f / max).toInt()

            // --- labels ---
            views.setTextViewText(R.id.txt_percent, if (muted) "MUTED" else "$percent%")
            views.setTextViewText(R.id.txt_level, "$cur / $max")

            // --- mute button icon ---
            views.setImageViewResource(
                R.id.btn_mute,
                if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
            )

            // --- filled slider segments ---
            val filled = if (muted) 0 else Math.round(percent * SLIDER_SEGMENTS / 100f)
            for (i in SEGMENT_IDS.indices) {
                views.setImageViewResource(
                    SEGMENT_IDS[i],
                    if (i < filled) R.drawable.seg_on else R.drawable.seg_off
                )
                // tapping segment i sets volume to that fraction
                val targetPercent = ((i + 1) * 100) / SLIDER_SEGMENTS
                views.setOnClickPendingIntent(
                    SEGMENT_IDS[i],
                    pi(context, ACTION_SET_LEVEL, 1000 + i, targetPercent)
                )
            }

            // --- buttons ---
            views.setOnClickPendingIntent(R.id.btn_minus, pi(context, ACTION_VOL_DOWN, 1))
            views.setOnClickPendingIntent(R.id.btn_plus, pi(context, ACTION_VOL_UP, 2))
            views.setOnClickPendingIntent(R.id.btn_mute, pi(context, ACTION_MUTE_TOGGLE, 3))

            // tapping the readout opens the app
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            var f = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(
                R.id.txt_percent,
                PendingIntent.getActivity(context, 4, openApp, f)
            )

            mgr.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val showUi = Prefs.showSystemUi(context)
        when (intent.action) {
            ACTION_VOL_UP -> {
                VolumeController.adjust(context, 1, showUi)
                VolumeController.buzz(context)
                refreshAll(context)
            }
            ACTION_VOL_DOWN -> {
                VolumeController.adjust(context, -1, showUi)
                VolumeController.buzz(context)
                refreshAll(context)
            }
            ACTION_MUTE_TOGGLE -> {
                VolumeController.toggleMute(context, showUi)
                VolumeController.buzz(context)
                refreshAll(context)
            }
            ACTION_SET_LEVEL -> {
                val percent = intent.getIntExtra(EXTRA_LEVEL_PERCENT, -1)
                if (percent >= 0) {
                    val max = VolumeController.maxVolume(context)
                    VolumeController.setLevel(context, Math.round(percent * max / 100f), showUi)
                    VolumeController.buzz(context)
                }
                refreshAll(context)
            }
            "android.media.VOLUME_CHANGED_ACTION" -> refreshAll(context)
        }
    }
}
