package com.guardianapp.mobile.sms;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Patterns;

import com.guardianapp.mobile.ui.security.SecurityAnalysisItem;
import com.guardianapp.mobile.ui.security.SecurityAnalysisStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

public final class DeviceSmsInboxRepository {

    private static final String[] PROJECTION = {
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
    };
    private static final String SORT_ORDER = Telephony.Sms.DATE + " DESC LIMIT 200";

    private DeviceSmsInboxRepository() {
    }

    public static List<SecurityAnalysisItem> loadInbox(Context context) {
        List<SecurityAnalysisItem> items = new ArrayList<>();
        if (context == null || !SmsAccessHelper.hasSmsPermissions(context)) {
            return items;
        }

        SecurityAnalysisStore.init(context);
        List<SecurityAnalysisItem> analyzedItems = SecurityAnalysisStore.getAll();

        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                PROJECTION,
                null,
                null,
                SORT_ORDER
        )) {
            if (cursor == null) {
                return items;
            }

            int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
            int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);

            while (cursor.moveToNext()) {
                String sender = addressIndex >= 0 ? cursor.getString(addressIndex) : null;
                String message = bodyIndex >= 0 ? cursor.getString(bodyIndex) : null;
                long timestamp = dateIndex >= 0 ? cursor.getLong(dateIndex) : System.currentTimeMillis();

                SecurityAnalysisItem analyzed = findMatchingAnalyzedItem(analyzedItems, sender, message, timestamp);
                if (analyzed != null) {
                    items.add(new SecurityAnalysisItem(
                            timestamp,
                            SecurityAnalysisItem.CHANNEL_SMS,
                            safeSender(sender),
                            safeMessage(message),
                            analyzed.getUrl(),
                            analyzed.getStatus(),
                            analyzed.getReason(),
                            analyzed.isBlocked(),
                            analyzed.isWhitelisted(),
                            analyzed.getTrustedProvider(),
                            analyzed.getStorageBucket(),
                            analyzed.getReviewState()
                    ));
                    continue;
                }

                String detectedUrl = extractFirstUrl(message);
                items.add(new SecurityAnalysisItem(
                        timestamp,
                        SecurityAnalysisItem.CHANNEL_SMS,
                        safeSender(sender),
                        safeMessage(message),
                        detectedUrl,
                        detectedUrl == null ? "NO_URL" : "UNREAD_ANALYSIS",
                        detectedUrl == null
                                ? "SMS cargado desde el dispositivo."
                                : "SMS cargado desde el dispositivo. Aun no fue analizado por Manto.",
                        false,
                        false,
                        null,
                        SecurityAnalysisItem.BUCKET_INBOX,
                        SecurityAnalysisItem.REVIEW_LOCAL_ONLY
                ));
            }
        } catch (SecurityException ignored) {
            return new ArrayList<>();
        }

        return items;
    }

    private static SecurityAnalysisItem findMatchingAnalyzedItem(List<SecurityAnalysisItem> candidates,
                                                                 String sender,
                                                                 String message,
                                                                 long timestamp) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (SecurityAnalysisItem item : candidates) {
            if (item == null) {
                continue;
            }
            if (!SecurityAnalysisItem.CHANNEL_SMS.equals(item.getChannel())) {
                continue;
            }
            if (!normalize(item.getSender()).equals(normalize(sender))) {
                continue;
            }
            if (!normalize(item.getMessage()).equals(normalize(message))) {
                continue;
            }
            long delta = Math.abs(item.getTimestampMillis() - timestamp);
            if (delta <= 5 * 60 * 1000L) {
                return item;
            }
        }
        return null;
    }

    private static String extractFirstUrl(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = Patterns.WEB_URL.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return candidate;
            }
            return "https://" + candidate;
        }
        return null;
    }

    private static String safeSender(String sender) {
        return sender == null || sender.isBlank() ? "Desconocido" : sender.trim();
    }

    private static String safeMessage(String message) {
        return message == null ? "" : message.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
