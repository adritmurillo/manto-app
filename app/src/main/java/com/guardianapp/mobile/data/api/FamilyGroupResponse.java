package com.guardianapp.mobile.data.api;

import java.util.List;

public class FamilyGroupResponse {
    private String id;
    private String name;
    private String primaryHostUserId;
    private List<MemberResponse> members;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPrimaryHostUserId() {
        return primaryHostUserId;
    }

    public List<MemberResponse> getMembers() {
        return members;
    }

    public static class MemberResponse {
        private String userId;
        private String role;

        public String getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }
    }
}
