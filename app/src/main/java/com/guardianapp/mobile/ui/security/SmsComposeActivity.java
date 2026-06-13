package com.guardianapp.mobile.ui.security;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;

public class SmsComposeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_compose);

        String target = resolveTarget(getIntent());
        TextView tvRecipient = findViewById(R.id.tvComposeRecipient);
        if (target != null && !target.isBlank()) {
            tvRecipient.setText("Destino detectado: " + target);
        }

        findViewById(R.id.btnComposeClose).setOnClickListener(v -> finish());
    }

    private String resolveTarget(Intent intent) {
        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        if (data == null) {
            return null;
        }
        String schemeSpecificPart = data.getSchemeSpecificPart();
        return schemeSpecificPart == null ? null : schemeSpecificPart.trim();
    }
}
