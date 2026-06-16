package com.guardianapp.mobile.ui.security;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.R;

public class SmsComposeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_compose);

        String target = resolveTarget(getIntent());
        TextView tvRecipient = findViewById(R.id.tvComposeRecipient);
        EditText etMessage = findViewById(R.id.etComposeMessage);

        tvRecipient.setText(target == null || target.isBlank() ? "Sin destinatario" : target);

        String body = getIntent() != null ? getIntent().getStringExtra("sms_body") : null;
        if (body != null && !body.isBlank()) {
            etMessage.setText(body);
        }

        findViewById(R.id.btnComposeClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnComposeSend).setOnClickListener(v ->
                Toast.makeText(this, "Envio SMS se completara en el siguiente modulo", Toast.LENGTH_SHORT).show()
        );
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
