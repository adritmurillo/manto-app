package com.guardianapp.mobile.data.api;

import java.util.List;

public class AnalyzeSingleUrlResponse {
    private String url;
    private String status;
    private String reason;
    private int heuristicScore;
    private List<String> signals;
    private boolean whitelisted;
    private String trustedProvider;
    private String source;
    private String detectedAt;

    public String getUrl() {
        return url;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public int getHeuristicScore() {
        return heuristicScore;
    }

    public List<String> getSignals() {
        return signals;
    }

    public boolean isWhitelisted() {
        return whitelisted;
    }

    public String getTrustedProvider() {
        return trustedProvider;
    }

    public String getSource() {
        return source;
    }

    public String getDetectedAt() {
        return detectedAt;
    }
}
