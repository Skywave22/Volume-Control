package com.skywave.gesturevolume

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * Detects a TWO-FINGER SWIPE UP / DOWN anywhere on screen and changes the media volume.
 *
 * ============================ IMPORTANT SAFETY NOTE ============================
 * There are three ways to see touches from an AccessibilityService, and two of them
 * would ruin the device:
 *
 *  1. FLAG_REQUEST_TOUCH_EXPLORATION_MODE + onGesture(GESTURE_2_FINGER_SWIPE_UP)
 *     -> puts the whole device into TalkBack-style explore-by-touch. A single tap no
 *        longer taps; the user must double-tap everything. Unacceptable.
 *
 *  2. setMotionEventSources(SOURCE_TOUCHSCREEN) alone (API 34)
 *     -> per the platform docs: "MotionEvents from sources in getMotionEventSources()
 *        are NOT sent to the rest of the system." That means this service would swallow
 *        every single touch on the phone. The device would become unusable and the user
 *        would have to reboot into safe mode. NEVER do this on a touchscreen source.
 *
 *  3. setMotionEventSources(...) + setObservedMotionEventSources(...) (API 35 / Android 15)
 *     -> "observing" mode: we receive a copy of the events and they STILL flow through to
 *        the app underneath. This is the only safe option, and it is what we use.
 *
 * Because option 3 is API 35 and the project compiles against SDK 34, the two setters are
 * invoked reflectively. If EITHER call is unavailable we register nothing at all and the
 * gesture feature simply stays off - we never fall back to option 2.
 * ===============================================================================
 */
class VolumeGestureService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeGestureService"

        /** Base distance in pixels the fingers travel per volume step (at sensitivity 3). */
        private const val BASE_STEP_PX = 130f

        /** Reject mostly-horizontal drags so we never fight scrolling or pinch-to-zoom. */
        private const val VERTICAL_RATIO = 1.4f

        @Volatile
        var isRunning: Boolean = false
            private set

        /** True when the OS actually granted us safe pass-through touch observation. */
        @Volatile
        var isObservingTouches: Boolean = false
            private set
    }

    // --- gesture tracking state -------------------------------------------------
    private var tracking = false
    private var startAvgX = 0f
    private var startAvgY = 0f
    private var lastStepY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Prefs.gesturesEnabled(this) // prime the cache
        isObservingTouches = enableSafeTouchObservation()
        Log.i(TAG, "connected; sdk=${Build.VERSION.SDK_INT}; observing=$isObservingTouches")
    }

    /**
     * Enables pass-through touch observation. Returns false (and registers NOTHING) if the
     * device is older than Android 15, so we can never end up in the "swallow all touches" state.
     */
    private fun enableSafeTouchObservation(): Boolean {
        if (Build.VERSION.SDK_INT < 35) {
            Log.w(TAG, "Android 15+ required for safe touch observation; gestures disabled")
            return false
        }
        val info = serviceInfo ?: return false
        return try {
            val cls = AccessibilityServiceInfo::class.java
            val setSources = cls.getMethod("setMotionEventSources", Int::class.javaPrimitiveType)
            // Must exist BEFORE we opt into observing, otherwise we risk consuming touches.
            val setObserved =
                cls.getMethod("setObservedMotionEventSources", Int::class.javaPrimitiveType)

            val touchscreen = InputDevice.SOURCE_TOUCHSCREEN
            setSources.invoke(info, touchscreen)
            // This is the call that makes the events pass through to the rest of the system.
            setObserved.invoke(info, touchscreen)
            serviceInfo = info
            true
        } catch (t: Throwable) {
            Log.e(TAG, "observed motion events unavailable - gestures disabled", t)
            // Make absolutely sure we are not left listening in consuming mode.
            runCatching {
                val cls = AccessibilityServiceInfo::class.java
                cls.getMethod("setMotionEventSources", Int::class.javaPrimitiveType)
                    .invoke(info, 0)
                serviceInfo = info
            }
            false
        }
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (!isObservingTouches) return
        if (!Prefs.gesturesEnabledCache) return
        if (event.source and InputDevice.SOURCE_TOUCHSCREEN != InputDevice.SOURCE_TOUCHSCREEN) return

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                when {
                    event.pointerCount == 2 -> beginTracking(event)
                    // 3+ fingers belongs to someone else (system nav, screenshot, etc.)
                    event.pointerCount > 2 -> tracking = false
                }
            }

            MotionEvent.ACTION_MOVE -> if (tracking && event.pointerCount == 2) handleMove(event)

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
    }

    private fun beginTracking(event: MotionEvent) {
        startAvgX = avgX(event)
        startAvgY = avgY(event)
        lastStepY = startAvgY
        tracking = true
    }

    private fun handleMove(event: MotionEvent) {
        val curX = avgX(event)
        val curY = avgY(event)

        val totalDx = curX - startAvgX
        val totalDy = curY - startAvgY

        // Mostly horizontal so far? Not our gesture.
        if (abs(totalDx) > abs(totalDy) / VERTICAL_RATIO && abs(totalDy) < BASE_STEP_PX) return

        val stepPx = stepDistancePx()
        val dySinceStep = curY - lastStepY
        if (abs(dySinceStep) < stepPx) return

        val steps = (dySinceStep / stepPx).toInt()
        if (steps == 0) return

        // Screen Y grows downward, so swiping UP (negative dy) must RAISE the volume.
        VolumeController.adjust(this, -steps, Prefs.showSystemUi(this))
        VolumeController.buzz(this)
        VolumeWidgetProvider.refreshAll(this)
        lastStepY += steps * stepPx
    }

    /** Sensitivity 1..5 -> long deliberate drag at 1, hair-trigger at 5. */
    private fun stepDistancePx(): Float = BASE_STEP_PX * when (Prefs.sensitivity(this)) {
        1 -> 1.8f
        2 -> 1.35f
        3 -> 1.0f
        4 -> 0.72f
        else -> 0.5f
    }

    private fun avgX(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getX(i)
        return sum / e.pointerCount
    }

    private fun avgY(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getY(i)
        return sum / e.pointerCount
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    override fun onInterrupt() { /* not used */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isRunning = false
        isObservingTouches = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        isObservingTouches = false
        super.onDestroy()
    }
}
