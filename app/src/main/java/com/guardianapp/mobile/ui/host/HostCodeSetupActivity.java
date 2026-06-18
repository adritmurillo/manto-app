package com.guardianapp.mobile.ui.host;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.CreateFamilyGroupRequest;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.InvitationResponse;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HostCodeSetupActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";

    private EditText etCircleName;
    private TextView tvInvitationCode;
    private Button btnGenerate;
    private Button btnShare;
    private Button btnContinue;

    private String userId;
    private String lastInviteLink;
    private final Handler linkPollingHandler = new Handler(Looper.getMainLooper());
    private Runnable linkPollingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_code_setup);

        userId = getIntent() != null ? getIntent().getStringExtra(EXTRA_USER_ID) : null;

        etCircleName     = findViewById(R.id.etName);
        tvInvitationCode = findViewById(R.id.tvGeneratedCode);
        btnGenerate      = findViewById(R.id.btnGenerateCode);
        btnShare         = findViewById(R.id.btnShareInviteLink);
        btnContinue      = findViewById(R.id.btnSetupContinue);

        etCircleName.setText(buildDefaultCircleName());
        btnShare.setEnabled(false);

        // tvBack.setOnClickListener(v -> finish());
        btnGenerate.setOnClickListener(v -> generateInvitationCode());
        btnShare.setOnClickListener(v -> shareOrCopyInviteLink());
        btnContinue.setOnClickListener(v -> openHostDashboard());
    }

    private String buildDefaultCircleName() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String display = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (display != null && !display.isBlank()) {
                return "Familia " + display;
            }
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email != null && email.contains("@")) {
                return "Familia " + email.split("@")[0];
            }
        }
        return "Familia Segura";
    }

    private void generateInvitationCode() {
        if (userId == null || userId.isBlank()) {
            Toast.makeText(this, "No se encontró el usuario anfitrión", Toast.LENGTH_SHORT).show();
            return;
        }
        btnGenerate.setEnabled(false);
        ensurePrimaryFamilyGroupThenGenerateInvitation();
    }

    private void ensurePrimaryFamilyGroupThenGenerateInvitation() {
        RetrofitClient.getApiService().getMyFamilyGroups(userId)
                .enqueue(new Callback<List<FamilyGroupResponse>>() {
                    @Override
                    public void onResponse(Call<List<FamilyGroupResponse>> call, Response<List<FamilyGroupResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            doGenerateInvitation();
                            return;
                        }

                        String name = etCircleName.getText() != null ? etCircleName.getText().toString().trim() : "";
                        if (name.isBlank()) {
                            name = buildDefaultCircleName();
                        }
                        RetrofitClient.getApiService()
                                .createFamilyGroup(userId, new CreateFamilyGroupRequest(name))
                                .enqueue(new Callback<FamilyGroupResponse>() {
                                    @Override
                                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                                        if (response.isSuccessful() && response.body() != null) {
                                            doGenerateInvitation();
                                        } else {
                                            btnGenerate.setEnabled(true);
                                            Toast.makeText(HostCodeSetupActivity.this, "No se pudo crear el círculo", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                                        btnGenerate.setEnabled(true);
                                        Toast.makeText(HostCodeSetupActivity.this, "Error de red creando círculo", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<List<FamilyGroupResponse>> call, Throwable t) {
                        btnGenerate.setEnabled(true);
                        Toast.makeText(HostCodeSetupActivity.this, "Error consultando círculos", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void doGenerateInvitation() {
        RetrofitClient.getApiService().createInvitation(userId).enqueue(new Callback<InvitationResponse>() {
            @Override
            public void onResponse(Call<InvitationResponse> call, Response<InvitationResponse> response) {
                btnGenerate.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    String token = response.body().getToken();
                    tvInvitationCode.setText(token);
                    lastInviteLink = response.body().getShareableLink();
                    btnShare.setEnabled(lastInviteLink != null && !lastInviteLink.isBlank());
                    Toast.makeText(HostCodeSetupActivity.this, "Código generado", Toast.LENGTH_SHORT).show();
                    startLinkPolling();
                } else {
                    Toast.makeText(HostCodeSetupActivity.this, "Error al generar código", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<InvitationResponse> call, Throwable t) {
                btnGenerate.setEnabled(true);
                Toast.makeText(HostCodeSetupActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareOrCopyInviteLink() {
        if (lastInviteLink == null || lastInviteLink.isBlank()) {
            String token = tvInvitationCode.getText() != null ? tvInvitationCode.getText().toString() : "";
            if (!token.isBlank() && !"M4nt0-W15K".equals(token)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("codigo", token));
                    Toast.makeText(this, "Código copiado al portapapeles", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Primero genera un código", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        String token = tvInvitationCode.getText() != null ? tvInvitationCode.getText().toString() : "";
        share.putExtra(Intent.EXTRA_TEXT, lastInviteLink + "\n\nCódigo: " + token);
        try {
            startActivity(Intent.createChooser(share, "Compartir invitación"));
        } catch (Exception ex) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("invite", lastInviteLink));
                Toast.makeText(this, "Link copiado al portapapeles", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLinkPolling() {
        stopLinkPolling();
        linkPollingRunnable = new Runnable() {
            @Override
            public void run() {
                RetrofitClient.getApiService().getMyLinks(userId).enqueue(new Callback<List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (userId.equals(link.getHostId())
                                        && ("PENDING".equals(link.getStatus()) || "ACTIVE".equals(link.getStatus()))) {
                                    openHostDashboard();
                                    return;
                                }
                            }
                        }
                        linkPollingHandler.postDelayed(linkPollingRunnable, 3000);
                    }

                    @Override
                    public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                        linkPollingHandler.postDelayed(linkPollingRunnable, 3000);
                    }
                });
            }
        };
        linkPollingHandler.post(linkPollingRunnable);
    }

    private void stopLinkPolling() {
        if (linkPollingRunnable != null) {
            linkPollingHandler.removeCallbacks(linkPollingRunnable);
        }
    }

    private void openHostDashboard() {
        stopLinkPolling();
        Intent intent = new Intent(this, HostDashboardActivity.class);
        intent.putExtra("HOST_ID", userId);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLinkPolling();
    }
}
