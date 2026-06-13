package com.guardianapp.mobile.ui.common;

import android.app.Activity;
import android.content.Intent;

import com.guardianapp.mobile.ui.main.DeviceSetupActivity;
import com.guardianapp.mobile.ui.protecteduser.ProtectedSessionStore;

public final class AppNavigator {

    private AppNavigator() {
    }

    public static void goToDeviceSetup(Activity activity, String userId) {
        ProtectedSessionStore.clear(activity);
        Intent intent = new Intent(activity, DeviceSetupActivity.class);
        intent.putExtra(DeviceSetupActivity.EXTRA_USER_ID, userId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
