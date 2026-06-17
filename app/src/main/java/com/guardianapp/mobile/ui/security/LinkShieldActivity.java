package com.guardianapp.mobile.ui.security;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.AnalyzeSingleUrlResponse;
import com.guardianapp.mobile.data.api.RegisterBlacklistUrlResponse;
import com.guardianapp.mobile.data.security.LinkShieldRepository;
import com.guardianapp.mobile.ui.common.FamilyAccessGuard;
import com.guardianapp.mobile.ui.host.FamilyCircleActivity;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class LinkShieldActivity extends AppCompatActivity {

    private final LinkShieldRepository repository = new LinkShieldRepository();
    private final LinkShieldAdapter adapter = new LinkShieldAdapter();

    private String hostId;
    private EditText etAnalyzeUrl;
    private EditText etBlacklistUrl;
    private TextView tvAnalysisResult;
    private Button btnAnalyzeLink;
    private Button btnBlockDomain;
    private final android.os.Handler familyGuardHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable familyGuardRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_shield);

        hostId = getIntent() != null ? getIntent().getStringExtra("HOST_ID") : null;

        etAnalyzeUrl = findViewById(R.id.etAnalyzeUrl);
        etBlacklistUrl = findViewById(R.id.etBlacklistUrl);
        tvAnalysisResult = findViewById(R.id.tvAnalysisResult);
        btnAnalyzeLink = findViewById(R.id.btnAnalyzeLink);
        btnBlockDomain = findViewById(R.id.btnBlockDomain);
        androidx.recyclerview.widget.RecyclerView rvBlocked = findViewById(R.id.rvLinkShieldBlocked);

        if (rvBlocked != null) {
            rvBlocked.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            rvBlocked.setAdapter(adapter);
        }

        findViewById(R.id.btnBackShield).setOnClickListener(v -> finish());
        btnAnalyzeLink.setOnClickListener(v -> analyzeUrl());
        btnBlockDomain.setOnClickListener(v -> blockDomain());
        adapter.setOnUnblockClickListener((item, position) -> unblockDomain(item));

        android.widget.Button btnGoToAppControl = findViewById(R.id.btnGoToAppControl);
        if (btnGoToAppControl != null) {
            // Se invoca el método inteligente
            btnGoToAppControl.setOnClickListener(v -> navigateToAppControlSafely());
        }

        setupBottomNavigation();
        refreshBlockedList();
    }

    private void navigateToAppControlSafely() {
        if (hostId == null) {
            Toast.makeText(this, "Error: No hay sesión activa", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Conectando con el círculo familiar...", Toast.LENGTH_SHORT).show();

        RetrofitClient.getApiService().getMyFamilyGroups(hostId).enqueue(new retrofit2.Callback<List<FamilyGroupResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<List<FamilyGroupResponse>> call, retrofit2.Response<List<FamilyGroupResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    FamilyGroupResponse group = response.body().get(0);
                    String linkId = group.getId();
                    String protectedId = null;

                    if (group.getMembers() != null) {
                        for (FamilyGroupResponse.MemberResponse member : group.getMembers()) {
                            if ("PROTECTED".equals(member.getRole())) {
                                protectedId = member.getUserId();
                                break;
                            }
                        }
                    }

                    if (protectedId == null) {
                        Toast.makeText(LinkShieldActivity.this, "Aún no tienes a un usuario Protegido en tu círculo", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Intent intent = new Intent(LinkShieldActivity.this, com.guardianapp.mobile.ui.host.HostAppControlActivity.class);
                    intent.putExtra("HOST_ID", hostId);
                    intent.putExtra("LINK_ID", linkId);
                    intent.putExtra("PROTECTED_ID", protectedId);
                    startActivity(intent);

                } else {
                    Toast.makeText(LinkShieldActivity.this, "No se encontró el círculo familiar", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<FamilyGroupResponse>> call, Throwable t) {
                Toast.makeText(LinkShieldActivity.this, "Error de red al buscar el círculo", Toast.LENGTH_SHORT).show();
            }
        });
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

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavLinkShield);
        if (bottomNav == null) {
            return;
        }
        bottomNav.setSelectedItemId(R.id.nav_security);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_security) {
                return true;
            }
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, HostDashboardActivity.class);
                intent.putExtra("HOST_ID", hostId);
                startActivity(intent);
                return true;
            }
            if (id == R.id.nav_family) {
                if (hostId == null || hostId.isBlank()) {
                    Toast.makeText(this, "Host ID no disponible", Toast.LENGTH_SHORT).show();
                    return true;
                }
                Intent intent = new Intent(this, FamilyCircleActivity.class);
                intent.putExtra("HOST_ID", hostId);
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

    private void analyzeUrl() {
        String normalizedUrl = normalizeUrl(etAnalyzeUrl.getText().toString());
        if (normalizedUrl == null) {
            Toast.makeText(this, "Ingresa una URL valida", Toast.LENGTH_SHORT).show();
            return;
        }

        setAnalyzeLoading(true);
        repository.analyzeUrl(normalizedUrl, new LinkShieldRepository.ResultCallback<AnalyzeSingleUrlResponse>() {
            @Override
            public void onSuccess(AnalyzeSingleUrlResponse data) {
                setAnalyzeLoading(false);
                String status = normalizeStatus(data.getStatus());
                String reason = data.getReason() == null || data.getReason().isBlank()
                        ? "Sin detalle del analisis."
                        : data.getReason();
                boolean blocked = isBlockedStatus(status);

                SecurityAnalysisStore.add(new SecurityAnalysisItem(
                        System.currentTimeMillis(),
                        SecurityAnalysisItem.CHANNEL_LINK,
                        "manual-input",
                        "Analisis manual de URL",
                        data.getUrl() != null ? data.getUrl() : normalizedUrl,
                        status,
                        reason,
                        blocked,
                        data.isWhitelisted(),
                        data.getTrustedProvider()
                ));

                etAnalyzeUrl.setText(data.getUrl() != null ? data.getUrl() : normalizedUrl);
                if (etBlacklistUrl.getText().toString().isBlank()) {
                    etBlacklistUrl.setText(data.getUrl() != null ? data.getUrl() : normalizedUrl);
                }
                tvAnalysisResult.setText(buildAnalysisSummary(data, status, reason));
                Toast.makeText(
                        LinkShieldActivity.this,
                        blocked ? "Riesgo detectado. Puedes bloquear el dominio." : "URL segura.",
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onError(Throwable error) {
                setAnalyzeLoading(false);
                if (isStatusCode(error, 400)) {
                    tvAnalysisResult.setText("Error de validacion: URL vacia o invalida.");
                    Toast.makeText(LinkShieldActivity.this, "VALIDATION_ERROR (400)", Toast.LENGTH_SHORT).show();
                    return;
                }
                tvAnalysisResult.setText("No se pudo analizar la URL.");
                Toast.makeText(LinkShieldActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void blockDomain() {
        String normalizedUrl = normalizeUrl(etBlacklistUrl.getText().toString());
        if (normalizedUrl == null) {
            Toast.makeText(this, "Ingresa una URL valida para bloquear", Toast.LENGTH_SHORT).show();
            return;
        }

        setBlockLoading(true);
        repository.registerBlacklistUrl(normalizedUrl, new LinkShieldRepository.ResultCallback<RegisterBlacklistUrlResponse>() {
            @Override
            public void onSuccess(RegisterBlacklistUrlResponse data) {
                setBlockLoading(false);
                SecurityAnalysisStore.add(new SecurityAnalysisItem(
                        System.currentTimeMillis(),
                        SecurityAnalysisItem.CHANNEL_LINK,
                        "manual-blacklist",
                        "Bloqueo manual en lista negra",
                        data.getUrl() != null ? data.getUrl() : normalizedUrl,
                        "BLACKLISTED",
                        "Dominio agregado a lista negra",
                        true,
                        false,
                        null
                ));
                tvAnalysisResult.setText("Dominio bloqueado en lista negra:\n" + (data.getUrl() != null ? data.getUrl() : normalizedUrl));
                Toast.makeText(LinkShieldActivity.this, "Dominio bloqueado correctamente", Toast.LENGTH_SHORT).show();
                refreshBlockedList();
            }

            @Override
            public void onError(Throwable error) {
                setBlockLoading(false);
                if (isStatusCode(error, 409)) {
                    Toast.makeText(LinkShieldActivity.this, "La URL ya existe en lista negra (409)", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isStatusCode(error, 400)) {
                    Toast.makeText(LinkShieldActivity.this, "VALIDATION_ERROR (400)", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(LinkShieldActivity.this, "No se pudo bloquear: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void unblockDomain(SecurityAnalysisItem item) {
        String url = item != null ? item.getUrl() : null;
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null) {
            Toast.makeText(this, "URL invalida", Toast.LENGTH_SHORT).show();
            return;
        }
        repository.removeBlacklistUrl(normalizedUrl, new LinkShieldRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                SecurityAnalysisStore.removeByUrl(normalizedUrl);
                Toast.makeText(LinkShieldActivity.this, "Dominio desbloqueado", Toast.LENGTH_SHORT).show();
                refreshBlockedList();
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(LinkShieldActivity.this, "No se pudo desbloquear", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshBlockedList() {
        List<SecurityAnalysisItem> items = SecurityAnalysisStore.getAll().stream()
                .filter(SecurityAnalysisItem::isBlocked)
                .collect(Collectors.toList());
        adapter.setItems(items);
    }

    private String normalizeUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isBlank()) {
            return null;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        if (!Patterns.WEB_URL.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    private String normalizeStatus(String status) {
        return status == null ? "UNKNOWN" : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlockedStatus(String status) {
        return "PHISHING".equals(status)
                || "MALWARE".equals(status)
                || "UNWANTED".equals(status)
                || "SUSPICIOUS".equals(status)
                || "ERROR".equals(status);
    }

    private void setAnalyzeLoading(boolean loading) {
        btnAnalyzeLink.setEnabled(!loading);
        btnAnalyzeLink.setText(loading ? "Analizando..." : "Analizar Link");
    }

    private void setBlockLoading(boolean loading) {
        btnBlockDomain.setEnabled(!loading);
        btnBlockDomain.setText(loading ? "Bloqueando..." : "Bloquear Dominio");
    }

    private String buildAnalysisSummary(AnalyzeSingleUrlResponse data, String status, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estado: ").append(status).append('\n');
        sb.append("Reason: ").append(reason).append('\n');
        sb.append("Score: ").append(data.getHeuristicScore()).append('\n');
        sb.append("Signals: ").append(joinSignals(data.getSignals())).append('\n');
        sb.append("Source: ").append(safeValue(data.getSource())).append('\n');
        sb.append("DetectedAt: ").append(safeValue(data.getDetectedAt())).append('\n');
        sb.append("Whitelisted: ").append(data.isWhitelisted() ? "true" : "false");
        return sb.toString();
    }

    private String joinSignals(List<String> signals) {
        if (signals == null || signals.isEmpty()) {
            return "-";
        }
        return String.join(", ", signals);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean isStatusCode(Throwable error, int statusCode) {
        return error instanceof LinkShieldRepository.ApiFailure
                && ((LinkShieldRepository.ApiFailure) error).getStatusCode() == statusCode;
    }

    private void startFamilyGuard() {
        if (hostId == null || hostId.isBlank()) {
            return;
        }
        stopFamilyGuard();
        familyGuardRunnable = new Runnable() {
            @Override
            public void run() {
                FamilyAccessGuard.ensureInFamily(LinkShieldActivity.this, hostId, () ->
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
}