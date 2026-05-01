package com.guardianapp.mobile.api;

public class FamilyInvitationResponse {
    private String id;
    private String familyGroupId;
    private String invitedByUserId;
    private String targetRole;
    private String token;
    private String status;
    private String expiresAt;

    public String getId() {
        return id;
    }

    public String getFamilyGroupId() {
        return familyGroupId;
    }

    public String getInvitedByUserId() {
        return invitedByUserId;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getToken() {
        return token;
    }

    public String getStatus() {
        return status;
    }

    public String getExpiresAt() {
        return expiresAt;
    }
}
