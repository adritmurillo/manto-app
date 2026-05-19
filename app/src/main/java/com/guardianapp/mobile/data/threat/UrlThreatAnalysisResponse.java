package com.guardianapp.mobile.data.threat;

import java.util.List;

public class UrlThreatAnalysisResponse {
    private String url;
    private String status;
    private String reason;
    private int heuristicScore;
    private List<String> signals;
    private boolean whitelisted;
    private String trustedProvider;

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
}
