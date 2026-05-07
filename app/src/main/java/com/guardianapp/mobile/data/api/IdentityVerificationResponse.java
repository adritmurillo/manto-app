package com.guardianapp.mobile.data.api;

public class IdentityVerificationResponse {
    private String id;
    private String linkId;
    private String protectedUserId;
    private String hostUserId;
    private String claimedPerson;
    private String challengeCode;
    private String status;
    private String resolutionNote;

    public String getId() {
        return id;
    }

    public String getLinkId() {
        return linkId;
    }

    public String getProtectedUserId() {
        return protectedUserId;
    }

    public String getHostUserId() {
        return hostUserId;
    }

    public String getClaimedPerson() {
        return claimedPerson;
    }

    public String getChallengeCode() {
        return challengeCode;
    }

    public String getStatus() {
        return status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isApproved() {
        return "APPROVED".equals(status);
    }

    public boolean isRejected() {
        return "REJECTED".equals(status);
    }

    public boolean isExpired() {
        return "EXPIRED".equals(status);
    }
}
