package com.guardianapp.mobile.data.api;

public class EmergencyAlertResponse {
    private String id;
    private String linkId;
    private String protectedUserId;
    private String primaryHostUserId;
    private double latitude;
    private double longitude;
    private String status;
    private String createdAt;
    private String resolvedAt;
    private String resolvedByUserId;
    private String resolutionType;
    private String resolutionNote;
    private long secondsActive;

    public String getId() {
        return id;
    }

    public String getLinkId() {
        return linkId;
    }

    public String getProtectedUserId() {
        return protectedUserId;
    }

    public String getPrimaryHostUserId() {
        return primaryHostUserId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedByUserId() {
        return resolvedByUserId;
    }

    public long getSecondsActive() {
        return secondsActive;
    }
}
