package com.mad.cw.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.MainActivity;
import com.mad.cw.ProfilePreferences;
import com.mad.cw.R;
import com.mad.cw.supabase.ProfileRemoteRepository;
import com.mad.cw.supabase.SessionStore;
import com.mad.cw.supabase.SupabaseAuthApi;
import com.mad.cw.supabase.SupabaseRestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class login extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SessionStore.init(this);
        setContentView(R.layout.activity_login);

        EditText etEmail = findViewById(R.id.et_email);
        EditText etPassword = findViewById(R.id.et_password);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tv_register);
        TextView tvForgot = findViewById(R.id.tv_forgot_password);

        tvForgot.setOnClickListener(
                v -> Toast.makeText(this, R.string.auth_password_reset_hint, Toast.LENGTH_LONG).show());

        btnLogin.setOnClickListener(v -> {
            String email = textOf(etEmail);
            String password = textOf(etPassword);

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, R.string.auth_fill_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AuthValidation.isValidEmail(email)) {
                Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < AuthValidation.MIN_PASSWORD_LENGTH) {
                Toast.makeText(this, R.string.auth_password_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SupabaseRestClient.isConfigured()) {
                Toast.makeText(this, R.string.auth_supabase_not_configured, Toast.LENGTH_LONG).show();
                return;
            }

            btnLogin.setEnabled(false);
            try {
                io.execute(() -> {
                try {
                    SupabaseAuthApi api = new SupabaseAuthApi();
                    final SupabaseAuthApi.AuthResponse res = api.signInWithPassword(email, password);
                    if (res.success) {
                        SessionStore.saveSession(
                                res.accessToken, res.refreshToken, res.expiresInSeconds, res.userId);
                        try {
                            ProfileRemoteRepository.FetchedProfile fetched =
                                    ProfileRemoteRepository.fetchMyProfile();
                            ProfilePreferences.copyFromRecord(login.this, fetched.record);
                        } catch (Exception ignored) {
                            // Dashboard / Profile will retry fetch.
                        }
                    }
                    safePost(() -> {
                        btnLogin.setEnabled(true);
                        if (res.success) {
                            startActivity(new Intent(login.this, MainActivity.class));
                            finish();
                        } else {
                            Toast.makeText(
                                            login.this,
                                            res.errorMessage != null
                                                    ? res.errorMessage
                                                    : getString(R.string.auth_failed),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                } catch (Exception e) {
                    safePost(() -> {
                        btnLogin.setEnabled(true);
                        Toast.makeText(
                                        login.this,
                                        e.getMessage() != null
                                                ? e.getMessage()
                                                : getString(R.string.auth_network_error),
                                        Toast.LENGTH_LONG)
                                .show();
                    });
                }
            });
            } catch (RejectedExecutionException ignored) {
                btnLogin.setEnabled(true);
            }
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, register.class));
            finish();
        });
    }

    private void safePost(Runnable action) {
        runOnUiThread(() -> {
            if (destroyed || isFinishing() || isDestroyed()) {
                return;
            }
            action.run();
        });
    }

    private static String textOf(EditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        io.shutdown();
        super.onDestroy();
    }
}
