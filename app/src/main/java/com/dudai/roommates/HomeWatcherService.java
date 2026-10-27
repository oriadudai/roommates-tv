package com.dudai.roommates;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * On Google TV / Android TV the Home button opens Google's own home screen.
 * This service notices when that screen comes up and immediately opens our screen instead.
 */
public class HomeWatcherService extends AccessibilityService {

    private static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx";   // Google TV
    private static final String ANDROID_TV_HOME = "com.google.android.tvlauncher";        // older Android TV

    private static final long MIN_GAP_MS = 800;
    private long lastLaunch;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String pkg = String.valueOf(event.getPackageName());
        String cls = String.valueOf(event.getClassName());
        boolean isHome =
                (GOOGLE_TV_HOME.equals(pkg) && cls.contains("HomeActivity"))
                || (ANDROID_TV_HOME.equals(pkg) && cls.endsWith(".MainActivity"));
        if (!isHome) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < MIN_GAP_MS) return;
        lastLaunch = now;

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
    }
}
