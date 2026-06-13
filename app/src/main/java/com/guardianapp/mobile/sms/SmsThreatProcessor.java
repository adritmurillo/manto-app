package com.guardianapp.mobile.sms;

import android.content.Context;
import android.util.Patterns;

import com.guardianapp.mobile.data.api.CreateSmsThreatAlertRequest;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.SmsThreatAlertResponse;
import com.guardianapp.mobile.data.threat.ThreatAnalysisRepository;
import com.guardianapp.mobile.ui.protecteduser.ProtectedSessionStore;
import com.guardianapp.mobile.ui.security.SecurityAnalysisItem;
import com.guardianapp.mobile.ui.security.SecurityAnalysisStore;

import java.util.Locale;
import java.util.regex.Matcher;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class SmsThreatProcessor {

    public interface ResultCallback {
        void onProcessed(ProcessResult result);

        void onError(Throwable error);
    }

    public static final class ProcessResult {
        private final SecurityAnalysisItem item;
        private final boolean analyzedWithBackend;
        private final boolean hostAlertRequested;

        public ProcessResult(SecurityAnalysisItem item,
                             boolean analyzedWithBackend,
                             boolean hostAlertRequested) {
            this.item = item;
            this.analyzedWithBackend = analyzedWithBackend;
            this.hostAlertRequested = hostAlertRequested;
        }

        public SecurityAnalysisItem getItem() {
            return item;
        }

        public boolean isAnalyzedWithBackend() {
            return analyzedWithBackend;
        }

        public boolean isHostAlertRequested() {
            return hostAlertRequested;
        }
    }

    private SmsThreatProcessor() {
    }

    public static void processIncomingMessage(Context context,
                                              String sender,
                                              String message,
                                              String providedUrl,
                                              ResultCallback callback) {
        if (context == null) {
            callback.onError(new IllegalArgumentException("Context is required"));
            return;
        }
        SecurityAnalysisStore.init(context);

        String normalizedSender = sender == null || sender.isBlank() ? "Desconocido" : sender.trim();
        String normalizedMessage = message == null ? "" : message.trim();
        String detectedUrl = firstNonBlank(providedUrl, extractFirstUrl(normalizedMessage));

        if (detectedUrl == null) {
            SecurityAnalysisItem item = new SecurityAnalysisItem(
                    System.currentTimeMillis(),
                    SecurityAnalysisItem.CHANNEL_SMS,
                    normalizedSender,
                    normalizedMessage,
                    null,
                    "NO_URL",
                    "SMS recibido sin enlaces detectables. No se envio al backend.",
                    false,
                    false,
                    null,
                    SecurityAnalysisItem.BUCKET_INBOX,
                    SecurityAnalysisItem.REVIEW_LOCAL_ONLY
            );
            SecurityAnalysisStore.add(item);
            callback.onProcessed(new ProcessResult(item, false, false));
            return;
        }

        new ThreatAnalysisRepository().analyzeMessageAndUrl(
                normalizedMessage,
                detectedUrl,
                normalizedSender,
                new ThreatAnalysisRepository.CallbackResult() {
                    @Override
                    public void onResult(ThreatAnalysisRepository.ThreatDecision decision) {
                        boolean hostAlertRequested = shouldRequestHostAlert(context, decision);
                        String bucket = decision.isBlocked() && !decision.isWhitelisted()
                                ? SecurityAnalysisItem.BUCKET_QUARANTINE
                                : SecurityAnalysisItem.BUCKET_INBOX;
                        String reviewState = deriveReviewState(decision, hostAlertRequested);
                        SecurityAnalysisItem item = new SecurityAnalysisItem(
                                System.currentTimeMillis(),
                                SecurityAnalysisItem.CHANNEL_SMS,
                                normalizedSender,
                                normalizedMessage,
                                decision.getAnalyzedUrl() != null ? decision.getAnalyzedUrl() : detectedUrl,
                                decision.getUrlStatus(),
                                decision.getReason(),
                                decision.isBlocked(),
                                decision.isWhitelisted(),
                                decision.getTrustedProvider(),
                                bucket,
                                reviewState
                        );
                        SecurityAnalysisStore.add(item);
                        boolean requested = requestHostAlertIfNeeded(
                                context,
                                normalizedSender,
                                normalizedMessage,
                                detectedUrl,
                                decision
                        );
                        callback.onProcessed(new ProcessResult(item, true, requested || hostAlertRequested));
                    }

                    @Override
                    public void onError(Throwable error) {
                        callback.onError(error);
                    }
                }
        );
    }

    private static boolean requestHostAlertIfNeeded(Context context,
                                                    String sender,
                                                    String message,
                                                    String fallbackUrl,
                                                    ThreatAnalysisRepository.ThreatDecision decision) {
        if (!shouldRequestHostAlert(context, decision)) {
            return false;
        }
        String linkId = ProtectedSessionStore.getLinkId(context);
        String protectedId = ProtectedSessionStore.getProtectedId(context);

        String analyzedUrl = decision.getAnalyzedUrl() != null ? decision.getAnalyzedUrl() : fallbackUrl;
        CreateSmsThreatAlertRequest request = new CreateSmsThreatAlertRequest(
                linkId,
                protectedId,
                sender,
                message,
                analyzedUrl == null ? "" : analyzedUrl,
                normalizeThreatStatus(decision),
                decision.getReason()
        );

        RetrofitClient.getApiService().createSmsThreatAlert(request).enqueue(new Callback<SmsThreatAlertResponse>() {
            @Override
            public void onResponse(Call<SmsThreatAlertResponse> call, Response<SmsThreatAlertResponse> response) {
            }

            @Override
            public void onFailure(Call<SmsThreatAlertResponse> call, Throwable t) {
            }
        });
        return true;
    }

    private static boolean shouldRequestHostAlert(Context context,
                                                  ThreatAnalysisRepository.ThreatDecision decision) {
        if (context == null || decision == null || !decision.isBlocked()) {
            return false;
        }
        String linkId = ProtectedSessionStore.getLinkId(context);
        String protectedId = ProtectedSessionStore.getProtectedId(context);
        return linkId != null && !linkId.isBlank()
                && protectedId != null && !protectedId.isBlank();
    }

    private static String extractFirstUrl(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = Patterns.WEB_URL.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return candidate;
            }
            return "https://" + candidate;
        }
        return null;
    }

    private static String normalizeThreatStatus(ThreatAnalysisRepository.ThreatDecision decision) {
        String value = normalize(decision.getUrlStatus());
        if ("PHISHING".equals(value)
                || "MALWARE".equals(value)
                || "UNWANTED".equals(value)
                || "SUSPICIOUS".equals(value)
                || "ERROR".equals(value)) {
            return value;
        }
        return decision.isBlocked() ? "SUSPICIOUS" : "SAFE";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static String deriveReviewState(ThreatAnalysisRepository.ThreatDecision decision,
                                            boolean hostAlertRequested) {
        if (decision == null) {
            return SecurityAnalysisItem.REVIEW_LOCAL_ONLY;
        }
        if (decision.isWhitelisted()) {
            return SecurityAnalysisItem.REVIEW_HOST_ALLOWED;
        }
        if (decision.isBlocked()) {
            return hostAlertRequested
                    ? SecurityAnalysisItem.REVIEW_PENDING_HOST
                    : SecurityAnalysisItem.REVIEW_LOCAL_BLOCKED;
        }
        return SecurityAnalysisItem.REVIEW_LOCAL_ONLY;
    }
}
