package com.guardianapp.mobile.ui.host;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.CreateFamilyInvitationRequest;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.FamilyInvitationResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Basic family circle management screen for hosts.
 */
public class FamilyCircleActivity extends AppCompatActivity {

    private String hostId;
    private String familyId;

    private TextView tvFamilyName;
    private TextView tvMembers;
    private TextView tvInviteSecondaryCode;
    private TextView tvInviteProtectedCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_circle);

        hostId = getIntent().getStringExtra("HOST_ID");

        tvFamilyName = findViewById(R.id.tvFamilyNameValue);
        tvMembers = findViewById(R.id.tvFamilyMembersValue);
        tvInviteSecondaryCode = findViewById(R.id.tvSecondaryInviteCode);
        tvInviteProtectedCode = findViewById(R.id.tvProtectedInviteCode);

        Button btnRefresh = findViewById(R.id.btnRefreshFamily);
        Button btnInviteSecondary = findViewById(R.id.btnInviteSecondaryHost);
        Button btnInviteProtected = findViewById(R.id.btnInviteProtected);

        btnRefresh.setOnClickListener(v -> loadMyFamily());
        btnInviteSecondary.setOnClickListener(v -> createFamilyInvite("SECONDARY_HOST"));
        btnInviteProtected.setOnClickListener(v -> createFamilyInvite("PROTECTED"));

        loadMyFamily();
    }

    private void loadMyFamily() {
        if (hostId == null || hostId.isBlank()) {
            Toast.makeText(this, "Falta HOST_ID", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getApiService().getMyFamilyGroups(hostId)
                .enqueue(new Callback<List<FamilyGroupResponse>>() {
                    @Override
                    public void onResponse(Call<List<FamilyGroupResponse>> call, Response<List<FamilyGroupResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            FamilyGroupResponse group = response.body().get(0);
                            familyId = group.getId();
                            String name = group.getName();
                            if (name == null || name.isBlank()) {
                                name = "Circulo familiar";
                            }
                            tvFamilyName.setText(name + " (" + familyId + ")");
                            tvMembers.setText(formatMembers(group));
                            return;
                        }
                        tvFamilyName.setText("Sin circulo familiar");
                        tvMembers.setText("No tienes miembros aun");
                    }

                    @Override
                    public void onFailure(Call<List<FamilyGroupResponse>> call, Throwable t) {
                        Toast.makeText(FamilyCircleActivity.this, "Error de red cargando familia", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createFamilyInvite(String targetRole) {
        if (hostId == null || hostId.isBlank() || familyId == null || familyId.isBlank()) {
            Toast.makeText(this, "Primero carga o crea tu circulo familiar", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getApiService()
                .createFamilyInvitation(familyId, hostId, new CreateFamilyInvitationRequest(targetRole))
                .enqueue(new Callback<FamilyInvitationResponse>() {
                    @Override
                    public void onResponse(Call<FamilyInvitationResponse> call, Response<FamilyInvitationResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String token = response.body().getToken();
                            if ("SECONDARY_HOST".equals(targetRole)) {
                                tvInviteSecondaryCode.setText(token);
                                Toast.makeText(FamilyCircleActivity.this, "Token ayudante generado", Toast.LENGTH_SHORT).show();
                            } else {
                                tvInviteProtectedCode.setText(token);
                                Toast.makeText(FamilyCircleActivity.this, "Token protegido generado", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(FamilyCircleActivity.this, "No se pudo crear invitacion", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<FamilyInvitationResponse> call, Throwable t) {
                        Toast.makeText(FamilyCircleActivity.this, "Error de red creando invitacion", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String formatMembers(FamilyGroupResponse group) {
        if (group.getMembers() == null || group.getMembers().isEmpty()) {
            return "Sin miembros";
        }
        StringBuilder builder = new StringBuilder();
        for (FamilyGroupResponse.MemberResponse member : group.getMembers()) {
            builder.append("• ")
                    .append(member.getRole())
                    .append(" - ")
                    .append(member.getUserId())
                    .append("\n");
        }
        return builder.toString().trim();
    }
}
