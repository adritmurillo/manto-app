package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

// Importar la librería de Firebase
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Importar Retrofit y nuestros DTOs
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.api.UserResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

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

                                        // 3. Preguntamos si tiene vínculos activos
                                        RetrofitClient.getApiService().getActiveLinks(postgresId).enqueue(new Callback<List<LinkResponse>>() {
                                            @Override
                                            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> responseLinks) {
                                                Intent intent;

                                                if (responseLinks.isSuccessful() && responseLinks.body() != null && !responseLinks.body().isEmpty()) {
                                                    // Tiene vínculos. Averiguar si es Anfitrión o Protegido
                                                    LinkResponse link = responseLinks.body().get(0);

                                                    if (link.getHostId().equals(postgresId)) {
                                                        // Es el Anfitrión
                                                        intent = new Intent(MainActivity.this, HostDashboardActivity.class);
                                                    } else {
                                                        // Es el Abuelo/Protegido
                                                        intent = new Intent(MainActivity.this, ProtectedDashboardActivity.class);
                                                    }
                                                } else {
                                                    // No tiene vínculos. Va a la pantalla de crear/ingresar códigos.
                                                    intent = new Intent(MainActivity.this, HomeActivity.class);
                                                }

                                                startActivity(intent);
                                                finish(); // Cerramos MainActivity
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
}