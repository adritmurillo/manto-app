package com.guardianapp.mobile.ui.security;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.CreateSmsThreatAlertRequest;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.SmsThreatAlertResponse;
import com.guardianapp.mobile.data.threat.ThreatAnalysisRepository;
import com.guardianapp.mobile.ui.host.FamilyCircleActivity;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SecurityMirrorActivity extends AppCompatActivity {

    private final ThreatAnalysisRepository threatAnalysisRepository = new ThreatAnalysisRepository();
    private final SecurityMirrorAdapter adapter = new SecurityMirrorAdapter();

    private EditText etSender;
    private EditText etMessage;
    private EditText etUrl;
    private TextView tvSectionTitle;
    private String hostId;
    private String protectedUserId;
    private String linkId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_mirror);
        hostId = getIntent() != null ? getIntent().getStringExtra("HOST_ID") : null;
        protectedUserId = getIntent() != null ? getIntent().getStringExtra("PROTECTED_ID") : null;
        linkId = getIntent() != null ? getIntent().getStringExtra("LINK_ID") : null;

        etSender = findViewById(R.id.etMirrorSender);
        etMessage = findViewById(R.id.etMirrorMessage);
        etUrl = findViewById(R.id.etMirrorUrl);
        tvSectionTitle = findViewById(R.id.tvMirrorSectionTitle);

        RecyclerView rv = findViewById(R.id.rvSecurityMirror);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnBackMirror).setOnClickListener(v -> finish());
        findViewById(R.id.btnGoShield).setOnClickListener(v -> {
            Intent intent = new Intent(this, LinkShieldActivity.class);
            startActivity(intent);
        });

        Button btnPhishing = findViewById(R.id.btnSimulatePhishing);
        Button btnWhitelist = findViewById(R.id.btnSimulateWhitelist);
        Button btnAnalyzeCustom = findViewById(R.id.btnAnalyzeCustom);
        Button chipTodos = findViewById(R.id.chipMirrorTodos);
        Button chipBloqueados = findViewById(R.id.chipMirrorBloqueados);
        Button chipSospechosos = findViewById(R.id.chipMirrorSospechosos);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavSecurityMirror);

        btnPhishing.setOnClickListener(v -> simulatePhishing());
        btnWhitelist.setOnClickListener(v -> simulateWhitelist());
        btnAnalyzeCustom.setOnClickListener(v -> analyzeCustomInput());

        chipTodos.setOnClickListener(v -> renderItems(FilterType.ALL));
        chipBloqueados.setOnClickListener(v -> renderItems(FilterType.BLOCKED));
        chipSospechosos.setOnClickListener(v -> renderItems(FilterType.SUSPICIOUS));

        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_security);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_security) {
                    Intent intent = new Intent(this, LinkShieldActivity.class);
                    intent.putExtra("HOST_ID", hostId);
                    startActivity(intent);
                    return true;
                }
                if (id == R.id.nav_home) {
                    if (hostId == null || hostId.isBlank()) {
                        Toast.makeText(this, "Volviendo al flujo protegido", Toast.LENGTH_SHORT).show();
                        finish();
                        return true;
                    }
                    Intent intent = new Intent(this, HostDashboardActivity.class);
                    intent.putExtra("HOST_ID", hostId);
                    startActivity(intent);
                    return true;
                }
                if (id == R.id.nav_family) {
                    if (hostId == null || hostId.isBlank()) {
                        Toast.makeText(this, "Solo disponible para anfitrion", Toast.LENGTH_SHORT).show();
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

        renderItems(FilterType.ALL);
    }

    private void analyzeCustomInput() {
        String sender = etSender.getText().toString().trim();
        String message = etMessage.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        if (sender.isBlank() || message.isBlank() || url.isBlank()) {
            Toast.makeText(this, "Completa remitente, mensaje y URL", Toast.LENGTH_SHORT).show();
            return;
        }
        analyzeAndStore(sender, message, url);
    }

    private void simulatePhishing() {
        String sender = "+21 973749157";
        String message = "URGENTE BCP: cuenta bloqueada por actividad sospechosa. Para evitar bloqueo, ingresa tu clave, PIN y codigo de seguridad ahora.";
        String url = "http://verifica-cuenta-bcp-segura.xyz/login";
        etSender.setText(sender);
        etMessage.setText(message);
        etUrl.setText(url);
        analyzeAndStore(sender, message, url);
    }

    private void simulateWhitelist() {
        String sender = "BCP";
        String message = "[BCP] Tu estado de cuenta mensual esta disponible en tu banca digital oficial.";
        String url = "https://viabcp.com";
        etSender.setText(sender);
        etMessage.setText(message);
        etUrl.setText(url);
        analyzeAndStore(sender, message, url);
    }

    private void analyzeAndStore(String sender, String message, String url) {
        Toast.makeText(this, "Analizando mensaje...", Toast.LENGTH_SHORT).show();
        threatAnalysisRepository.analyzeMessageAndUrl(message, url, sender, new ThreatAnalysisRepository.CallbackResult() {
            @Override
            public void onResult(ThreatAnalysisRepository.ThreatDecision decision) {
                runOnUiThread(() -> {
                    SecurityAnalysisStore.add(new SecurityAnalysisItem(
                            System.currentTimeMillis(),
                            SecurityAnalysisItem.CHANNEL_SMS,
                            sender,
                            message,
                            decision.getAnalyzedUrl() != null ? decision.getAnalyzedUrl() : url,
                            decision.getUrlStatus(),
                            decision.getReason(),
                            decision.isBlocked(),
                            decision.isWhitelisted(),
                            decision.getTrustedProvider()
                    ));
                    maybeCreateHostAlert(sender, message, url, decision);
                    renderItems(FilterType.ALL);
                    String outcome = decision.isWhitelisted()
                            ? "Mensaje marcado en lista blanca."
                            : (decision.isBlocked() ? "Mensaje bloqueado por seguridad." : "Mensaje analizado sin bloqueo.");
                    Toast.makeText(SecurityMirrorActivity.this, outcome, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() ->
                        Toast.makeText(SecurityMirrorActivity.this, "Error analizando: " + error.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void maybeCreateHostAlert(String sender,
                                      String message,
                                      String fallbackUrl,
                                      ThreatAnalysisRepository.ThreatDecision decision) {
        if (!decision.isBlocked()) {
            return;
        }
        if (linkId == null || linkId.isBlank() || protectedUserId == null || protectedUserId.isBlank()) {
            Toast.makeText(this, "Phishing detectado localmente, pero falta LINK_ID/PROTECTED_ID para alertar al anfitrion.", Toast.LENGTH_LONG).show();
            return;
        }
        String analyzedUrl = decision.getAnalyzedUrl() != null ? decision.getAnalyzedUrl() : fallbackUrl;
        CreateSmsThreatAlertRequest request = new CreateSmsThreatAlertRequest(
                linkId,
                protectedUserId,
                sender == null ? "unknown" : sender,
                message == null ? "" : message,
                analyzedUrl == null ? "" : analyzedUrl,
                normalizeThreatStatus(decision),
                decision.getReason()
        );
        RetrofitClient.getApiService().createSmsThreatAlert(request).enqueue(new Callback<SmsThreatAlertResponse>() {
            @Override
            public void onResponse(Call<SmsThreatAlertResponse> call, Response<SmsThreatAlertResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SecurityMirrorActivity.this, "Alerta enviada al anfitrion.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SmsThreatAlertResponse> call, Throwable t) {
            }
        });
    }

    private String normalizeThreatStatus(ThreatAnalysisRepository.ThreatDecision decision) {
        String value = normalize(decision.getUrlStatus());
        if ("PHISHING".equals(value)
                || "MALWARE".equals(value)
                || "UNWANTED".equals(value)
                || "SUSPICIOUS".equals(value)
                || "ERROR".equals(value)) {
            return value;
        }
        return decision.isBlocked() ? "SUSPICIOUS" : "SAFE";
    }

    private void renderItems(FilterType filterType) {
        List<SecurityAnalysisItem> source = SecurityAnalysisStore.getAll();
        List<SecurityAnalysisItem> filtered = new ArrayList<>();
        for (SecurityAnalysisItem item : source) {
            if (!SecurityAnalysisItem.CHANNEL_SMS.equals(item.getChannel())) {
                continue;
            }
            if (filterType == FilterType.BLOCKED && !item.isBlocked()) {
                continue;
            }
            if (filterType == FilterType.SUSPICIOUS
                    && ("SAFE".equals(normalize(item.getStatus())) || item.isWhitelisted())) {
                continue;
            }
            filtered.add(item);
        }
        adapter.setItems(filtered);
        tvSectionTitle.setText(filtered.isEmpty() ? "Sin eventos todavía" : "Bloqueados por seguridad");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private enum FilterType {
        ALL,
        BLOCKED,
        SUSPICIOUS
    }
}
