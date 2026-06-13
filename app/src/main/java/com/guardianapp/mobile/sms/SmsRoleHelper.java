package com.guardianapp.mobile.sms;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;

public final class SmsRoleHelper {

    private SmsRoleHelper() {
    }

    public static boolean isDefaultSmsApp(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }
        String defaultPackage = Telephony.Sms.getDefaultSmsPackage(context);
        return context.getPackageName().equals(defaultPackage);
    }

    public static boolean canRequestDefaultRole(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            return roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS);
        }
        return true;
    }

    public static void requestDefaultSmsRole(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = activity.getSystemService(RoleManager.class);
            if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return;
            }
            intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
        } else {
            intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.getPackageName());
        }
        activity.startActivity(intent);
    }
}
