package com.guardianapp.mobile.api;

public class AlertResponse {
    private String id;
    private String linkId;
    private String protectedUserId;
    private String suspiciousUrl;
    private String reason;
    private String status;
    private boolean urlAllowed;

    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getSuspiciousUrl() { return suspiciousUrl; }
    public boolean isUrlAllowed() { return urlAllowed; }
}