<div align="center">

# 🎛️ Gesture Volume

**Control your Android volume with a two-finger swipe — anywhere, in any app.**
Plus an all-in-one home screen widget with `+`, `−`, mute and a tap slider.

</div>

---

## ✋ The Gesture

| Gesture | Action |
|---|---|
| **Two-finger swipe UP** | 🔊 Volume up |
| **Two-finger swipe DOWN** | 🔉 Volume down |

Keep swiping to keep changing the volume — each configured distance travelled is one volume step, so a long drag sweeps through the whole range in one motion.

Works **system-wide**: in YouTube, Spotify, games, the browser, the home screen — anywhere.

### It does *not* break your phone

This is the important part. Android's built-in multi-finger accessibility gestures (`GESTURE_2_FINGER_SWIPE_UP` and friends) only fire when `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` is enabled — which throws the whole device into TalkBack-style *explore-by-touch*, where a single tap no longer taps and you must double-tap everything. That is unusable for a utility app.

Gesture Volume instead uses **`setObservedMotionEventSources()`** (Android 15 / API 35), which puts the service in *observing* mode: we receive a copy of each touch event **and the event still flows through to the app underneath**. Your normal taps, scrolls, swipes and pinch-to-zoom all behave exactly as before.

> ⚠️ There is a dangerous middle option the app deliberately avoids: calling `setMotionEventSources(SOURCE_TOUCHSCREEN)` **without** the observing flag. The platform docs state those events "are **not** sent to the rest of the system" — an app doing that swallows every touch on the device. `VolumeGestureService` registers **nothing at all** unless the observing API is confirmed present, so it can never leave your phone in that state.

Horizontal drags are explicitly rejected (a vertical:horizontal ratio gate), and gestures with 3+ fingers are ignored, so it never fights with pinch-to-zoom or system navigation.

> ⚠️ **Requires Android 15 (API 35) or newer** for the gesture feature. The widget works on **Android 7.0+**.

---

## 🧩 The Widget

A single widget containing everything:

```
┌─────────────────────────────────┐
│  65%                    9 / 15  │
│  ┌───────┐ ┌───────┐ ┌───────┐  │
│  │   −   │ │  🔊   │ │   +   │  │
│  └───────┘ └───────┘ └───────┘  │
│  ▮▮▮▮▮▮▮▯▯▯                     │
└─────────────────────────────────┘
```

- **`−` / `+`** — step the volume down / up
- **Mute button** — toggles mute & unmute; the icon changes to reflect the current state, and unmuting restores your previous level
- **10-segment tap slider** — tap any segment to jump straight to that level
- **Live readout** — percentage and raw `current / max`; tap it to open the app

Add it from your launcher's widget picker → **Volume Control**. Resizable.

> RemoteViews (the widget framework) cannot host a real draggable `SeekBar`, so the slider is a row of tap targets styled as a progress bar. It gives the same one-touch "set volume to X" behaviour and works on every launcher.

---

## ⚙️ Settings

| Setting | Description |
|---|---|
| **Enable gestures** | Master on/off without disabling the accessibility service |
| **Swipe sensitivity** | 5 levels — from a long deliberate drag per step to hair-trigger |
| **Haptic feedback** | Short buzz on every volume step |
| **Show system volume bar** | Whether Android's native volume popup appears |

The app also has a live in-app volume slider and `−` / mute / `+` buttons.

---

## 📲 Install

1. Grab the latest `GestureVolume-*-release.apk` from the [**Releases**](../../releases) page.
2. Install it (you may need to allow "install from unknown sources").
3. Open the app → **Enable in Accessibility** → turn on **Gesture Volume**.
4. Try a two-finger swipe up. 🎉

### Privacy

The accessibility service declares `android:canRetrieveWindowContent="false"` — it **cannot read anything on your screen**. It only receives touch coordinates. No internet permission is requested; nothing leaves your device.

---

## 🏗️ Building

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # signed release APK
```

Output lands in `app/build/outputs/apk/`.

### CI

`.github/workflows/build-apk.yml` builds both variants and publishes them to a GitHub Release on every push to `main`, on any `v*` tag, or manually via **Actions → Build APK & Release → Run workflow**.

Signing uses the committed debug-grade keystore by default. To sign with your own, add these repository secrets:

| Secret | Meaning |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 your.jks` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

---

## 🗂️ Project layout

```
app/src/main/java/com/skywave/gesturevolume/
├── VolumeGestureService.kt   # two-finger swipe detection (onMotionEvent)
├── VolumeWidgetProvider.kt   # home screen widget + its click actions
├── VolumeController.kt       # single source of truth for volume changes
├── Prefs.kt                  # settings storage
└── MainActivity.kt           # setup UI & settings
```

---

<div align="center">
Made for <a href="https://github.com/Skywave22">@Skywave22</a>
</div>
