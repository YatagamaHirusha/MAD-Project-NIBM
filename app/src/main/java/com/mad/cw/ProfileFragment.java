package com.mad.cw;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private static final String DATE_PATTERN = "MM/dd/yyyy";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.US);

    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etDob;
    private TextInputEditText etLocation;
    private TextInputEditText etOccupation;
    private TextInputEditText etBio;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etName = view.findViewById(R.id.et_profile_name);
        etEmail = view.findViewById(R.id.et_profile_email);
        etDob = view.findViewById(R.id.et_profile_dob);
        etLocation = view.findViewById(R.id.et_profile_location);
        etOccupation = view.findViewById(R.id.et_profile_occupation);
        etBio = view.findViewById(R.id.et_profile_bio);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_profile);

        etDob.setOnClickListener(v -> showDatePicker());

        btnSave.setOnClickListener(v -> saveProfile());

        loadProfile();
    }

    private void loadProfile() {
        SharedPreferences p = ProfilePreferences.get(requireContext());
        etName.setText(p.getString(ProfilePreferences.KEY_DISPLAY_NAME, ""));
        etEmail.setText(p.getString(ProfilePreferences.KEY_EMAIL, ""));
        etDob.setText(p.getString(ProfilePreferences.KEY_DOB, ""));
        etLocation.setText(p.getString(ProfilePreferences.KEY_LOCATION, ""));
        etOccupation.setText(p.getString(ProfilePreferences.KEY_OCCUPATION, ""));
        etBio.setText(p.getString(ProfilePreferences.KEY_BIO, ""));
    }

    private void showDatePicker() {
        Calendar initial = Calendar.getInstance();
        String existing = etDob.getText() != null ? etDob.getText().toString().trim() : "";
        if (!existing.isEmpty()) {
            try {
                initial.setTime(dateFormat.parse(existing));
            } catch (ParseException ignored) {
                // keep default today
            }
        }

        new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar sel = Calendar.getInstance();
                    sel.set(year, month, dayOfMonth);
                    etDob.setText(dateFormat.format(sel.getTime()));
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void saveProfile() {
        SharedPreferences.Editor e = ProfilePreferences.get(requireContext()).edit();
        e.putString(ProfilePreferences.KEY_DISPLAY_NAME, textOf(etName));
        e.putString(ProfilePreferences.KEY_EMAIL, textOf(etEmail));
        e.putString(ProfilePreferences.KEY_DOB, textOf(etDob));
        e.putString(ProfilePreferences.KEY_LOCATION, textOf(etLocation));
        e.putString(ProfilePreferences.KEY_OCCUPATION, textOf(etOccupation));
        e.putString(ProfilePreferences.KEY_BIO, textOf(etBio));
        e.apply();
        Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText field) {
        if (field.getText() == null) return "";
        return field.getText().toString().trim();
    }
}
