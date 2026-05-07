package com.guardianapp.mobile.data.api;

public class ConfirmLinkRequest {
    // Ya no lo enviamos como "pin", lo enviamos como "connectionCode"
    private String connectionCode;

    public ConfirmLinkRequest(String connectionCode) {
        this.connectionCode = connectionCode;
    }
}
