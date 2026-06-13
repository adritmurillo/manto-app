package com.guardianapp.mobile.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.EmergencyAlertResponse;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.TriggerEmergencyAlertRequest;
import com.guardianapp.mobile.ui.protecteduser.ProtectedSessionStore;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class EmergencyShortcutDispatcher {

    private static final String CHANNEL_ID = "emergency_shortcut_channel";
    private static final long TRIGGER_COOLDOWN_MS = 15000L;

    private static long lastTriggerAt;

    private EmergencyShortcutDispatcher() {
    }

    public static void trigger(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTriggerAt < TRIGGER_COOLDOWN_MS) {
            return;
        }
        lastTriggerAt = now;

        String protectedId = ProtectedSessionStore.getProtectedId(appContext);
        String storedLinkId = ProtectedSessionStore.getLinkId(appContext);
        if (protectedId == null || protectedId.isBlank()) {
            showNotification(appContext, "SOS no disponible", "Abre Manto una vez como protegido para habilitar el atajo.");
            return;
        }

        if (!hasLocationPermission(appContext)) {
            showNotification(appContext, "Falta permiso", "Otorga permiso de ubicacion a Manto para usar el atajo SOS.");
            return;
        }

        resolveActiveLinkAndTrigger(appContext, protectedId, storedLinkId);
    }

    private static void resolveActiveLinkAndTrigger(Context context, String protectedId, String fallbackLinkId) {
        RetrofitClient.getApiService().getMyLinks(protectedId).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                String activeLinkId = fallbackLinkId;
                if (response.isSuccessful() && response.body() != null) {
                    for (LinkResponse link : response.body()) {
                        if (link != null
                                && protectedId.equals(link.getProtectedUserId())
                                && "ACTIVE".equals(link.getStatus())) {
                            activeLinkId = link.getId();
                            break;
                        }
                    }
                }

                if (activeLinkId == null || activeLinkId.isBlank()) {
                    showNotification(context, "Sin vinculo activo", "No se encontro un vinculo activo para activar la emergencia.");
                    return;
                }

                ProtectedSessionStore.save(context, protectedId, activeLinkId);
                requestLocationAndTrigger(context, protectedId, activeLinkId);
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                if (fallbackLinkId == null || fallbackLinkId.isBlank()) {
                    showNotification(context, "Error de red", "No se pudo validar el vinculo para la alerta SOS.");
                    return;
                }
                requestLocationAndTrigger(context, protectedId, fallbackLinkId);
            }
        });
    }

    private static void requestLocationAndTrigger(Context context, String protectedId, String linkId) {
        Location lastKnown = getBestLastKnownLocation(context);
        if (lastKnown != null) {
            sendEmergency(context, protectedId, linkId, lastKnown);
            return;
        }

        requestSingleLocation(context, new LocationResultCallback() {
            @Override
            public void onLocation(Location location) {
                sendEmergency(context, protectedId, linkId, location);
            }

            @Override
            public void onError() {
                showNotification(context, "Ubicacion no disponible", "No se pudo obtener tu ubicacion para la alerta SOS.");
            }
        });
    }

    private static void sendEmergency(Context context, String protectedId, String linkId, Location location) {
        TriggerEmergencyAlertRequest request = new TriggerEmergencyAlertRequest(
                linkId,
                protectedId,
                location.getLatitude(),
                location.getLongitude()
        );

        RetrofitClient.getApiService().triggerEmergency(request).enqueue(new Callback<EmergencyAlertResponse>() {
            @Override
            public void onResponse(Call<EmergencyAlertResponse> call, Response<EmergencyAlertResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showNotification(context, "Emergencia activada", "Tus anfitriones fueron notificados.");
                } else {
                    showNotification(context, "Error SOS", "No se pudo activar la emergencia.");
                }
            }

            @Override
            public void onFailure(Call<EmergencyAlertResponse> call, Throwable t) {
                showNotification(context, "Error de red", "No se pudo enviar la alerta SOS.");
            }
        });
    }

    private static void requestSingleLocation(Context context, LocationResultCallback callback) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onError();
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            callback.onError();
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] completed = {false};
        LocationListener[] holder = new LocationListener[1];

        Runnable timeout = () -> {
            if (completed[0]) {
                return;
            }
            completed[0] = true;
            if (holder[0] != null) {
                try {
                    locationManager.removeUpdates(holder[0]);
                } catch (SecurityException ignored) {
                }
            }
            callback.onError();
        };

        holder[0] = location -> {
            if (completed[0]) {
                return;
            }
            completed[0] = true;
            handler.removeCallbacks(timeout);
            try {
                locationManager.removeUpdates(holder[0]);
            } catch (SecurityException ignored) {
            }
            callback.onLocation(location);
        };

        try {
            if (gpsEnabled) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, holder[0], Looper.getMainLooper());
            }
            if (networkEnabled) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, holder[0], Looper.getMainLooper());
            }
            handler.postDelayed(timeout, 10000L);
        } catch (SecurityException ex) {
            callback.onError();
        } catch (IllegalArgumentException ex) {
            callback.onError();
        }
    }

    private static Location getBestLastKnownLocation(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !hasLocationPermission(context)) {
            return null;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            return null;
        }

        Location gpsLocation = gpsEnabled ? locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
        Location networkLocation = networkEnabled ? locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) : null;

        if (gpsLocation == null) {
            return networkLocation;
        }
        if (networkLocation == null) {
            return gpsLocation;
        }
        return gpsLocation.getTime() >= networkLocation.getTime() ? gpsLocation : networkLocation;
    }

    private static boolean hasLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static void showNotification(Context context, String title, String body) {
        createNotificationChannelIfNeeded(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                    .notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private static void createNotificationChannelIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SOS Shortcut",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Estado del atajo SOS en segundo plano");

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private interface LocationResultCallback {
        void onLocation(Location location);
        void onError();
    }
}
