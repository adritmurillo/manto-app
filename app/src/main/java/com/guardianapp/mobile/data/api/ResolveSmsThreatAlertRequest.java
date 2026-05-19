package com.guardianapp.mobile.data.api;

public class ResolveSmsThreatAlertRequest {
    private final String hostId;
    private final boolean allowAccess;
    private final String note;

    public ResolveSmsThreatAlertRequest(String hostId, boolean allowAccess, String note) {
        this.hostId = hostId;
        this.allowAccess = allowAccess;
        this.note = note;
    }
}
