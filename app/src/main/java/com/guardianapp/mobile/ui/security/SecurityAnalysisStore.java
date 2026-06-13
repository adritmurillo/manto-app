package com.guardianapp.mobile.ui.security;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SecurityAnalysisStore {

    private static final int MAX_ITEMS = 100;
    private static final String PREFS = "security_analysis_store";
    private static final String KEY_ITEMS = "items";
    private static final List<SecurityAnalysisItem> ITEMS = new ArrayList<>();
    private static final Gson GSON = new Gson();
    private static Context appContext;
    private static boolean loaded;

    private SecurityAnalysisStore() {
    }

    public static synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        ensureLoaded();
    }

    public static synchronized void add(SecurityAnalysisItem item) {
        ensureLoaded();
        if (item == null) {
            return;
        }
        if (isRecentDuplicate(item)) {
            return;
        }
        ITEMS.add(0, item);
        if (ITEMS.size() > MAX_ITEMS) {
            ITEMS.remove(ITEMS.size() - 1);
        }
        persist();
    }

    public static synchronized List<SecurityAnalysisItem> getAll() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(ITEMS));
    }

    public static synchronized List<SecurityAnalysisItem> getSmsInboxItems() {
        ensureLoaded();
        List<SecurityAnalysisItem> result = new ArrayList<>();
        for (SecurityAnalysisItem item : ITEMS) {
            if (!SecurityAnalysisItem.CHANNEL_SMS.equals(item.getChannel())) {
                continue;
            }
            if (item.isInInbox()) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<SecurityAnalysisItem> getSmsQuarantineItems() {
        ensureLoaded();
        List<SecurityAnalysisItem> result = new ArrayList<>();
        for (SecurityAnalysisItem item : ITEMS) {
            if (!SecurityAnalysisItem.CHANNEL_SMS.equals(item.getChannel())) {
                continue;
            }
            if (item.isInQuarantine()) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized int countSmsInboxItems() {
        return getSmsInboxItems().size();
    }

    public static synchronized int countSmsQuarantineItems() {
        return getSmsQuarantineItems().size();
    }

    public static synchronized int countBlockedLinksCurrentMonth() {
        ensureLoaded();
        int count = 0;
        for (SecurityAnalysisItem item : ITEMS) {
            if (item.getUrl() == null || item.getUrl().isBlank()) {
                continue;
            }
            if (item.isBlocked()) {
                count++;
            }
        }
        return count;
    }

    public static synchronized void removeByUrl(String url) {
        ensureLoaded();
        if (url == null || url.isBlank()) {
            return;
        }
        ITEMS.removeIf(item -> url.equalsIgnoreCase(item.getUrl()));
        persist();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (appContext == null) {
            return;
        }
        String json = prefs().getString(KEY_ITEMS, null);
        if (json == null || json.isBlank()) {
            return;
        }
        List<SecurityAnalysisItem> saved = GSON.fromJson(
                json,
                new TypeToken<List<SecurityAnalysisItem>>() { }.getType()
        );
        if (saved != null) {
            ITEMS.clear();
            ITEMS.addAll(saved);
        }
    }

    private static void persist() {
        if (appContext == null) {
            return;
        }
        prefs().edit().putString(KEY_ITEMS, GSON.toJson(ITEMS)).apply();
    }

    private static boolean isRecentDuplicate(SecurityAnalysisItem candidate) {
        for (SecurityAnalysisItem existing : ITEMS) {
            if (!safeEquals(existing.getChannel(), candidate.getChannel())) {
                continue;
            }
            if (!safeEquals(existing.getSender(), candidate.getSender())) {
                continue;
            }
            if (!safeEquals(existing.getMessage(), candidate.getMessage())) {
                continue;
            }
            if (!safeEquals(existing.getUrl(), candidate.getUrl())) {
                continue;
            }
            long delta = Math.abs(existing.getTimestampMillis() - candidate.getTimestampMillis());
            if (delta <= 15000L) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
