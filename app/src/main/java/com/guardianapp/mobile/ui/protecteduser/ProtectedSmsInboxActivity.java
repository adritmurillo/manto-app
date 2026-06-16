package com.guardianapp.mobile.ui.protecteduser;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.sms.DeviceSmsInboxRepository;
import com.guardianapp.mobile.sms.SmsAccessHelper;
import com.guardianapp.mobile.sms.SmsRoleHelper;
import com.guardianapp.mobile.ui.security.SecurityAnalysisItem;
import com.guardianapp.mobile.ui.security.SecurityAnalysisStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProtectedSmsInboxActivity extends AppCompatActivity {

    private static final int REQUEST_SMS_PERMISSIONS = 2301;

    private final ProtectedSmsInboxAdapter adapter = new ProtectedSmsInboxAdapter();
    private TextView tvSectionTitle;
    private TextView tvEmpty;
    private FilterType currentFilter = FilterType.ALL;
    private boolean defaultRoleRequestedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_protected_sms_inbox);
        SecurityAnalysisStore.init(getApplicationContext());

        tvSectionTitle = findViewById(R.id.tvProtectedSmsSectionTitle);
        tvEmpty = findViewById(R.id.tvProtectedSmsEmpty);

        RecyclerView rvMessages = findViewById(R.id.rvProtectedSmsMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        findViewById(R.id.btnBackProtectedSms).setOnClickListener(v -> finish());
        findViewById(R.id.chipProtectedSmsAll).setOnClickListener(v -> applyFilter(FilterType.ALL));
        findViewById(R.id.chipProtectedSmsBlocked).setOnClickListener(v -> applyFilter(FilterType.BLOCKED));
        findViewById(R.id.chipProtectedSmsSuspicious).setOnClickListener(v -> applyFilter(FilterType.SUSPICIOUS));

        applyFilter(FilterType.ALL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureSmsPermissionsAndDefaultRole();
        applyFilter(currentFilter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSIONS) {
            if (SmsAccessHelper.hasSmsPermissions(this)) {
                requestDefaultSmsRoleIfNeeded();
            } else {
                Toast.makeText(this, "Permisos SMS denegados", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void ensureSmsPermissionsAndDefaultRole() {
        if (!SmsAccessHelper.hasSmsPermissions(this)) {
            SmsAccessHelper.requestSmsPermissions(this, REQUEST_SMS_PERMISSIONS);
            return;
        }
        requestDefaultSmsRoleIfNeeded();
    }

    private void requestDefaultSmsRoleIfNeeded() {
        if (defaultRoleRequestedOnce) {
            return;
        }
        if (!SmsRoleHelper.canRequestDefaultRole(this)) {
            return;
        }
        if (SmsRoleHelper.isDefaultSmsApp(this)) {
            return;
        }
        defaultRoleRequestedOnce = true;
        SmsRoleHelper.requestDefaultSmsRole(this);
    }

    private void applyFilter(FilterType filterType) {
        currentFilter = filterType;
        List<SecurityAnalysisItem> source = DeviceSmsInboxRepository.loadInbox(this);
        List<SecurityAnalysisItem> filtered = new ArrayList<>();

        for (SecurityAnalysisItem item : source) {
            if (filterType == FilterType.BLOCKED && !isBlocked(item)) {
                continue;
            }
            if (filterType == FilterType.SUSPICIOUS && !isSuspicious(item)) {
                continue;
            }
            filtered.add(item);
        }

        adapter.setItems(filtered);
        tvSectionTitle.setText(resolveSectionTitle(filterType));
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isBlocked(SecurityAnalysisItem item) {
        return item != null && item.isBlocked();
    }

    private boolean isSuspicious(SecurityAnalysisItem item) {
        if (item == null) {
            return false;
        }
        String status = normalize(item.getStatus());
        return item.isBlocked()
                || "SUSPICIOUS".equals(status)
                || "PHISHING".equals(status)
                || "MALWARE".equals(status)
                || "ERROR".equals(status);
    }

    private String resolveSectionTitle(FilterType filterType) {
        if (filterType == FilterType.BLOCKED) {
            return "Bloqueados por seguridad";
        }
        if (filterType == FilterType.SUSPICIOUS) {
            return "Mensajes sospechosos";
        }
        return "Todos tus mensajes";
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
