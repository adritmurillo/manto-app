package com.guardianapp.mobile.api;

public class AddFamilyMemberRequest {
    private String memberUserId;
    private String role;

    public AddFamilyMemberRequest(String memberUserId, String role) {
        this.memberUserId = memberUserId;
        this.role = role;
    }
}
