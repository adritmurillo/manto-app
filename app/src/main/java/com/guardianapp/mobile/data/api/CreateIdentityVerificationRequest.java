package com.guardianapp.mobile.data.api;

public class CreateIdentityVerificationRequest {
    private final String linkId;
    private final String protectedUserId;
    private final String claimedPerson;

    public CreateIdentityVerificationRequest(String linkId, String protectedUserId, String claimedPerson) {
        this.linkId = linkId;
        this.protectedUserId = protectedUserId;
        this.claimedPerson = claimedPerson;
    }
}
