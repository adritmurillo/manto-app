package com.guardianapp.mobile.data.api;

public class BlockedAppResponse {
    private String id;
    private String familyGroupId;
    private String packageName;
    private String appName;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFamilyGroupId() { return familyGroupId; }
    public void setFamilyGroupId(String familyGroupId) { this.familyGroupId = familyGroupId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}