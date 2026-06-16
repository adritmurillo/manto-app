package com.guardianapp.mobile.data.api;

import java.util.List;

public class ReportInstalledAppsRequest {
    private List<AppInfo> apps;

    public ReportInstalledAppsRequest(List<AppInfo> apps) {
        this.apps = apps;
    }

    public List<AppInfo> getApps() { return apps; }
    public void setApps(List<AppInfo> apps) { this.apps = apps; }

    public static class AppInfo {
        private String packageName;
        private String appName;

        public AppInfo(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
    }
}