package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class ProtectedDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_protected_dashboard);

        Button btnSos = findViewById(R.id.btnSos);
        Button btnSimulateThreat = findViewById(R.id.btnSimulateThreat);
        TextView tvLogout = findViewById(R.id.tvLogoutProtected);

        btnSos.setOnClickListener(v ->
                Toast.makeText(this, "¡Alerta enviada a tu familiar!", Toast.LENGTH_LONG).show()
        );

        // El botón mágico para la Demo
        btnSimulateThreat.setOnClickListener(v ->
                Toast.makeText(this, "Se bloqueó un SMS malicioso. Avisando al Anfitrión...", Toast.LENGTH_LONG).show()
        );

        tvLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}