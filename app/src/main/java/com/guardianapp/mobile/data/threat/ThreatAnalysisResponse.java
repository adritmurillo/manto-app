package com.guardianapp.mobile.data.threat;

import java.util.List;

public class ThreatAnalysisResponse {
    private String status;
    private String source;
    private String detectedAt;
    private int totalUrls;
    private int analyzedUrls;
    private int invalidUrls;
    private List<UrlThreatAnalysisResponse> urlResults;

    public String getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public String getDetectedAt() {
        return detectedAt;
    }

    public int getTotalUrls() {
        return totalUrls;
    }

    public int getAnalyzedUrls() {
        return analyzedUrls;
    }

    public int getInvalidUrls() {
        return invalidUrls;
    }

    public List<UrlThreatAnalysisResponse> getUrlResults() {
        return urlResults;
    }
}
