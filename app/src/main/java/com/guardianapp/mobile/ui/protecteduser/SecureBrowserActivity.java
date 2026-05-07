package com.guardianapp.mobile.ui.protecteduser;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.AlertResponse;
import com.guardianapp.mobile.data.api.CreateAlertRequest;
import com.guardianapp.mobile.data.api.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SecureBrowserActivity extends AppCompatActivity {

    private WebView webView;
    private LinearLayout layoutBlockScreen;
    private TextView tvWaitMessage;

    private String miIdProtegido;
    private String idDelVinculo;
    private String currentAlertId;

    private Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secure_browser);

        miIdProtegido = getIntent().getStringExtra("PROTECTED_ID");
        idDelVinculo = getIntent().getStringExtra("LINK_ID");

        webView = findViewById(R.id.webView);
        layoutBlockScreen = findViewById(R.id.layoutBlockScreen);
        tvWaitMessage = findViewById(R.id.tvWaitMessage);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // ¡CORREGIDO! Interceptamos la URL falsa de tu HTML
                if (url.contains("robar-datos") || url.contains("banco-falso")) {
                    triggerBlockAndAlert(url);
                    return true;
                }
                return false;
            }
        });

        webView.getSettings().setJavaScriptEnabled(true);

        // ¡CORREGIDO! Cargamos tu formulario HTML falso de la carpeta assets
        webView.loadUrl("file:///android_asset/formulario_trampa.html");
    }

    private void triggerBlockAndAlert(String maliciousUrl) {
        layoutBlockScreen.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        CreateAlertRequest request = new CreateAlertRequest(idDelVinculo, miIdProtegido, maliciousUrl, "Phishing detectado");
        RetrofitClient.getApiService().createAlert(request).enqueue(new Callback<AlertResponse>() {
            @Override
            public void onResponse(Call<AlertResponse> call, Response<AlertResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentAlertId = response.body().getId();
                    startPollingAlertStatus(); // Empezamos a preguntar si ya nos dieron permiso
                }
            }
            @Override
            public void onFailure(Call<AlertResponse> call, Throwable t) {}
        });
    }

    private void startPollingAlertStatus() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                RetrofitClient.getApiService().getAlert(currentAlertId).enqueue(new Callback<AlertResponse>() {
                    @Override
                    public void onResponse(Call<AlertResponse> call, Response<AlertResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String status = response.body().getStatus();
                            if ("RESOLVED_SAFE".equals(status)) {
                                // Permiso concedido, quitamos pantalla roja
                                layoutBlockScreen.setVisibility(View.GONE);
                                webView.setVisibility(View.VISIBLE);
                                webView.loadUrl(response.body().getSuspiciousUrl());
                                stopPolling();
                            } else if ("RESOLVED_BLOCKED".equals(status)) {
                                tvWaitMessage.setText("Tu familiar ha denegado el acceso permanentemente.");
                                stopPolling();
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<AlertResponse> call, Throwable t) {}
                });
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        if (pollingRunnable != null) pollingHandler.removeCallbacks(pollingRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}
