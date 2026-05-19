package com.guardianapp.mobile.ui.security;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.ui.host.FamilyCircleActivity;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LinkShieldActivity extends AppCompatActivity {

    private final LinkShieldAdapter adapter = new LinkShieldAdapter();
    private String hostId;
    private TextView tvBlockedCount;
    private TextView tvPhishingCount;
    private TextView tvMalwareCount;
    private TextView tvSafeCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_shield);
        hostId = getIntent() != null ? getIntent().getStringExtra("HOST_ID") : null;

        tvBlockedCount = findViewById(R.id.tvShieldBlockedCount);
        tvPhishingCount = findViewById(R.id.tvShieldPhishingCount);
        tvMalwareCount = findViewById(R.id.tvShieldMalwareCount);
        tvSafeCount = findViewById(R.id.tvShieldSafeCount);

        RecyclerView rv = findViewById(R.id.rvLinkShield);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnBackShield).setOnClickListener(v -> finish());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavLinkShield);
        if (bottomNav != null) {
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshContent();
    }

    private void refreshContent() {
        List<SecurityAnalysisItem> source = SecurityAnalysisStore.getAll();
        List<SecurityAnalysisItem> linkItems = new ArrayList<>();
        int phishing = 0;
        int malware = 0;
        int safe = 0;

        for (SecurityAnalysisItem item : source) {
            if (!SecurityAnalysisItem.CHANNEL_SMS.equals(item.getChannel())
                    && !SecurityAnalysisItem.CHANNEL_LINK.equals(item.getChannel())) {
                continue;
            }

            if (item.getUrl() == null || item.getUrl().isBlank()) {
                continue;
            }

            linkItems.add(item);
            String status = normalize(item.getStatus());
            if ("PHISHING".equals(status)) {
                phishing++;
            } else if ("MALWARE".equals(status)) {
                malware++;
            } else if ("SAFE".equals(status) || item.isWhitelisted()) {
                safe++;
            }
        }

        adapter.setItems(linkItems);
        tvBlockedCount.setText(String.valueOf(SecurityAnalysisStore.countBlockedLinksCurrentMonth()));
        tvPhishingCount.setText(phishing + " Phishing");
        tvMalwareCount.setText(malware + " Malware");
        tvSafeCount.setText(safe + " Safe");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
