package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.api.ConfirmLinkRequest;
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerificationActivity extends AppCompatActivity {

    private EditText etPinCode;
    private Button btnVerifyPin;

    private String idDelVinculo;
    private String miIdProtegido;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        // 1. Rescatamos los IDs de la mochila (que nos mandó el MainActivity)
        idDelVinculo = getIntent().getStringExtra("LINK_ID");
        miIdProtegido = getIntent().getStringExtra("PROTECTED_ID");

        etPinCode = findViewById(R.id.etPinCode);
        btnVerifyPin = findViewById(R.id.btnVerifyPin);

        // Validación de seguridad por si falla el enrutador
        if (idDelVinculo == null || miIdProtegido == null) {
            Toast.makeText(this, "Error interno: Faltan IDs de seguridad", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 2. Escuchamos el clic
        btnVerifyPin.setOnClickListener(v -> verifyPin());
    }

    private void verifyPin() {
        String pin = etPinCode.getText().toString().trim();

        if (pin.length() != 6) {
            Toast.makeText(this, "El PIN debe tener exactamente 6 números", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bloqueamos el botón temporalmente para que el abuelo no le dé doble clic
        btnVerifyPin.setEnabled(false);
        btnVerifyPin.setText("Verificando en servidor...");

        ConfirmLinkRequest request = new ConfirmLinkRequest(pin);

        // 3. Enviamos el PIN al backend
        RetrofitClient.getApiService().confirmLink(idDelVinculo, miIdProtegido, request).enqueue(new Callback<LinkResponse>() {
            @Override
            public void onResponse(Call<LinkResponse> call, Response<LinkResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(VerificationActivity.this, "¡Protección Activada Oficialmente!", Toast.LENGTH_SHORT).show();

                    // Route based on the user's family role.
                    routeAfterActivation();
                    finish(); // Matamos esta pantalla para que no pueda volver atrás

                } else {
                    Toast.makeText(VerificationActivity.this, "PIN incorrecto, intenta de nuevo", Toast.LENGTH_SHORT).show();
                    resetButton();
                }
            }

            @Override
            public void onFailure(Call<LinkResponse> call, Throwable t) {
                Toast.makeText(VerificationActivity.this, "Error de red al conectar con el servidor", Toast.LENGTH_SHORT).show();
                resetButton();
            }
        });
    }

    private void resetButton() {
        btnVerifyPin.setEnabled(true);
        btnVerifyPin.setText("Verificar PIN");
    }

    private void routeAfterActivation() {
        // If the user is a host (primary or secondary) in any family group, go to Host dashboard.
        // Otherwise, it's a protected user.
        RetrofitClient.getApiService().getMyFamilyGroups(miIdProtegido).enqueue(new Callback<java.util.List<com.guardianapp.mobile.api.FamilyGroupResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<com.guardianapp.mobile.api.FamilyGroupResponse>> call, Response<java.util.List<com.guardianapp.mobile.api.FamilyGroupResponse>> response) {
                boolean isHost = false;
                if (response.isSuccessful() && response.body() != null) {
                    for (com.guardianapp.mobile.api.FamilyGroupResponse g : response.body()) {
                        if (g == null || g.getMembers() == null) continue;
                        for (com.guardianapp.mobile.api.FamilyGroupResponse.MemberResponse m : g.getMembers()) {
                            if (m != null && miIdProtegido.equals(m.getUserId()) && ("PRIMARY_HOST".equals(m.getRole()) || "SECONDARY_HOST".equals(m.getRole()))) {
                                isHost = true;
                                break;
                            }
                        }
                        if (isHost) break;
                    }
                }

                Intent intent;
                if (isHost) {
                    intent = new Intent(VerificationActivity.this, HostDashboardActivity.class);
                    intent.putExtra("HOST_ID", miIdProtegido);
                } else {
                    intent = new Intent(VerificationActivity.this, ProtectedDashboardActivity.class);
                    intent.putExtra("PROTECTED_ID", miIdProtegido);
                    intent.putExtra("LINK_ID", idDelVinculo);
                }
                startActivity(intent);
            }

            @Override
            public void onFailure(Call<java.util.List<com.guardianapp.mobile.api.FamilyGroupResponse>> call, Throwable t) {
                Intent intent = new Intent(VerificationActivity.this, ProtectedDashboardActivity.class);
                intent.putExtra("PROTECTED_ID", miIdProtegido);
                intent.putExtra("LINK_ID", idDelVinculo);
                startActivity(intent);
            }
        });
    }
}
