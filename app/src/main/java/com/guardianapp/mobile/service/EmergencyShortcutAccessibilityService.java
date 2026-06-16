package com.guardianapp.mobile.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class EmergencyShortcutAccessibilityService extends AccessibilityService {

    private static final int PRESS_TARGET = 3;
    private static final long PRESS_WINDOW_MS = 1500L;

    private int volumeDownPressCount;
    private long lastVolumeDownPressAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 0;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                String packageName = event.getPackageName().toString();
                if (!packageName.equals(getPackageName())) {
                    if (com.guardianapp.mobile.data.appcontrol.BlockedAppsStore.isBlocked(getApplicationContext(), packageName)) {
                        android.content.Intent intent = new android.content.Intent(this, com.guardianapp.mobile.ui.protecteduser.AppBlockedActivity.class);
                        intent.putExtra("BLOCKED_PACKAGE", packageName);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN
                || event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastVolumeDownPressAt > PRESS_WINDOW_MS) {
            volumeDownPressCount = 0;
        }
        lastVolumeDownPressAt = now;
        volumeDownPressCount++;

        if (volumeDownPressCount >= PRESS_TARGET) {
            volumeDownPressCount = 0;
            EmergencyShortcutDispatcher.trigger(getApplicationContext());
        }

        return false;
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null || enabledServices.isBlank()) {
            return false;
        }
        String serviceId = new ComponentName(context, EmergencyShortcutAccessibilityService.class).flattenToString();
        return enabledServices.contains(serviceId);
    }
}