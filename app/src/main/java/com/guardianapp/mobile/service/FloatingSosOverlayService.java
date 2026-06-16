package com.guardianapp.mobile.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.overlay.OverlayPermissionHelper;

public class FloatingSosOverlayService extends Service {

    private static final String TAG = "FloatingSosOverlay";
    private static final String CHANNEL_ID = "floating_sos_overlay";
    private static final int NOTIFICATION_ID = 3021;

    private static boolean running;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams overlayParams;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            attachOverlay();
            running = overlayView != null;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to start floating SOS overlay", ex);
            Toast.makeText(this, "No se pudo iniciar el boton flotante SOS", Toast.LENGTH_LONG).show();
            running = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Toast.makeText(this, "Permiso overlay no concedido", Toast.LENGTH_SHORT).show();
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (overlayView == null) {
            attachOverlay();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        detachOverlay();
        running = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void attachOverlay() {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            return;
        }
        if (overlayView != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        TextView button = new TextView(this);
        button.setText("SOS");
        button.setTextSize(18f);
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        int size = dp(72);
        button.setWidth(size);
        button.setHeight(size);
        button.setPadding(0, 0, 0, 0);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xFFD63D3D);
        background.setStroke(dp(2), 0x55FFFFFF);
        button.setBackground(background);
        button.setElevation(dp(8));
        button.setAlpha(0.96f);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        overlayParams = new WindowManager.LayoutParams(
                size,
                size,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = dp(18);
        overlayParams.y = dp(220);

        final float[] touchData = new float[4];
        final boolean[] dragged = {false};

        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchData[0] = overlayParams.x;
                    touchData[1] = overlayParams.y;
                    touchData[2] = event.getRawX();
                    touchData[3] = event.getRawY();
                    dragged[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    int nextX = (int) (touchData[0] - (event.getRawX() - touchData[2]));
                    int nextY = (int) (touchData[1] + (event.getRawY() - touchData[3]));
                    if (Math.abs(nextX - overlayParams.x) > 4 || Math.abs(nextY - overlayParams.y) > 4) {
                        dragged[0] = true;
                    }
                    overlayParams.x = nextX;
                    overlayParams.y = nextY;
                    windowManager.updateViewLayout(overlayView, overlayParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    return dragged[0];
                default:
                    return false;
            }
        });

        button.setOnClickListener(v ->
                Toast.makeText(this, "Manten presionado el boton SOS para enviar la alerta", Toast.LENGTH_SHORT).show()
        );
        button.setOnLongClickListener(v -> {
            Toast.makeText(this, "Activando SOS...", Toast.LENGTH_SHORT).show();
            EmergencyShortcutDispatcher.trigger(getApplicationContext());
            return true;
        });

        overlayView = button;
        try {
            windowManager.addView(overlayView, overlayParams);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to attach overlay view", ex);
            overlayView = null;
            Toast.makeText(this, "No se pudo mostrar el boton flotante", Toast.LENGTH_LONG).show();
        }
    }

    private void detachOverlay() {
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }
        overlayView = null;
        windowManager = null;
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SOS flotante activo")
                .setContentText("Manten presionado el boton flotante para activar la emergencia.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SOS Flotante",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Mantiene activo el boton SOS flotante");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
