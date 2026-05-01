package com.guardianapp.mobile.invite;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores a pending invite token so the app can continue after login/registration.
 */
public final class PendingInviteStore {

    private static final String PREFS = "pending_invite_prefs";
    private static final String KEY_TOKEN = "pending_invite_token";

    private PendingInviteStore() {
    }

    public static void save(Context context, String token) {
        if (context == null || token == null) return;
        String normalized = token.trim().toUpperCase();
        if (normalized.isEmpty()) return;
        prefs(context).edit().putString(KEY_TOKEN, normalized).apply();
    }

    public static String peek(Context context) {
        return prefs(context).getString(KEY_TOKEN, null);
    }

    public static String pop(Context context) {
        SharedPreferences p = prefs(context);
        String token = p.getString(KEY_TOKEN, null);
        if (token != null) {
            p.edit().remove(KEY_TOKEN).apply();
        }
        return token;
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_TOKEN).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
