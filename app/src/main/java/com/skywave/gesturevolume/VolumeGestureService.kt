package com.skywave.gesturevolume

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlin.math.abs

/**
 * Detects a TWO-FINGER SWIPE UP / DOWN anywhere on screen and changes the media volume.
 *
 * Implementation note
 * -------------------
 * Android's built-in multi-finger gesture ids (GESTURE_2_FINGER_SWIPE_UP, etc.) only fire when
 * FLAG_REQUEST_TOUCH_EXPLORATION_MODE is set, which puts the whole device into TalkBack-style
 * explore-by-touch and breaks normal tapping. That is unacceptable for a utility app.
 *
 * Instead we use setMotionEventSources()/onMotionEvent() (API 34+), which lets an accessibility
 * service OBSERVE raw touch events without consuming them, so every other app keeps working
 * exactly as before. We then do our own two-finger swipe recognition.
 */
class VolumeGestureService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeGestureService"

        /** Base distance in pixels the fingers must travel for one volume step (sensitivity 3). */
        private const val BASE_STEP_PX = 130f

        /** Fingers must move mostly vertically: |dy| must exceed |dx| by this ratio. */
        private const val VERTICAL_RATIO = 1.4f

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    // --- gesture tracking state -------------------------------------------------
    private var tracking = false
    private var startAvgY = 0f
    private var startAvgX = 0f
    private var lastStepY = 0f
    private var consumedAnyStep = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        applyMotionEventConfig()
        Log.i(TAG, "connected; sdk=${Build.VERSION.SDK_INT}")
    }

    private fun applyMotionEventConfig() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "Android 14+ required for system-wide motion observation")
            return
        }
        try {
            val info: AccessibilityServiceInfo = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS
            serviceInfo = info
            setMotionEventSources(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        } catch (t: Throwable) {
            Log.e(TAG, "failed to enable motion events", t)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMotionEvent(event: MotionEvent) {
        if (!Prefs.gesturesEnabledCache) return

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) beginTracking(event)
                // 3+ fingers -> abandon, that's someone else's gesture
                if (event.pointerCount > 2) tracking = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking && event.pointerCount == 2) handleMove(event)
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (tracking && consumedAnyStep) {
                    Log.d(TAG, "gesture finished")
                }
                tracking = false
                consumedAnyStep = false
            }
        }
    }

    private fun beginTracking(event: MotionEvent) {
        startAvgY = avgY(event)
        startAvgX = avgX(event)
        lastStepY = startAvgY
        tracking = true
        consumedAnyStep = false
    }

    private fun handleMove(event: MotionEvent) {
        val curY = avgY(event)
        val curX = avgX(event)

        val totalDy = curY - startAvgY
        val totalDx = curX - startAvgX

        // Reject mostly-horizontal drags so we don't fight with pinch/scroll/back gestures.
        if (abs(totalDx) > abs(totalDy) / VERTICAL_RATIO && abs(totalDy) < BASE_STEP_PX) return

        val stepPx = stepDistancePx()
        val dySinceStep = curY - lastStepY

        if (abs(dySinceStep) >= stepPx) {
            val steps = (dySinceStep / stepPx).toInt()
            if (steps != 0) {
                // Screen Y grows downward: swipe UP (negative dy) must RAISE volume.
                val volumeSteps = -steps
                VolumeController.adjust(this, volumeSteps, Prefs.showSystemUi(this))
                VolumeController.buzz(this)
                VolumeWidgetProvider.refreshAll(this)
                lastStepY += steps * stepPx
                consumedAnyStep = true
            }
        }
    }

    /** Sensitivity 1..5 -> larger swipe needed at 1, hair-trigger at 5. */
    private fun stepDistancePx(): Float {
        val s = Prefs.sensitivity(this)
        val multiplier = when (s) {
            1 -> 1.8f
            2 -> 1.35f
            3 -> 1.0f
            4 -> 0.72f
            else -> 0.5f
        }
        return BASE_STEP_PX * multiplier
    }

    private fun avgY(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getY(i)
        return sum / e.pointerCount
    }

    private fun avgX(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getX(i)
        return sum / e.pointerCount
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    override fun onInterrupt() { /* not used */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
