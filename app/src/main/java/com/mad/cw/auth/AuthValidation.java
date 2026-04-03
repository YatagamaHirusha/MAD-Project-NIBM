package com.mad.cw.auth;

import android.app.DatePickerDialog;
import android.content.Context;
import android.util.Patterns;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Client-side checks for auth forms. Server remains authoritative.
 */
public final class AuthValidation {

    public static final String DOB_PATTERN = "MM/dd/yyyy";
    public static final int MIN_AGE_YEARS = 18;
    public static final int MIN_NAME_LENGTH = 2;
    public static final int MIN_PASSWORD_LENGTH = 6;

    private AuthValidation() {}

    private static SimpleDateFormat newDobFormat() {
        SimpleDateFormat fmt = new SimpleDateFormat(DOB_PATTERN, Locale.US);
        fmt.setLenient(false);
        return fmt;
    }

    public static boolean isValidEmail(@Nullable String email) {
        if (email == null) {
            return false;
        }
        String e = email.trim();
        return !e.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(e).matches();
    }

    public static boolean isValidSignupName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        return name.trim().length() >= MIN_NAME_LENGTH;
    }

    public static boolean isValidSignupPassword(@Nullable String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /** Parses {@link #DOB_PATTERN}; returns null if missing or invalid (including impossible dates). */
    @Nullable
    public static Calendar parseDob(@Nullable String dobText) {
        if (dobText == null) {
            return null;
        }
        String s = dobText.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat fmt = newDobFormat();
            Calendar cal = Calendar.getInstance();
            cal.setTime(fmt.parse(s));
            trimToDateStart(cal);
            return cal;
        } catch (ParseException e) {
            return null;
        }
    }

    private static void trimToDateStart(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    /** Full years between {@code dob} and {@code today} (local calendar). */
    public static int ageInYears(@NonNull Calendar dob, @NonNull Calendar today) {
        int years = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        int monthDiff = today.get(Calendar.MONTH) - dob.get(Calendar.MONTH);
        if (monthDiff < 0
                || (monthDiff == 0 && today.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH))) {
            years--;
        }
        return years;
    }

    public static boolean isAtLeastYearsOld(@Nullable Calendar dob, int minYears, @NonNull Calendar today) {
        if (dob == null) {
            return false;
        }
        return ageInYears(dob, today) >= minYears;
    }

    /** Latest birth date a user may pick so they can be at least {@code minYears} old today. */
    @NonNull
    public static Calendar latestBirthDateForMinAge(int minYears) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, -minYears);
        trimToDateStart(c);
        return c;
    }

    @NonNull
    public static Calendar earliestReasonableBirthDate() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, -120);
        trimToDateStart(c);
        return c;
    }

    /**
     * Opens a date picker on tap, writing {@link #DOB_PATTERN} into {@code field}.
     * Bounds: reasonable oldest date … latest date that still allows {@link #MIN_AGE_YEARS}+.
     */
    public static void attachDobPicker(@NonNull Context context, @NonNull EditText field) {
        field.setOnClickListener(v -> showDobPicker(context, field));
    }

    private static void showDobPicker(@NonNull Context context, @NonNull EditText field) {
        Calendar initial = Calendar.getInstance();
        initial.add(Calendar.YEAR, -25);
        String existing = field.getText() != null ? field.getText().toString().trim() : "";
        if (!existing.isEmpty()) {
            Calendar parsed = parseDob(existing);
            if (parsed != null) {
                initial = parsed;
            }
        }

        Calendar maxBirth = latestBirthDateForMinAge(MIN_AGE_YEARS);
        Calendar minBirth = earliestReasonableBirthDate();

        DatePickerDialog dialog = new DatePickerDialog(
                context,
                (view, year, month, dayOfMonth) -> {
                    Calendar sel = Calendar.getInstance();
                    sel.set(year, month, dayOfMonth);
                    trimToDateStart(sel);
                    field.setText(newDobFormat().format(sel.getTime()));
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMinDate(minBirth.getTimeInMillis());
        Calendar maxEnd = (Calendar) maxBirth.clone();
        maxEnd.set(Calendar.HOUR_OF_DAY, 23);
        maxEnd.set(Calendar.MINUTE, 59);
        maxEnd.set(Calendar.SECOND, 59);
        maxEnd.set(Calendar.MILLISECOND, 999);
        dialog.getDatePicker().setMaxDate(maxEnd.getTimeInMillis());
        dialog.show();
    }
}
