package com.guardianapp.mobile.data.api;

public class TriggerEmergencyAlertRequest {
    private String linkId;
    private String protectedUserId;
    private double latitude;
    private double longitude;

    public TriggerEmergencyAlertRequest(String linkId, String protectedUserId, double latitude, double longitude) {
        this.linkId = linkId;
        this.protectedUserId = protectedUserId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
