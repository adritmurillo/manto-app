package com.guardianapp.mobile.api;

public class ResolveEmergencyAlertRequest {
    private String hostId;
    private String resolutionType;
    private String note;

    public ResolveEmergencyAlertRequest(String hostId, String resolutionType, String note) {
        this.hostId = hostId;
        this.resolutionType = resolutionType;
        this.note = note;
    }
}
