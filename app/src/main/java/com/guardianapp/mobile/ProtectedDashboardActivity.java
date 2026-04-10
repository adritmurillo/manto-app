package com.guardianapp.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class ProtectedDashboardActivity extends AppCompatActivity {

    private String miIdProtegido;
    private String idDelVinculo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_protected_dashboard);

        miIdProtegido = getIntent().getStringExtra("PROTECTED_ID");
        idDelVinculo = getIntent().getStringExtra("LINK_ID");

        Button btnSimulateThreat = findViewById(R.id.btnSimulateThreat);
        TextView tvLogout = findViewById(R.id.tvLogoutProtected);

        // ¡Abrimos la trampa!
        btnSimulateThreat.setOnClickListener(v -> {
            Intent intent = new Intent(this, SecureBrowserActivity.class);
            intent.putExtra("PROTECTED_ID", miIdProtegido);
            intent.putExtra("LINK_ID", idDelVinculo);
            startActivity(intent);
        });

        tvLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}