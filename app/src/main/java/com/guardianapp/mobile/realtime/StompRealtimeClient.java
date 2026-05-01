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
        // Avoid leaking multiple sockets if connect() is called twice.
        disconnect();

        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                // Disable heartbeats. We don't implement STOMP heart-beat frames on the client side,
                // and advertising them can lead to server-side timeouts/transport errors.
                String connectFrame = "CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000";
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

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                subscribed = false;
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                subscribed = false;
            }
        });
    }

    public void disconnect() {
        subscribed = false;
        if (webSocket != null) {
            // Best-effort graceful shutdown on the broker side.
            webSocket.send("DISCONNECT\n\n\u0000");
            webSocket.close(1000, "bye");
            webSocket = null;
        }
    }
}
