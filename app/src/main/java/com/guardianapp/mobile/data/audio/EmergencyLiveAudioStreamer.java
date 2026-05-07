package com.guardianapp.mobile.data.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

/**
 * Captures PCM audio and streams chunks in realtime via raw WebSocket.
 * Also writes PCM locally and converts to WAV on stop.
 */
public class EmergencyLiveAudioStreamer {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final OkHttpClient okHttpClient = new OkHttpClient();
    private AudioRecord audioRecord;
    private WebSocket webSocket;
    private Thread captureThread;
    private volatile boolean running;
    private long startedAtMillis;

    private File pcmFile;
    private File wavFile;

    public void start(Context context, String wsBaseUrl, String emergencyId, String protectedUserId, File cacheDir) throws IOException {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Missing RECORD_AUDIO permission");
        }

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBuffer, 4096);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IOException("No se pudo inicializar AudioRecord");
        }

        File dir = new File(cacheDir, "emergency-live-audio");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear carpeta de audio");
        }

        pcmFile = new File(dir, "emergency-" + emergencyId + "-" + System.currentTimeMillis() + ".pcm");
        wavFile = new File(dir, "emergency-" + emergencyId + "-" + System.currentTimeMillis() + ".wav");

        String wsUrl = wsBaseUrl + "/ws/emergency-audio?emergencyId=" + emergencyId + "&role=protected&userId=" + protectedUserId;
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = okHttpClient.newWebSocket(request, new okhttp3.WebSocketListener() {
        });

        running = true;
        startedAtMillis = System.currentTimeMillis();
        audioRecord.startRecording();

        captureThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
                while (running) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        webSocket.send(okio.ByteString.of(buffer, 0, read));
                        fos.write(buffer, 0, read);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "emergency-live-audio-capture");
        captureThread.start();
    }

    public RecordingResult stopAndBuildWav() {
        running = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }

        if (captureThread != null) {
            try {
                captureThread.join(800);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        if (webSocket != null) {
            webSocket.close(1000, "done");
            webSocket = null;
        }

        if (pcmFile == null || !pcmFile.exists()) {
            return null;
        }

        try {
            convertPcmToWav(pcmFile, wavFile, SAMPLE_RATE, 1, 16);
            long size = wavFile.exists() ? wavFile.length() : 0L;
            int durationSeconds = Math.max(1, (int) ((System.currentTimeMillis() - startedAtMillis) / 1000));
            return new RecordingResult(wavFile, durationSeconds, size);
        } catch (Exception e) {
            return null;
        }
    }

    private void convertPcmToWav(File pcm, File wav, int sampleRate, int channels, int bitsPerSample) throws IOException {
        byte[] pcmData = readAllBytes(pcm);
        int byteRate = sampleRate * channels * bitsPerSample / 8;

        try (FileOutputStream out = new FileOutputStream(wav)) {
            writeString(out, "RIFF");
            writeIntLE(out, 36 + pcmData.length);
            writeString(out, "WAVE");
            writeString(out, "fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, (short) 1);
            writeShortLE(out, (short) channels);
            writeIntLE(out, sampleRate);
            writeIntLE(out, byteRate);
            writeShortLE(out, (short) (channels * bitsPerSample / 8));
            writeShortLE(out, (short) bitsPerSample);
            writeString(out, "data");
            writeIntLE(out, pcmData.length);
            out.write(pcmData);
        }
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            long len = file.length();
            if (len > Integer.MAX_VALUE) {
                throw new IOException("File too large");
            }
            byte[] data = new byte[(int) len];
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset != data.length) {
                throw new IOException("Unexpected EOF");
            }
            return data;
        }
    }

    private void writeString(FileOutputStream out, String value) throws IOException {
        out.write(value.getBytes());
    }

    private void writeIntLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private void writeShortLE(FileOutputStream out, short value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    public boolean isRunning() {
        return running;
    }

    public static class RecordingResult {
        private final File file;
        private final int durationSeconds;
        private final long fileSizeBytes;

        public RecordingResult(File file, int durationSeconds, long fileSizeBytes) {
            this.file = file;
            this.durationSeconds = durationSeconds;
            this.fileSizeBytes = fileSizeBytes;
        }

        public File file() {
            return file;
        }

        public int durationSeconds() {
            return durationSeconds;
        }

        public long fileSizeBytes() {
            return fileSizeBytes;
        }
    }
}
