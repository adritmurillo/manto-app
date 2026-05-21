package com.guardianapp.mobile.ui.host;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

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
    private Button btnLeaveFamily;
    private Button btnRemoveMember;
    private FamilyGroupResponse currentGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_circle);

        hostId = getIntent().getStringExtra("HOST_ID");

        tvFamilyName = findViewById(R.id.tvFamilyNameValue);
        tvMembers = findViewById(R.id.tvFamilyMembersValue);
        tvInviteSecondaryCode = findViewById(R.id.tvSecondaryInviteCode);
        tvInviteProtectedCode = findViewById(R.id.tvProtectedInviteCode);
        btnLeaveFamily = findViewById(R.id.btnLeaveFamily);
        btnRemoveMember = findViewById(R.id.btnRemoveMember);

        Button btnRefresh = findViewById(R.id.btnRefreshFamily);
        Button btnInviteSecondary = findViewById(R.id.btnInviteSecondaryHost);
        Button btnInviteProtected = findViewById(R.id.btnInviteProtected);

        btnRefresh.setOnClickListener(v -> loadMyFamily());
        btnInviteSecondary.setOnClickListener(v -> createFamilyInvite("SECONDARY_HOST"));
        btnInviteProtected.setOnClickListener(v -> createFamilyInvite("PROTECTED"));
        btnLeaveFamily.setOnClickListener(v -> leaveFamily());
        btnRemoveMember.setOnClickListener(v -> showRemoveMemberDialog());

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
                            currentGroup = group;
                            familyId = group.getId();
                            String name = group.getName();
                            if (name == null || name.isBlank()) {
                                name = "Circulo familiar";
                            }
                            tvFamilyName.setText(name);
                            tvMembers.setText(formatMembers(group));
                            boolean isPrimary = hostId != null && hostId.equals(group.getPrimaryHostUserId());
                            btnRemoveMember.setEnabled(isPrimary);
                            btnRemoveMember.setAlpha(isPrimary ? 1f : 0.4f);
                            return;
                        }
                        currentGroup = null;
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
            if (member == null) {
                continue;
            }
            String displayName = resolveDisplayName(member.getUserId());
            builder.append("• ")
                    .append(member.getRole())
                    .append(" - ")
                    .append(displayName)
                    .append("\n");
            prefetchUserName(member.getUserId());
        }
        return builder.toString().trim();
    }

    private String resolveDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "Usuario";
        }
        String cached = NameCache.get(userId);
        return cached != null ? cached : userId;
    }

    private void prefetchUserName(String userId) {
        if (userId == null || userId.isBlank() || NameCache.get(userId) != null) {
            return;
        }
        RetrofitClient.getApiService().getUserById(userId)
                .enqueue(new Callback<com.guardianapp.mobile.data.api.UserResponse>() {
                    @Override
                    public void onResponse(Call<com.guardianapp.mobile.data.api.UserResponse> call, Response<com.guardianapp.mobile.data.api.UserResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getName() != null) {
                            NameCache.put(userId, response.body().getName());
                            if (currentGroup != null) {
                                tvMembers.setText(formatMembers(currentGroup));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<com.guardianapp.mobile.data.api.UserResponse> call, Throwable t) {
                    }
                });
    }

    private void leaveFamily() {
        if (hostId == null || hostId.isBlank() || familyId == null || familyId.isBlank()) {
            Toast.makeText(this, "No se encontró el círculo familiar", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentGroup != null && hostId.equals(currentGroup.getPrimaryHostUserId())) {
            Toast.makeText(this, "El anfitrión principal no puede salir", Toast.LENGTH_SHORT).show();
            return;
        }
        RetrofitClient.getApiService()
                .removeFamilyMember(familyId, hostId, hostId)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(FamilyCircleActivity.this, "Saliste del círculo familiar", Toast.LENGTH_SHORT).show();
                            familyId = null;
                            currentGroup = null;
                            tvFamilyName.setText("Sin circulo familiar");
                            tvMembers.setText("No tienes miembros aun");
                        } else {
                            Toast.makeText(FamilyCircleActivity.this, "No se pudo salir del círculo", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                        Toast.makeText(FamilyCircleActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showRemoveMemberDialog() {
        if (currentGroup == null || currentGroup.getMembers() == null || currentGroup.getMembers().isEmpty()) {
            Toast.makeText(this, "No hay miembros para eliminar", Toast.LENGTH_SHORT).show();
            return;
        }
        if (hostId == null || !hostId.equals(currentGroup.getPrimaryHostUserId())) {
            Toast.makeText(this, "Solo el anfitrión principal puede eliminar miembros", Toast.LENGTH_SHORT).show();
            return;
        }

        List<FamilyGroupResponse.MemberResponse> candidates = currentGroup.getMembers().stream()
                .filter(member -> member != null && member.getUserId() != null && !member.getUserId().equals(hostId))
                .toList();

        if (candidates.isEmpty()) {
            Toast.makeText(this, "No hay miembros removibles", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            FamilyGroupResponse.MemberResponse member = candidates.get(i);
            String name = resolveDisplayName(member.getUserId());
            labels[i] = member.getRole() + " - " + name;
        }

        new AlertDialog.Builder(this)
                .setTitle("Eliminar miembro")
                .setItems(labels, (dialog, which) -> removeMember(candidates.get(which).getUserId()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void removeMember(String memberUserId) {
        if (familyId == null || familyId.isBlank()) {
            Toast.makeText(this, "No se encontró el círculo familiar", Toast.LENGTH_SHORT).show();
            return;
        }
        RetrofitClient.getApiService()
                .removeFamilyMember(familyId, memberUserId, hostId)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(FamilyCircleActivity.this, "Miembro eliminado", Toast.LENGTH_SHORT).show();
                            currentGroup = response.body();
                            tvMembers.setText(formatMembers(currentGroup));
                        } else {
                            Toast.makeText(FamilyCircleActivity.this, "No se pudo eliminar", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                        Toast.makeText(FamilyCircleActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static class NameCache {
        private static final java.util.Map<String, String> CACHE = new java.util.HashMap<>();

        static String get(String userId) {
            return CACHE.get(userId);
        }

        static void put(String userId, String name) {
            CACHE.put(userId, name);
        }
    }
}
