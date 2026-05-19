package com.guardianapp.mobile.ui.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SecurityAnalysisStore {

    private static final int MAX_ITEMS = 100;
    private static final List<SecurityAnalysisItem> ITEMS = new ArrayList<>();

    private SecurityAnalysisStore() {
    }

    public static synchronized void add(SecurityAnalysisItem item) {
        ITEMS.add(0, item);
        if (ITEMS.size() > MAX_ITEMS) {
            ITEMS.remove(ITEMS.size() - 1);
        }
    }

    public static synchronized List<SecurityAnalysisItem> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(ITEMS));
    }

    public static synchronized int countBlockedLinksCurrentMonth() {
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
}
