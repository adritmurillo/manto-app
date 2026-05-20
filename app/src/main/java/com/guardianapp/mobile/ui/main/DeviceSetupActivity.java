package com.guardianapp.mobile.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.ui.auth.JoinCircleActivity;
import com.guardianapp.mobile.ui.host.HostCodeSetupActivity;

public class DeviceSetupActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    public static final String EXTRA_PREFILL_INVITE_TOKEN = "EXTRA_PREFILL_INVITE_TOKEN";
    public static final String EXTRA_ROLE_PREFILL = "EXTRA_ROLE_PREFILL";
    public static final String ROLE_HOST = "HOST";
    public static final String ROLE_PROTECTED = "PROTECTED";

    private MaterialCardView cardHost;
    private MaterialCardView cardProtected;
    private Button btnContinue;

    private String userId;
    private String inviteToken;
    private String selectedRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_setup);

        userId = getIntent() != null ? getIntent().getStringExtra(EXTRA_USER_ID) : null;
        inviteToken = getIntent() != null ? getIntent().getStringExtra(EXTRA_PREFILL_INVITE_TOKEN) : null;
        selectedRole = getIntent() != null ? getIntent().getStringExtra(EXTRA_ROLE_PREFILL) : null;

        cardHost = findViewById(R.id.cardRoleHost);
        cardProtected = findViewById(R.id.cardRoleProtected);
        btnContinue = findViewById(R.id.btnSetupContinue);
        TextView tvBack = findViewById(R.id.tvBackToLoginFromSetup);

        cardHost.setOnClickListener(v -> selectRole(ROLE_HOST));
        cardProtected.setOnClickListener(v -> selectRole(ROLE_PROTECTED));

        btnContinue.setOnClickListener(v -> continueFlow());
        tvBack.setOnClickListener(v -> finish());

        selectRole(ROLE_PROTECTED.equals(selectedRole) ? ROLE_PROTECTED : ROLE_HOST);
    }

    private void selectRole(String role) {
        selectedRole = role;

        boolean hostSelected = ROLE_HOST.equals(role);
        cardHost.setCardBackgroundColor(getColor(hostSelected ? R.color.role_card_selected : R.color.role_card_default));
        cardProtected.setCardBackgroundColor(getColor(hostSelected ? R.color.role_card_default : R.color.role_card_selected));
        cardHost.setStrokeWidth(hostSelected ? 3 : 0);
        cardProtected.setStrokeWidth(hostSelected ? 0 : 3);
        cardHost.setStrokeColor(getColor(R.color.manto_green));
        cardProtected.setStrokeColor(getColor(R.color.manto_green));

        btnContinue.setEnabled(true);
        btnContinue.setAlpha(1f);
    }

    private void continueFlow() {
        if (userId == null || userId.isBlank()) {
            Toast.makeText(this, "No se encontró el usuario. Inicia sesión nuevamente.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent;
        if (ROLE_HOST.equals(selectedRole)) {
            intent = new Intent(this, HostCodeSetupActivity.class);
            intent.putExtra(HostCodeSetupActivity.EXTRA_USER_ID, userId);
        } else {
            intent = new Intent(this, JoinCircleActivity.class);
            intent.putExtra(JoinCircleActivity.EXTRA_USER_ID, userId);
        }
        if (inviteToken != null && !inviteToken.isBlank()) {
            intent.putExtra(JoinCircleActivity.EXTRA_PREFILL_INVITE_TOKEN, inviteToken);
        }
        startActivity(intent);
        finish();
    }
}
