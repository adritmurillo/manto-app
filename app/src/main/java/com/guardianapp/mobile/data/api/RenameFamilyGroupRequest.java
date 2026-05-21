package com.guardianapp.mobile.data.api;

public class RenameFamilyGroupRequest {
    private final String name;

    public RenameFamilyGroupRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

