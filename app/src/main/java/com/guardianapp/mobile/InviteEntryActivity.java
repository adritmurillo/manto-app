package com.guardianapp.mobile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.guardianapp.mobile.invite.PendingInviteStore;

/**
 * Entry point for invite links.
 * Supports:
 * - manto://invite/<TOKEN>
 * - https://<host>/i/<TOKEN> (Android App Links, when configured)
 */
public class InviteEntryActivity extends AppCompatActivity {

    public static final String EXTRA_INVITE_TOKEN = "INVITE_TOKEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String token = parseToken(getIntent() != null ? getIntent().getData() : null);
        if (token != null && !token.isBlank()) {
            PendingInviteStore.save(this, token);
        }

        // Always go to MainActivity; it will drive login/registration and then continue.
        Intent next = new Intent(this, MainActivity.class);
        if (token != null && !token.isBlank()) {
            next.putExtra(EXTRA_INVITE_TOKEN, token);
        }
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(next);
        finish();
    }

    private static String parseToken(Uri uri) {
        if (uri == null) return null;

        // manto://invite/<TOKEN>
        if ("manto".equalsIgnoreCase(uri.getScheme()) && "invite".equalsIgnoreCase(uri.getHost())) {
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                return path.substring(1); // remove leading '/'
            }
        }

        // https://<host>/i/<TOKEN>
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                String prefix = "/i/";
                int idx = path.indexOf(prefix);
                if (idx >= 0) {
                    String rest = path.substring(idx + prefix.length());
                    int slash = rest.indexOf('/');
                    return (slash >= 0 ? rest.substring(0, slash) : rest);
                }
            }
        }

        return null;
    }
}
