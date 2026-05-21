package com.guardianapp.mobile.ui.protecteduser;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.EmergencyAlertResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.TriggerEmergencyAlertRequest;
import com.guardianapp.mobile.data.api.EmergencyAudioRecordingResponse;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.audio.EmergencyLiveAudioStreamer;
import com.guardianapp.mobile.data.realtime.StompRealtimeClient;
import com.guardianapp.mobile.ui.main.MainActivity;
import com.guardianapp.mobile.ui.common.AppNavigator;
import com.guardianapp.mobile.ui.common.FamilyAccessGuard;
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

    private static final String TAG = "ProtectedDashboard";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1201;
    private static final long EMERGENCY_AUDIO_MAX_DURATION_MS = 30 * 60 * 1000L;

    private String miIdProtegido;
    private String idDelVinculo;
    private String currentEmergencyId;
    private EmergencyLiveAudioStreamer emergencyLiveAudioStreamer;
    private final Handler emergencyAudioHandler = new Handler(Looper.getMainLooper());
    private Runnable emergencyStopRunnable;
    private LocationListener emergencyLocationListener;
    private Runnable emergencyLocationTimeout;
    private boolean pendingEmergencyAfterPermission;
    private boolean requestingEmergencyLocation;
    private final StompRealtimeClient linkRealtimeClient = new StompRealtimeClient();
    private final StompRealtimeClient emergencyStatusClient = new StompRealtimeClient();
    private final Handler familyGuardHandler = new Handler(Looper.getMainLooper());
    private Runnable familyGuardRunnable;

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
        Button btnSos = findViewById(R.id.btnSos);
        TextView tvLogout = findViewById(R.id.tvLogoutProtected);
        emergencyLiveAudioStreamer = new EmergencyLiveAudioStreamer();

        // Flujo real de bloqueo por WebView seguro.
        btnSimulateThreat.setOnClickListener(v -> {
            Intent intent = new Intent(this, SecureBrowserActivity.class);
            intent.putExtra("PROTECTED_ID", miIdProtegido);
            intent.putExtra("LINK_ID", idDelVinculo);
            startActivity(intent);
        });

        btnSos.setOnClickListener(v -> validateAccessBeforeEmergency(this::showEmergencyConfirmationDialog));

        checkActiveEmergencyOnEntry();

        tvLogout.setOnClickListener(v -> {
            linkRealtimeClient.disconnect();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        connectRealtime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startFamilyGuard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopFamilyGuard();
    }

    private void recoverLinkIdFromBackend() {
        RetrofitClient.getApiService().getMyLinks(miIdProtegido)
                .enqueue(new Callback<java.util.List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<java.util.List<LinkResponse>> call,
                                           Response<java.util.List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (miIdProtegido.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                                    idDelVinculo = link.getId();
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.List<LinkResponse>> call, Throwable t) {
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
        if (!hasLocationPermission()) {
            pendingEmergencyAfterPermission = true;
            requestLocationPermissionIfNeeded();
            return;
        }

        Location location = getBestLastKnownLocation();
        if (location == null) {
            requestSingleLocationThenTrigger();
            return;
        }

        sendEmergencyRequest(location);
    }

    private void sendEmergencyRequest(Location location) {
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

    private void requestSingleLocationThenTrigger() {
        if (requestingEmergencyLocation) {
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "No se pudo acceder al GPS", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            Toast.makeText(this, "Activa el GPS e intenta de nuevo", Toast.LENGTH_LONG).show();
            return;
        }

        requestingEmergencyLocation = true;
        emergencyLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                cleanupEmergencyLocationRequest();
                sendEmergencyRequest(location);
            }
        };

        try {
            if (gpsEnabled) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, emergencyLocationListener, Looper.getMainLooper());
            }
            if (networkEnabled) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, emergencyLocationListener, Looper.getMainLooper());
            }
        } catch (SecurityException ex) {
            cleanupEmergencyLocationRequest();
            Toast.makeText(this, "Permiso de ubicacion requerido", Toast.LENGTH_SHORT).show();
            return;
        } catch (IllegalArgumentException ex) {
            cleanupEmergencyLocationRequest();
            Toast.makeText(this, "Proveedor de ubicacion no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        emergencyLocationTimeout = () -> {
            cleanupEmergencyLocationRequest();
            Toast.makeText(this, "No se pudo obtener ubicacion. Intenta nuevamente.", Toast.LENGTH_LONG).show();
        };
        emergencyAudioHandler.postDelayed(emergencyLocationTimeout, 10000L);
        Toast.makeText(this, "Obteniendo ubicacion...", Toast.LENGTH_SHORT).show();
    }

    private void cleanupEmergencyLocationRequest() {
        requestingEmergencyLocation = false;
        if (emergencyLocationTimeout != null) {
            emergencyAudioHandler.removeCallbacks(emergencyLocationTimeout);
        }
        emergencyLocationTimeout = null;

        if (emergencyLocationListener != null) {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                try {
                    locationManager.removeUpdates(emergencyLocationListener);
                } catch (SecurityException ignored) {
                }
            }
        }
        emergencyLocationListener = null;
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
            connectEmergencyStatus(emergencyId);
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
        emergencyAudioHandler.postDelayed(emergencyStopRunnable, EMERGENCY_AUDIO_MAX_DURATION_MS);
    }

    private void stopAndUploadEmergencyAudio() {
        if (currentEmergencyId == null || currentEmergencyId.isBlank()) {
            return;
        }

        if (!emergencyLiveAudioStreamer.isRunning()) {
            return;
        }
        EmergencyLiveAudioStreamer.RecordingResult result = emergencyLiveAudioStreamer.stopAndBuildWav();
        if (result == null || result.file() == null || !result.file().exists()) {
            return;
        }

        uploadEmergencyAudio(currentEmergencyId, result.file(), result.durationSeconds());
    }

    private void uploadEmergencyAudio(String emergencyId, File audioFile, int durationSeconds) {
        Log.d(TAG, "Uploading emergency audio: id=" + emergencyId + " file=" + audioFile.getName() + " size=" + audioFile.length());
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
                            Log.d(TAG, "Emergency audio upload success: code=" + response.code());
                            Toast.makeText(ProtectedDashboardActivity.this, "Audio de emergencia subido", Toast.LENGTH_SHORT).show();
                            if (audioFile.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                audioFile.delete();
                            }
                        } else {
                            Log.e(TAG, "Emergency audio upload failed: code=" + response.code());
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo subir el audio", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<EmergencyAudioRecordingResponse> call, Throwable t) {
                        Log.e(TAG, "Emergency audio upload error", t);
                        Toast.makeText(ProtectedDashboardActivity.this, "Error subiendo audio", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void connectEmergencyStatus(String emergencyId) {
        if (emergencyId == null || emergencyId.isBlank()) {
            return;
        }
        String wsUrl = RetrofitClient.getWebSocketUrl();
        String topic = "/topic/emergency/" + emergencyId + "/status";
        emergencyStatusClient.connect(wsUrl, topic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                if (body != null && body.contains("RESOLVED")) {
                    runOnUiThread(() -> {
                        stopAndUploadEmergencyAudio();
                        emergencyStatusClient.disconnect();
                    });
                }
            }

            @Override
            public void onConnected() {
            }
        });
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

    private void requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = false;
            if (grantResults != null) {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
            }
            if (granted && pendingEmergencyAfterPermission) {
                pendingEmergencyAfterPermission = false;
                triggerEmergency();
            } else if (!granted && pendingEmergencyAfterPermission) {
                pendingEmergencyAfterPermission = false;
                Toast.makeText(this, "Permiso de ubicacion requerido para emergencia", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectRealtime() {
        if (miIdProtegido == null || miIdProtegido.isBlank()) {
            return;
        }
        String wsUrl = RetrofitClient.getWebSocketUrl();
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
                .enqueue(new Callback<java.util.List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<java.util.List<LinkResponse>> call,
                                           Response<java.util.List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (miIdProtegido.equals(link.getProtectedUserId())) {
                                    if ("ACTIVE".equals(link.getStatus())) {
                                        idDelVinculo = link.getId();
                                        return;
                                    }
                                }
                            }
                            AppNavigator.goToDeviceSetup(ProtectedDashboardActivity.this, miIdProtegido);
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.List<LinkResponse>> call, Throwable t) {
                    }
                });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopFamilyGuard();
        if (emergencyStopRunnable != null) {
            emergencyAudioHandler.removeCallbacks(emergencyStopRunnable);
        }
        cleanupEmergencyLocationRequest();
        stopAndUploadEmergencyAudio();
        linkRealtimeClient.disconnect();
        emergencyStatusClient.disconnect();
    }

    private void startFamilyGuard() {
        stopFamilyGuard();
        familyGuardRunnable = new Runnable() {
            @Override
            public void run() {
                FamilyAccessGuard.ensureInFamily(ProtectedDashboardActivity.this, miIdProtegido, () ->
                        familyGuardHandler.postDelayed(this, 5000L)
                );
            }
        };
        familyGuardHandler.post(familyGuardRunnable);
    }

    private void stopFamilyGuard() {
        if (familyGuardRunnable != null) {
            familyGuardHandler.removeCallbacks(familyGuardRunnable);
        }
    }

    private void validateAccessBeforeEmergency(Runnable onAllowed) {
        FamilyAccessGuard.ensureInFamily(this, miIdProtegido, () ->
                RetrofitClient.getApiService().getMyLinks(miIdProtegido).enqueue(new Callback<java.util.List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<java.util.List<LinkResponse>> call, Response<java.util.List<LinkResponse>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo validar tu vinculo", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        for (LinkResponse link : response.body()) {
                            if (miIdProtegido.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                                idDelVinculo = link.getId();
                                onAllowed.run();
                                return;
                            }
                        }
                        AppNavigator.goToDeviceSetup(ProtectedDashboardActivity.this, miIdProtegido);
                    }

                    @Override
                    public void onFailure(Call<java.util.List<LinkResponse>> call, Throwable t) {
                        Toast.makeText(ProtectedDashboardActivity.this, "Error validando vinculo", Toast.LENGTH_SHORT).show();
                    }
                })
        );
    }

}
