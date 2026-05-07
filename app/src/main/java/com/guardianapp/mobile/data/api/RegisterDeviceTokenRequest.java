package com.guardianapp.mobile.data.api;

public class RegisterDeviceTokenRequest {
    private final String token;
    private final String platform;

    public RegisterDeviceTokenRequest(String token, String platform) {
        this.token = token;
        this.platform = platform;
    }
}
