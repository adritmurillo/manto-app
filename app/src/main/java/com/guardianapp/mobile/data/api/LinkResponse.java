package com.guardianapp.mobile.data.api;

import com.google.gson.annotations.SerializedName;

public class LinkResponse {
    private String id;
    private String hostId;
    // Backend uses "protectedId"; keep getter name stable in app.
    @SerializedName("protectedId")
    private String protectedUserId;
    private String status;
    private String connectionCode; // ¡NUEVO! Coincide con tu base de datos

    public String getId() { return id; }
    public String getHostId() { return hostId; }
    public String getProtectedUserId() { return protectedUserId; }
    public String getStatus() { return status; }
    public String getConnectionCode() { return connectionCode; }
}
