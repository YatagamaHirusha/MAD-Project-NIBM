package com.mad.cw.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.shell.AccountSync;
import com.mad.cw.profile.GenderOptions;
import com.mad.cw.shell.MainActivity;
import com.mad.cw.profile.ProfilePreferences;
import com.mad.cw.R;
import com.mad.cw.supabase.auth.SupabaseAuthApi;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.repositories.ProfileRemoteRepository;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class register extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SessionStore.init(this);
        setContentView(R.layout.activity_register);

        EditText etName = findViewById(R.id.et_name);
        EditText etEmail = findViewById(R.id.et_reg_email);
        EditText etDob = findViewById(R.id.et_dob);
        EditText etPassword = findViewById(R.id.et_reg_password);
        EditText etPasswordConfirm = findViewById(R.id.et_reg_password_confirm);
        AutoCompleteTextView actLocation = findViewById(R.id.act_reg_location);
        AutoCompleteTextView actGender = findViewById(R.id.act_reg_gender);
        LocationOccupationOptions.setupLocationDropdown(this, actLocation);
        Button btnRegister = findViewById(R.id.btn_register);
        TextView tvLogin = findViewById(R.id.tv_login_link);

        AuthValidation.attachDobPicker(this, etDob);
        GenderOptions.setupGenderDropdown(this, actGender);

        btnRegister.setOnClickListener(v -> {
            String name = textOf(etName);
            String email = textOf(etEmail);
            String dobStr = textOf(etDob);
            String genderValue = GenderOptions.genderValueFromInput(this, textOf(actGender));
            String password = textOf(etPassword);
            String passwordConfirm = textOf(etPasswordConfirm);

            if (!AuthValidation.isValidSignupName(name)) {
                toast(R.string.auth_invalid_name);
                return;
            }
            if (!AuthValidation.isValidEmail(email)) {
                toast(R.string.auth_invalid_email);
                return;
            }
            if (dobStr.isEmpty()) {
                toast(R.string.auth_dob_required);
                return;
            }
            Calendar dob = AuthValidation.parseDob(dobStr);
            if (dob == null) {
                toast(R.string.auth_dob_invalid);
                return;
            }
            Calendar today = Calendar.getInstance();
            if (!AuthValidation.isAtLeastYearsOld(dob, AuthValidation.MIN_AGE_YEARS, today)) {
                toast(R.string.auth_adults_only);
                return;
            }
            if (!AuthValidation.isValidSignupPassword(password)) {
                toast(R.string.auth_password_short);
                return;
            }
            if (passwordConfirm.isEmpty()) {
                toast(R.string.auth_fill_fields);
                return;
            }
            if (!password.equals(passwordConfirm)) {
                toast(R.string.auth_password_mismatch);
                return;
            }
            if (!GenderOptions.isValidGenderValue(genderValue)) {
                toast(R.string.auth_gender_required);
                return;
            }
            if (!LocationOccupationOptions.isValidLocation(textOf(actLocation))) {
                toast(R.string.profile_location_must_choose);
                return;
            }
            final String regLocation = LocationOccupationOptions.canonicalLocation(textOf(actLocation));
            if (!SupabaseRestClient.isConfigured()) {
                toast(R.string.auth_supabase_not_configured);
                return;
            }

            btnRegister.setEnabled(false);
            try {
                io.execute(() -> {
                try {
                    SupabaseAuthApi api = new SupabaseAuthApi();
                    SupabaseAuthApi.AuthResponse res = api.signUp(email, password, name);
                    String profileSyncError = null;
                    if (res.success) {
                        String previousUserId = SessionStore.getUserId();
                        SessionStore.saveSession(res.accessToken, res.refreshToken, res.expiresInSeconds, res.userId);
                        String newUserId = res.userId != null ? res.userId.trim() : "";
                        if (!newUserId.isEmpty() && !newUserId.equals(previousUserId)) {
                            AccountScopedLocalStore.clearForNewAccount(register.this.getApplicationContext());
                        }
                        try {
                            ProfileRemoteRepository.upsertMyProfile(
                                    res.accessToken,
                                    res.userId,
                                    name,
                                    email,
                                    dobStr,
                                    genderValue,
                                    regLocation);
                        } catch (Exception e) {
                            profileSyncError = e.getMessage() != null ? e.getMessage() : e.toString();
                        }
                        AccountSync.syncFromServer(register.this.getApplicationContext());
                        SharedPreferences p = ProfilePreferences.get(register.this);
                        SharedPreferences.Editor pe = p.edit();
                        if (p.getString(ProfilePreferences.KEY_DISPLAY_NAME, "").trim().isEmpty()) {
                            pe.putString(ProfilePreferences.KEY_DISPLAY_NAME, name)
                                    .putString(ProfilePreferences.KEY_EMAIL, email)
                                    .putString(ProfilePreferences.KEY_DOB, dobStr)
                                    .putString(ProfilePreferences.KEY_GENDER, genderValue);
                        }
                        pe.putString(ProfilePreferences.KEY_LOCATION, regLocation).commit();
                    }
                    final String syncErr = profileSyncError;
                    final SupabaseAuthApi.AuthResponse resFinal = res;
                    safePost(() -> {
                        btnRegister.setEnabled(true);
                        if (resFinal.success) {
                            if (syncErr != null) {
                                Toast.makeText(
                                                register.this,
                                                getString(R.string.auth_profile_sync_warn) + " " + syncErr,
                                                Toast.LENGTH_LONG)
                                        .show();
                            }
                            startActivity(new Intent(register.this, MainActivity.class));
                            finish();
                        } else if (resFinal.needsEmailConfirmation) {
                            Toast.makeText(register.this, R.string.auth_confirm_email, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(
                                            register.this,
                                            resFinal.errorMessage != null
                                                    ? resFinal.errorMessage
                                                    : getString(R.string.auth_failed),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                } catch (Exception e) {
                    safePost(() -> {
                        btnRegister.setEnabled(true);
                        Toast.makeText(
                                        register.this,
                                        e.getMessage() != null ? e.getMessage() : getString(R.string.auth_network_error),
                                        Toast.LENGTH_LONG)
                                .show();
                    });
                }
            });
            } catch (RejectedExecutionException ignored) {
                btnRegister.setEnabled(true);
            }
        });

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, login.class));
            finish();
        });
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
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

    private static String textOf(AutoCompleteTextView e) {
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
