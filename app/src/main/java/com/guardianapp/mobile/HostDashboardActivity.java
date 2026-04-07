package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class HostDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host_dashboard);

        Button btnRefresh = findViewById(R.id.btnRefreshStatus);
        TextView tvLogout = findViewById(R.id.tvLogoutHost);

        btnRefresh.setOnClickListener(v ->
                Toast.makeText(this, "Actualizando estado de tus protegidos...", Toast.LENGTH_SHORT).show()
        );

        tvLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}