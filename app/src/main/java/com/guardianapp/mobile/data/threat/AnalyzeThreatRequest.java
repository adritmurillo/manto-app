package com.guardianapp.mobile.data.threat;

import java.util.List;

public class AnalyzeThreatRequest {
    private final String message;
    private final List<String> urls;
    private final String sender;

    public AnalyzeThreatRequest(String message, List<String> urls, String sender) {
        this.message = message;
        this.urls = urls;
        this.sender = sender;
    }
}
