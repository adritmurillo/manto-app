package com.guardianapp.mobile.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.audio.EmergencyLiveAudioPlayer;

public class EmergencyLiveAudioService extends Service {

    public static final String ACTION_START = "com.guardianapp.mobile.action.EMERGENCY_AUDIO_START";
    public static final String ACTION_STOP = "com.guardianapp.mobile.action.EMERGENCY_AUDIO_STOP";
    public static final String EXTRA_WS_BASE_URL = "extra_ws_base_url";
    public static final String EXTRA_EMERGENCY_ID = "extra_emergency_id";
    public static final String EXTRA_HOST_ID = "extra_host_id";

    private static final String CHANNEL_ID = "emergency_audio_channel";
    private static final int NOTIFICATION_ID = 2201;

    private static EmergencyLiveAudioService instance;

    private final EmergencyLiveAudioPlayer player = new EmergencyLiveAudioPlayer();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            String wsBaseUrl = intent.getStringExtra(EXTRA_WS_BASE_URL);
            String emergencyId = intent.getStringExtra(EXTRA_EMERGENCY_ID);
            String hostId = intent.getStringExtra(EXTRA_HOST_ID);
            if (!player.isRunning() && wsBaseUrl != null && emergencyId != null && hostId != null) {
                startForeground(NOTIFICATION_ID, buildNotification());
                player.start(wsBaseUrl, emergencyId, hostId);
            }
        } else if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopPlayback();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopPlayback() {
        if (player.isRunning()) {
            player.stop();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Escucha en vivo activa")
                .setContentText("Reproduciendo audio de emergencia")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Audio",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void start(Context context, String wsBaseUrl, String emergencyId, String hostId) {
        Intent intent = new Intent(context, EmergencyLiveAudioService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_WS_BASE_URL, wsBaseUrl);
        intent.putExtra(EXTRA_EMERGENCY_ID, emergencyId);
        intent.putExtra(EXTRA_HOST_ID, hostId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, EmergencyLiveAudioService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isRunning() {
        return instance != null && instance.player.isRunning();
    }

    public static long getSilenceMillis() {
        if (instance == null) {
            return 0L;
        }
        return instance.player.getSilenceMillis();
    }
}
