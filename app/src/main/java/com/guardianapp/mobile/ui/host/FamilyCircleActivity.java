package com.guardianapp.mobile.ui.host;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.CreateFamilyInvitationRequest;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.FamilyInvitationResponse;
import com.guardianapp.mobile.data.api.RenameFamilyGroupRequest;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.ui.common.AppNavigator;
import com.guardianapp.mobile.ui.common.FamilyAccessGuard;
import com.guardianapp.mobile.ui.security.LinkShieldActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Family circle management screen.
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
    private Button btnRenameFamily;
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
        btnRenameFamily = findViewById(R.id.btnRenameFamily);
        ImageButton btnBack = findViewById(R.id.btnBackFamilyCircle);
        BottomNavigationView bottomNavFamily = findViewById(R.id.bottomNavFamily);

        Button btnRefresh = findViewById(R.id.btnRefreshFamily);
        Button btnInviteSecondary = findViewById(R.id.btnInviteSecondaryHost);
        Button btnInviteProtected = findViewById(R.id.btnInviteProtected);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnRefresh.setOnClickListener(v -> loadMyFamily());
        btnInviteSecondary.setOnClickListener(v -> createFamilyInvite("SECONDARY_HOST"));
        btnInviteProtected.setOnClickListener(v -> createFamilyInvite("PROTECTED"));
        btnLeaveFamily.setOnClickListener(v -> leaveFamily());
        btnRemoveMember.setOnClickListener(v -> showRemoveMemberDialog());
        btnRenameFamily.setOnClickListener(v -> showRenameDialog());

        if (bottomNavFamily != null) {
            bottomNavFamily.setSelectedItemId(R.id.nav_family);
            bottomNavFamily.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    finish();
                    return true;
                }
                if (id == R.id.nav_security) {
                    Intent intent = new Intent(this, LinkShieldActivity.class);
                    intent.putExtra("HOST_ID", hostId);
                    startActivity(intent);
                    return true;
                }
                if (id == R.id.nav_family) {
                    return true;
                }
                if (id == R.id.nav_settings) {
                    Toast.makeText(this, "Ajustes aun no disponible", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }

        loadMyFamily();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FamilyAccessGuard.ensureInFamily(this, hostId, null);
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
                            btnRenameFamily.setEnabled(isPrimary);
                            btnRenameFamily.setAlpha(isPrimary ? 1f : 0.4f);
                            return;
                        }

                        currentGroup = null;
                        familyId = null;
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
            Toast.makeText(this, "No se encontro el circulo familiar", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentGroup != null && hostId.equals(currentGroup.getPrimaryHostUserId())) {
            RetrofitClient.getApiService()
                    .disbandFamilyGroup(familyId, hostId)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(FamilyCircleActivity.this, "Circulo familiar disuelto", Toast.LENGTH_SHORT).show();
                                AppNavigator.goToDeviceSetup(FamilyCircleActivity.this, hostId);
                            } else {
                                Toast.makeText(FamilyCircleActivity.this, "No se pudo disolver el circulo", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(FamilyCircleActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        }

        RetrofitClient.getApiService()
                .removeFamilyMember(familyId, hostId, hostId)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(FamilyCircleActivity.this, "Saliste del circulo familiar", Toast.LENGTH_SHORT).show();
                            AppNavigator.goToDeviceSetup(FamilyCircleActivity.this, hostId);
                        } else {
                            Toast.makeText(FamilyCircleActivity.this, "No se pudo salir del circulo", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Solo el anfitrion principal puede eliminar miembros", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "No se encontro el circulo familiar", Toast.LENGTH_SHORT).show();
            return;
        }
        RetrofitClient.getApiService()
                .removeFamilyMember(familyId, memberUserId, hostId)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(FamilyCircleActivity.this, "Miembro eliminado", Toast.LENGTH_SHORT).show();
                            loadMyFamily();
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

    private void showRenameDialog() {
        if (currentGroup == null || familyId == null || familyId.isBlank()) {
            Toast.makeText(this, "Primero carga el circulo familiar", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hostId.equals(currentGroup.getPrimaryHostUserId())) {
            Toast.makeText(this, "Solo el anfitrion principal puede renombrar", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setText(currentGroup.getName() == null ? "" : currentGroup.getName());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Cambiar nombre del circulo")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> renameFamily(input.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void renameFamily(String newNameRaw) {
        String newName = newNameRaw == null ? "" : newNameRaw.trim();
        if (newName.length() < 3) {
            Toast.makeText(this, "El nombre debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }
        RetrofitClient.getApiService()
                .renameFamilyGroup(familyId, hostId, new RenameFamilyGroupRequest(newName))
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            currentGroup = response.body();
                            tvFamilyName.setText(currentGroup.getName());
                            Toast.makeText(FamilyCircleActivity.this, "Nombre actualizado", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(FamilyCircleActivity.this, "No se pudo actualizar el nombre", Toast.LENGTH_SHORT).show();
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
