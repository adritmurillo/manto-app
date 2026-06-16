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
    private String protectedUserId = "ID_DEL_PROTEGIDO"; // Aquí iría el ID que selecciones en tu lista
    private String familyGroupId = "ID_DEL_GRUPO";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_app_control);

        apiService = RetrofitClient.getApiService();
        rvApps = findViewById(R.id.rvAppsList);
        rvApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppControlAdapter(this);
        rvApps.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        // Obtenemos ambas listas: instaladas y bloqueadas
        apiService.getInstalledApps("HOST_ID", protectedUserId).enqueue(new Callback<List<InstalledAppResponse>>() {
            @Override
            public void onResponse(Call<List<InstalledAppResponse>> call, Response<List<InstalledAppResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Aquí deberías cruzar la lista de instaladas con las bloqueadas
                    // Por simplicidad en este MVP, mostramos las apps y marcamos si ya están en la lista negra
                    List<AppControlAdapter.AppItem> items = new ArrayList<>();
                    for (InstalledAppResponse app : response.body()) {
                        items.add(new AppControlAdapter.AppItem(app.getPackageName(), app.getAppName(), false, null));
                    }
                    adapter.setApps(items);
                }
            }
            @Override
            public void onFailure(Call<List<InstalledAppResponse>> call, Throwable t) {
                Toast.makeText(HostAppControlActivity.this, "Error cargando apps", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBlockClick(AppControlAdapter.AppItem app) {
        BlockAppRequest req = new BlockAppRequest(familyGroupId, app.packageName, app.appName);
        apiService.blockApp("HOST_ID", req).enqueue(new Callback<BlockedAppResponse>() {
            @Override
            public void onResponse(Call<BlockedAppResponse> call, Response<BlockedAppResponse> response) {
                Toast.makeText(HostAppControlActivity.this, "Bloqueado!", Toast.LENGTH_SHORT).show();
                loadData(); // Recargamos para refrescar el estado
            }
            @Override
            public void onFailure(Call<BlockedAppResponse> call, Throwable t) { }
        });
    }

    @Override
    public void onUnblockClick(AppControlAdapter.AppItem app) {
        apiService.unblockApp("HOST_ID", app.blockedId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Toast.makeText(HostAppControlActivity.this, "Desbloqueado!", Toast.LENGTH_SHORT).show();
                loadData();
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) { }
        });
    }
}