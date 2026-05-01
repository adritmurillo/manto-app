package com.guardianapp.mobile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.guardianapp.mobile.api.CreateIdentityVerificationRequest;
import com.guardianapp.mobile.api.EmergencyAlertResponse;
import com.guardianapp.mobile.api.IdentityVerificationResponse;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.api.TriggerEmergencyAlertRequest;
import com.guardianapp.mobile.api.EmergencyAudioRecordingResponse;
import com.guardianapp.mobile.audio.EmergencyLiveAudioStreamer;
import com.guardianapp.mobile.realtime.StompRealtimeClient;
import com.google.firebase.auth.FirebaseAuth;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProtectedDashboardActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1201;

    private String miIdProtegido;
    private String idDelVinculo;
    private String currentVerificationId;
    private String currentEmergencyId;
    private EmergencyLiveAudioStreamer emergencyLiveAudioStreamer;
    private final Handler emergencyAudioHandler = new Handler(Looper.getMainLooper());
    private Runnable emergencyStopRunnable;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    private final StompRealtimeClient identityRealtimeClient = new StompRealtimeClient();
    private final StompRealtimeClient linkRealtimeClient = new StompRealtimeClient();
    private AlertDialog verificationCodeDialog;
    private LinearLayout layoutVerificationCodeInfo;
    private TextView tvVerificationCodePersistent;
    private TextView tvVerificationCodeStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_protected_dashboard);

        miIdProtegido = getIntent().getStringExtra("PROTECTED_ID");
        idDelVinculo = getIntent().getStringExtra("LINK_ID");

        if (miIdProtegido == null || miIdProtegido.isBlank()) {
            miIdProtegido = FirebaseAuth.getInstance().getUid();
        }

        if (idDelVinculo == null && miIdProtegido != null && !miIdProtegido.isBlank()) {
            recoverLinkIdFromBackend();
        }

        Button btnSimulateThreat = findViewById(R.id.btnSimulateThreat);
        Button btnCallHost = findViewById(R.id.btnCallHost);
        Button btnSos = findViewById(R.id.btnSos);
        TextView tvLogout = findViewById(R.id.tvLogoutProtected);
        layoutVerificationCodeInfo = findViewById(R.id.layoutVerificationCodeInfo);
        tvVerificationCodePersistent = findViewById(R.id.tvVerificationCodePersistent);
        tvVerificationCodeStatus = findViewById(R.id.tvVerificationCodeStatus);
        emergencyLiveAudioStreamer = new EmergencyLiveAudioStreamer();

        // ¡Abrimos la trampa!
        btnSimulateThreat.setOnClickListener(v -> {
            Intent intent = new Intent(this, SecureBrowserActivity.class);
            intent.putExtra("PROTECTED_ID", miIdProtegido);
            intent.putExtra("LINK_ID", idDelVinculo);
            startActivity(intent);
        });

        btnCallHost.setOnClickListener(v -> showIdentityVerificationDialog());

        btnSos.setOnClickListener(v -> showEmergencyConfirmationDialog());

        checkActiveEmergencyOnEntry();

        tvLogout.setOnClickListener(v -> {
            stopVerificationPolling();
            identityRealtimeClient.disconnect();
            linkRealtimeClient.disconnect();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        connectRealtime();
    }

    private void recoverLinkIdFromBackend() {
        RetrofitClient.getApiService().getMyLinks(miIdProtegido)
                .enqueue(new Callback<java.util.List<com.guardianapp.mobile.api.LinkResponse>>() {
                    @Override
                    public void onResponse(Call<java.util.List<com.guardianapp.mobile.api.LinkResponse>> call,
                                           Response<java.util.List<com.guardianapp.mobile.api.LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (com.guardianapp.mobile.api.LinkResponse link : response.body()) {
                                if (miIdProtegido.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                                    idDelVinculo = link.getId();
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.List<com.guardianapp.mobile.api.LinkResponse>> call, Throwable t) {
                    }
                });
    }

    private void showEmergencyConfirmationDialog() {
        if (miIdProtegido == null || idDelVinculo == null) {
            Toast.makeText(this, "Faltan datos del vinculo", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Activar emergencia")
                .setMessage("Se notificara a tus anfitriones y se compartira tu ubicacion actual.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Activar", (dialog, which) -> triggerEmergency())
                .show();
    }

    private void triggerEmergency() {
        Location location = getBestLastKnownLocation();
        if (location == null) {
            Toast.makeText(this, "No se pudo obtener ubicacion. Activa el GPS e intenta de nuevo.", Toast.LENGTH_LONG).show();
            requestLocationPermissionIfNeeded();
            return;
        }

        TriggerEmergencyAlertRequest request = new TriggerEmergencyAlertRequest(
                idDelVinculo,
                miIdProtegido,
                location.getLatitude(),
                location.getLongitude()
        );

        RetrofitClient.getApiService().triggerEmergency(request)
                .enqueue(new Callback<EmergencyAlertResponse>() {
                    @Override
                    public void onResponse(Call<EmergencyAlertResponse> call, Response<EmergencyAlertResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            currentEmergencyId = response.body().getId();
                            startEmergencyAudioRecording(currentEmergencyId);
                            Toast.makeText(
                                    ProtectedDashboardActivity.this,
                                    "Emergencia activada. Tus anfitriones fueron notificados.",
                                    Toast.LENGTH_LONG
                            ).show();
                        } else {
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo activar la emergencia", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<EmergencyAlertResponse> call, Throwable t) {
                        Toast.makeText(ProtectedDashboardActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkActiveEmergencyOnEntry() {
        if (miIdProtegido == null || miIdProtegido.isBlank()) {
            return;
        }
        RetrofitClient.getApiService().getActiveEmergenciesForProtected(miIdProtegido)
                .enqueue(new Callback<java.util.List<EmergencyAlertResponse>>() {
                    @Override
                    public void onResponse(Call<java.util.List<EmergencyAlertResponse>> call,
                                           Response<java.util.List<EmergencyAlertResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            EmergencyAlertResponse active = response.body().get(0);
                            if (active != null && active.getId() != null) {
                                currentEmergencyId = active.getId();
                                if (!emergencyLiveAudioStreamer.isRunning()) {
                                    startEmergencyAudioRecording(currentEmergencyId);
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.List<EmergencyAlertResponse>> call, Throwable t) {
                    }
                });
    }

    private void startEmergencyAudioRecording(String emergencyId) {
        if (emergencyId == null || emergencyId.isBlank()) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    LOCATION_PERMISSION_REQUEST_CODE + 1
            );
            return;
        }

        try {
            emergencyLiveAudioStreamer.start(
                    ProtectedDashboardActivity.this,
                    RetrofitClient.getWebSocketBaseUrl(),
                    emergencyId,
                    miIdProtegido,
                    getCacheDir()
            );
            scheduleEmergencyAudioStopAndUpload();
            Toast.makeText(this, "Grabacion de emergencia iniciada", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "No se pudo iniciar grabacion de audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void scheduleEmergencyAudioStopAndUpload() {
        if (emergencyStopRunnable != null) {
            emergencyAudioHandler.removeCallbacks(emergencyStopRunnable);
        }
        emergencyStopRunnable = this::stopAndUploadEmergencyAudio;
        emergencyAudioHandler.postDelayed(emergencyStopRunnable, 30 * 60 * 1000L);
    }

    private void stopAndUploadEmergencyAudio() {
        if (currentEmergencyId == null || currentEmergencyId.isBlank() || !emergencyLiveAudioStreamer.isRunning()) {
            return;
        }

        EmergencyLiveAudioStreamer.RecordingResult result = emergencyLiveAudioStreamer.stopAndBuildWav();
        if (result == null || result.file() == null || !result.file().exists()) {
            return;
        }

        uploadEmergencyAudio(currentEmergencyId, result.file(), result.durationSeconds());
    }

    private void uploadEmergencyAudio(String emergencyId, File audioFile, int durationSeconds) {
        RequestBody fileBody = RequestBody.create(audioFile, MediaType.parse("audio/wav"));
        MultipartBody.Part audioPart = MultipartBody.Part.createFormData("audio", audioFile.getName(), fileBody);

        Map<String, RequestBody> params = new HashMap<>();
        params.put("durationSeconds", RequestBody.create(
                String.valueOf(durationSeconds),
                MediaType.parse("text/plain")));

        RetrofitClient.getApiService()
                .uploadEmergencyAudio(emergencyId, miIdProtegido, audioPart, params)
                .enqueue(new Callback<EmergencyAudioRecordingResponse>() {
                    @Override
                    public void onResponse(Call<EmergencyAudioRecordingResponse> call, Response<EmergencyAudioRecordingResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ProtectedDashboardActivity.this, "Audio de emergencia subido", Toast.LENGTH_SHORT).show();
                            if (audioFile.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                audioFile.delete();
                            }
                        } else {
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo subir el audio", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<EmergencyAudioRecordingResponse> call, Throwable t) {
                        Toast.makeText(ProtectedDashboardActivity.this, "Error subiendo audio", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private Location getBestLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionIfNeeded();
            return null;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }

        Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (gpsLocation == null) {
            return networkLocation;
        }
        if (networkLocation == null) {
            return gpsLocation;
        }
        return gpsLocation.getTime() >= networkLocation.getTime() ? gpsLocation : networkLocation;
    }

    private void requestLocationPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private void connectRealtime() {
        if (miIdProtegido == null || miIdProtegido.isBlank()) {
            return;
        }
        String wsUrl = RetrofitClient.getWebSocketUrl();
        String topic = "/topic/protected/" + miIdProtegido + "/identity-verifications";
        identityRealtimeClient.connect(wsUrl, topic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                runOnUiThread(() -> {
                    if (currentVerificationId != null) {
                        startVerificationPolling();
                    }
                });
            }

            @Override
            public void onConnected() {
            }
        });

        String linkTopic = "/topic/protected/" + miIdProtegido + "/links";
        linkRealtimeClient.connect(wsUrl, linkTopic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                runOnUiThread(ProtectedDashboardActivity.this::refreshLinkStatus);
            }

            @Override
            public void onConnected() {
            }
        });
    }

    private void refreshLinkStatus() {
        if (miIdProtegido == null) {
            return;
        }
        RetrofitClient.getApiService().getMyLinks(miIdProtegido)
                .enqueue(new Callback<java.util.List<com.guardianapp.mobile.api.LinkResponse>>() {
                    @Override
                    public void onResponse(Call<java.util.List<com.guardianapp.mobile.api.LinkResponse>> call,
                                           Response<java.util.List<com.guardianapp.mobile.api.LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (com.guardianapp.mobile.api.LinkResponse link : response.body()) {
                                if (miIdProtegido.equals(link.getProtectedUserId())) {
                                    if ("ACTIVE".equals(link.getStatus())) {
                                        Intent intent = new Intent(ProtectedDashboardActivity.this, ProtectedDashboardActivity.class);
                                        intent.putExtra("PROTECTED_ID", miIdProtegido);
                                        intent.putExtra("LINK_ID", link.getId());
                                        startActivity(intent);
                                        finish();
                                        return;
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.List<com.guardianapp.mobile.api.LinkResponse>> call, Throwable t) {
                    }
                });
    }

    private void showIdentityVerificationDialog() {
        if (miIdProtegido == null || idDelVinculo == null) {
            Toast.makeText(this, "Faltan datos del vínculo", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Ejemplo: Mi hijo Adrián");

        new AlertDialog.Builder(this)
                .setTitle("Verificar identidad")
                .setMessage("¿Quién dice ser la persona que llama?")
                .setView(input)
                .setPositiveButton("Enviar verificación", (dialog, which) -> {
                    String claimedPerson = input.getText().toString().trim();
                    if (claimedPerson.isEmpty()) {
                        claimedPerson = "Familiar";
                    }
                    createIdentityVerification(claimedPerson);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void createIdentityVerification(String claimedPerson) {
        CreateIdentityVerificationRequest request = new CreateIdentityVerificationRequest(
                idDelVinculo,
                miIdProtegido,
                claimedPerson
        );

        RetrofitClient.getApiService().createIdentityVerification(request)
                .enqueue(new Callback<IdentityVerificationResponse>() {
                    @Override
                    public void onResponse(Call<IdentityVerificationResponse> call, Response<IdentityVerificationResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            currentVerificationId = response.body().getId();
                            showVerificationCodeDialog(response.body().getChallengeCode());
                            startVerificationPolling();
                        } else {
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo enviar la verificación", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<IdentityVerificationResponse> call, Throwable t) {
                        Toast.makeText(ProtectedDashboardActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startVerificationPolling() {
        stopVerificationPolling();
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentVerificationId == null) {
                    return;
                }
                RetrofitClient.getApiService().getIdentityVerification(currentVerificationId)
                        .enqueue(new Callback<IdentityVerificationResponse>() {
                            @Override
                            public void onResponse(Call<IdentityVerificationResponse> call, Response<IdentityVerificationResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    IdentityVerificationResponse verification = response.body();
                                    if (verification.isApproved()) {
                                        stopVerificationPolling();
                                        dismissVerificationCodeDialog();
                                        updateVerificationCodePanel(null, "Confirmado: era tu familiar", false);
                                        Toast.makeText(
                                                ProtectedDashboardActivity.this,
                                                "Verificado: tu familiar confirmó que sí es él/ella",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    } else if (verification.isRejected()) {
                                        stopVerificationPolling();
                                        dismissVerificationCodeDialog();
                                        updateVerificationCodePanel(null, "Alerta: no era tu familiar", false);
                                        Toast.makeText(
                                                ProtectedDashboardActivity.this,
                                                "Alerta: tu familiar indicó que NO es la persona real",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    } else if (verification.isExpired()) {
                                        stopVerificationPolling();
                                        dismissVerificationCodeDialog();
                                        updateVerificationCodePanel(null, "Verificación expirada", false);
                                        Toast.makeText(
                                                ProtectedDashboardActivity.this,
                                                "La verificación expiró. Intenta de nuevo.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<IdentityVerificationResponse> call, Throwable t) {
                            }
                        });
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopVerificationPolling() {
        if (pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    private void showVerificationCodeDialog(String challengeCode) {
        dismissVerificationCodeDialog();
        updateVerificationCodePanel(challengeCode, "Esperando respuesta de tu familiar", true);
        verificationCodeDialog = new AlertDialog.Builder(this)
                .setTitle("Verificación enviada")
                .setMessage(
                        "Comparte este código con tu familiar durante la llamada:\n\n" +
                        challengeCode +
                        "\n\nEspera su confirmación para saber si la llamada es real."
                )
                .setCancelable(false)
                .setPositiveButton("Entendido", null)
                .create();
        verificationCodeDialog.show();
    }

    private void dismissVerificationCodeDialog() {
        if (verificationCodeDialog != null && verificationCodeDialog.isShowing()) {
            verificationCodeDialog.dismiss();
        }
        verificationCodeDialog = null;
    }

    private void updateVerificationCodePanel(String code, String status, boolean visible) {
        if (layoutVerificationCodeInfo == null) {
            return;
        }
        layoutVerificationCodeInfo.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            tvVerificationCodePersistent.setText(code != null ? code : "------");
            tvVerificationCodeStatus.setText(status != null ? status : "Esperando respuesta de tu familiar");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emergencyStopRunnable != null) {
            emergencyAudioHandler.removeCallbacks(emergencyStopRunnable);
        }
        stopAndUploadEmergencyAudio();
        stopVerificationPolling();
        identityRealtimeClient.disconnect();
        linkRealtimeClient.disconnect();
        dismissVerificationCodeDialog();
    }
}
