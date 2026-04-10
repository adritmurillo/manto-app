package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.guardianapp.mobile.api.AlertResponse;
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.ResolveAlertRequest;
import com.guardianapp.mobile.api.RetrofitClient;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HostDashboardActivity extends AppCompatActivity {

    private String miIdAnfitrion;
    private Handler pollingHandler;
    private Runnable pollingRunnable;

    private LinearLayout layoutPendingAction;
    private TextView tvConnectionCodeDisplay;
    private Button btnRefreshStatus;
    private boolean isAlertShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_dashboard);

        miIdAnfitrion = getIntent().getStringExtra("HOST_ID");

        // Enlazamos las vistas
        layoutPendingAction = findViewById(R.id.layoutPendingAction);
        tvConnectionCodeDisplay = findViewById(R.id.tvConnectionCodeDisplay);
        btnRefreshStatus = findViewById(R.id.btnRefreshStatus);
        TextView tvLogout = findViewById(R.id.tvLogoutHost);

        // Botón para actualizar manualmente la vista y ver si ya pusieron el PIN
        btnRefreshStatus.setOnClickListener(v -> {
            Toast.makeText(this, "Actualizando estado...", Toast.LENGTH_SHORT).show();
            loadLinkStatus();
        });

        tvLogout.setOnClickListener(v -> {
            stopPolling();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 1. Cargamos el estado del vínculo al entrar a la pantalla
        loadLinkStatus();

        // 2. Iniciamos el radar para buscar alertas de páginas falsas
        startPolling();
    }

    private void startPolling() {
        pollingHandler = new Handler(Looper.getMainLooper());
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAlertShowing) checkPendingAlerts();
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    private void checkPendingAlerts() {
        if (miIdAnfitrion == null) return;

        RetrofitClient.getApiService().getPendingAlerts(miIdAnfitrion).enqueue(new Callback<List<AlertResponse>>() {
            @Override
            public void onResponse(Call<List<AlertResponse>> call, Response<List<AlertResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    showAlertPopup(response.body().get(0));
                }
            }
            @Override
            public void onFailure(Call<List<AlertResponse>> call, Throwable t) {}
        });
    }

    private void showAlertPopup(AlertResponse alert) {
        isAlertShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ ¡ALERTA DE SEGURIDAD!");
        builder.setMessage("Tu protegido intentó entrar a un sitio sospechoso:\n" + alert.getSuspiciousUrl());
        builder.setCancelable(false);

        builder.setPositiveButton("Mantener Bloqueado", (dialog, which) ->
                resolveAlert(alert.getId(), false, "Bloqueado")
        );

        builder.setNegativeButton("Es seguro, permitir", (dialog, which) ->
                resolveAlert(alert.getId(), true, "Verificado")
        );

        builder.show();
    }

    private void resolveAlert(String alertId, boolean allow, String note) {
        ResolveAlertRequest request = new ResolveAlertRequest(miIdAnfitrion, allow, note);
        RetrofitClient.getApiService().resolveAlert(alertId, request).enqueue(new Callback<AlertResponse>() {
            @Override
            public void onResponse(Call<AlertResponse> call, Response<AlertResponse> response) {
                isAlertShowing = false;
            }
            @Override
            public void onFailure(Call<AlertResponse> call, Throwable t) {
                isAlertShowing = false; // Igual liberamos para no trabar el app
            }
        });
    }

    private void loadLinkStatus() {
        if (miIdAnfitrion == null) return;

        RetrofitClient.getApiService().getMyLinks(miIdAnfitrion).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    LinkResponse link = response.body().get(0);

                    if ("PENDING".equals(link.getStatus())) {
                        // Mostramos el cartel gigante naranja con el código de 6 dígitos
                        layoutPendingAction.setVisibility(View.VISIBLE);
                        tvConnectionCodeDisplay.setText(link.getConnectionCode());
                    } else {
                        // Si ya está activo, lo ocultamos
                        layoutPendingAction.setVisibility(View.GONE);
                    }
                }
            }
            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                Toast.makeText(HostDashboardActivity.this, "Error conectando con servidor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}