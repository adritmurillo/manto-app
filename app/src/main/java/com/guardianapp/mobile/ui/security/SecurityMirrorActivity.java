package com.guardianapp.mobile.ui.security;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.sms.SmsRoleHelper;
import com.guardianapp.mobile.sms.SmsThreatProcessor;
import com.guardianapp.mobile.ui.common.FamilyAccessGuard;
import com.guardianapp.mobile.ui.host.FamilyCircleActivity;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SecurityMirrorActivity extends AppCompatActivity {

    private static final int REQUEST_SMS_PERMISSIONS = 2001;
    private final SecurityMirrorAdapter adapter = new SecurityMirrorAdapter();

    private EditText etSender;
    private EditText etMessage;
    private EditText etUrl;
    private TextView tvSectionTitle;
    private TextView tvMirrorSummary;
    private TextView tvSmsRoleStatus;
    private Button btnRequestDefaultSms;
    private View layoutSmsRoleCard;
    private String hostId;
    private final android.os.Handler familyGuardHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable familyGuardRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_mirror);
        SecurityAnalysisStore.init(getApplicationContext());
        hostId = getIntent() != null ? getIntent().getStringExtra("HOST_ID") : null;

        etSender = findViewById(R.id.etMirrorSender);
        etMessage = findViewById(R.id.etMirrorMessage);
        etUrl = findViewById(R.id.etMirrorUrl);
        tvSectionTitle = findViewById(R.id.tvMirrorSectionTitle);
        tvMirrorSummary = findViewById(R.id.tvMirrorSummary);
        tvSmsRoleStatus = findViewById(R.id.tvSmsRoleStatus);
        btnRequestDefaultSms = findViewById(R.id.btnRequestDefaultSms);
        layoutSmsRoleCard = findViewById(R.id.layoutSmsRoleCard);

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
        btnRequestDefaultSms.setOnClickListener(v -> {
            if (!hasSmsPermissions()) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                        },
                        REQUEST_SMS_PERMISSIONS
                );
                return;
            }
            if (!SmsRoleHelper.canRequestDefaultRole(this)) {
                Toast.makeText(this, "Este dispositivo no permite configurar rol SMS desde aqui.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (SmsRoleHelper.isDefaultSmsApp(this)) {
                Toast.makeText(this, "Manto ya es la app SMS predeterminada.", Toast.LENGTH_SHORT).show();
                return;
            }
            SmsRoleHelper.requestDefaultSmsRole(this);
        });

        chipTodos.setOnClickListener(v -> renderItems(FilterType.ALL));
        chipBloqueados.setOnClickListener(v -> renderItems(FilterType.INBOX));
        chipSospechosos.setOnClickListener(v -> renderItems(FilterType.QUARANTINE));

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

        layoutSmsRoleCard.setVisibility(hostId == null || hostId.isBlank() ? View.VISIBLE : View.GONE);
        updateSmsRoleUi();
        renderItems(FilterType.ALL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSmsRoleUi();
        renderItems(FilterType.ALL);
        startFamilyGuard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopFamilyGuard();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSIONS) {
            updateSmsRoleUi();
            boolean granted = hasSmsPermissions();
            Toast.makeText(
                    this,
                    granted ? "Permisos SMS concedidos." : "Permisos SMS denegados.",
                    Toast.LENGTH_SHORT
            ).show();
        }
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
        String message = "URGENTE BCP: cuenta bloqueada por actividad sospechosa. Para evitar bloqueo, ingresa tu clave y codigo de seguridad ahora.";
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
        SmsThreatProcessor.processIncomingMessage(getApplicationContext(), sender, message, url, new SmsThreatProcessor.ResultCallback() {
            @Override
            public void onProcessed(SmsThreatProcessor.ProcessResult result) {
                runOnUiThread(() -> {
                    renderItems(FilterType.ALL);
                    SecurityAnalysisItem item = result.getItem();
                    String outcome;
                    if ("NO_URL".equals(normalize(item.getStatus()))) {
                        outcome = "SMS recibido sin URL. Se guardo solo como registro local.";
                    } else if (item.isWhitelisted()) {
                        outcome = "Mensaje marcado en lista blanca.";
                    } else if (item.isBlocked()) {
                        outcome = "Mensaje bloqueado por seguridad.";
                    } else {
                        outcome = "Mensaje analizado sin bloqueo.";
                    }
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

    private void renderItems(FilterType filterType) {
        List<SecurityAnalysisItem> source = SecurityAnalysisStore.getAll();
        List<SecurityAnalysisItem> filtered = new ArrayList<>();
        for (SecurityAnalysisItem item : source) {
            if (!SecurityAnalysisItem.CHANNEL_SMS.equals(item.getChannel())) {
                continue;
            }
            if (filterType == FilterType.INBOX && !item.isInInbox()) {
                continue;
            }
            if (filterType == FilterType.QUARANTINE && !item.isInQuarantine()) {
                continue;
            }
            filtered.add(item);
        }
        adapter.setItems(filtered);
        tvSectionTitle.setText(resolveSectionTitle(filterType, filtered.isEmpty()));
        tvMirrorSummary.setText(
                SecurityAnalysisStore.countSmsInboxItems() + " recibidos seguros, "
                        + SecurityAnalysisStore.countSmsQuarantineItems() + " en cuarentena"
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private enum FilterType {
        ALL,
        INBOX,
        QUARANTINE
    }

    private void startFamilyGuard() {
        if (hostId == null || hostId.isBlank()) {
            return;
        }
        stopFamilyGuard();
        familyGuardRunnable = new Runnable() {
            @Override
            public void run() {
                FamilyAccessGuard.ensureInFamily(SecurityMirrorActivity.this, hostId, () ->
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

    private void updateSmsRoleUi() {
        if (layoutSmsRoleCard != null && layoutSmsRoleCard.getVisibility() != View.VISIBLE) {
            return;
        }
        if (!hasSmsPermissions()) {
            tvSmsRoleStatus.setText(getString(R.string.sms_permission_required));
            btnRequestDefaultSms.setEnabled(true);
            btnRequestDefaultSms.setAlpha(1f);
            btnRequestDefaultSms.setText(R.string.sms_permission_request);
            return;
        }
        boolean isDefaultSmsApp = SmsRoleHelper.isDefaultSmsApp(this);
        tvSmsRoleStatus.setText(isDefaultSmsApp
                ? getString(R.string.sms_role_active)
                : getString(R.string.sms_role_required));
        btnRequestDefaultSms.setText(R.string.sms_role_request);
        btnRequestDefaultSms.setEnabled(!isDefaultSmsApp);
        btnRequestDefaultSms.setAlpha(isDefaultSmsApp ? 0.55f : 1f);
    }

    private String resolveSectionTitle(FilterType filterType, boolean empty) {
        if (empty) {
            return "Sin mensajes en esta bandeja";
        }
        if (filterType == FilterType.INBOX) {
            return "SMS recibidos";
        }
        if (filterType == FilterType.QUARANTINE) {
            return "SMS en cuarentena";
        }
        return "Todos los eventos SMS";
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }
}
