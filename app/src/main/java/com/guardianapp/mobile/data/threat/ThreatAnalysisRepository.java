package com.guardianapp.mobile.data.threat;

import com.guardianapp.mobile.data.api.RetrofitClient;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ThreatAnalysisRepository {

    public interface CallbackResult {
        void onResult(ThreatDecision decision);

        void onError(Throwable error);
    }

    public static class ThreatDecision {
        private final boolean blocked;
        private final String reason;
        private final String globalStatus;
        private final String urlStatus;
        private final boolean whitelisted;
        private final String trustedProvider;
        private final String analyzedUrl;

        public ThreatDecision(boolean blocked,
                              String reason,
                              String globalStatus,
                              String urlStatus,
                              boolean whitelisted,
                              String trustedProvider,
                              String analyzedUrl) {
            this.blocked = blocked;
            this.reason = reason;
            this.globalStatus = globalStatus;
            this.urlStatus = urlStatus;
            this.whitelisted = whitelisted;
            this.trustedProvider = trustedProvider;
            this.analyzedUrl = analyzedUrl;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public String getReason() {
            return reason;
        }

        public String getGlobalStatus() {
            return globalStatus;
        }

        public String getUrlStatus() {
            return urlStatus;
        }

        public boolean isWhitelisted() {
            return whitelisted;
        }

        public String getTrustedProvider() {
            return trustedProvider;
        }

        public String getAnalyzedUrl() {
            return analyzedUrl;
        }
    }

    public void analyzeUrl(String url, CallbackResult callback) {
        analyzeMessageAndUrl("", url, "android-app", callback);
    }

    public void analyzeMessageAndUrl(String message, String url, String sender, CallbackResult callback) {
        AnalyzeThreatRequest request = new AnalyzeThreatRequest(
                message == null ? "" : message,
                Collections.singletonList(url),
                sender == null ? "android-app" : sender
        );
        executeAnalyze(request, callback);
    }

    private void executeAnalyze(AnalyzeThreatRequest request, CallbackResult callback) {
        RetrofitClient.getApiService().analyzeThreat(request).enqueue(new Callback<ThreatAnalysisResponse>() {
            @Override
            public void onResponse(Call<ThreatAnalysisResponse> call, Response<ThreatAnalysisResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(new IllegalStateException("Threat API error: " + response.code()));
                    return;
                }
                callback.onResult(buildDecision(response.body()));
            }

            @Override
            public void onFailure(Call<ThreatAnalysisResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    private ThreatDecision buildDecision(ThreatAnalysisResponse response) {
        String globalStatus = normalize(response.getStatus());
        UrlThreatAnalysisResponse firstUrl = first(response.getUrlResults());
        String urlStatus = firstUrl == null ? "ERROR" : normalize(firstUrl.getStatus());
        String reason = firstUrl == null ? "No threat details returned by backend." : firstUrl.getReason();

        boolean blockForGlobalStatus = isRiskStatus(globalStatus);
        boolean blockForUrlStatus = isUnsafeUrlStatus(urlStatus);
        boolean shouldBlock = blockForGlobalStatus || blockForUrlStatus;

        if (reason == null || reason.isBlank()) {
            reason = shouldBlock
                    ? "Threat analysis marked this URL as unsafe."
                    : "No threats detected.";
        }

        return new ThreatDecision(
                shouldBlock,
                reason,
                globalStatus,
                urlStatus,
                firstUrl != null && firstUrl.isWhitelisted(),
                firstUrl == null ? null : firstUrl.getTrustedProvider(),
                firstUrl == null ? null : firstUrl.getUrl()
        );
    }

    private UrlThreatAnalysisResponse first(List<UrlThreatAnalysisResponse> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    private String normalize(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isRiskStatus(String status) {
        return "FRAUD_RISK".equals(status)
                || "MALWARE_RISK".equals(status)
                || "UNWANTED_RISK".equals(status)
                || "SUSPICIOUS_RISK".equals(status)
                || "PARTIAL_ANALYSIS".equals(status)
                || "ANALYSIS_ERROR".equals(status);
    }

    private boolean isUnsafeUrlStatus(String status) {
        return "PHISHING".equals(status)
                || "MALWARE".equals(status)
                || "UNWANTED".equals(status)
                || "SUSPICIOUS".equals(status)
                || "ERROR".equals(status);
    }
}
