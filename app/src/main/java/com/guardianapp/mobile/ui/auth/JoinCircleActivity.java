package com.guardianapp.mobile.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.guardianapp.mobile.ui.protecteduser.ProtectedDashboardActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JoinCircleActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    public static final String EXTRA_PREFILL_INVITE_TOKEN = "EXTRA_PREFILL_INVITE_TOKEN";

    private String userId;
    private EditText etInvitationCode;
    private final Handler routeRetryHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_circle);

        userId = getIntent() != null ? getIntent().getStringExtra(EXTRA_USER_ID) : null;

        etInvitationCode = findViewById(R.id.etJoinInvitationCode);
        Button btnValidate = findViewById(R.id.btnValidateJoinCode);
        Button btnBack = findViewById(R.id.btnBackJoinFlow);
        TextView tvPersonalCode = findViewById(R.id.tvPersonalCodeValue);
        TextView tvHelp = findViewById(R.id.tvNoCodeHelp);

        String prefill = getIntent() != null ? getIntent().getStringExtra(EXTRA_PREFILL_INVITE_TOKEN) : null;
        if (prefill != null && !prefill.isBlank()) {
            etInvitationCode.setText(prefill.trim().toUpperCase());
        }

        tvPersonalCode.setText(buildPersonalCode(userId));

        btnValidate.setOnClickListener(v -> validateCode());
        btnBack.setOnClickListener(v -> finish());
        tvHelp.setOnClickListener(v -> Toast.makeText(this, "Solicita el código al administrador familiar", Toast.LENGTH_SHORT).show());
    }

    private String buildPersonalCode(String value) {
        if (value == null || value.isBlank()) {
            return "M4nt0-W15K";
        }
        String clean = value.replace("-", "").toUpperCase();
        if (clean.length() < 8) {
            clean = (clean + "MANTO1234").substring(0, 8);
        }
        return clean.substring(0, 4) + "-" + clean.substring(4, 8);
    }

    private void validateCode() {
        if (userId == null || userId.isBlank()) {
            Toast.makeText(this, "No se encontró el usuario", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = etInvitationCode.getText() != null ? etInvitationCode.getText().toString().trim() : "";
        if (token.isBlank()) {
            Toast.makeText(this, "Ingresa el código de invitación", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getApiService().acceptInvitation(token, userId).enqueue(new Callback<LinkResponse>() {
            @Override
            public void onResponse(Call<LinkResponse> call, Response<LinkResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LinkResponse link = response.body();
                    Toast.makeText(JoinCircleActivity.this, "Código validado", Toast.LENGTH_SHORT).show();
                    Intent intent;
                    if ("ACTIVE".equals(link.getStatus())) {
                        intent = new Intent(JoinCircleActivity.this, ProtectedDashboardActivity.class);
                    } else {
                        intent = new Intent(JoinCircleActivity.this, VerificationActivity.class);
                    }
                    intent.putExtra("PROTECTED_ID", userId);
                    intent.putExtra("LINK_ID", link.getId());
                    startActivity(intent);
                    finish();
                } else {
                    tryAcceptFamilyInvitation(token);
                }
            }

            @Override
            public void onFailure(Call<LinkResponse> call, Throwable t) {
                tryAcceptFamilyInvitation(token);
            }
        });
    }

    private void tryAcceptFamilyInvitation(String token) {
        RetrofitClient.getApiService().acceptFamilyInvitation(token, userId)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(JoinCircleActivity.this, "Te uniste al círculo familiar", Toast.LENGTH_SHORT).show();
                            String role = null;
                            if (response.body().getMembers() != null) {
                                for (FamilyGroupResponse.MemberResponse member : response.body().getMembers()) {
                                    if (member != null && userId.equals(member.getUserId())) {
                                        role = member.getRole();
                                        break;
                                    }
                                }
                            }
                            if ("SECONDARY_HOST".equals(role)) {
                                routeToVerificationOrDashboardWithRetry(true, 0);
                            } else {
                                routeToVerificationOrDashboardWithRetry(false, 0);
                            }
                        } else {
                            Toast.makeText(JoinCircleActivity.this, "Código inválido o expirado", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                        Toast.makeText(JoinCircleActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void routeToVerificationOrDashboardWithRetry(boolean activeGoesToHostDashboard, int attempt) {
        RetrofitClient.getApiService().getMyLinks(userId).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LinkResponse pending = null;
                    LinkResponse active = null;
                    for (LinkResponse link : response.body()) {
                        if (!userId.equals(link.getProtectedUserId())) {
                            continue;
                        }
                        if ("PENDING".equals(link.getStatus())) {
                            pending = link;
                            break;
                        }
                        if (active == null && "ACTIVE".equals(link.getStatus())) {
                            active = link;
                        }
                    }

                    LinkResponse chosen = pending != null ? pending : active;
                    if (chosen != null) {
                        Intent intent;
                        if ("ACTIVE".equals(chosen.getStatus())) {
                            if (activeGoesToHostDashboard) {
                                intent = new Intent(JoinCircleActivity.this, HostDashboardActivity.class);
                                intent.putExtra("HOST_ID", userId);
                            } else {
                                intent = new Intent(JoinCircleActivity.this, ProtectedDashboardActivity.class);
                                intent.putExtra("PROTECTED_ID", userId);
                                intent.putExtra("LINK_ID", chosen.getId());
                            }
                        } else {
                            intent = new Intent(JoinCircleActivity.this, VerificationActivity.class);
                            intent.putExtra("PROTECTED_ID", userId);
                            intent.putExtra("LINK_ID", chosen.getId());
                        }
                        startActivity(intent);
                        finish();
                        return;
                    }
                }

                if (attempt < 6) {
                    routeRetryHandler.postDelayed(() -> routeToVerificationOrDashboardWithRetry(activeGoesToHostDashboard, attempt + 1), 600L);
                } else {
                    Toast.makeText(JoinCircleActivity.this, "No se pudo resolver el vínculo todavía", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                if (attempt < 6) {
                    routeRetryHandler.postDelayed(() -> routeToVerificationOrDashboardWithRetry(activeGoesToHostDashboard, attempt + 1), 600L);
                } else {
                    Toast.makeText(JoinCircleActivity.this, "Error consultando vínculo", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
