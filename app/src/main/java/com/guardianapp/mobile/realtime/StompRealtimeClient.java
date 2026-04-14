package com.guardianapp.mobile.realtime;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.UUID;

/**
 * Minimal STOMP-over-WebSocket client for server push events.
 */
public class StompRealtimeClient {

    public interface EventListener {
        void onEvent(String body);
        void onConnected();
    }

    private final OkHttpClient okHttpClient = new OkHttpClient();
    private WebSocket webSocket;
    private boolean subscribed;

    public void connect(String wsUrl, String topic, EventListener listener) {
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                String connectFrame = "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\u0000";
                webSocket.send(connectFrame);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (!subscribed && text.startsWith("CONNECTED")) {
                    String subscribeFrame = "SUBSCRIBE\nid:" + UUID.randomUUID() + "\ndestination:" + topic + "\n\n\u0000";
                    webSocket.send(subscribeFrame);
                    subscribed = true;
                    listener.onConnected();
                    return;
                }
                if (text.startsWith("MESSAGE")) {
                    int bodyStart = text.indexOf("\n\n");
                    if (bodyStart >= 0) {
                        String body = text.substring(bodyStart + 2).replace("\u0000", "").trim();
                        listener.onEvent(body);
                    }
                }
            }
        });
    }

    public void disconnect() {
        subscribed = false;
        if (webSocket != null) {
            webSocket.close(1000, "bye");
            webSocket = null;
        }
    }
}
