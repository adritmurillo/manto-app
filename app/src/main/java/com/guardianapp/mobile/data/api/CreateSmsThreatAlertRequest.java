package com.guardianapp.mobile.data.api;

public class CreateSmsThreatAlertRequest {
    private final String linkId;
    private final String protectedUserId;
    private final String sender;
    private final String messageExcerpt;
    private final String detectedUrl;
    private final String analysisStatus;
    private final String analysisReason;

    public CreateSmsThreatAlertRequest(String linkId,
                                       String protectedUserId,
                                       String sender,
                                       String messageExcerpt,
                                       String detectedUrl,
                                       String analysisStatus,
                                       String analysisReason) {
        this.linkId = linkId;
        this.protectedUserId = protectedUserId;
        this.sender = sender;
        this.messageExcerpt = messageExcerpt;
        this.detectedUrl = detectedUrl;
        this.analysisStatus = analysisStatus;
        this.analysisReason = analysisReason;
    }
}
