package com.blackbox.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * All Black Box state lives here.
 *
 * The lock is stored using SystemClock.elapsedRealtime() (time since the phone booted),
 * so changing the phone's date/time does not shorten it. A restart clears the lock:
 * the boot counter changes (and elapsedRealtime goes back to ~0), which is the
 * "restart phone to unlock" escape hatch.
 */
final class Store {
    private static final String PREFS = "blackbox";
    private static final String K_BLOCKED = "blocked";
    private static final String K_EXCEPTION = "exception";
    private static final String K_PROTECT = "protect_settings";
    private static final String K_START = "lock_start";
    private static final String K_END = "lock_end";
    private static final String K_BOOT = "lock_boot";

    /** Apps that can be used to switch Black Box off or uninstall it. */
    private static final String[] PROTECTED_PKGS = {
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller"
    };

    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), "boot_count", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    // ---- lock state -------------------------------------------------------------

    static boolean isLocked(Context c) {
        SharedPreferences p = sp(c);
        long end = p.getLong(K_END, 0L);
        if (end == 0L) return false;
        long now = SystemClock.elapsedRealtime();
        long start = p.getLong(K_START, 0L);
        boolean rebooted = bootCount(c) != p.getInt(K_BOOT, -1) || now < start;
        if (rebooted || now >= end) {
            p.edit().putLong(K_END, 0L).apply();
            return false;
        }
        return true;
    }

    static long remaining(Context c) {
        long end = sp(c).getLong(K_END, 0L);
        return Math.max(0L, end - SystemClock.elapsedRealtime());
    }

    static void startLock(Context c, long durationMs) {
        long now = SystemClock.elapsedRealtime();
        sp(c).edit()
                .putLong(K_START, now)
                .putLong(K_END, now + durationMs)
                .putInt(K_BOOT, bootCount(c))
                .apply();
    }

    static String format(long ms) {
        long total = (ms + 999L) / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    // ---- selection --------------------------------------------------------------

    static Set<String> getBlocked(Context c) {
        return new HashSet<>(sp(c).getStringSet(K_BLOCKED, new HashSet<String>()));
    }

    static void setBlocked(Context c, Set<String> pkgs) {
        if (isLocked(c)) return;
        sp(c).edit().putStringSet(K_BLOCKED, new HashSet<>(pkgs)).apply();
    }

    static String getException(Context c) {
        return sp(c).getString(K_EXCEPTION, null);
    }

    /** Choosing or changing the exception is only allowed while NOT locked. */
    static void setException(Context c, String pkg) {
        if (isLocked(c)) return;
        sp(c).edit().putString(K_EXCEPTION, pkg).apply();
    }

    /** Removing the exception is allowed any time (it only makes the lock stricter). */
    static void clearException(Context c) {
        sp(c).edit().remove(K_EXCEPTION).apply();
    }

    static boolean getProtect(Context c) {
        return sp(c).getBoolean(K_PROTECT, true);
    }

    static void setProtect(Context c, boolean on) {
        if (isLocked(c)) return;
        sp(c).edit().putBoolean(K_PROTECT, on).apply();
    }

    // ---- decision used by the accessibility service -----------------------------

    static boolean shouldBlock(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        if (!isLocked(c)) return false;
        if (getProtect(c)) {
            for (String p : PROTECTED_PKGS) {
                if (p.equals(pkg)) return true;
            }
        }
        if (pkg.equals(getException(c))) return false;
        return getBlocked(c).contains(pkg);
    }
}
