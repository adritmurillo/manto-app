package com.guardianapp.mobile.ui.common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class StartupPermissionManager {

    private StartupPermissionManager() {
    }

    public static String[] getMissingBasePermissions(Context context) {
        List<String> missing = new ArrayList<>();
        addIfMissing(context, missing, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(context, missing, Manifest.permission.ACCESS_COARSE_LOCATION);
        addIfMissing(context, missing, Manifest.permission.RECORD_AUDIO);
        addIfMissing(context, missing, Manifest.permission.RECEIVE_SMS);
        addIfMissing(context, missing, Manifest.permission.READ_SMS);
        addIfMissing(context, missing, Manifest.permission.SEND_SMS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(context, missing, Manifest.permission.POST_NOTIFICATIONS);
        }

        return missing.toArray(new String[0]);
    }

    public static boolean hasBasePermissions(Context context) {
        return getMissingBasePermissions(context).length == 0;
    }

    public static void requestBasePermissions(Activity activity, int requestCode) {
        if (activity == null) {
            return;
        }
        String[] missing = getMissingBasePermissions(activity);
        if (missing.length == 0) {
            return;
        }
        ActivityCompat.requestPermissions(activity, missing, requestCode);
    }

    public static boolean shouldRequestBackgroundLocation(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        boolean foregroundGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean backgroundGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return foregroundGranted && !backgroundGranted;
    }

    public static void requestBackgroundLocation(Activity activity, int requestCode) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                requestCode
        );
    }

    private static void addIfMissing(Context context, List<String> target, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            target.add(permission);
        }
    }
}
