package com.guardianapp.mobile.ui.host;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.SmsThreatAlertResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HostSmsAlertsActivity extends AppCompatActivity {

    public static final String EXTRA_HOST_ID = "EXTRA_HOST_ID";

    private final HostSmsAlertsAdapter adapter = new HostSmsAlertsAdapter();
    private final List<SmsThreatAlertResponse> allAlerts = new ArrayList<>();
    private String hostId;
    private TextView tvEmpty;
    private TextView tvSummary;
    private TextView tvSectionTitle;
    private EditText etSearch;
    private FilterType currentFilter = FilterType.ALL;
    private Button chipAll;
    private Button chipBlocked;
    private Button chipSuspicious;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_sms_alerts);

        hostId = getIntent() != null ? getIntent().getStringExtra(EXTRA_HOST_ID) : null;

        RecyclerView rvAlerts = findViewById(R.id.rvHostSmsAlerts);
        tvEmpty = findViewById(R.id.tvHostSmsEmpty);
        tvSummary = findViewById(R.id.tvHostSmsSummary);
        tvSectionTitle = findViewById(R.id.tvHostSmsSectionTitle);
        etSearch = findViewById(R.id.etHostSmsSearch);
        chipAll = findViewById(R.id.chipHostSmsAll);
        chipBlocked = findViewById(R.id.chipHostSmsBlocked);
        chipSuspicious = findViewById(R.id.chipHostSmsSuspicious);

        rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        rvAlerts.setAdapter(adapter);

        findViewById(R.id.btnBackHostSmsAlerts).setOnClickListener(v -> finish());
        chipAll.setOnClickListener(v -> applyFilters(FilterType.ALL));
        chipBlocked.setOnClickListener(v -> applyFilters(FilterType.BLOCKED));
        chipSuspicious.setOnClickListener(v -> applyFilters(FilterType.SUSPICIOUS));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters(currentFilter);
            }
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavHostSms);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_security);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    startActivity(new Intent(this, HostDashboardActivity.class)
                            .putExtra("HOST_ID", hostId));
                    finish();
                    return true;
                }
                if (id == R.id.nav_security) {
                    return true;
                }
                if (id == R.id.nav_family) {
                    startActivity(new Intent(this, FamilyCircleActivity.class)
                            .putExtra("HOST_ID", hostId));
                    return true;
                }
                if (id == R.id.nav_settings) {
                    Toast.makeText(this, "Ajustes aun no disponible", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }

        loadHistory();
    }

    private void loadHistory() {
        if (hostId == null || hostId.isBlank()) {
            Toast.makeText(this, "No se encontro el anfitrion", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        RetrofitClient.getApiService().getSmsThreatHistory(hostId)
                .enqueue(new Callback<List<SmsThreatAlertResponse>>() {
                    @Override
                    public void onResponse(Call<List<SmsThreatAlertResponse>> call,
                                           Response<List<SmsThreatAlertResponse>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            tvSummary.setText("No se pudieron cargar los SMS");
                            tvEmpty.setVisibility(View.VISIBLE);
                            allAlerts.clear();
                            adapter.setItems(null);
                            return;
                        }

                        allAlerts.clear();
                        allAlerts.addAll(response.body());
                        updateSummary();
                        applyFilters(currentFilter);
                    }

                    @Override
                    public void onFailure(Call<List<SmsThreatAlertResponse>> call, Throwable t) {
                        tvSummary.setText("Error cargando historial SMS");
                        tvEmpty.setVisibility(View.VISIBLE);
                        allAlerts.clear();
                        adapter.setItems(null);
                    }
                });
    }

    private void updateSummary() {
        long suspiciousCount = 0;
        for (SmsThreatAlertResponse item : allAlerts) {
            if (isSuspicious(item)) {
                suspiciousCount++;
            }
        }
        tvSummary.setText(allAlerts.size() + " SMS registrados, " + suspiciousCount + " sospechosos");
    }

    private void applyFilters(FilterType filterType) {
        currentFilter = filterType;
        List<SmsThreatAlertResponse> filtered = new ArrayList<>();
        String query = normalize(etSearch.getText() == null ? "" : etSearch.getText().toString());

        for (SmsThreatAlertResponse item : allAlerts) {
            if (!matchesFilter(item, filterType)) {
                continue;
            }
            if (!matchesSearch(item, query)) {
                continue;
            }
            filtered.add(item);
        }

        adapter.setItems(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        tvSectionTitle.setText(resolveSectionTitle(filterType));
        updateChipStyles(filterType);
    }

    private boolean matchesFilter(SmsThreatAlertResponse item, FilterType filterType) {
        if (filterType == FilterType.BLOCKED) {
            return isBlocked(item);
        }
        if (filterType == FilterType.SUSPICIOUS) {
            return isSuspicious(item);
        }
        return true;
    }

    private boolean matchesSearch(SmsThreatAlertResponse item, String query) {
        if (query.isBlank()) {
            return true;
        }
        return normalize(item.getSender()).contains(query)
                || normalize(item.getMessageExcerpt()).contains(query)
                || normalize(item.getDetectedUrl()).contains(query);
    }

    private boolean isBlocked(SmsThreatAlertResponse item) {
        if (item == null) {
            return false;
        }
        String lifecycle = normalize(item.getStatus());
        return lifecycle.contains("BLOCKED") || !item.isUrlAllowed();
    }

    private boolean isSuspicious(SmsThreatAlertResponse item) {
        if (item == null) {
            return false;
        }
        String analysisStatus = normalize(item.getAnalysisStatus());
        return isBlocked(item)
                || "PHISHING".equals(analysisStatus)
                || "MALWARE".equals(analysisStatus)
                || "SUSPICIOUS".equals(analysisStatus)
                || "ERROR".equals(analysisStatus)
                || "PENDING".equals(normalize(item.getStatus()));
    }

    private String resolveSectionTitle(FilterType filterType) {
        if (filterType == FilterType.BLOCKED) {
            return "Bloqueados por seguridad";
        }
        if (filterType == FilterType.SUSPICIOUS) {
            return "Mensajes sospechosos";
        }
        return "Todos los mensajes recibidos";
    }

    private void updateChipStyles(FilterType filterType) {
        updateChip(chipAll, filterType == FilterType.ALL);
        updateChip(chipBlocked, filterType == FilterType.BLOCKED);
        updateChip(chipSuspicious, filterType == FilterType.SUSPICIOUS);
    }

    private void updateChip(Button chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_safe : R.drawable.bg_chip_neutral);
        chip.setTextColor(getColor(selected ? android.R.color.white : R.color.black));
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
