package com.guardianapp.mobile.ui.security;

public class SecurityAnalysisItem {
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_LINK = "LINK";
    public static final String BUCKET_INBOX = "INBOX";
    public static final String BUCKET_QUARANTINE = "QUARANTINE";
    public static final String REVIEW_LOCAL_ONLY = "LOCAL_ONLY";
    public static final String REVIEW_PENDING_HOST = "PENDING_HOST";
    public static final String REVIEW_LOCAL_BLOCKED = "LOCAL_BLOCKED";
    public static final String REVIEW_HOST_ALLOWED = "HOST_ALLOWED";
    public static final String REVIEW_HOST_BLOCKED = "HOST_BLOCKED";

    private final long timestampMillis;
    private final String channel;
    private final String sender;
    private final String message;
    private final String url;
    private final String status;
    private final String reason;
    private final boolean blocked;
    private final boolean whitelisted;
    private final String trustedProvider;
    private final String storageBucket;
    private final String reviewState;

    public SecurityAnalysisItem(long timestampMillis,
                                String channel,
                                String sender,
                                String message,
                                String url,
                                String status,
                                String reason,
                                boolean blocked,
                                boolean whitelisted,
                                String trustedProvider) {
        this(
                timestampMillis,
                channel,
                sender,
                message,
                url,
                status,
                reason,
                blocked,
                whitelisted,
                trustedProvider,
                deriveBucket(channel, blocked, whitelisted),
                deriveReviewState(blocked, whitelisted)
        );
    }

    public SecurityAnalysisItem(long timestampMillis,
                                String channel,
                                String sender,
                                String message,
                                String url,
                                String status,
                                String reason,
                                boolean blocked,
                                boolean whitelisted,
                                String trustedProvider,
                                String storageBucket,
                                String reviewState) {
        this.timestampMillis = timestampMillis;
        this.channel = channel;
        this.sender = sender;
        this.message = message;
        this.url = url;
        this.status = status;
        this.reason = reason;
        this.blocked = blocked;
        this.whitelisted = whitelisted;
        this.trustedProvider = trustedProvider;
        this.storageBucket = storageBucket;
        this.reviewState = reviewState;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public String getChannel() {
        return channel;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public String getUrl() {
        return url;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isWhitelisted() {
        return whitelisted;
    }

    public String getTrustedProvider() {
        return trustedProvider;
    }

    public String getStorageBucket() {
        return storageBucket == null || storageBucket.isBlank() ? BUCKET_INBOX : storageBucket;
    }

    public String getReviewState() {
        return reviewState == null || reviewState.isBlank() ? REVIEW_LOCAL_ONLY : reviewState;
    }

    public boolean isInInbox() {
        return BUCKET_INBOX.equals(getStorageBucket());
    }

    public boolean isInQuarantine() {
        return BUCKET_QUARANTINE.equals(getStorageBucket());
    }

    private static String deriveBucket(String channel, boolean blocked, boolean whitelisted) {
        if (!CHANNEL_SMS.equals(channel)) {
            return BUCKET_INBOX;
        }
        if (blocked && !whitelisted) {
            return BUCKET_QUARANTINE;
        }
        return BUCKET_INBOX;
    }

    private static String deriveReviewState(boolean blocked, boolean whitelisted) {
        if (whitelisted) {
            return REVIEW_HOST_ALLOWED;
        }
        if (blocked) {
            return REVIEW_LOCAL_BLOCKED;
        }
        return REVIEW_LOCAL_ONLY;
    }
}
