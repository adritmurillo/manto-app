package com.guardianapp.mobile.ui.protecteduser;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.appcontrol.BlockedApp;
import com.guardianapp.mobile.data.appcontrol.BlockedAppsStore;

public class AppBlockedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_blocked);

        TextView tvAppName = findViewById(R.id.tvBlockedAppName);
        Button btnReturn = findViewById(R.id.btnReturnHome);

        String packageName = getIntent().getStringExtra("BLOCKED_PACKAGE");

        if (packageName != null) {
            BlockedApp appInfo = BlockedAppsStore.getBlockedInfo(this, packageName);
            if (appInfo != null && appInfo.getAppName() != null) {
                tvAppName.setText(appInfo.getAppName());
            } else {
                tvAppName.setText(packageName);
            }
        }

        btnReturn.setOnClickListener(v -> returnToHomeScreen());

        // ==========================================
        // NUEVA FORMA MODERNA DE ATRAPAR EL BOTÓN ATRÁS
        // ==========================================
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Al presionar atrás, lo mandamos al inicio
                returnToHomeScreen();
            }
        });
    }

    private void returnToHomeScreen() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
        finish();
    }
}