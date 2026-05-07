package com.guardianapp.mobile.data.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Receives realtime PCM chunks over raw WebSocket and plays them via AudioTrack.
 */
public class EmergencyLiveAudioPlayer {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final OkHttpClient okHttpClient = new OkHttpClient();
    private AudioTrack audioTrack;
    private WebSocket webSocket;
    private volatile boolean running;
    private volatile long lastChunkAtMillis;

    public void start(String wsBaseUrl, String emergencyId, String hostUserId) {
        if (running) {
            return;
        }

        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
        int bufferSize = Math.max(minBuffer, 4096);

        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(ENCODING)
                        .setChannelMask(CHANNEL_OUT)
                        .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioTrack.WRITE_BLOCKING
        );
        audioTrack.play();

        String wsUrl = wsBaseUrl + "/ws/emergency-audio?emergencyId=" + emergencyId + "&role=host&userId=" + hostUserId;
        Request request = new Request.Builder().url(wsUrl).build();
        running = true;
        lastChunkAtMillis = System.currentTimeMillis();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (!running || audioTrack == null) {
                    return;
                }
                byte[] chunk = bytes.toByteArray();
                lastChunkAtMillis = System.currentTimeMillis();
                audioTrack.write(chunk, 0, chunk.length);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // ignore control text frames
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                stop();
            }
        });
    }

    public void stop() {
        running = false;

        if (webSocket != null) {
            webSocket.close(1000, "done");
            webSocket = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public long getSilenceMillis() {
        return Math.max(0L, System.currentTimeMillis() - lastChunkAtMillis);
    }
}
