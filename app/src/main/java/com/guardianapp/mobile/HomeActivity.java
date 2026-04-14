package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.guardianapp.mobile.api.InvitationResponse;
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.api.UserResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private Button btnGenerateCode, btnLinkAccount;
    private TextView tvGeneratedCode;
    private EditText etInvitationCode;

    private String currentUserIdPostgres = null; // Aquí guardaremos el UUID real
    private final Handler linkPollingHandler = new Handler(Looper.getMainLooper());
    private Runnable linkPollingRunnable;

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
                            checkAndRouteIfAlreadyLinked();
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
                    startLinkPolling();
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
                    routeProtectedToVerificationOrDashboard();

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

    private void routeProtectedToVerificationOrDashboard() {
        RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (LinkResponse link : response.body()) {
                        if (currentUserIdPostgres.equals(link.getProtectedUserId())) {
                            Intent intent;
                            if ("ACTIVE".equals(link.getStatus())) {
                                intent = new Intent(HomeActivity.this, ProtectedDashboardActivity.class);
                            } else {
                                intent = new Intent(HomeActivity.this, VerificationActivity.class);
                            }
                            intent.putExtra("PROTECTED_ID", currentUserIdPostgres);
                            intent.putExtra("LINK_ID", link.getId());
                            startActivity(intent);
                            finish();
                            return;
                        }
                    }
                }

                Intent fallback = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(fallback);
                finish();
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                Intent fallback = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(fallback);
                finish();
            }
        });
    }

    private void startLinkPolling() {
        stopLinkPolling();
        linkPollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentUserIdPostgres == null) {
                    return;
                }
                RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (currentUserIdPostgres.equals(link.getHostId()) &&
                                        ("PENDING".equals(link.getStatus()) || "ACTIVE".equals(link.getStatus()))) {
                                    stopLinkPolling();
                                    Intent intent = new Intent(HomeActivity.this, HostDashboardActivity.class);
                                    intent.putExtra("HOST_ID", currentUserIdPostgres);
                                    startActivity(intent);
                                    finish();
                                    return;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                    }
                });
                linkPollingHandler.postDelayed(this, 3000);
            }
        };
        linkPollingHandler.post(linkPollingRunnable);
    }

    private void stopLinkPolling() {
        if (linkPollingRunnable != null) {
            linkPollingHandler.removeCallbacks(linkPollingRunnable);
        }
    }

    private void checkAndRouteIfAlreadyLinked() {
        if (currentUserIdPostgres == null) {
            return;
        }
        RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (LinkResponse link : response.body()) {
                        if (currentUserIdPostgres.equals(link.getHostId()) &&
                                ("PENDING".equals(link.getStatus()) || "ACTIVE".equals(link.getStatus()))) {
                            Intent intent = new Intent(HomeActivity.this, HostDashboardActivity.class);
                            intent.putExtra("HOST_ID", currentUserIdPostgres);
                            startActivity(intent);
                            finish();
                            return;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLinkPolling();
    }
}
