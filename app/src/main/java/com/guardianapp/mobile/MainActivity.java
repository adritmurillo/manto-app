package com.guardianapp.mobile;

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
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.NotificationRegistrar;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.api.UserResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATIONS = 1001;

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Button btnLogin;
    private TextView tvCreateAccount;

    // Declarar la variable de autenticación
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

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

                            // 2. Buscamos el UUID en PostgreSQL a través de Spring Boot
                            RetrofitClient.getApiService().getUserByEmail(userEmail).enqueue(new Callback<UserResponse>() {
                                @Override
                                public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                                    if (response.isSuccessful() && response.body() != null) {
                                        String postgresId = response.body().getId();
                                        NotificationRegistrar.registerToken(postgresId);

                                        // 3. Preguntamos si tiene CUALQUIER vínculo
                                        RetrofitClient.getApiService().getMyLinks(postgresId).enqueue(new Callback<List<LinkResponse>>() {
                                            @Override
                                            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> responseLinks) {
                                                Intent intent;

                                                // Si la lista NO está vacía (tiene vínculos pendientes o activos)
                                                if (responseLinks.isSuccessful() && responseLinks.body() != null && !responseLinks.body().isEmpty()) {
                                                    LinkResponse link = responseLinks.body().get(0);

                                                    if (link.getHostId().equals(postgresId)) {
                                                        // Es el Anfitrión (Él siempre va al Dashboard, ahí verá el PIN)
                                                        intent = new Intent(MainActivity.this, HostDashboardActivity.class);
                                                        intent.putExtra("HOST_ID", postgresId);
                                                    } else {
                                                        // Es el Abuelo/Protegido
                                                        if ("ACTIVE".equals(link.getStatus())) {
                                                            // Ya está validado -> Va a su panel con el botón de la trampa
                                                            intent = new Intent(MainActivity.this, ProtectedDashboardActivity.class);
                                                        } else {
                                                            // Está PENDING -> Va a la pantalla para poner el PIN
                                                            intent = new Intent(MainActivity.this, VerificationActivity.class);
                                                        }
                                                        // A ambos lados mandamos los IDs en la mochila
                                                        intent.putExtra("PROTECTED_ID", postgresId);
                                                        intent.putExtra("LINK_ID", link.getId());
                                                    }
                                                } else {
                                                    // No tiene ningún vínculo. Va a la pantalla de crear/poner código
                                                    intent = new Intent(MainActivity.this, HomeActivity.class);
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
}
