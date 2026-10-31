package com.blackbox.app;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Watches which app comes to the front. While a lock is running, any blocked app
 * is immediately sent back to the home screen.
 */
public class BlockerService extends AccessibilityService {
    private long lastToast = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence name = event.getPackageName();
        if (name == null) return;
        if (!Store.shouldBlock(this, name.toString())) return;

        performGlobalAction(GLOBAL_ACTION_HOME);

        long now = SystemClock.elapsedRealtime();
        if (now - lastToast > 1500L) {
            lastToast = now;
            Toast.makeText(this,
                    "Black Box: locked, " + Store.format(Store.remaining(this)) + " left",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onInterrupt() {
    }
}
