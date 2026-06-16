package com.guardianapp.mobile.sms;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.ui.protecteduser.ProtectedSmsInboxActivity;
import com.guardianapp.mobile.ui.security.SecurityAnalysisItem;

public final class SmsNotificationHelper {

    private static final String TAG = "SmsNotificationHelper";
    private static final String CHANNEL_ID = "manto_sms";
    private static final String CHANNEL_NAME = "Mensajes SMS";

    private SmsNotificationHelper() {
    }

    public static void showIncomingSmsNotification(Context context, SecurityAnalysisItem item) {
        if (context == null || item == null) {
            return;
        }

        createNotificationChannelIfNeeded(context);

        String title;
        if (item.isBlocked()) {
            title = "SMS sospechoso detectado";
        } else if (item.isWhitelisted()) {
            title = "SMS seguro recibido";
        } else {
            title = "Nuevo SMS recibido";
        }

        String sender = item.getSender() == null || item.getSender().isBlank()
                ? "Desconocido"
                : item.getSender().trim();
        String message = item.getMessage() == null || item.getMessage().isBlank()
                ? "Abre Manto para revisar el mensaje"
                : item.getMessage().trim();

        Intent intent = new Intent(context, ProtectedSmsInboxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(sender + ": " + message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(sender + ": " + message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            //noinspection MissingPermission
            NotificationManagerCompat.from(context)
                    .notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException se) {
            Log.w(TAG, "Missing notification permission; skipping SMS notification", se);
        }
    }

    public static void showAnalysisErrorNotification(Context context, String sender, String message) {
        if (context == null) {
            return;
        }

        SecurityAnalysisItem fallbackItem = new SecurityAnalysisItem(
                System.currentTimeMillis(),
                SecurityAnalysisItem.CHANNEL_SMS,
                sender == null ? "Desconocido" : sender,
                message == null ? "No se pudo analizar el SMS recibido." : message,
                null,
                "ERROR",
                "No se pudo analizar el SMS recibido.",
                false,
                false,
                null,
                SecurityAnalysisItem.BUCKET_INBOX,
                SecurityAnalysisItem.REVIEW_LOCAL_ONLY
        );
        showIncomingSmsNotification(context, fallbackItem);
    }

    private static void createNotificationChannelIfNeeded(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notificaciones de SMS recibidos y analizados por Manto");

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
