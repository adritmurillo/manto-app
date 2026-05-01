package com.guardianapp.mobile;

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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.guardianapp.mobile.api.AlertResponse;
import com.guardianapp.mobile.api.EmergencyAlertResponse;
import com.guardianapp.mobile.api.EmergencyAudioRecordingResponse;
import com.guardianapp.mobile.api.ResolveEmergencyAlertRequest;
import com.guardianapp.mobile.api.IdentityVerificationResponse;
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.ResolveAlertRequest;
import com.guardianapp.mobile.api.RespondIdentityVerificationRequest;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.audio.EmergencyLiveAudioPlayer;
import com.guardianapp.mobile.realtime.StompRealtimeClient;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HostDashboardActivity extends AppCompatActivity {

    private String miIdAnfitrion;
    private Handler pollingHandler;
    private Runnable pollingRunnable;

    private LinearLayout layoutPendingAction;
    private TextView tvConnectionCodeDisplay;
    private Button btnRefreshStatus;
    private boolean isAlertShowing = false;
    private final EmergencyLiveAudioPlayer emergencyLiveAudioPlayer = new EmergencyLiveAudioPlayer();
    private final StompRealtimeClient verificationRealtimeClient = new StompRealtimeClient();
    private final StompRealtimeClient linkRealtimeClient = new StompRealtimeClient();
    private final Handler audioHealthHandler = new Handler(Looper.getMainLooper());
    private Runnable audioHealthRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_dashboard);

        miIdAnfitrion = getIntent().getStringExtra("HOST_ID");

        // Enlazamos las vistas
        layoutPendingAction = findViewById(R.id.layoutPendingAction);
        tvConnectionCodeDisplay = findViewById(R.id.tvConnectionCodeDisplay);
        btnRefreshStatus = findViewById(R.id.btnRefreshStatus);
        Button btnFamilyCircle = findViewById(R.id.btnFamilyCircle);
        TextView tvLogout = findViewById(R.id.tvLogoutHost);

        // Botón para actualizar manualmente la vista y ver si ya pusieron el PIN
        btnRefreshStatus.setOnClickListener(v -> {
            Toast.makeText(this, "Actualizando estado...", Toast.LENGTH_SHORT).show();
            loadLinkStatus();
        });

        btnFamilyCircle.setOnClickListener(v -> {
            Intent intent = new Intent(HostDashboardActivity.this, FamilyCircleActivity.class);
            intent.putExtra("HOST_ID", miIdAnfitrion);
            startActivity(intent);
        });

        // Secondary hosts should not manage the family circle.
        btnFamilyCircle.setVisibility(View.GONE);
        RetrofitClient.getApiService().getMyFamilyGroups(miIdAnfitrion).enqueue(new Callback<List<com.guardianapp.mobile.api.FamilyGroupResponse>>() {
            @Override
            public void onResponse(Call<List<com.guardianapp.mobile.api.FamilyGroupResponse>> call, Response<List<com.guardianapp.mobile.api.FamilyGroupResponse>> response) {
                boolean isPrimaryHost = false;
                if (response.isSuccessful() && response.body() != null) {
                    for (com.guardianapp.mobile.api.FamilyGroupResponse g : response.body()) {
                        if (g == null || g.getMembers() == null) continue;
                        for (com.guardianapp.mobile.api.FamilyGroupResponse.MemberResponse m : g.getMembers()) {
                            if (m != null && miIdAnfitrion.equals(m.getUserId()) && "PRIMARY_HOST".equals(m.getRole())) {
                                isPrimaryHost = true;
                                break;
                            }
                        }
                        if (isPrimaryHost) break;
                    }
                }
                btnFamilyCircle.setVisibility(isPrimaryHost ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<List<com.guardianapp.mobile.api.FamilyGroupResponse>> call, Throwable t) {
                // Keep hidden on failure.
                btnFamilyCircle.setVisibility(View.GONE);
            }
        });

        tvLogout.setOnClickListener(v -> {
            stopPolling();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 1. Cargamos el estado del vínculo al entrar a la pantalla
        loadLinkStatus();

        // 2. Iniciamos el radar para buscar alertas de páginas falsas
        startPolling();

        connectRealtime();
    }

    private void connectRealtime() {
        if (miIdAnfitrion == null || miIdAnfitrion.isBlank()) {
            return;
        }
        String wsUrl = RetrofitClient.getWebSocketUrl();
        String verificationTopic = "/topic/host/" + miIdAnfitrion + "/identity-verifications";
        verificationRealtimeClient.connect(wsUrl, verificationTopic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                runOnUiThread(() -> {
                    if (!isAlertShowing) {
                        checkPendingIdentityVerifications();
                    }
                });
            }

            @Override
            public void onConnected() {
            }
        });

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
    }

    private void startPolling() {
        pollingHandler = new Handler(Looper.getMainLooper());
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAlertShowing) {
                    checkPendingIdentityVerifications();
                    checkPendingAlerts();
                    checkActiveEmergencies();
                }
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void checkPendingIdentityVerifications() {
        if (miIdAnfitrion == null || isAlertShowing) return;

        RetrofitClient.getApiService().getPendingIdentityVerifications(miIdAnfitrion)
                .enqueue(new Callback<List<IdentityVerificationResponse>>() {
                    @Override
                    public void onResponse(Call<List<IdentityVerificationResponse>> call, Response<List<IdentityVerificationResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty() && !isAlertShowing) {
                            showIdentityVerificationPopup(response.body().get(0));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<IdentityVerificationResponse>> call, Throwable t) {
                    }
                });
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
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    showAlertPopup(response.body().get(0));
                }
            }
            @Override
            public void onFailure(Call<List<AlertResponse>> call, Throwable t) {}
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

    private void showIdentityVerificationPopup(IdentityVerificationResponse verification) {
        isAlertShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 Verificación de identidad");
        builder.setMessage(
                "Tu protegido solicita verificar una llamada.\n\n" +
                "Persona que dice ser: " + verification.getClaimedPerson() + "\n" +
                "Código de verificación familiar: " + verification.getChallengeCode() + "\n\n" +
                "Confirma que ese mismo código lo vea tu familiar."
        );
        builder.setCancelable(false);

        builder.setPositiveButton("No soy yo", (dialog, which) ->
                respondIdentityVerification(verification.getId(), false, "No soy yo, cortar llamada")
        );

        builder.setNegativeButton("Sí soy yo", (dialog, which) ->
                respondIdentityVerification(verification.getId(), true, "Sí, identidad confirmada")
        );

        builder.show();
    }

    private void checkActiveEmergencies() {
        if (miIdAnfitrion == null || isAlertShowing) return;

        RetrofitClient.getApiService().getActiveEmergencies(miIdAnfitrion)
                .enqueue(new Callback<List<EmergencyAlertResponse>>() {
                    @Override
                    public void onResponse(Call<List<EmergencyAlertResponse>> call, Response<List<EmergencyAlertResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty() && !isAlertShowing) {
                            showEmergencyPopup(response.body().get(0));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<EmergencyAlertResponse>> call, Throwable t) {
                    }
                });
    }

    private void showEmergencyPopup(EmergencyAlertResponse emergency) {
        isAlertShowing = true;
        boolean isPrimaryHost = miIdAnfitrion != null && miIdAnfitrion.equals(emergency.getPrimaryHostUserId());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 EMERGENCIA ACTIVA");
        builder.setMessage(
                "Tu familiar activo el boton de emergencia.\n\n" +
                        "Ubicacion:\n" +
                        "Lat: " + emergency.getLatitude() + "\n" +
                        "Lon: " + emergency.getLongitude() + "\n\n" +
                        (isPrimaryHost
                                ? "Eres anfitrion principal. Puedes resolver esta emergencia."
                                : "Modo lectura: solo el anfitrion principal puede resolver.")
        );
        builder.setCancelable(false);

        if (isPrimaryHost) {
            builder.setPositiveButton("Falsa alarma", (dialog, which) ->
                    resolveEmergency(emergency.getId(), "FALSE_ALARM", "Marcada como falsa alarma")
            );
            builder.setNeutralButton("Todo bien", (dialog, which) ->
                    resolveEmergency(emergency.getId(), "ALL_SAFE", "Situacion controlada")
            );
            builder.setNegativeButton("Policia enviada", (dialog, which) ->
                    resolveEmergency(emergency.getId(), "POLICE_SENT", "Se contacto a autoridades")
            );
        } else {
            builder.setPositiveButton("Contactar anfitrion principal", (dialog, which) -> {
                Toast.makeText(
                        HostDashboardActivity.this,
                        "Notifica por llamada o mensaje al anfitrion principal para resolver.",
                        Toast.LENGTH_LONG
                ).show();
                isAlertShowing = false;
            });
        }

        builder.setOnDismissListener(dialog -> isAlertShowing = false);
        builder.show();
        fetchLatestEmergencyAudioAndOfferPlayback(emergency.getId());
        startEmergencyLiveListening(emergency.getId());
    }

    private void startEmergencyLiveListening(String emergencyId) {
        if (emergencyId == null || emergencyId.isBlank()) {
            return;
        }
        if (!emergencyLiveAudioPlayer.isRunning()) {
            emergencyLiveAudioPlayer.start(RetrofitClient.getWebSocketBaseUrl(), emergencyId, miIdAnfitrion);
            startAudioHealthCheck();
            Toast.makeText(this, "Escucha en vivo activada", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAudioHealthCheck() {
        stopAudioHealthCheck();
        audioHealthRunnable = new Runnable() {
            @Override
            public void run() {
                if (emergencyLiveAudioPlayer.isRunning() && emergencyLiveAudioPlayer.getSilenceMillis() > 15000L) {
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
                            emergencyLiveAudioPlayer.stop();
                            stopAudioHealthCheck();
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

    private void respondIdentityVerification(String verificationId, boolean approved, String note) {
        RespondIdentityVerificationRequest request = new RespondIdentityVerificationRequest(
                miIdAnfitrion,
                approved,
                note
        );
        RetrofitClient.getApiService().respondIdentityVerification(verificationId, request)
                .enqueue(new Callback<IdentityVerificationResponse>() {
                    @Override
                    public void onResponse(Call<IdentityVerificationResponse> call, Response<IdentityVerificationResponse> response) {
                        isAlertShowing = false;
                    }

                    @Override
                    public void onFailure(Call<IdentityVerificationResponse> call, Throwable t) {
                        isAlertShowing = false;
                    }
                });
    }

    private void loadLinkStatus() {
        if (miIdAnfitrion == null) return;

        RetrofitClient.getApiService().getMyLinks(miIdAnfitrion).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    LinkResponse pending = null;
                    for (LinkResponse link : response.body()) {
                        if ("PENDING".equals(link.getStatus())) {
                            pending = link;
                            break;
                        }
                    }

                    if (pending != null) {
                        // Mostramos el cartel gigante naranja con el código de 6 dígitos
                        layoutPendingAction.setVisibility(View.VISIBLE);
                        tvConnectionCodeDisplay.setText(pending.getConnectionCode());
                    } else {
                        // Si no hay pendientes, lo ocultamos
                        layoutPendingAction.setVisibility(View.GONE);
                    }
                }
            }
            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                Toast.makeText(HostDashboardActivity.this, "Error conectando con servidor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        emergencyLiveAudioPlayer.stop();
        stopAudioHealthCheck();
        stopPolling();
        verificationRealtimeClient.disconnect();
        linkRealtimeClient.disconnect();
    }
}
