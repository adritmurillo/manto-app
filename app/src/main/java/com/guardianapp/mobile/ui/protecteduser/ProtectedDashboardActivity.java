package com.guardianapp.mobile.ui.protecteduser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.EmergencyAlertResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.TriggerEmergencyAlertRequest;
import com.guardianapp.mobile.data.api.EmergencyAudioRecordingResponse;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.audio.EmergencyLiveAudioStreamer;
import com.guardianapp.mobile.data.realtime.StompRealtimeClient;
import com.guardianapp.mobile.overlay.OverlayPermissionHelper;
import com.guardianapp.mobile.service.EmergencyShortcutAccessibilityService;
import com.guardianapp.mobile.service.FloatingSosOverlayService;
import com.guardianapp.mobile.sms.SmsAccessHelper;
import com.guardianapp.mobile.sms.SmsRoleHelper;
import com.guardianapp.mobile.ui.main.MainActivity;
import com.guardianapp.mobile.ui.common.AppNavigator;
import com.guardianapp.mobile.ui.common.FamilyAccessGuard;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.content.pm.ApplicationInfo;

import com.guardianapp.mobile.data.api.GuardianApiService;
import com.guardianapp.mobile.data.api.ReportInstalledAppsRequest;
import com.guardianapp.mobile.data.api.BlockedAppResponse;
import com.guardianapp.mobile.data.appcontrol.BlockedApp;
import com.guardianapp.mobile.data.appcontrol.BlockedAppsStore;

public class ProtectedDashboardActivity extends AppCompatActivity {

    private static final String TAG = "ProtectedDashboard";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1201;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1301;
    private static final long EMERGENCY_AUDIO_MAX_DURATION_MS = 30 * 60 * 1000L;
    private static final int EMERGENCY_VOLUME_PRESS_TARGET = 3;
    private static final long EMERGENCY_VOLUME_PRESS_WINDOW_MS = 1500L;

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
    private int volumeDownPressCount;
    private long lastVolumeDownPressAt;
    private boolean smsPermissionRequestedOnce;
    private boolean smsRoleRequestedOnce;
    private boolean smsSettingsPromptShown;

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

        Button btnSos = findViewById(R.id.btnSos);
        Button btnEnableShortcut = findViewById(R.id.btnEnableShortcut);
        Button btnEnableFloatingSos = findViewById(R.id.btnEnableFloatingSos);
        Button btnViewSms = findViewById(R.id.btnViewSms);
        TextView tvLogout = findViewById(R.id.tvLogoutProtected);
        emergencyLiveAudioStreamer = new EmergencyLiveAudioStreamer();
        persistProtectedSession();

        btnSos.setOnClickListener(v -> validateAccessBeforeEmergency(this::showEmergencyConfirmationDialog));
        if (btnEnableShortcut != null) {
            btnEnableShortcut.setOnClickListener(v -> openAccessibilitySettings());
        }
        if (btnEnableFloatingSos != null) {
            btnEnableFloatingSos.setOnClickListener(v -> toggleFloatingSos());
        }
        if (btnViewSms != null) {
            btnViewSms.setOnClickListener(v -> openSmsInbox());
        }

        checkActiveEmergencyOnEntry();

