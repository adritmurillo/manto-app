package com.guardianapp.mobile.data.api;

public class RespondIdentityVerificationRequest {
    private final String hostUserId;
    private final Boolean approved;
    private final String note;

    public RespondIdentityVerificationRequest(String hostUserId, Boolean approved, String note) {
        this.hostUserId = hostUserId;
        this.approved = approved;
        this.note = note;
    }
}
