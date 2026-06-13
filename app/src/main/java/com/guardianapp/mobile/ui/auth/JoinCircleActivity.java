package com.guardianapp.mobile.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JoinCircleActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    public static final String EXTRA_PREFILL_INVITE_TOKEN = "EXTRA_PREFILL_INVITE_TOKEN";

    private String userId;
    private EditText etInvitationCode;

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
            etInvitationCode.setText(normalizeInvitationInput(prefill));
        }

        tvPersonalCode.setText(buildPersonalCode(userId));

        btnValidate.setOnClickListener(v -> validateCode());
        btnBack.setOnClickListener(v -> finish());
        tvHelp.setOnClickListener(v ->
                Toast.makeText(this, "Solicita el codigo al administrador familiar", Toast.LENGTH_SHORT).show()
        );
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
            Toast.makeText(this, "No se encontro el usuario", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawValue = etInvitationCode.getText() != null ? etInvitationCode.getText().toString() : "";
        String token = normalizeInvitationInput(rawValue);
        if (token.isBlank()) {
            Toast.makeText(this, "Ingresa el codigo de invitacion", Toast.LENGTH_SHORT).show();
            return;
        }
        etInvitationCode.setText(token);

        RetrofitClient.getApiService().acceptInvitation(token, userId).enqueue(new Callback<LinkResponse>() {
            @Override
            public void onResponse(Call<LinkResponse> call, Response<LinkResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(JoinCircleActivity.this, "Codigo validado", Toast.LENGTH_SHORT).show();
                    openProtectedDashboard(response.body().getId());
                    return;
                }

                String invitationError = buildInviteErrorMessage(response);
                tryAcceptFamilyInvitation(token, invitationError);
            }

            @Override
            public void onFailure(Call<LinkResponse> call, Throwable t) {
                tryAcceptFamilyInvitation(token, "Error de red validando el codigo");
            }
        });
    }

    private void tryAcceptFamilyInvitation(String token, String previousError) {
        RetrofitClient.getApiService().acceptFamilyInvitation(token, userId)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful()) {
                            String role = extractJoinedRole(response.body());
                            Toast.makeText(JoinCircleActivity.this, "Te uniste al circulo familiar", Toast.LENGTH_SHORT).show();

                            if ("SECONDARY_HOST".equals(role)) {
                                Intent intent = new Intent(JoinCircleActivity.this, HostDashboardActivity.class);
                                intent.putExtra("HOST_ID", userId);
                                startActivity(intent);
                                finish();
                            } else {
                                openProtectedDashboard(null);
                            }
                            return;
                        }

                        String familyError = buildInviteErrorMessage(response);
                        Toast.makeText(JoinCircleActivity.this, selectBestError(previousError, familyError), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                        Toast.makeText(JoinCircleActivity.this, previousError, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String extractJoinedRole(FamilyGroupResponse group) {
        if (group == null || group.getMembers() == null) {
            return null;
        }
        for (FamilyGroupResponse.MemberResponse member : group.getMembers()) {
            if (member != null && userId.equals(member.getUserId())) {
                return member.getRole();
            }
        }
        return null;
    }

    private void openProtectedDashboard(String linkId) {
        Intent intent = new Intent(this, ProtectedDashboardActivity.class);
        intent.putExtra("PROTECTED_ID", userId);
        if (linkId != null && !linkId.isBlank()) {
            intent.putExtra("LINK_ID", linkId);
        }
        startActivity(intent);
        finish();
    }

    private String normalizeInvitationInput(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String value = rawValue.trim();
        if (value.isBlank()) {
            return "";
        }

        try {
            Uri uri = Uri.parse(value);
            String queryToken = uri.getQueryParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
            String lastPathSegment = uri.getLastPathSegment();
            if (looksLikeToken(lastPathSegment)) {
                return lastPathSegment.trim();
            }
        } catch (Exception ignored) {
        }

        Pattern pattern = Pattern.compile("([A-Za-z0-9_-]{6,})");
        Matcher matcher = pattern.matcher(value);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }
        return lastMatch != null ? lastMatch : value;
    }

    private boolean looksLikeToken(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{6,}");
    }

    private String buildInviteErrorMessage(Response<?> response) {
        if (response == null) {
            return "No se pudo validar el codigo";
        }

        String code = "";
        String message = "";
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                message = sanitizeError(raw);
                if (raw != null && !raw.isBlank()) {
                    JSONObject json = new JSONObject(raw);
                    code = json.optString("code", "");
                    message = json.optString("message", message);
                }
            }
        } catch (IOException ignored) {
        } catch (Exception ignored) {
        }

        if ("INVITATION_EXPIRED".equals(code) || "FAMILY_INVITATION_EXPIRED".equals(code)) {
            return "Codigo expirado";
        }
        if ("INVITATION_CANCELLED".equals(code) || "FAMILY_INVITATION_CANCELLED".equals(code)) {
            return "Codigo cancelado";
        }
        if ("INVITATION_ALREADY_ACCEPTED".equals(code) || "FAMILY_INVITATION_ALREADY_ACCEPTED".equals(code)) {
            return "Codigo ya utilizado";
        }
        if ("INVITATION_NOT_FOUND".equals(code) || "FAMILY_INVITATION_NOT_FOUND".equals(code)) {
            return "Codigo invalido";
        }

        if (response.code() == 410) {
            return "Codigo expirado";
        }
        if (response.code() == 404) {
            return "Codigo invalido";
        }
        if (response.code() == 400 && message.toLowerCase().contains("accepted")) {
            return "Codigo ya utilizado";
        }
        if (response.code() == 400 && message.toLowerCase().contains("cancel")) {
            return "Codigo cancelado";
        }

        return "No se pudo validar el codigo";
    }

    private String sanitizeError(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() > 140 ? sanitized.substring(0, 140) + "..." : sanitized;
    }

    private String selectBestError(String previousError, String familyError) {
        if (familyError != null && !familyError.isBlank() && !"No se pudo validar el codigo".equals(familyError)) {
            return familyError;
        }
        if (previousError != null && !previousError.isBlank()) {
            return previousError;
        }
        return "No se pudo validar el codigo";
    }
}
