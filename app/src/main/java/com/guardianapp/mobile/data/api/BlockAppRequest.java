package com.guardianapp.mobile.data.api;

public class BlockAppRequest {
    private String familyGroupId;
    private String packageName;
    private String appName;

    public BlockAppRequest(String familyGroupId, String packageName, String appName) {
        this.familyGroupId = familyGroupId;
        this.packageName = packageName;
        this.appName = appName;
    }

    public String getFamilyGroupId() { return familyGroupId; }
    public void setFamilyGroupId(String familyGroupId) { this.familyGroupId = familyGroupId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}