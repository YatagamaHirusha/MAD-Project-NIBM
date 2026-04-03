package com.mad.cw;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.mad.cw.auth.AuthValidation;
import com.mad.cw.supabase.ProfileRecord;
import com.mad.cw.supabase.ProfileRemoteRepository;
import com.mad.cw.supabase.SessionStore;
import com.mad.cw.supabase.SupabaseRestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ProfileFragment extends Fragment {

    private TextView tvProfileSubtitle;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etDob;
    private AutoCompleteTextView actGender;
    private AutoCompleteTextView actTargetGender;
    private AutoCompleteTextView actLocation;
    private AutoCompleteTextView actOccupation;
    private TextInputEditText etBio;
    private MaterialButton btnSave;
    private MaterialButton btnEditPreferences;
    private View cardProfileEditor;

    private ExecutorService io;
    private volatile boolean viewDestroyed;
    /** From last successful fetch; {@code false} after load error (prefer upsert on next save). */
    private boolean profileRowExists;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewDestroyed = false;
        io = Executors.newSingleThreadExecutor();

        tvProfileSubtitle = view.findViewById(R.id.tv_profile_subtitle);
        etName = view.findViewById(R.id.et_profile_name);
        etEmail = view.findViewById(R.id.et_profile_email);
        etDob = view.findViewById(R.id.et_profile_dob);
        actGender = view.findViewById(R.id.act_profile_gender);
        actTargetGender = view.findViewById(R.id.act_profile_target_gender);
        actLocation = view.findViewById(R.id.act_profile_location);
        actOccupation = view.findViewById(R.id.act_profile_occupation);
        etBio = view.findViewById(R.id.et_profile_bio);
        btnSave = view.findViewById(R.id.btn_save_profile);
        btnEditPreferences = view.findViewById(R.id.btn_edit_preferences);
        cardProfileEditor = view.findViewById(R.id.card_profile_editor);
        ImageButton ibLogout = view.findViewById(R.id.ib_profile_logout);
        MaterialButton btnRetakeEcr = view.findViewById(R.id.btn_retake_ecr);

        AuthValidation.attachDobPicker(requireContext(), etDob);
        GenderOptions.setupGenderDropdown(requireContext(), actGender);
        GenderOptions.setupTargetGenderDropdown(requireContext(), actTargetGender);
        LocationOccupationOptions.setupLocationDropdown(requireContext(), actLocation);
        LocationOccupationOptions.setupOccupationDropdown(requireContext(), actOccupation);

        btnSave.setOnClickListener(v -> saveProfileToRemote());
        if (ibLogout != null) {
            ibLogout.setOnClickListener(v -> performLogout(ibLogout));
        }
        if (btnEditPreferences != null && cardProfileEditor != null) {
            btnEditPreferences.setOnClickListener(v -> toggleProfileEditor());
        }
        if (btnRetakeEcr != null) {
            btnRetakeEcr.setOnClickListener(
                    v -> startActivity(new Intent(requireContext(), Questionnaire.class)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRemoteProfile();
    }

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        if (io != null) {
            io.shutdown();
            io = null;
        }
        super.onDestroyView();
    }

    private void safePost(Runnable action) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (viewDestroyed || !isAdded()) {
                return;
            }
            action.run();
        });
    }

    private void loadRemoteProfile() {
        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            loadProfileFromLocalPrefs();
            updateSubtitleFromForm();
            expandEditorFromCachedPrefsIfNeeded();
            return;
        }

        try {
            io.execute(() -> {
                try {
                    ProfileRemoteRepository.FetchedProfile fetched = ProfileRemoteRepository.fetchMyProfile();
                    safePost(() -> {
                        profileRowExists = fetched.rowExists;
                        applyRecordToForm(fetched.record);
                        ProfilePreferences.copyFromRecord(requireContext(), fetched.record);
                        updateSubtitleFromRecord(fetched.record);
                        expandEditorWhenRemoteDataPresent(fetched.record, fetched.rowExists);
                    });
                } catch (Exception e) {
                    safePost(() -> {
                        profileRowExists = false;
                        loadProfileFromLocalPrefs();
                        Toast.makeText(requireContext(), R.string.profile_load_failed, Toast.LENGTH_LONG).show();
                        updateSubtitleFromForm();
                        expandEditorFromCachedPrefsIfNeeded();
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            loadProfileFromLocalPrefs();
        }
    }

    private void applyRecordToForm(ProfileRecord r) {
        Context ctx = requireContext();
        etName.setText(r.displayName);
        etEmail.setText(r.email);
        etDob.setText(r.dateOfBirth);
        actGender.setText(GenderOptions.labelForGenderValue(ctx, r.gender), false);
        actLocation.setText(LocationOccupationOptions.canonicalLocation(r.location), false);
        actOccupation.setText(LocationOccupationOptions.canonicalOccupation(r.occupation), false);
        actTargetGender.setText(GenderOptions.labelForTargetGenderValue(ctx, r.targetGender), false);
        etBio.setText(r.bio);
    }

    private void loadProfileFromLocalPrefs() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        SharedPreferences p = ProfilePreferences.get(ctx);
        etName.setText(p.getString(ProfilePreferences.KEY_DISPLAY_NAME, ""));
        etEmail.setText(p.getString(ProfilePreferences.KEY_EMAIL, ""));
        etDob.setText(p.getString(ProfilePreferences.KEY_DOB, ""));
        actLocation.setText(
                LocationOccupationOptions.canonicalLocation(p.getString(ProfilePreferences.KEY_LOCATION, "")),
                false);
        actOccupation.setText(
                LocationOccupationOptions.canonicalOccupation(
                        p.getString(ProfilePreferences.KEY_OCCUPATION, "")),
                false);
        String g = p.getString(ProfilePreferences.KEY_GENDER, "");
        String tg = p.getString(ProfilePreferences.KEY_TARGET_GENDER, "");
        actGender.setText(GenderOptions.labelForGenderValue(ctx, g), false);
        actTargetGender.setText(GenderOptions.labelForTargetGenderValue(ctx, tg), false);
        etBio.setText(p.getString(ProfilePreferences.KEY_BIO, ""));
    }

    private void expandEditorFromCachedPrefsIfNeeded() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        SharedPreferences p = ProfilePreferences.get(ctx);
        ProfileRecord r =
                new ProfileRecord(
                        p.getString(ProfilePreferences.KEY_DISPLAY_NAME, ""),
                        p.getString(ProfilePreferences.KEY_EMAIL, ""),
                        p.getString(ProfilePreferences.KEY_DOB, ""),
                        p.getString(ProfilePreferences.KEY_BIO, ""),
                        p.getString(ProfilePreferences.KEY_LOCATION, ""),
                        p.getString(ProfilePreferences.KEY_OCCUPATION, ""),
                        p.getString(ProfilePreferences.KEY_GENDER, ""),
                        p.getString(ProfilePreferences.KEY_TARGET_GENDER, ""));
        expandEditorWhenRemoteDataPresent(r, true);
    }

    /** Shows the form when we loaded a row from the server so saved DB values are visible immediately. */
    private void expandEditorWhenRemoteDataPresent(ProfileRecord r, boolean rowExists) {
        if (cardProfileEditor == null || btnEditPreferences == null || !rowExists) {
            return;
        }
        boolean any =
                !isBlank(r.displayName)
                        || !isBlank(r.email)
                        || !isBlank(r.dateOfBirth)
                        || !isBlank(r.location)
                        || !isBlank(r.occupation)
                        || !isBlank(r.bio)
                        || !isBlank(r.gender)
                        || !isBlank(r.targetGender);
        if (any) {
            cardProfileEditor.setVisibility(View.VISIBLE);
            btnEditPreferences.setText(getString(R.string.profile_done_editing));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void mirrorPrefsFromForm() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        ProfilePreferences.get(ctx).edit()
                .putString(ProfilePreferences.KEY_DISPLAY_NAME, textOf(etName))
                .putString(ProfilePreferences.KEY_EMAIL, textOf(etEmail))
                .putString(ProfilePreferences.KEY_DOB, textOf(etDob))
                .putString(
                        ProfilePreferences.KEY_LOCATION,
                        LocationOccupationOptions.canonicalLocation(textOf(actLocation)))
                .putString(
                        ProfilePreferences.KEY_OCCUPATION,
                        LocationOccupationOptions.canonicalOccupation(textOf(actOccupation)))
                .putString(
                        ProfilePreferences.KEY_GENDER,
                        GenderOptions.genderValueFromInput(ctx, textOf(actGender)))
                .putString(
                        ProfilePreferences.KEY_TARGET_GENDER,
                        GenderOptions.targetGenderValueFromInput(ctx, textOf(actTargetGender)))
                .putString(ProfilePreferences.KEY_BIO, textOf(etBio))
                .commit();
    }

    private void updateSubtitleFromRecord(ProfileRecord r) {
        if (tvProfileSubtitle == null) {
            return;
        }
        boolean complete = ProfileFormValidator.isBasicProfileComplete(
                r.displayName,
                r.email,
                r.dateOfBirth,
                r.location,
                r.occupation,
                r.bio,
                r.gender,
                r.targetGender);
        tvProfileSubtitle.setText(
                complete ? getString(R.string.profile_subtitle) : getString(R.string.profile_subtitle_incomplete));
    }

    private void updateSubtitleFromForm() {
        if (tvProfileSubtitle == null) {
            return;
        }
        Context ctx = requireContext();
        boolean complete =
                ProfileFormValidator.isBasicProfileComplete(
                        textOf(etName),
                        textOf(etEmail),
                        textOf(etDob),
                        LocationOccupationOptions.canonicalLocation(textOf(actLocation)),
                        LocationOccupationOptions.canonicalOccupation(textOf(actOccupation)),
                        textOf(etBio),
                        GenderOptions.genderValueFromInput(ctx, textOf(actGender)),
                        GenderOptions.targetGenderValueFromInput(ctx, textOf(actTargetGender)));
        tvProfileSubtitle.setText(
                complete ? getString(R.string.profile_subtitle) : getString(R.string.profile_subtitle_incomplete));
    }

    private void saveProfileToRemote() {
        String name = textOf(etName);
        String email = textOf(etEmail);
        String dob = textOf(etDob);
        Context ctx = requireContext();
        String location = LocationOccupationOptions.canonicalLocation(textOf(actLocation));
        String occupation = LocationOccupationOptions.canonicalOccupation(textOf(actOccupation));
        if (!LocationOccupationOptions.isValidLocation(location)) {
            Toast.makeText(ctx, R.string.profile_location_must_choose, Toast.LENGTH_LONG).show();
            return;
        }
        if (!LocationOccupationOptions.isValidOccupation(occupation)) {
            Toast.makeText(ctx, R.string.profile_occupation_must_choose, Toast.LENGTH_LONG).show();
            return;
        }
        String bio = textOf(etBio);
        String genderValue = GenderOptions.genderValueFromInput(ctx, textOf(actGender));
        String targetValue = GenderOptions.targetGenderValueFromInput(ctx, textOf(actTargetGender));

        int validation =
                ProfileFormValidator.validateBasicFields(
                        name, email, dob, location, occupation, bio, genderValue, targetValue);
        if (validation != 0) {
            Toast.makeText(requireContext(), validation, Toast.LENGTH_LONG).show();
            return;
        }

        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            mirrorPrefsFromForm();
            Toast.makeText(requireContext(), R.string.profile_not_signed_in, Toast.LENGTH_LONG).show();
            return;
        }

        final boolean rowExistsSnapshot = profileRowExists;
        btnSave.setEnabled(false);
        try {
            io.execute(() -> {
                try {
                    ProfileRemoteRepository.saveMyProfileForm(
                            name,
                            email,
                            dob,
                            bio,
                            location,
                            occupation,
                            genderValue,
                            targetValue,
                            rowExistsSnapshot);
                    safePost(() -> {
                        profileRowExists = true;
                        btnSave.setEnabled(true);
                        actLocation.setText(location, false);
                        actOccupation.setText(occupation, false);
                        mirrorPrefsFromForm();
                        updateSubtitleFromForm();
                        Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    safePost(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(
                                        requireContext(),
                                        getString(R.string.profile_save_failed)
                                                + (e.getMessage() != null ? " " + e.getMessage() : ""),
                                        Toast.LENGTH_LONG)
                                .show();
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            btnSave.setEnabled(true);
        }
    }

    private void toggleProfileEditor() {
        if (cardProfileEditor == null || btnEditPreferences == null) {
            return;
        }
        boolean opening = cardProfileEditor.getVisibility() != View.VISIBLE;
        cardProfileEditor.setVisibility(opening ? View.VISIBLE : View.GONE);
        btnEditPreferences.setText(
                opening
                        ? getString(R.string.profile_done_editing)
                        : getString(R.string.profile_edit_preferences));
    }

    private void performLogout(View logoutControl) {
        logoutControl.setEnabled(false);
        FragmentActivity activity = requireActivity();
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            AppSignOut.run(app);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                Intent intent = new Intent(activity, Welcome.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
                activity.finish();
            });
        }, "sign-out").start();
    }

    private static String textOf(EditText field) {
        if (field.getText() == null) {
            return "";
        }
        return field.getText().toString().trim();
    }
}
