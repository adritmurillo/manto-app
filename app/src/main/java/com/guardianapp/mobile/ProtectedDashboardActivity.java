package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.guardianapp.mobile.api.CreateIdentityVerificationRequest;
import com.guardianapp.mobile.api.IdentityVerificationResponse;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.realtime.StompRealtimeClient;
import com.google.firebase.auth.FirebaseAuth;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProtectedDashboardActivity extends AppCompatActivity {

    private String miIdProtegido;
    private String idDelVinculo;
    private String currentVerificationId;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    private final StompRealtimeClient realtimeClient = new StompRealtimeClient();
    private AlertDialog verificationCodeDialog;
    private LinearLayout layoutVerificationCodeInfo;
    private TextView tvVerificationCodePersistent;
    private TextView tvVerificationCodeStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_protected_dashboard);

        miIdProtegido = getIntent().getStringExtra("PROTECTED_ID");
        idDelVinculo = getIntent().getStringExtra("LINK_ID");

        Button btnSimulateThreat = findViewById(R.id.btnSimulateThreat);
        Button btnCallHost = findViewById(R.id.btnCallHost);
        TextView tvLogout = findViewById(R.id.tvLogoutProtected);
        layoutVerificationCodeInfo = findViewById(R.id.layoutVerificationCodeInfo);
        tvVerificationCodePersistent = findViewById(R.id.tvVerificationCodePersistent);
        tvVerificationCodeStatus = findViewById(R.id.tvVerificationCodeStatus);

        // ¡Abrimos la trampa!
        btnSimulateThreat.setOnClickListener(v -> {
            Intent intent = new Intent(this, SecureBrowserActivity.class);
            intent.putExtra("PROTECTED_ID", miIdProtegido);
            intent.putExtra("LINK_ID", idDelVinculo);
            startActivity(intent);
        });

        btnCallHost.setOnClickListener(v -> showIdentityVerificationDialog());

        tvLogout.setOnClickListener(v -> {
            stopVerificationPolling();
            realtimeClient.disconnect();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        connectRealtime();
    }

    private void connectRealtime() {
        if (miIdProtegido == null || miIdProtegido.isBlank()) {
            return;
        }
        String wsUrl = RetrofitClient.getWebSocketUrl();
        String topic = "/topic/protected/" + miIdProtegido + "/identity-verifications";
        realtimeClient.connect(wsUrl, topic, new StompRealtimeClient.EventListener() {
            @Override
            public void onEvent(String body) {
                runOnUiThread(() -> {
                    if (currentVerificationId != null) {
                        startVerificationPolling();
                    }
                });
            }

            @Override
            public void onConnected() {
            }
        });
    }

    private void showIdentityVerificationDialog() {
        if (miIdProtegido == null || idDelVinculo == null) {
            Toast.makeText(this, "Faltan datos del vínculo", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Ejemplo: Mi hijo Adrián");

        new AlertDialog.Builder(this)
                .setTitle("Verificar identidad")
                .setMessage("¿Quién dice ser la persona que llama?")
                .setView(input)
                .setPositiveButton("Enviar verificación", (dialog, which) -> {
                    String claimedPerson = input.getText().toString().trim();
                    if (claimedPerson.isEmpty()) {
                        claimedPerson = "Familiar";
                    }
                    createIdentityVerification(claimedPerson);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void createIdentityVerification(String claimedPerson) {
        CreateIdentityVerificationRequest request = new CreateIdentityVerificationRequest(
                idDelVinculo,
                miIdProtegido,
                claimedPerson
        );

        RetrofitClient.getApiService().createIdentityVerification(request)
                .enqueue(new Callback<IdentityVerificationResponse>() {
                    @Override
                    public void onResponse(Call<IdentityVerificationResponse> call, Response<IdentityVerificationResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            currentVerificationId = response.body().getId();
                            showVerificationCodeDialog(response.body().getChallengeCode());
                            startVerificationPolling();
                        } else {
                            Toast.makeText(ProtectedDashboardActivity.this, "No se pudo enviar la verificación", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<IdentityVerificationResponse> call, Throwable t) {
                        Toast.makeText(ProtectedDashboardActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startVerificationPolling() {
        stopVerificationPolling();
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentVerificationId == null) {
                    return;
                }
                RetrofitClient.getApiService().getIdentityVerification(currentVerificationId)
                        .enqueue(new Callback<IdentityVerificationResponse>() {
                            @Override
                            public void onResponse(Call<IdentityVerificationResponse> call, Response<IdentityVerificationResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    IdentityVerificationResponse verification = response.body();
                                    if (verification.isApproved()) {
                                        stopVerificationPolling();
                                        dismissVerificationCodeDialog();
                                        updateVerificationCodePanel(null, "Confirmado: era tu familiar", false);
                                        Toast.makeText(
                                                ProtectedDashboardActivity.this,
                                                "Verificado: tu familiar confirmó que sí es él/ella",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    } else if (verification.isRejected()) {
                                        stopVerificationPolling();
                                        dismissVerificationCodeDialog();
                                        updateVerificationCodePanel(null, "Alerta: no era tu familiar", false);
                                        Toast.makeText(
                                                ProtectedDashboardActivity.this,
                                                "Alerta: tu familiar indicó que NO es la persona real",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    } else if (verification.isExpired()) {
                                        stopVerificationPolling();
                                        dismissVerificationCodeDialog();
                                        updateVerificationCodePanel(null, "Verificación expirada", false);
                                        Toast.makeText(
                                                ProtectedDashboardActivity.this,
                                                "La verificación expiró. Intenta de nuevo.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<IdentityVerificationResponse> call, Throwable t) {
                            }
                        });
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopVerificationPolling() {
        if (pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    private void showVerificationCodeDialog(String challengeCode) {
        dismissVerificationCodeDialog();
        updateVerificationCodePanel(challengeCode, "Esperando respuesta de tu familiar", true);
        verificationCodeDialog = new AlertDialog.Builder(this)
                .setTitle("Verificación enviada")
                .setMessage(
                        "Comparte este código con tu familiar durante la llamada:\n\n" +
                        challengeCode +
                        "\n\nEspera su confirmación para saber si la llamada es real."
                )
                .setCancelable(false)
                .setPositiveButton("Entendido", null)
                .create();
        verificationCodeDialog.show();
    }

    private void dismissVerificationCodeDialog() {
        if (verificationCodeDialog != null && verificationCodeDialog.isShowing()) {
            verificationCodeDialog.dismiss();
        }
        verificationCodeDialog = null;
    }

    private void updateVerificationCodePanel(String code, String status, boolean visible) {
        if (layoutVerificationCodeInfo == null) {
            return;
        }
        layoutVerificationCodeInfo.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            tvVerificationCodePersistent.setText(code != null ? code : "------");
            tvVerificationCodeStatus.setText(status != null ? status : "Esperando respuesta de tu familiar");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVerificationPolling();
        realtimeClient.disconnect();
        dismissVerificationCodeDialog();
    }
}
