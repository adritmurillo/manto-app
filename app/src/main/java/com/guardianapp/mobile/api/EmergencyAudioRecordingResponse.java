package com.guardianapp.mobile.api;

public class EmergencyAudioRecordingResponse {
    private String id;
    private String emergencyAlertId;
    private String storageProvider;
    private String status;
    private String storageFileId;
    private String playbackUrl;
    private Integer durationSeconds;
    private Long fileSizeBytes;

    public String getId() {
        return id;
    }

    public String getEmergencyAlertId() {
        return emergencyAlertId;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getStatus() {
        return status;
    }

    public String getStorageFileId() {
        return storageFileId;
    }

    public String getPlaybackUrl() {
        return playbackUrl;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }
}
