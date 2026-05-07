package com.guardianapp.mobile.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Importaciones limpias de tu API y Retrofit
import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.RegisterUserRequest;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.UserResponse;
import com.guardianapp.mobile.ui.main.MainActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.guardianapp.mobile.ui.invite.PendingInviteStore;

public class RegisterActivity extends AppCompatActivity {

    // UI Elements
    private TextInputEditText etName, etEmail, etPhone, etPassword, etConfirmPassword;
    private MaterialCheckBox cbTerms;
    private Button btnRegister;
    private TextView tvBackToLogin;

    // Firebase Authentication
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();

        // Bindings
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

        // Register Button Click
        btnRegister.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            // 1. Check empty fields
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Check if passwords match
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // 3. Check Terms and Conditions
            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "You must accept the Terms and Conditions", Toast.LENGTH_SHORT).show();
                return;
            }

            // 4. Create user in Firebase
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // FIREBASE OK -> AHORA ENVIAMOS A POSTGRESQL (SPRING BOOT)

                            // Usamos las clases directamente gracias a los imports de arriba
                            RegisterUserRequest apiRequest = new RegisterUserRequest(name, email, phone);

                            RetrofitClient.getApiService().registerUser(apiRequest)
                                    .enqueue(new Callback<UserResponse>() {

                                        @Override
                                        public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                                            if (response.isSuccessful() && response.body() != null) {
                                                // ¡Spring Boot respondió 201 CREATED!
                                                Toast.makeText(RegisterActivity.this, "Success! User saved in Manto Database", Toast.LENGTH_LONG).show();

                                                // If the user came from an invite link, go straight back to Login to continue.
                                                // MainActivity will auto-continue using the stored token.
                                                if (PendingInviteStore.peek(RegisterActivity.this) != null) {
                                                    finish();
                                                    return;
                                                }

                                                finish();
                                            } else {
                                                Toast.makeText(RegisterActivity.this, "API Error: " + response.code(), Toast.LENGTH_LONG).show();
                                            }
                                        }

                                        @Override
                                        public void onFailure(Call<UserResponse> call, Throwable t) {
                                            // Error de red (Servidor apagado, no hay internet, etc)
                                            Toast.makeText(RegisterActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });

                        } else {
                            Toast.makeText(this, "Registration Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        }); // <--- ESTE ES EL CIERRE QUE TE FALTABA PARA EL BOTÓN DE REGISTRO

        // Back to Login
        tvBackToLogin.setOnClickListener(v -> finish());
    }
}
