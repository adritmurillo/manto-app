package com.guardianapp.mobile;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.guardianapp.mobile.api.InvitationResponse;
import com.guardianapp.mobile.api.CreateFamilyGroupRequest;
import com.guardianapp.mobile.api.FamilyGroupResponse;
import com.guardianapp.mobile.api.LinkResponse;
import com.guardianapp.mobile.api.RetrofitClient;
import com.guardianapp.mobile.api.UserResponse;

import com.guardianapp.mobile.invite.PendingInviteStore;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private Button btnGenerateCode, btnLinkAccount;
    private Button btnShareInviteLink;
    private TextView tvLogoutHome;
    private TextView tvGeneratedCode;
    private EditText etInvitationCode;
    private static final String STATE_INVITE_TOKEN = "state_invite_token";
    public static final String EXTRA_PREFILL_INVITE_TOKEN = "EXTRA_PREFILL_INVITE_TOKEN";

    private String currentUserIdPostgres = null; // Aquí guardaremos el UUID real
    private final Handler linkPollingHandler = new Handler(Looper.getMainLooper());
    private Runnable linkPollingRunnable;

    private final Handler routeRetryHandler = new Handler(Looper.getMainLooper());

    private String lastInviteLink = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvLogoutHome = findViewById(R.id.tvLogoutHome);
        btnGenerateCode = findViewById(R.id.btnGenerateCode);
        tvGeneratedCode = findViewById(R.id.tvGeneratedCode);
        etInvitationCode = findViewById(R.id.etInvitationCode);
        btnLinkAccount = findViewById(R.id.btnLinkAccount);
        btnShareInviteLink = findViewById(R.id.btnShareInviteLink);

        tvLogoutHome.setOnClickListener(v -> {
            PendingInviteStore.clear(HomeActivity.this);
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        if (savedInstanceState != null) {
            String savedToken = savedInstanceState.getString(STATE_INVITE_TOKEN);
            if (savedToken != null && !savedToken.isBlank()) {
                tvGeneratedCode.setText(savedToken);
            }
        }

        // If we arrived from an invite link, prefill and attempt to continue after we fetch UUID.
        if (getIntent() != null) {
            String prefill = getIntent().getStringExtra(EXTRA_PREFILL_INVITE_TOKEN);
            if (prefill != null && !prefill.isBlank()) {
                etInvitationCode.setText(prefill.trim().toUpperCase());
            }
        }

        // Desactivar botones temporalmente hasta que tengamos el UUID
        btnGenerateCode.setEnabled(false);
        btnLinkAccount.setEnabled(false);

        fetchPostgresUserId();

        // Botón: SOY ANFITRIÓN (Generar)
        btnGenerateCode.setOnClickListener(v -> generateInvitationCode());

        btnShareInviteLink.setOnClickListener(v -> shareOrCopyInviteLink());

        // Botón: SOY PROTEGIDO (Vincular)
        btnLinkAccount.setOnClickListener(v -> acceptInvitationCode());
    }

    private void fetchPostgresUserId() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || firebaseUser.getEmail() == null) return;

        String email = firebaseUser.getEmail();

        RetrofitClient.getApiService().getUserByEmail(email).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                            currentUserIdPostgres = response.body().getId(); // Guardamos el UUID
                            btnGenerateCode.setEnabled(true);
                            btnLinkAccount.setEnabled(true);
                            Toast.makeText(HomeActivity.this, "Conectado al Backend", Toast.LENGTH_SHORT).show();

                            // Auto-continue if invite token was prefilled (from link).
                            String token = etInvitationCode.getText() != null ? etInvitationCode.getText().toString().trim() : "";
                            if (!token.isEmpty()) {
                                acceptInvitationCode();
                                return;
                            }

                            checkAndRouteIfAlreadyLinked();
                        } else {
                            Toast.makeText(HomeActivity.this, "Error al obtener perfil", Toast.LENGTH_SHORT).show();
                        }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateInvitationCode() {
        if (currentUserIdPostgres == null) return;

        ensurePrimaryFamilyGroupThenGenerateInvitation();
    }

    private void ensurePrimaryFamilyGroupThenGenerateInvitation() {
        RetrofitClient.getApiService().getMyFamilyGroups(currentUserIdPostgres)
                .enqueue(new Callback<List<FamilyGroupResponse>>() {
                    @Override
                    public void onResponse(Call<List<FamilyGroupResponse>> call, Response<List<FamilyGroupResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            doGenerateInvitation();
                            return;
                        }

                        String defaultName = "Familia de " + (FirebaseAuth.getInstance().getCurrentUser() != null
                                ? (FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null
                                    ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName()
                                    : (FirebaseAuth.getInstance().getCurrentUser().getEmail() != null
                                        ? FirebaseAuth.getInstance().getCurrentUser().getEmail().split("@")[0]
                                        : "Anfitrion"))
                                : "Anfitrion");

                        RetrofitClient.getApiService()
                                .createFamilyGroup(currentUserIdPostgres, new CreateFamilyGroupRequest(defaultName))
                                .enqueue(new Callback<FamilyGroupResponse>() {
                                    @Override
                                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                                        if (response.isSuccessful() && response.body() != null) {
                                            doGenerateInvitation();
                                        } else {
                                            Toast.makeText(HomeActivity.this, "No se pudo crear el grupo familiar", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                                        Toast.makeText(HomeActivity.this, "Error de red creando familia", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<List<FamilyGroupResponse>> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error de red consultando familias", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void doGenerateInvitation() {
        RetrofitClient.getApiService().createInvitation(currentUserIdPostgres).enqueue(new Callback<InvitationResponse>() {
            @Override
            public void onResponse(Call<InvitationResponse> call, Response<InvitationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvGeneratedCode.setText(response.body().getToken());
                    lastInviteLink = response.body().getShareableLink();
                    btnShareInviteLink.setEnabled(lastInviteLink != null && !lastInviteLink.isBlank());
                    Toast.makeText(HomeActivity.this, "Codigo generado para tu circulo familiar", Toast.LENGTH_SHORT).show();
                    startLinkPolling();
                } else {
                    Toast.makeText(HomeActivity.this, "Error al generar codigo", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<InvitationResponse> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareOrCopyInviteLink() {
        if (lastInviteLink == null || lastInviteLink.isBlank()) {
            Toast.makeText(this, "Primero genera un codigo", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, lastInviteLink + "\n\nSi no tienes la app instalada, instala y luego ingresa este codigo: " + tvGeneratedCode.getText());
        try {
            startActivity(Intent.createChooser(share, "Compartir invitacion"));
        } catch (Exception ex) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("invite", lastInviteLink));
                Toast.makeText(this, "Link copiado al portapapeles", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (tvGeneratedCode != null) {
            outState.putString(STATE_INVITE_TOKEN, tvGeneratedCode.getText().toString());
        }
    }

    private void acceptInvitationCode() {
        if (currentUserIdPostgres == null) return;

        String token = etInvitationCode.getText().toString().trim();
        if (token.isEmpty()) {
            Toast.makeText(this, "Ingresa un código", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getApiService().acceptInvitation(token, currentUserIdPostgres).enqueue(new Callback<LinkResponse>() {
            @Override
            public void onResponse(Call<LinkResponse> call, Response<LinkResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(HomeActivity.this, "¡Código aceptado! Configurando seguridad...", Toast.LENGTH_SHORT).show();

                    LinkResponse link = response.body();
                    Intent intent;
                    if ("ACTIVE".equals(link.getStatus())) {
                        intent = new Intent(HomeActivity.this, ProtectedDashboardActivity.class);
                    } else {
                        intent = new Intent(HomeActivity.this, VerificationActivity.class);
                    }
                    intent.putExtra("PROTECTED_ID", currentUserIdPostgres);
                    intent.putExtra("LINK_ID", link.getId());
                    startActivity(intent);
                    finish();

                } else {
                    tryAcceptFamilyInvitation(token);
                }
            }

            @Override
            public void onFailure(Call<LinkResponse> call, Throwable t) {
                tryAcceptFamilyInvitation(token);
            }
        });
    }

    private void tryAcceptFamilyInvitation(String token) {
        RetrofitClient.getApiService().acceptFamilyInvitation(token, currentUserIdPostgres)
                .enqueue(new Callback<FamilyGroupResponse>() {
                    @Override
                    public void onResponse(Call<FamilyGroupResponse> call, Response<FamilyGroupResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(HomeActivity.this, "Te uniste al circulo familiar", Toast.LENGTH_SHORT).show();
                            // Backend creates a PENDING link for PROTECTED and SECONDARY_HOST.
                            // Route based on role: secondary hosts should reach the host dashboard after PIN.
                            FamilyGroupResponse group = response.body();
                            String role = null;
                            if (group != null && group.getMembers() != null) {
                                for (FamilyGroupResponse.MemberResponse member : group.getMembers()) {
                                    if (member != null && currentUserIdPostgres.equals(member.getUserId())) {
                                        role = member.getRole();
                                        break;
                                    }
                                }
                            }
                            if ("SECONDARY_HOST".equals(role)) {
                                routeSecondaryHostToVerificationOrDashboard();
                            } else {
                                // Retry briefly so it works immediately after accept.
                                routeProtectedToVerificationOrDashboardWithRetry();
                            }
                        } else {
                            Toast.makeText(HomeActivity.this, "Codigo invalido o expirado", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<FamilyGroupResponse> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void routeSecondaryHostToVerificationOrDashboard() {
        routeToVerificationOrDashboardWithRetry(true, 0);
    }

    private void routeProtectedToVerificationOrDashboardWithRetry() {
        routeToVerificationOrDashboardWithRetry(false, 0);
    }

    private void routeToVerificationOrDashboardWithRetry(boolean activeGoesToHostDashboard, int attempt) {
        if (currentUserIdPostgres == null) {
            return;
        }
        RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LinkResponse pending = null;
                    LinkResponse active = null;

                    for (LinkResponse link : response.body()) {
                        if (!currentUserIdPostgres.equals(link.getProtectedUserId())) {
                            continue;
                        }
                        if ("PENDING".equals(link.getStatus())) {
                            pending = link;
                            break;
                        }
                        if (active == null && "ACTIVE".equals(link.getStatus())) {
                            active = link;
                        }
                    }

                    LinkResponse chosen = pending != null ? pending : active;
                    if (chosen != null) {
                        Intent intent;
                        if ("ACTIVE".equals(chosen.getStatus())) {
                            intent = new Intent(HomeActivity.this,
                                    activeGoesToHostDashboard ? HostDashboardActivity.class : ProtectedDashboardActivity.class);
                            if (activeGoesToHostDashboard) {
                                intent.putExtra("HOST_ID", currentUserIdPostgres);
                            } else {
                                intent.putExtra("PROTECTED_ID", currentUserIdPostgres);
                                intent.putExtra("LINK_ID", chosen.getId());
                            }
                        } else {
                            intent = new Intent(HomeActivity.this, VerificationActivity.class);
                            intent.putExtra("PROTECTED_ID", currentUserIdPostgres);
                            intent.putExtra("LINK_ID", chosen.getId());
                        }
                        startActivity(intent);
                        finish();
                        return;
                    }
                }

                // The accept call may return before the link shows up in reads; retry briefly.
                if (attempt < 6) {
                    routeRetryHandler.postDelayed(() -> routeToVerificationOrDashboardWithRetry(activeGoesToHostDashboard, attempt + 1), 600L);
                    return;
                }

                // Give up and keep the user on this screen.
                btnLinkAccount.setEnabled(true);
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                if (attempt < 6) {
                    routeRetryHandler.postDelayed(() -> routeToVerificationOrDashboardWithRetry(activeGoesToHostDashboard, attempt + 1), 600L);
                    return;
                }
                btnLinkAccount.setEnabled(true);
            }
        });
    }

    private void routeProtectedToVerificationOrDashboard() {
        RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LinkResponse pending = null;
                    LinkResponse active = null;
                    for (LinkResponse link : response.body()) {
                        if (!currentUserIdPostgres.equals(link.getProtectedUserId())) {
                            continue;
                        }
                        if ("PENDING".equals(link.getStatus())) {
                            pending = link;
                            break;
                        }
                        if (active == null && "ACTIVE".equals(link.getStatus())) {
                            active = link;
                        }
                    }

                    LinkResponse chosen = pending != null ? pending : active;
                    if (chosen != null) {
                        Intent intent;
                        if ("ACTIVE".equals(chosen.getStatus())) {
                            intent = new Intent(HomeActivity.this, ProtectedDashboardActivity.class);
                        } else {
                            intent = new Intent(HomeActivity.this, VerificationActivity.class);
                        }
                        intent.putExtra("PROTECTED_ID", currentUserIdPostgres);
                        intent.putExtra("LINK_ID", chosen.getId());
                        startActivity(intent);
                        finish();
                        return;
                    }
                }

                Intent fallback = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(fallback);
                finish();
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                Intent fallback = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(fallback);
                finish();
            }
        });
    }

    private void startLinkPolling() {
        stopLinkPolling();
        linkPollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentUserIdPostgres == null) {
                    return;
                }
                RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
                    @Override
                    public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (LinkResponse link : response.body()) {
                                if (currentUserIdPostgres.equals(link.getHostId()) &&
                                        ("PENDING".equals(link.getStatus()) || "ACTIVE".equals(link.getStatus()))) {
                                    stopLinkPolling();
                                    Intent intent = new Intent(HomeActivity.this, HostDashboardActivity.class);
                                    intent.putExtra("HOST_ID", currentUserIdPostgres);
                                    startActivity(intent);
                                    finish();
                                    return;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                    }
                });
                linkPollingHandler.postDelayed(this, 3000);
            }
        };
        linkPollingHandler.post(linkPollingRunnable);
    }

    private void stopLinkPolling() {
        if (linkPollingRunnable != null) {
            linkPollingHandler.removeCallbacks(linkPollingRunnable);
        }
    }

    private void checkAndRouteIfAlreadyLinked() {
        if (currentUserIdPostgres == null) {
            return;
        }
        RetrofitClient.getApiService().getMyLinks(currentUserIdPostgres).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (LinkResponse link : response.body()) {
                        if (currentUserIdPostgres.equals(link.getHostId()) &&
                                ("PENDING".equals(link.getStatus()) || "ACTIVE".equals(link.getStatus()))) {
                            Intent intent = new Intent(HomeActivity.this, HostDashboardActivity.class);
                            intent.putExtra("HOST_ID", currentUserIdPostgres);
                            startActivity(intent);
                            finish();
                            return;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLinkPolling();
    }
}
