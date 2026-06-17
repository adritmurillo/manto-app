package com.guardianapp.mobile.ui.host;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.BlockAppRequest;
import com.guardianapp.mobile.data.api.BlockedAppResponse;
import com.guardianapp.mobile.data.api.GuardianApiService;
import com.guardianapp.mobile.data.api.InstalledAppResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HostAppControlActivity extends AppCompatActivity implements AppControlAdapter.OnAppActionClickListener {

    private RecyclerView rvApps;
    private AppControlAdapter adapter;
    private GuardianApiService apiService;
    private String protectedUserId;
    private String familyGroupId;
    private String hostId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_app_control);

        hostId = getIntent().getStringExtra("HOST_ID");
        familyGroupId = getIntent().getStringExtra("LINK_ID");
        protectedUserId = getIntent().getStringExtra("PROTECTED_ID");

        Toast.makeText(this, "ID Protegido: " + protectedUserId, Toast.LENGTH_LONG).show();

        if (hostId == null || familyGroupId == null || protectedUserId == null) {
            android.util.Log.e("HostAppControl", "Error crítico: Faltan credenciales de Firebase en el Intent.");
            android.widget.Toast.makeText(this, "Error de sincronización con el servidor", android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        apiService = RetrofitClient.getApiService();
        rvApps = findViewById(R.id.rvAppsList);
        rvApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppControlAdapter(this);
        rvApps.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        // Evitamos que Retrofit explote si el ID es nulo
        if (protectedUserId == null) {
            Toast.makeText(this, "Falta el ID del protegido (Es nulo)", Toast.LENGTH_LONG).show();
            return;
        }

        apiService.getInstalledApps(hostId, protectedUserId).enqueue(new Callback<List<InstalledAppResponse>>() {
            @Override
            public void onResponse(Call<List<InstalledAppResponse>> call, Response<List<InstalledAppResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<AppControlAdapter.AppItem> items = new ArrayList<>();
                    for (InstalledAppResponse app : response.body()) {
                        items.add(new AppControlAdapter.AppItem(app.getPackageName(), app.getAppName(), false, null));
                    }
                    adapter.setApps(items);
                } else {
                    // Si el servidor responde pero con error (ej. 404 o 500)
                    Toast.makeText(HostAppControlActivity.this, "Error del servidor: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<InstalledAppResponse>> call, Throwable t) {
                // Si no hay internet, ngrok está mal o Retrofit falla
                android.util.Log.e("AppControl", "Error crítico: " + t.getMessage());
                Toast.makeText(HostAppControlActivity.this, "Fallo red: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onBlockClick(AppControlAdapter.AppItem app) {
        // Usamos la variable familyGroupId real que ya tenemos
        BlockAppRequest req = new BlockAppRequest(familyGroupId, app.packageName, app.appName);

        // OJO: Cambiamos "HOST_ID" por la variable hostId real que recuperamos en el onCreate
        apiService.blockApp(hostId, req).enqueue(new Callback<BlockedAppResponse>() {
            @Override
            public void onResponse(Call<BlockedAppResponse> call, Response<BlockedAppResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HostAppControlActivity.this, "¡Guardado en Base de Datos!", Toast.LENGTH_SHORT).show();
                    loadData(); // Recargamos la lista para ver el cambio de botón
                } else {
                    // Si el servidor lo rechaza, aquí veremos el código de error real (ej: 400, 404, 500)
                    Toast.makeText(HostAppControlActivity.this, "Servidor rechazó bloqueo: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BlockedAppResponse> call, Throwable t) {
                Toast.makeText(HostAppControlActivity.this, "Fallo de red al bloquear: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onUnblockClick(AppControlAdapter.AppItem app) {
        if (app.blockedId == null) return;

        apiService.unblockApp(hostId, app.blockedId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HostAppControlActivity.this, "¡Desbloqueado con éxito!", Toast.LENGTH_SHORT).show();
                    loadData();
                } else {
                    Toast.makeText(HostAppControlActivity.this, "Error al desbloquear: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(HostAppControlActivity.this, "Fallo de red al desbloquear", Toast.LENGTH_SHORT).show();
            }
        });
    }
}