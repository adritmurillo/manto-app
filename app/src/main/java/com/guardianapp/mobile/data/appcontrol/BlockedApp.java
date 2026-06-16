package com.guardianapp.mobile.data.appcontrol;

public class BlockedApp {
    private String packageName;
    private String appName;

    public BlockedApp(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
    }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}