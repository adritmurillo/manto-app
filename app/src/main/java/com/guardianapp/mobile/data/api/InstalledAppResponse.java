package com.guardianapp.mobile.data.api;

public class InstalledAppResponse {
    private String id;
    private String packageName;
    private String appName;
    private String reportedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getReportedAt() { return reportedAt; }
    public void setReportedAt(String reportedAt) { this.reportedAt = reportedAt; }
}