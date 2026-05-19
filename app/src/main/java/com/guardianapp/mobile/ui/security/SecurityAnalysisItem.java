package com.guardianapp.mobile.ui.security;

public class SecurityAnalysisItem {
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_LINK = "LINK";

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
}
