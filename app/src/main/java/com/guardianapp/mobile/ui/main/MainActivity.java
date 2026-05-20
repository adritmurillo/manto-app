package com.guardianapp.mobile.ui.main;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

// Importar la librería de Firebase
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Importar Retrofit y nuestros DTOs
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.NotificationRegistrar;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.UserResponse;
import com.guardianapp.mobile.ui.auth.RegisterActivity;
import com.guardianapp.mobile.ui.auth.VerificationActivity;
import com.guardianapp.mobile.ui.host.HostDashboardActivity;
import com.guardianapp.mobile.ui.invite.InviteEntryActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.guardianapp.mobile.ui.invite.PendingInviteStore;
import com.guardianapp.mobile.ui.protecteduser.ProtectedDashboardActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATIONS = 1001;

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Button btnLogin;
    private TextView tvCreateAccount;

    // Declarar la variable de autenticación
    private FirebaseAuth mAuth;

    @Override
    protected void onStart() {
        super.onStart();

        // If the user is already logged in, continue automatically (supports invite link flow).
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

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // If we arrived from an invite link, persist it so we can continue after login/registration.
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

            // Validar que no estén vacíos
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            // ¡La magia de Firebase! Le pasamos las credenciales
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {

                            // 1. Firebase dijo que sí. Obtenemos el correo.
                            Toast.makeText(MainActivity.this, "Verificando perfil...", Toast.LENGTH_SHORT).show();
                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser == null || currentUser.getEmail() == null) return;

                            String userEmail = currentUser.getEmail();

                            continueAfterFirebaseLogin(userEmail);

                        } else {
                            // Firebase dijo que no (contraseña mal, correo no existe, etc)
                            Toast.makeText(MainActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvCreateAccount.setOnClickListener(v -> {
            // Navigate to Register Screen
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
        // 2. Buscamos el UUID en PostgreSQL a través de Spring Boot
        RetrofitClient.getApiService().getUserByEmail(userEmail).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String postgresId = response.body().getId();
                    NotificationRegistrar.registerToken(postgresId);

                    // If there is a pending invite token, auto-continue the linking flow.
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

                    // 3. Preguntamos si tiene CUALQUIER vínculo
                    RetrofitClient.getApiService().getMyLinks(postgresId).enqueue(new Callback<List<LinkResponse>>() {
                        @Override
                        public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> responseLinks) {
                            Intent intent;

                            // Si la lista NO está vacía (tiene vínculos pendientes o activos)
                            if (responseLinks.isSuccessful() && responseLinks.body() != null && !responseLinks.body().isEmpty()) {
                                // Prefer pending link for protected users, so the PIN screen shows immediately.
                                LinkResponse chosen = responseLinks.body().get(0);
                                LinkResponse pendingAsProtected = null;
                                for (LinkResponse l : responseLinks.body()) {
                                    if (!postgresId.equals(l.getHostId()) && postgresId.equals(l.getProtectedUserId()) && "PENDING".equals(l.getStatus())) {
                                        pendingAsProtected = l;
                                        break;
                                    }
                                }
                                if (pendingAsProtected != null) {
                                    chosen = pendingAsProtected;
                                }

                                if (chosen.getHostId().equals(postgresId)) {
                                    // Es el Anfitrión (Él siempre va al Dashboard, ahí verá el PIN)
                                    intent = new Intent(MainActivity.this, HostDashboardActivity.class);
                                    intent.putExtra("HOST_ID", postgresId);
                                } else {
                                    // Este usuario es el "protected" del vínculo (puede ser PROTECTED real o SECONDARY_HOST).
                                    if ("ACTIVE".equals(chosen.getStatus())) {
                                        // Si ya está ACTIVE, lo mandamos al dashboard de host si el usuario es host en alguna familia.
                                        routeToHostOrProtectedDashboard(postgresId);
                                        return;
                                    }
                                    // Está PENDING -> Va a la pantalla para poner el PIN
                                    intent = new Intent(MainActivity.this, VerificationActivity.class);
                                    intent.putExtra("PROTECTED_ID", postgresId);
                                    intent.putExtra("LINK_ID", chosen.getId());
                                }
                            } else {
                                // No tiene vínculos: mostrar selector inicial.
                                intent = new Intent(MainActivity.this, DeviceSetupActivity.class);
                                intent.putExtra(DeviceSetupActivity.EXTRA_USER_ID, postgresId);
                            }

                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                            Toast.makeText(MainActivity.this, "Error de red buscando vínculos", Toast.LENGTH_SHORT).show();
                        }
                    });

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
                boolean isHost = false;
                if (response.isSuccessful() && response.body() != null) {
                    for (FamilyGroupResponse g : response.body()) {
                        if (g == null || g.getMembers() == null) continue;
                        for (FamilyGroupResponse.MemberResponse m : g.getMembers()) {
                            if (m != null && postgresId.equals(m.getUserId()) && ("PRIMARY_HOST".equals(m.getRole()) || "SECONDARY_HOST".equals(m.getRole()))) {
                                isHost = true;
                                break;
                            }
                        }
                        if (isHost) break;
                    }
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
                resolveProtectedExtrasAndOpenDashboard(postgresId);
            }
        });
    }

    private void resolveProtectedExtrasAndOpenDashboard(String protectedUserId) {
        RetrofitClient.getApiService().getMyLinks(protectedUserId).enqueue(new Callback<java.util.List<LinkResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<LinkResponse>> call, Response<java.util.List<LinkResponse>> response) {
                String linkId = null;
                if (response.isSuccessful() && response.body() != null) {
                    for (LinkResponse link : response.body()) {
                        if (protectedUserId.equals(link.getProtectedUserId()) && "ACTIVE".equals(link.getStatus())) {
                            linkId = link.getId();
                            break;
                        }
                    }
                }
                Intent intent = new Intent(MainActivity.this, ProtectedDashboardActivity.class);
                intent.putExtra("PROTECTED_ID", protectedUserId);
                if (linkId != null) {
                    intent.putExtra("LINK_ID", linkId);
                }
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(Call<java.util.List<LinkResponse>> call, Throwable t) {
                Intent intent = new Intent(MainActivity.this, ProtectedDashboardActivity.class);
                intent.putExtra("PROTECTED_ID", protectedUserId);
                startActivity(intent);
                finish();
            }
        });
    }
}
