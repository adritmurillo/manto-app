package com.guardianapp.mobile.ui.protecteduser;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProtectedSessionStore {

    private static final String PREFS = "protected_session_store";
    private static final String KEY_PROTECTED_ID = "protected_id";
    private static final String KEY_LINK_ID = "link_id";

    private ProtectedSessionStore() {
    }

    public static void save(Context context, String protectedId, String linkId) {
        if (context == null) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_PROTECTED_ID, blankToNull(protectedId))
                .putString(KEY_LINK_ID, blankToNull(linkId))
                .apply();
    }

    public static String getProtectedId(Context context) {
        return context == null ? null : prefs(context).getString(KEY_PROTECTED_ID, null);
    }

    public static String getLinkId(Context context) {
        return context == null ? null : prefs(context).getString(KEY_LINK_ID, null);
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
