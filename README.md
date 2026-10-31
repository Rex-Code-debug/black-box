# Black Box

Lock your dopamine apps (Instagram, YouTube, ...) for a set time. No unlock button.
One optional exception app. Restart the phone to unlock early.

## Build the APK

**Option A: GitHub (no Android Studio)**
1. Create a free GitHub account and a new empty repository.
2. Upload everything in this folder (keep the `.github` folder).
3. Open the Actions tab, run "Build APK", wait about 3 minutes.
4. Download the `BlackBox-apk` artifact, unzip it, install `app-debug.apk`.

**Option B: Android Studio**
Open this folder, then Build > Build APK(s).

## First run
1. Install the APK (allow "install unknown apps" for your browser/file manager).
2. Open Black Box, tap "Open Accessibility settings", switch Black Box on.
3. Tick apps, optionally star one as the exception, choose time, tap Start lock.

## Notes
- Phones from Xiaomi/Realme/Vivo/Oppo: set Black Box battery to "No restrictions"
  and allow auto-start, or the system may stop the blocker.
- Restarting the phone clears the lock (boot counter changes).
