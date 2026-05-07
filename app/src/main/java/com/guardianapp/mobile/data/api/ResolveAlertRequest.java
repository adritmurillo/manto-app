package com.guardianapp.mobile.data.api;

public class ResolveAlertRequest {
    private String hostId;
    private boolean allowAccess;
    private String note;

    public ResolveAlertRequest(String hostId, boolean allowAccess, String note) {
        this.hostId = hostId;
        this.allowAccess = allowAccess;
        this.note = note;
    }
}
