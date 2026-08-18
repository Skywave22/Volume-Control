# 📲 Installing Gesture Volume

Two different Android security features get in the way of **every** sideloaded accessibility app. Neither means anything is wrong with the APK. Here's what each one is and how to get past it.

---

## 1️⃣ "Unsafe app blocked" / "Blocked by Play Protect" — at install time

Google Play Protect scans APKs that didn't come from the Play Store. Apps that contain an **accessibility service** get extra scrutiny, because that's the API malware abuses. A brand-new app signed with an unknown certificate and downloaded from GitHub ticks every "unfamiliar" box.

**Get past it:**

- Tap **More details** ▸ **Install anyway**
- ⚠️ Do **not** tap "Got it" — that cancels the install

If you don't see "Install anyway":

1. **Play Store** ▸ profile icon ▸ **Play Protect** ▸ ⚙️ settings
2. Turn **Scan apps with Play Protect** off
3. Install the APK
4. **Turn it back on** afterwards

---

## 2️⃣ "Restricted setting" — when enabling Accessibility

This is the one most people hit. Since **Android 13** (and stricter in 14 and 15), an app installed from outside the Play Store is **not allowed to turn on Accessibility** until you explicitly override it. The toggle appears greyed out, or flips itself back off.

> Android calls this a "restricted setting" because accessibility services *can* read the screen. Gesture Volume declares `canRetrieveWindowContent="false"`, so it can't — but Android applies the restriction to all sideloaded apps regardless.

### The fix (Pixel / stock Android / most phones)

**Step 1 — trigger it.** Settings ▸ **Accessibility** ▸ **Gesture Volume** ▸ try to turn it on. You'll get the "Restricted setting" message. **Dismiss it.**
This step is mandatory — the override option stays hidden until Android has blocked you once.

**Step 2 — allow it.** Settings ▸ **Apps** ▸ **See all apps** ▸ **Gesture Volume**
→ **⋮** menu (top right) → **Allow restricted settings** → confirm with PIN/fingerprint.

> You must open the app from the full alphabetical app list. Reaching App Info from Recents or a long-press shortcut **won't show the ⋮ option**.

**Step 3 — enable.** Back to Accessibility ▸ **Gesture Volume** ▸ toggle on. ✅

The app has a **"Toggle greyed out?"** button on its main screen that shows these steps and jumps you straight to the right settings page.

### If "Allow restricted settings" isn't in the ⋮ menu

Common on **Android 15**, **OxygenOS 15** and **ColorOS 15**, where the option was removed. Use a *session-based* installer, which Android treats like a real app store:

1. Install **Split APKs Installer (SAI)** — or the APKMirror / Uptodown installer — **from the Play Store**
2. Uninstall Gesture Volume
3. Reinstall `GestureVolume-1.0-release.apk` **through that installer**
4. The restriction no longer applies — enable Accessibility normally

### Manufacturer notes

| Device | Extra step |
|---|---|
| **Samsung** (S24, Z Fold6+) | Turn off Settings ▸ Security and privacy ▸ **Auto Blocker** first — it blocks sideloading entirely |
| **Xiaomi / HyperOS** | Settings ▸ Apps ▸ Gesture Volume ▸ Battery saver ▸ **No restrictions**, and add it to **Autostart** |
| **OnePlus / Oppo** | Use the SAI method above on OS 15 |
| **Huawei / HarmonyOS** | Doesn't use the standard flow; HarmonyOS NEXT blocks sideloading completely |

### Via ADB (fastest, if you have a computer)

```bash
adb install GestureVolume-1.0-release.apk
adb shell appops set com.skywave.gesturevolume ACCESS_RESTRICTED_SETTINGS allow
```

Then enable it in Accessibility as normal.

---

## 3️⃣ Android 13 specifically — the gesture won't work

Worth being upfront: **on Android 13 the two-finger gesture cannot work at all.**

It relies on `setObservedMotionEventSources()`, added in **Android 15 (API 35)**. It's the only API that lets an app watch touches *without swallowing them*. On Android 13/14 the app deliberately registers nothing rather than fall back to an approach that would break your touchscreen.

**On Android 13 and 14 you still get:** the home screen widget (`−`, `+`, mute, tap slider), and the in-app volume controls. Only the swipe gesture is unavailable, and the app's status line tells you so.

| Feature | Android 7–12 | Android 13–14 | Android 15+ |
|---|:--:|:--:|:--:|
| Widget (+ / − / mute / slider) | ✅ | ✅ | ✅ |
| In-app controls | ✅ | ✅ | ✅ |
| Two-finger swipe gesture | ❌ | ❌ | ✅ |

---

## Why the app asks for so little

After the first release I removed two permissions that were declared but never used — `SYSTEM_ALERT_WINDOW` (draw over other apps) and `POST_NOTIFICATIONS`. Unused sensitive permissions make Play Protect more aggressive and show scary text on the install screen.

What's left is the honest minimum:

| Permission | Why |
|---|---|
| `MODIFY_AUDIO_SETTINGS` | Change the volume. That's the whole app. |
| `VIBRATE` | Haptic tick per volume step (can be switched off) |

There is **no `INTERNET` permission** — the app physically cannot send your data anywhere.