        tvLogout.setOnClickListener(v -> {
            linkRealtimeClient.disconnect();
            stopFloatingSosService();
            ProtectedSessionStore.clear(this);
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        connectRealtime();

        // ¡SOLUCIÓN AQUÍ! Ya no forzamos la sincronización ciegamente.
        // Solo la ejecutamos si ya tenemos ambos IDs a la mano.
        triggerSyncIfReady();
    }

    // NUEVO MÉTODO INTELIGENTE: Verifica que existan los IDs antes de llamar al servidor
    private void triggerSyncIfReady() {
        if (miIdProtegido != null && !miIdProtegido.isBlank()) {
            android.util.Log.d(TAG, "ID Protegido confirmado. Buscando el grupo familiar...");

            // 1. Le preguntamos al servidor en qué Grupo Familiar está tu hermano
            RetrofitClient.getApiService().getMyFamilyGroups(miIdProtegido).enqueue(new retrofit2.Callback<java.util.List<com.guardianapp.mobile.data.api.FamilyGroupResponse>>() {
                @Override
                public void onResponse(retrofit2.Call<java.util.List<com.guardianapp.mobile.data.api.FamilyGroupResponse>> call, retrofit2.Response<java.util.List<com.guardianapp.mobile.data.api.FamilyGroupResponse>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {

                        // ¡MAGIA! Extraemos el ID REAL de la familia.
                        String realFamilyGroupId = response.body().get(0).getId();
                        android.util.Log.d(TAG, "Grupo familiar encontrado: " + realFamilyGroupId);

                        // 2. Ahora SÍ sincronizamos usando el cajón correcto
                        syncAppControlData(miIdProtegido, realFamilyGroupId);

                    } else {
                        android.util.Log.e(TAG, "El protegido no está en un grupo familiar activo.");
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<java.util.List<com.guardianapp.mobile.data.api.FamilyGroupResponse>> call, Throwable t) {
                    android.util.Log.e(TAG, "Error de red buscando el grupo familiar: " + t.getMessage());
                }
            });
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (handleEmergencyVolumeShortcut()) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startFamilyGuard();
        updateShortcutButtonState();
        updateFloatingSosButtonState();
        ensureSmsProtectionReady();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopFamilyGuard();
    }

    private void recoverLinkIdFromBackend() {
        RetrofitClient.getApiService().getMyLinks(miIdProtegido)
                .enqueue(new Callback<List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<List<LinkResponse>> call,
                                           Response<List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (miIdProtegido.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                                    idDelVinculo = link.getId();
                                    persistProtectedSession();

                                    // ¡SOLUCIÓN AQUÍ! Ahora que el servidor nos dio el ID correcto, descargamos las apps.
                                    triggerSyncIfReady();
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                        Log.e(TAG, "Fallo al recuperar el Link ID", t);
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

    private boolean handleEmergencyVolumeShortcut() {
        long now = System.currentTimeMillis();
        if (now - lastVolumeDownPressAt > EMERGENCY_VOLUME_PRESS_WINDOW_MS) {
            volumeDownPressCount = 0;
        }

        lastVolumeDownPressAt = now;
        volumeDownPressCount++;

        if (volumeDownPressCount >= EMERGENCY_VOLUME_PRESS_TARGET) {
            volumeDownPressCount = 0;
            validateAccessBeforeEmergency(this::showEmergencyConfirmationDialog);
            Toast.makeText(this, "Atajo SOS detectado", Toast.LENGTH_SHORT).show();
            return true;
        }

        return false;
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
                .enqueue(new Callback<List<EmergencyAlertResponse>>() {
                    @Override
                    public void onResponse(Call<List<EmergencyAlertResponse>> call,
                                           Response<List<EmergencyAlertResponse>> response) {
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
                    public void onFailure(Call<List<EmergencyAlertResponse>> call, Throwable t) {
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

    @SuppressLint("MissingPermission")
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
            return;
        }

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (SmsAccessHelper.hasSmsPermissions(this)) {
                smsSettingsPromptShown = false;
                requestDefaultSmsRoleIfNeeded();
            } else {
                showSmsSettingsDialog();
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
                .enqueue(new Callback<List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<List<LinkResponse>> call,
                                           Response<List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (miIdProtegido.equals(link.getProtectedUserId())) {
                                    if ("ACTIVE".equals(link.getStatus())) {
                                        idDelVinculo = link.getId();
                                        persistProtectedSession();

                                        // ¡SOLUCIÓN AQUÍ! Descargamos apps cuando la conexión en tiempo real confirma el vínculo
                                        triggerSyncIfReady();
                                        return;
                                    }
                                }
                            }
                            AppNavigator.goToDeviceSetup(ProtectedDashboardActivity.this, miIdProtegido);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
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
                FamilyAccessGuard.ensureProtectedLinked(ProtectedDashboardActivity.this, miIdProtegido, () ->
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

    private void persistProtectedSession() {
        ProtectedSessionStore.save(this, miIdProtegido, idDelVinculo);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void updateShortcutButtonState() {
        Button btnEnableShortcut = findViewById(R.id.btnEnableShortcut);
        if (btnEnableShortcut == null) {
            return;
        }
        boolean enabled = EmergencyShortcutAccessibilityService.isEnabled(this);
        btnEnableShortcut.setText(enabled
                ? getString(R.string.sos_shortcut_enabled)
                : getString(R.string.enable_sos_shortcut));
        btnEnableShortcut.setEnabled(!enabled);
        btnEnableShortcut.setAlpha(enabled ? 0.7f : 1f);
    }

    private void updateFloatingSosButtonState() {
        Button btnEnableFloatingSos = findViewById(R.id.btnEnableFloatingSos);
        if (btnEnableFloatingSos == null) {
            return;
        }
        boolean running = FloatingSosOverlayService.isRunning();
        btnEnableFloatingSos.setText(running
                ? getString(R.string.sos_floating_button_enabled)
                : getString(R.string.enable_sos_floating_button));
        btnEnableFloatingSos.setEnabled(true);
        btnEnableFloatingSos.setAlpha(1f);
    }

    private void toggleFloatingSos() {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permiso overlay requerido")
                    .setMessage("Permite a Manto mostrarse sobre otras apps para usar el boton SOS flotante.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Abrir ajustes", (dialog, which) -> OverlayPermissionHelper.openOverlaySettings(this))
                    .show();
            return;
        }

        if (FloatingSosOverlayService.isRunning()) {
            stopFloatingSosService();
            Toast.makeText(this, "Boton SOS flotante desactivado", Toast.LENGTH_SHORT).show();
        } else {
            startFloatingSosService();
            Toast.makeText(this, "Intentando activar boton SOS flotante", Toast.LENGTH_SHORT).show();
        }
        updateFloatingSosButtonState();
    }

    private void startFloatingSosService() {
        Intent intent = new Intent(this, FloatingSosOverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopFloatingSosService() {
        stopService(new Intent(this, FloatingSosOverlayService.class));
    }

    private void openSmsInbox() {
        Intent intent = new Intent(this, ProtectedSmsInboxActivity.class);
        startActivity(intent);
    }

    private void ensureSmsProtectionReady() {
        if (!SmsAccessHelper.hasSmsPermissions(this)) {
            if (!smsPermissionRequestedOnce) {
                smsPermissionRequestedOnce = true;
                SmsAccessHelper.requestSmsPermissions(this, SMS_PERMISSION_REQUEST_CODE);
            } else if (!smsSettingsPromptShown) {
                showSmsSettingsDialog();
            }
            return;
        }

        smsSettingsPromptShown = false;
        requestDefaultSmsRoleIfNeeded();
    }

    private void requestDefaultSmsRoleIfNeeded() {
        if (SmsRoleHelper.isDefaultSmsApp(this)) {
            return;
        }
        if (!SmsRoleHelper.canRequestDefaultRole(this)) {
            Toast.makeText(this, "Tu dispositivo no permite asignar el rol SMS desde esta pantalla", Toast.LENGTH_LONG).show();
            return;
        }
        if (smsRoleRequestedOnce) {
            return;
        }
        smsRoleRequestedOnce = true;
        try {
            SmsRoleHelper.requestDefaultSmsRole(this);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No se pudo abrir el selector de app SMS", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSmsSettingsDialog() {
        if (smsSettingsPromptShown) {
            return;
        }
        smsSettingsPromptShown = true;

        new AlertDialog.Builder(this)
                .setTitle("Permiso SMS requerido")
                .setMessage("Tu telefono esta bloqueando el acceso a SMS. Abre los ajustes de la app, permite SMS y vuelve a Manto para activar la proteccion en tiempo real.")
                .setNegativeButton("Mas tarde", null)
                .setPositiveButton("Abrir ajustes", (dialog, which) -> SmsAccessHelper.openAppDetailsSettings(this))
                .show();
    }

    private void validateAccessBeforeEmergency(Runnable onAllowed) {
        FamilyAccessGuard.ensureProtectedLinked(this, miIdProtegido, () ->
                RetrofitClient.getApiService().getMyLinks(miIdProtegido).enqueue(new Callback<List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo validar tu vinculo", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        for (LinkResponse link : response.body()) {
                            if (miIdProtegido.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                                idDelVinculo = link.getId();
                                persistProtectedSession();
                                onAllowed.run();
                                return;
                            }
                        }
                        AppNavigator.goToDeviceSetup(ProtectedDashboardActivity.this, miIdProtegido);
                    }

                    @Override
                    public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                        Toast.makeText(ProtectedDashboardActivity.this, "Error validando vinculo", Toast.LENGTH_SHORT).show();
                    }
                })
        );
    }

    // ==========================================
    // SISTEMA DE CONTROL DE APPS (SINCRONIZACIÓN)
    // ==========================================

    private void syncAppControlData(String protectedUserId, String familyGroupId) {
        if (!BlockedAppsStore.shouldSync(this)) {
            Log.d("AppControl", "Caché reciente...");
            return;
        }

        GuardianApiService apiService = RetrofitClient.getApiService();
        reportInstalledAppsToBackend(apiService, protectedUserId);
        downloadBlockedApps(apiService, protectedUserId, familyGroupId);
    }

    private void reportInstalledAppsToBackend(GuardianApiService apiService, String protectedUserId) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<ReportInstalledAppsRequest.AppInfo> appsToReport = new ArrayList<>();

        for (ApplicationInfo packageInfo : packages) {
            // Filtro pro: Solo enviamos apps normales, ignoramos las internas del sistema Android
            if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                String appName = pm.getApplicationLabel(packageInfo).toString();
                String packageName = packageInfo.packageName;
                appsToReport.add(new ReportInstalledAppsRequest.AppInfo(packageName, appName));
            }
        }

        ReportInstalledAppsRequest request = new ReportInstalledAppsRequest(appsToReport);

        apiService.reportInstalledApps(protectedUserId, request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("AppControl", "Lista de apps reportada con éxito al backend.");
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("AppControl", "Error reportando apps: " + t.getMessage());
            }
        });
    }

    private void downloadBlockedApps(GuardianApiService apiService, String protectedUserId, String familyGroupId) {
        apiService.getMyRestrictions(protectedUserId, familyGroupId).enqueue(new Callback<List<BlockedAppResponse>>() {
            @Override
            public void onResponse(Call<List<BlockedAppResponse>> call, Response<List<BlockedAppResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<BlockedApp> localApps = new ArrayList<>();
                    for (BlockedAppResponse dto : response.body()) {
                        // Verificamos si los datos no llegan nulos
                        if (dto.getPackageName() != null) {
                            localApps.add(new BlockedApp(dto.getPackageName(), dto.getAppName()));
                        }
                    }

                    BlockedAppsStore.save(ProtectedDashboardActivity.this, localApps);

                    // 🚨 EL TOAST DE RAYOS X 🚨
                    Toast.makeText(ProtectedDashboardActivity.this,
                            "🛡️ Escudo sincronizado: " + localApps.size() + " apps en lista negra.",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ProtectedDashboardActivity.this, "❌ Error del servidor al descargar: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<List<BlockedAppResponse>> call, Throwable t) {
                Toast.makeText(ProtectedDashboardActivity.this, "❌ Fallo de red: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}