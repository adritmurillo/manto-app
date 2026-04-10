package com.guardianapp.mobile.api;

public class CreateAlertRequest {
    private String linkId;
    private String protectedUserId;
    private String url;
    private String reason;

    public CreateAlertRequest(String linkId, String protectedUserId, String url, String reason) {
        this.linkId = linkId;
        this.protectedUserId = protectedUserId;
        this.url = url;
        this.reason = reason;
    }
}