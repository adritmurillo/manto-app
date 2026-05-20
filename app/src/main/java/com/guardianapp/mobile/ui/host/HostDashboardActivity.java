package com.guardianapp.mobile.ui.host;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.location.Location;
import android.location.LocationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.view.LayoutInflater;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import android.media.MediaPlayer;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.firebase.auth.FirebaseAuth;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.AlertResponse;
import com.guardianapp.mobile.data.api.EmergencyAlertResponse;
import com.guardianapp.mobile.data.api.EmergencyAudioRecordingResponse;
import com.guardianapp.mobile.data.api.ResolveEmergencyAlertRequest;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.api.ResolveSmsThreatAlertRequest;
import com.guardianapp.mobile.data.api.ResolveAlertRequest;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.SmsThreatAlertResponse;
import com.guardianapp.mobile.data.realtime.StompRealtimeClient;
import com.guardianapp.mobile.service.EmergencyLiveAudioService;
import com.guardianapp.mobile.ui.main.MainActivity;
import com.guardianapp.mobile.ui.host.FamilyCircleActivity;
import com.guardianapp.mobile.ui.security.LinkShieldActivity;
import com.guardianapp.mobile.ui.security.SecurityMirrorActivity;

import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HostDashboardActivity extends AppCompatActivity {

    private static final int HOST_LOCATION_PERMISSION_REQUEST_CODE = 1401;

    private String miIdAnfitrion;
    private Handler pollingHandler;
    private Runnable pollingRunnable;

    private Button btnRefreshStatus;
    private boolean isAlertShowing = false;
    private TextView tvEmergencyHistoryEmpty;
    private RecyclerView rvEmergencyHistory;
    private EmergencyHistoryAdapter emergencyHistoryAdapter;
    private LinearLayout layoutEmergencyBanner;
    private TextView tvEmergencyBannerText;
    private Button btnOpenEmergencyBanner;
    private Button btnMuteEmergencyAudio;
    private EmergencyAlertResponse lastEmergencyAlert;
    private String dismissedEmergencyId;
    private boolean liveAudioMuted;
    private MediaPlayer emergencyAudioPlayer;
    private AlertDialog emergencyAudioDialog;
    private final StompRealtimeClient linkRealtimeClient = new StompRealtimeClient();
    private final StompRealtimeClient emergencyRealtimeClient = new StompRealtimeClient();
    private final Handler audioHealthHandler = new Handler(Looper.getMainLooper());
    private Runnable audioHealthRunnable;
    private String pendingMapEmergencyId;
    private EmergencyAlertResponse pendingMapEmergency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_dashboard);

        miIdAnfitrion = getIntent().getStringExtra("HOST_ID");

        // Enlazamos las vistas
        btnRefreshStatus = findViewById(R.id.btnRefreshStatus);
        tvEmergencyHistoryEmpty = findViewById(R.id.tvEmergencyHistoryEmpty);
        rvEmergencyHistory = findViewById(R.id.rvEmergencyHistory);
        layoutEmergencyBanner = findViewById(R.id.layoutEmergencyBanner);
        tvEmergencyBannerText = findViewById(R.id.tvEmergencyBannerText);
        btnOpenEmergencyBanner = findViewById(R.id.btnOpenEmergencyBanner);
        btnMuteEmergencyAudio = findViewById(R.id.btnMuteEmergencyAudio);
        Button btnOpenLinkShieldDashboard = findViewById(R.id.btnOpenLinkShieldDashboard);
        Button btnOpenSecurityMirrorDashboard = findViewById(R.id.btnOpenSecurityMirrorDashboard);
        TextView tvLogout = findViewById(R.id.tvLogoutHost);
        BottomNavigationView bottomNavHost = findViewById(R.id.bottomNavHost);

        // Botón para actualizar manualmente la vista
        btnRefreshStatus.setOnClickListener(v -> {
            Toast.makeText(this, "Actualizando estado...", Toast.LENGTH_SHORT).show();
            loadLinkStatus();
            loadEmergencyHistory();
        });

        btnOpenEmergencyBanner.setOnClickListener(v -> {
            if (lastEmergencyAlert != null) {
                showEmergencyPopup(lastEmergencyAlert);
            }
        });

        btnMuteEmergencyAudio.setOnClickListener(v -> toggleEmergencyAudio());


        btnOpenLinkShieldDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(HostDashboardActivity.this, LinkShieldActivity.class);
            intent.putExtra("HOST_ID", miIdAnfitrion);
            startActivity(intent);
        });

        btnOpenSecurityMirrorDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(HostDashboardActivity.this, SecurityMirrorActivity.class);
            intent.putExtra("HOST_ID", miIdAnfitrion);
            startActivity(intent);
        });

        if (bottomNavHost != null) {
            bottomNavHost.setSelectedItemId(R.id.nav_home);
            bottomNavHost.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                }
                if (id == R.id.nav_security) {
                    Intent intent = new Intent(HostDashboardActivity.this, LinkShieldActivity.class);
                    intent.putExtra("HOST_ID", miIdAnfitrion);
                    startActivity(intent);
                    return true;
                }
                if (id == R.id.nav_family) {
                    Intent intent = new Intent(HostDashboardActivity.this, FamilyCircleActivity.class);
                    intent.putExtra("HOST_ID", miIdAnfitrion);
                    startActivity(intent);
                    return true;
                }
                if (id == R.id.nav_settings) {
                    Toast.makeText(this, "Ajustes aun no disponible", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }


        tvLogout.setOnClickListener(v -> {
            stopPolling();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 1. Cargamos el estado del vínculo al entrar a la pantalla
        loadLinkStatus();
        setupEmergencyHistory();
        loadEmergencyHistory();

        // 2. Iniciamos el radar para buscar alertas de páginas falsas
        startPolling();

        connectRealtime();
    }

    private void connectRealtime() {
        if (miIdAnfitrion == null || miIdAnfitrion.isBlank()) {
            return;
        }
        String wsUrl = RetrofitClient.getWebSocketUrl();
        String linkTopic = "/topic/host/" + miIdAnfitrion + "/links";
        linkRealtimeClient.connect(wsUrl, linkTopic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                runOnUiThread(HostDashboardActivity.this::loadLinkStatus);
            }

            @Override
            public void onConnected() {
            }
        });

        String emergencyTopic = "/topic/host/" + miIdAnfitrion + "/emergencies";
        emergencyRealtimeClient.connect(wsUrl, emergencyTopic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                runOnUiThread(() -> {
                    checkActiveEmergencies();
                    loadEmergencyHistory();
                });
            }

            @Override
            public void onConnected() {
            }
        });
    }

    private void startPolling() {
        pollingHandler = new Handler(Looper.getMainLooper());
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAlertShowing) {
                    checkPendingAlerts();
                    checkPendingSmsThreatAlerts();
                    checkActiveEmergencies();
                }
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    private void checkPendingAlerts() {
        if (miIdAnfitrion == null) return;

        RetrofitClient.getApiService().getPendingAlerts(miIdAnfitrion).enqueue(new Callback<List<AlertResponse>>() {
            @Override
            public void onResponse(Call<List<AlertResponse>> call, Response<List<AlertResponse>> response) {
                if (isAlertShowing) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    showAlertPopup(response.body().get(0));
                }
            }
            @Override
            public void onFailure(Call<List<AlertResponse>> call, Throwable t) {}
        });
    }

    private void checkPendingSmsThreatAlerts() {
        if (miIdAnfitrion == null || isAlertShowing) return;

        RetrofitClient.getApiService().getPendingSmsThreatAlerts(miIdAnfitrion).enqueue(new Callback<List<SmsThreatAlertResponse>>() {
            @Override
            public void onResponse(Call<List<SmsThreatAlertResponse>> call, Response<List<SmsThreatAlertResponse>> response) {
                if (isAlertShowing) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    showSmsThreatAlertPopup(response.body().get(0));
                }
            }

            @Override
            public void onFailure(Call<List<SmsThreatAlertResponse>> call, Throwable t) {
            }
        });
    }

    private void showAlertPopup(AlertResponse alert) {
        isAlertShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ ¡ALERTA DE SEGURIDAD!");
        builder.setMessage("Tu protegido intentó entrar a un sitio sospechoso:\n" + alert.getSuspiciousUrl());
        builder.setCancelable(false);

        builder.setPositiveButton("Mantener Bloqueado", (dialog, which) ->
                resolveAlert(alert.getId(), false, "Bloqueado")
        );

        builder.setNegativeButton("Es seguro, permitir", (dialog, which) ->
                resolveAlert(alert.getId(), true, "Verificado")
        );

        builder.show();
    }

    private void resolveAlert(String alertId, boolean allow, String note) {
        ResolveAlertRequest request = new ResolveAlertRequest(miIdAnfitrion, allow, note);
        RetrofitClient.getApiService().resolveAlert(alertId, request).enqueue(new Callback<AlertResponse>() {
            @Override
            public void onResponse(Call<AlertResponse> call, Response<AlertResponse> response) {
                isAlertShowing = false;
            }
            @Override
            public void onFailure(Call<AlertResponse> call, Throwable t) {
                isAlertShowing = false; // Igual liberamos para no trabar el app
            }
        });
    }

    private void showSmsThreatAlertPopup(SmsThreatAlertResponse alert) {
        isAlertShowing = true;
        String status = alert.getAnalysisStatus() == null ? "SUSPICIOUS" : alert.getAnalysisStatus();
        String message = "Tu protegido recibio un SMS sospechoso.\n\n"
                + "Remitente: " + safeText(alert.getSender()) + "\n"
                + "Estado: " + status + "\n"
                + "URL: " + safeText(alert.getDetectedUrl()) + "\n\n"
                + "Mensaje:\n" + safeText(alert.getMessageExcerpt());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Alerta SMS de Phishing");
        builder.setMessage(message);
        builder.setCancelable(false);

        builder.setPositiveButton("Mantener Bloqueado", (dialog, which) ->
                resolveSmsThreatAlert(alert.getId(), false, "Bloqueado por anfitrion")
        );
        builder.setNegativeButton("Permitir", (dialog, which) ->
                resolveSmsThreatAlert(alert.getId(), true, "Marcado seguro por anfitrion")
        );

        builder.show();
    }

    private void resolveSmsThreatAlert(String alertId, boolean allow, String note) {
        ResolveSmsThreatAlertRequest request = new ResolveSmsThreatAlertRequest(miIdAnfitrion, allow, note);
        RetrofitClient.getApiService().resolveSmsThreatAlert(alertId, request)
                .enqueue(new Callback<SmsThreatAlertResponse>() {
                    @Override
                    public void onResponse(Call<SmsThreatAlertResponse> call, Response<SmsThreatAlertResponse> response) {
                        isAlertShowing = false;
                    }

                    @Override
                    public void onFailure(Call<SmsThreatAlertResponse> call, Throwable t) {
                        isAlertShowing = false;
                    }
                });
    }

    private void checkActiveEmergencies() {
        if (miIdAnfitrion == null || isAlertShowing) return;

        RetrofitClient.getApiService().getActiveEmergencies(miIdAnfitrion)
                .enqueue(new Callback<List<EmergencyAlertResponse>>() {
                    @Override
                    public void onResponse(Call<List<EmergencyAlertResponse>> call, Response<List<EmergencyAlertResponse>> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                            dismissedEmergencyId = null;
                            lastEmergencyAlert = null;
                            hideEmergencyBanner();
                            return;
                        }
                        if (isAlertShowing) {
                            return;
                        }
                        EmergencyAlertResponse active = response.body().get(0);
                        if (active == null || active.getId() == null) {
                            return;
                        }
                        if (dismissedEmergencyId != null && dismissedEmergencyId.equals(active.getId())) {
                            lastEmergencyAlert = active;
                            showEmergencyBanner(active);
                            return;
                        }
                        dismissedEmergencyId = null;
                        liveAudioMuted = false;
                        showEmergencyPopup(active);
                    }

                    @Override
                    public void onFailure(Call<List<EmergencyAlertResponse>> call, Throwable t) {
                    }
                });
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void showEmergencyPopup(EmergencyAlertResponse emergency) {
        isAlertShowing = true;
        lastEmergencyAlert = emergency;
        dismissedEmergencyId = null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_emergency_actions, null);
        TextView tvMessage = dialogView.findViewById(R.id.tvEmergencyMessage);
        Button btnMap = dialogView.findViewById(R.id.btnEmergencyMap);
        Button btnFalseAlarm = dialogView.findViewById(R.id.btnEmergencyFalseAlarm);
        Button btnAllSafe = dialogView.findViewById(R.id.btnEmergencyAllSafe);
        Button btnPolice = dialogView.findViewById(R.id.btnEmergencyPolice);
        Button btnClose = dialogView.findViewById(R.id.btnEmergencyClose);

        tvMessage.setText(
                "Tu familiar activo el boton de emergencia.\n\n" +
                        "Ubicacion:\n" +
                        "Lat: " + emergency.getLatitude() + "\n" +
                        "Lon: " + emergency.getLongitude() + "\n\n" +
                        "Puedes resolver esta emergencia."
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnMap.setOnClickListener(v -> openEmergencyInMaps(emergency));
        btnFalseAlarm.setOnClickListener(v -> {
            dialog.dismiss();
            resolveEmergency(emergency.getId(), "FALSE_ALARM", "Marcada como falsa alarma");
        });
        btnAllSafe.setOnClickListener(v -> {
            dialog.dismiss();
            resolveEmergency(emergency.getId(), "ALL_SAFE", "Situacion controlada");
        });
        btnPolice.setOnClickListener(v -> {
            dialog.dismiss();
            resolveEmergency(emergency.getId(), "POLICE_SENT", "Se contacto a autoridades");
        });
        btnClose.setOnClickListener(v -> {
            dialog.dismiss();
            showEmergencyBanner(emergency);
            dismissedEmergencyId = emergency.getId();
        });

        dialog.setOnDismissListener(d -> isAlertShowing = false);
        dialog.show();
        fetchLatestEmergencyAudioAndOfferPlayback(emergency.getId());
        startEmergencyLiveListening(emergency.getId());
    }

    private void showEmergencyBanner(EmergencyAlertResponse emergency) {
        if (layoutEmergencyBanner == null || emergency == null) {
            return;
        }
        String label = "Emergencia activa";
        if (emergency.getProtectedUserId() != null && !emergency.getProtectedUserId().isBlank()) {
            label += " - Protegido " + emergency.getProtectedUserId();
        }
        tvEmergencyBannerText.setText(label);
        layoutEmergencyBanner.setVisibility(View.VISIBLE);
        ViewCompat.setElevation(layoutEmergencyBanner, 8f);
        updateMuteButtonState();
    }

    private void hideEmergencyBanner() {
        if (layoutEmergencyBanner != null) {
            layoutEmergencyBanner.setVisibility(View.GONE);
        }
    }

    private void toggleEmergencyAudio() {
        if (EmergencyLiveAudioService.isRunning()) {
            EmergencyLiveAudioService.stop(this);
            liveAudioMuted = true;
        } else if (lastEmergencyAlert != null && lastEmergencyAlert.getId() != null) {
            EmergencyLiveAudioService.start(this, RetrofitClient.getWebSocketBaseUrl(), lastEmergencyAlert.getId(), miIdAnfitrion);
            liveAudioMuted = false;
        }
        updateMuteButtonState();
    }

    private void updateMuteButtonState() {
        if (btnMuteEmergencyAudio == null) {
            return;
        }
        if (EmergencyLiveAudioService.isRunning()) {
            btnMuteEmergencyAudio.setText("Silenciar audio en vivo");
            return;
        }
        btnMuteEmergencyAudio.setText(liveAudioMuted ? "Activar audio en vivo" : "Activar audio en vivo");
    }

    private void openEmergencyInMaps(EmergencyAlertResponse emergency) {
        if (emergency == null) {
            return;
        }
        if (!hasLocationPermission()) {
            pendingMapEmergency = emergency;
            pendingMapEmergencyId = emergency.getId();
            requestLocationPermissionIfNeeded();
            Toast.makeText(this, "Permiso de ubicacion requerido para rutas", Toast.LENGTH_SHORT).show();
            return;
        }

        Location hostLocation = getBestLastKnownLocation();
        String destination = emergency.getLatitude() + "," + emergency.getLongitude();
        String uri;
        if (hostLocation != null) {
            String origin = hostLocation.getLatitude() + "," + hostLocation.getLongitude();
            uri = "https://www.google.com/maps/dir/?api=1&origin=" + origin + "&destination=" + destination + "&travelmode=driving";
        } else {
            uri = "https://www.google.com/maps/search/?api=1&query=" + destination;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        }
    }

    public void openEmergencyInMapsFromHistory(EmergencyAlertResponse emergency) {
        openEmergencyInMaps(emergency);
    }

    public void showEmergencyHistoryDetails(EmergencyAlertResponse alert, java.util.List<com.guardianapp.mobile.data.api.EmergencyAudioRecordingResponse> audioList) {
        if (alert == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Estado: ").append(alert.getStatus() != null ? alert.getStatus() : "DESCONOCIDA").append("\n");
        if (alert.getResolutionType() != null) {
            sb.append("Resolucion: ").append(alert.getResolutionType()).append("\n");
        }
        if (alert.getResolutionNote() != null && !alert.getResolutionNote().isBlank()) {
            sb.append("Nota: ").append(alert.getResolutionNote()).append("\n");
        }
        sb.append("Lat: ").append(alert.getLatitude()).append("\n");
        sb.append("Lon: ").append(alert.getLongitude()).append("\n");

        String audioText = "Sin audio";
        if (audioList != null && !audioList.isEmpty() && audioList.get(0).getPlaybackUrl() != null) {
            audioText = "Audio disponible";
        }
        sb.append("Audio: ").append(audioText);

        new AlertDialog.Builder(this)
                .setTitle("Detalle de emergencia")
                .setMessage(sb.toString())
                .setPositiveButton("Ver mapa", (dialog, which) -> openEmergencyInMaps(alert))
                .setNeutralButton("Reproducir audio", (dialog, which) ->
                        openAudioPlayer(audioList != null && !audioList.isEmpty() ? audioList.get(0).getPlaybackUrl() : null))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    public void openAudioPlayer(String playbackUrl) {
        if (playbackUrl == null || playbackUrl.isBlank()) {
            Toast.makeText(this, "Audio no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        String absoluteUrl = toAbsolutePlaybackUrl(playbackUrl);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(absoluteUrl));
        startActivity(intent);
    }

    private void stopEmergencyAudioPlayback() {
        if (emergencyAudioPlayer != null) {
            try {
                emergencyAudioPlayer.stop();
            } catch (Exception ignored) {
            }
            emergencyAudioPlayer.release();
            emergencyAudioPlayer = null;
        }
        if (emergencyAudioPlayer != null) {
            try {
                emergencyAudioPlayer.stop();
            } catch (Exception ignored) {
            }
            emergencyAudioPlayer.release();
            emergencyAudioPlayer = null;
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                HOST_LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private Location getBestLastKnownLocation() {
        if (!hasLocationPermission()) {
            return null;
        }
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == HOST_LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = false;
            if (grantResults != null) {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
            }
            if (granted && pendingMapEmergencyId != null) {
                EmergencyAlertResponse emergency = pendingMapEmergency;
                pendingMapEmergency = null;
                pendingMapEmergencyId = null;
                if (emergency != null) {
                    openEmergencyInMaps(emergency);
                }
            } else if (!granted) {
                pendingMapEmergency = null;
                pendingMapEmergencyId = null;
                Toast.makeText(this, "Permiso de ubicacion denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startEmergencyLiveListening(String emergencyId) {
        if (emergencyId == null || emergencyId.isBlank()) {
            return;
        }
        if (liveAudioMuted) {
            updateMuteButtonState();
            return;
        }
        if (!EmergencyLiveAudioService.isRunning()) {
            EmergencyLiveAudioService.start(this, RetrofitClient.getWebSocketBaseUrl(), emergencyId, miIdAnfitrion);
            startAudioHealthCheck();
            Toast.makeText(this, "Escucha en vivo activada", Toast.LENGTH_SHORT).show();
        }
        updateMuteButtonState();
    }

    private void startAudioHealthCheck() {
        stopAudioHealthCheck();
        audioHealthRunnable = new Runnable() {
            @Override
            public void run() {
                if (EmergencyLiveAudioService.isRunning() && EmergencyLiveAudioService.getSilenceMillis() > 15000L) {
                    Toast.makeText(HostDashboardActivity.this, "Sin audio en vivo temporalmente", Toast.LENGTH_SHORT).show();
                }
                audioHealthHandler.postDelayed(this, 5000L);
            }
        };
        audioHealthHandler.postDelayed(audioHealthRunnable, 5000L);
    }

    private void stopAudioHealthCheck() {
        if (audioHealthRunnable != null) {
            audioHealthHandler.removeCallbacks(audioHealthRunnable);
        }
    }

    private void fetchLatestEmergencyAudioAndOfferPlayback(String emergencyId) {
        RetrofitClient.getApiService().getLatestEmergencyAudio(emergencyId)
                .enqueue(new Callback<EmergencyAudioRecordingResponse>() {
                    @Override
                    public void onResponse(Call<EmergencyAudioRecordingResponse> call, Response<EmergencyAudioRecordingResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getPlaybackUrl() != null) {
                            String playbackUrl = toAbsolutePlaybackUrl(response.body().getPlaybackUrl());
                            new AlertDialog.Builder(HostDashboardActivity.this)
                                    .setTitle("Audio disponible")
                                    .setMessage("Hay una grabacion de emergencia disponible. ¿Deseas abrirla ahora?")
                                    .setNegativeButton("Despues", null)
                                    .setPositiveButton("Abrir audio", (dialog, which) -> {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(playbackUrl));
                                        startActivity(intent);
                                    })
                                    .show();
                        }
                    }

                    @Override
                    public void onFailure(Call<EmergencyAudioRecordingResponse> call, Throwable t) {
                    }
                });
    }

    private String toAbsolutePlaybackUrl(String playbackUrl) {
        if (playbackUrl.startsWith("http://") || playbackUrl.startsWith("https://")) {
            return playbackUrl;
        }
        String baseUrl = RetrofitClient.getBaseUrl();
        if (baseUrl.endsWith("/") && playbackUrl.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + playbackUrl;
        }
        if (!baseUrl.endsWith("/") && !playbackUrl.startsWith("/")) {
            return baseUrl + "/" + playbackUrl;
        }
        return baseUrl + playbackUrl;
    }

    private void resolveEmergency(String emergencyId, String resolutionType, String note) {
        ResolveEmergencyAlertRequest request = new ResolveEmergencyAlertRequest(miIdAnfitrion, resolutionType, note);
        RetrofitClient.getApiService().resolveEmergency(emergencyId, request)
                .enqueue(new Callback<EmergencyAlertResponse>() {
                    @Override
                    public void onResponse(Call<EmergencyAlertResponse> call, Response<EmergencyAlertResponse> response) {
                        isAlertShowing = false;
                        if (response.isSuccessful()) {
                            EmergencyLiveAudioService.stop(HostDashboardActivity.this);
                            stopAudioHealthCheck();
                            hideEmergencyBanner();
                            dismissedEmergencyId = null;
                            lastEmergencyAlert = null;
                            liveAudioMuted = false;
                            Toast.makeText(HostDashboardActivity.this, "Emergencia resuelta", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(HostDashboardActivity.this, "No se pudo resolver la emergencia", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<EmergencyAlertResponse> call, Throwable t) {
                        isAlertShowing = false;
                        Toast.makeText(HostDashboardActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadLinkStatus() {
        if (miIdAnfitrion == null) return;

        RetrofitClient.getApiService().getMyLinks(miIdAnfitrion).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(HostDashboardActivity.this, "Error conectando con servidor", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                Toast.makeText(HostDashboardActivity.this, "Error conectando con servidor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadEmergencyHistory() {
        if (miIdAnfitrion == null || emergencyHistoryAdapter == null) {
            return;
        }

        RetrofitClient.getApiService().getEmergencyHistory(miIdAnfitrion)
                .enqueue(new Callback<List<EmergencyAlertResponse>>() {
                    @Override
                    public void onResponse(Call<List<EmergencyAlertResponse>> call, Response<List<EmergencyAlertResponse>> response) {
                        List<EmergencyAlertResponse> items = response.isSuccessful() ? response.body() : null;
                        if (items == null || items.isEmpty()) {
                            showEmergencyHistoryEmpty(true);
                            emergencyHistoryAdapter.setItems(null);
                            return;
                        }
                        showEmergencyHistoryEmpty(false);
                        emergencyHistoryAdapter.setItems(items);
                    }

                    @Override
                    public void onFailure(Call<List<EmergencyAlertResponse>> call, Throwable t) {
                        showEmergencyHistoryEmpty(true);
                    }
                });
    }

    private void setupEmergencyHistory() {
        if (rvEmergencyHistory == null) {
            return;
        }
        rvEmergencyHistory.setLayoutManager(new LinearLayoutManager(this));
        emergencyHistoryAdapter = new EmergencyHistoryAdapter(this, miIdAnfitrion);
        rvEmergencyHistory.setAdapter(emergencyHistoryAdapter);
        showEmergencyHistoryEmpty(true);
    }

    private void showEmergencyHistoryEmpty(boolean show) {
        if (tvEmergencyHistoryEmpty != null) {
            tvEmergencyHistoryEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (rvEmergencyHistory != null) {
            rvEmergencyHistory.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EmergencyLiveAudioService.stop(this);
        stopEmergencyAudioPlayback();
        stopAudioHealthCheck();
        stopPolling();
        linkRealtimeClient.disconnect();
        emergencyRealtimeClient.disconnect();
    }
}
