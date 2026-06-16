package com.guardianapp.mobile.data.api;

public class SmsThreatAlertResponse {
    private String id;
    private String linkId;
    private String protectedUserId;
    private String hostUserId;
    private String sender;
    private String messageExcerpt;
    private String detectedUrl;
    private String analysisStatus;
    private String analysisReason;
    private String status;
    private boolean urlAllowed;
    private String createdAt;
    private String resolvedAt;
    private String resolutionNote;
    private long minutesPending;

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getMessageExcerpt() {
        return messageExcerpt;
    }

    public String getDetectedUrl() {
        return detectedUrl;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public String getAnalysisReason() {
        return analysisReason;
    }

    public String getStatus() {
        return status;
    }

    public boolean isUrlAllowed() {
        return urlAllowed;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public long getMinutesPending() {
        return minutesPending;
    }
}
