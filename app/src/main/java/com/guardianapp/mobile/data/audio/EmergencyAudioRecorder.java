package com.guardianapp.mobile.data.audio;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.io.IOException;

/**
 * Records emergency audio to a local M4A file.
 */
public class EmergencyAudioRecorder {

    private final Context context;
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private long startedAtMillis;

    public EmergencyAudioRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public File start(String emergencyId) throws IOException {
        File dir = new File(context.getCacheDir(), "emergency-audio");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear carpeta temporal de audio");
        }

        outputFile = new File(dir, "emergency-" + emergencyId + "-" + System.currentTimeMillis() + ".m4a");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaRecorder = new MediaRecorder(context);
        } else {
            mediaRecorder = new MediaRecorder();
        }

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.prepare();
        mediaRecorder.start();
        startedAtMillis = System.currentTimeMillis();

        return outputFile;
    }

    public RecordingResult stop() {
        if (mediaRecorder == null) {
            return null;
        }

        try {
            mediaRecorder.stop();
        } catch (RuntimeException ignored) {
        }
        mediaRecorder.release();
        mediaRecorder = null;

        int durationSeconds = (int) Math.max(1, (System.currentTimeMillis() - startedAtMillis) / 1000);
        long fileSize = outputFile != null && outputFile.exists() ? outputFile.length() : 0L;
        return new RecordingResult(outputFile, durationSeconds, fileSize);
    }

    public boolean isRecording() {
        return mediaRecorder != null;
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
