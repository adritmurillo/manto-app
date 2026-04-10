package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.guardianapp.mobile.api.InvitationResponse;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.api.UserResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private Button btnGenerateCode, btnLinkAccount;
    private TextView tvGeneratedCode;
    private EditText etInvitationCode;

    private String currentUserIdPostgres = null; // Aquí guardaremos el UUID real

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnGenerateCode = findViewById(R.id.btnGenerateCode);
        tvGeneratedCode = findViewById(R.id.tvGeneratedCode);
        etInvitationCode = findViewById(R.id.etInvitationCode);
        btnLinkAccount = findViewById(R.id.btnLinkAccount);

        // Desactivar botones temporalmente hasta que tengamos el UUID
        btnGenerateCode.setEnabled(false);
        btnLinkAccount.setEnabled(false);

        fetchPostgresUserId();

        // Botón: SOY ANFITRIÓN (Generar)
        btnGenerateCode.setOnClickListener(v -> generateInvitationCode());

        // Botón: SOY PROTEGIDO (Vincular)
        btnLinkAccount.setOnClickListener(v -> acceptInvitationCode());
    }

    private void fetchPostgresUserId() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || firebaseUser.getEmail() == null) return;

        String email = firebaseUser.getEmail();

        RetrofitClient.getApiService().getUserByEmail(email).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUserIdPostgres = response.body().getId(); // Guardamos el UUID
                    btnGenerateCode.setEnabled(true);
                    btnLinkAccount.setEnabled(true);
                    Toast.makeText(HomeActivity.this, "Conectado al Backend", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(HomeActivity.this, "Error al obtener perfil", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateInvitationCode() {
        if (currentUserIdPostgres == null) return;

        RetrofitClient.getApiService().createInvitation(currentUserIdPostgres).enqueue(new Callback<InvitationResponse>() {
            @Override
            public void onResponse(Call<InvitationResponse> call, Response<InvitationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvGeneratedCode.setText(response.body().getToken());
                    Toast.makeText(HomeActivity.this, "Código generado", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(HomeActivity.this, "Error al generar código", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<InvitationResponse> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptInvitationCode() {
        if (currentUserIdPostgres == null) return;

        String token = etInvitationCode.getText().toString().trim();
        if (token.isEmpty()) {
            Toast.makeText(this, "Ingresa un código", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getApiService().acceptInvitation(token, currentUserIdPostgres).enqueue(new Callback<Object>() {
            @Override
            public void onResponse(Call<Object> call, Response<Object> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HomeActivity.this, "¡Código aceptado! Configurando seguridad...", Toast.LENGTH_SHORT).show();

                    // ¡EL CAMBIO FLUIDO!
                    // Regresamos al Enrutador (MainActivity) para que detecte que estamos PENDING
                    // y nos mande automáticamente a la pantalla de Verificación del PIN.
                    Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Cerramos esta pantalla para que no pueda volver atrás

                } else {
                    Toast.makeText(HomeActivity.this, "Código inválido o expirado", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }
}