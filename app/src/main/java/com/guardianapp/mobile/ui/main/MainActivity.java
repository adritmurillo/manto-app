package com.guardianapp.mobile.ui.main;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.NotificationRegistrar;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.UserResponse;
import com.guardianapp.mobile.ui.auth.RegisterActivity;
import com.guardianapp.mobile.ui.common.AppNavigator;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.guardianapp.mobile.ui.invite.InviteEntryActivity;
import com.guardianapp.mobile.ui.invite.PendingInviteStore;
import com.guardianapp.mobile.ui.protecteduser.ProtectedDashboardActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATIONS = 1001;

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Button btnLogin;
    private TextView tvCreateAccount;

    private FirebaseAuth mAuth;

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            continueAfterFirebaseLogin(currentUser.getEmail());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();

        mAuth = FirebaseAuth.getInstance();

        String inviteToken = getIntent() != null ? getIntent().getStringExtra(InviteEntryActivity.EXTRA_INVITE_TOKEN) : null;
        if (inviteToken != null && !inviteToken.isBlank()) {
            PendingInviteStore.save(this, inviteToken);
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvCreateAccount = findViewById(R.id.tvCreateAccount);

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString();
            String password = etPassword.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(MainActivity.this, "Verificando perfil...", Toast.LENGTH_SHORT).show();
                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser == null || currentUser.getEmail() == null) return;

                            continueAfterFirebaseLogin(currentUser.getEmail());
                        } else {
                            Toast.makeText(MainActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvCreateAccount.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQ_NOTIFICATIONS
        );
    }

    private void continueAfterFirebaseLogin(String userEmail) {
        RetrofitClient.getApiService().getUserByEmail(userEmail).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String postgresId = response.body().getId();
                    NotificationRegistrar.registerToken(postgresId);

                    String pendingToken = PendingInviteStore.pop(MainActivity.this);
                    if (pendingToken != null && !pendingToken.isBlank()) {
                        Intent intent = new Intent(MainActivity.this, DeviceSetupActivity.class);
                        intent.putExtra(DeviceSetupActivity.EXTRA_USER_ID, postgresId);
                        intent.putExtra(DeviceSetupActivity.EXTRA_PREFILL_INVITE_TOKEN, pendingToken);
                        intent.putExtra(DeviceSetupActivity.EXTRA_ROLE_PREFILL, DeviceSetupActivity.ROLE_PROTECTED);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    routeToHostOrProtectedDashboard(postgresId);
                } else {
                    Toast.makeText(MainActivity.this, "Usuario no encontrado en base de datos", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error buscando usuario", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void routeToHostOrProtectedDashboard(String postgresId) {
        RetrofitClient.getApiService().getMyFamilyGroups(postgresId).enqueue(new Callback<java.util.List<FamilyGroupResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<FamilyGroupResponse>> call, Response<java.util.List<FamilyGroupResponse>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    AppNavigator.goToDeviceSetup(MainActivity.this, postgresId);
                    return;
                }

                boolean isHost = false;
                for (FamilyGroupResponse g : response.body()) {
                    if (g == null || g.getMembers() == null) continue;
                    for (FamilyGroupResponse.MemberResponse m : g.getMembers()) {
                        if (m != null && postgresId.equals(m.getUserId())
                                && ("PRIMARY_HOST".equals(m.getRole()) || "SECONDARY_HOST".equals(m.getRole()))) {
                            isHost = true;
                            break;
                        }
                    }
                    if (isHost) break;
                }

                if (isHost) {
                    Intent intent = new Intent(MainActivity.this, HostDashboardActivity.class);
                    intent.putExtra("HOST_ID", postgresId);
                    startActivity(intent);
                    finish();
                } else {
                    resolveProtectedExtrasAndOpenDashboard(postgresId);
                }
            }

            @Override
            public void onFailure(Call<java.util.List<FamilyGroupResponse>> call, Throwable t) {
                AppNavigator.goToDeviceSetup(MainActivity.this, postgresId);
            }
        });
    }

    private void resolveProtectedExtrasAndOpenDashboard(String protectedUserId) {
        RetrofitClient.getApiService().getMyLinks(protectedUserId).enqueue(new Callback<java.util.List<com.guardianapp.mobile.data.api.LinkResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<com.guardianapp.mobile.data.api.LinkResponse>> call,
                                   Response<java.util.List<com.guardianapp.mobile.data.api.LinkResponse>> response) {
                String linkId = null;
                if (response.isSuccessful() && response.body() != null) {
                    for (com.guardianapp.mobile.data.api.LinkResponse link : response.body()) {
                        if (protectedUserId.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                            linkId = link.getId();
                            break;
                        }
                    }
                }

                if (linkId == null || linkId.isBlank()) {
                    AppNavigator.goToDeviceSetup(MainActivity.this, protectedUserId);
                    return;
                }

                Intent intent = new Intent(MainActivity.this, ProtectedDashboardActivity.class);
                intent.putExtra("PROTECTED_ID", protectedUserId);
                intent.putExtra("LINK_ID", linkId);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(Call<java.util.List<com.guardianapp.mobile.data.api.LinkResponse>> call, Throwable t) {
                AppNavigator.goToDeviceSetup(MainActivity.this, protectedUserId);
            }
        });
    }
}
