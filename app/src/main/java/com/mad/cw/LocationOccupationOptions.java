package com.mad.cw;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;

/**
 * Canonical location and occupation labels for profile and matching. Use these arrays everywhere so
 * stored values stay consistent for the backend.
 */
public final class LocationOccupationOptions {

    public static final String[] LOCATIONS = {
            "Colombo",
            "Gampaha",
            "Kalutara",
            "Galle",
            "Matara",
            "Hambantota",
            "Ratnapura",
            "Kegalle",
            "Badulla",
            "Monaragala",
            "Kandy",
    };

    public static final String[] OCCUPATIONS = {
            "Engineer",
            "Doctor",
            "Teacher",
            "Lawyer",
            "Accountant",
            "Bank Officer",
            "Software Developer",
            "Civil Servant",
            "Farmer",
            "Business Owner",
            "Marketing Executive",
            "Student",
            "Nurse",
            "Tourism Guide",
            "Driver",
            "Chef",
            "Police Officer",
            "Electrician",
            "Construction Worker",
            "Journalist",
            "Pharmacist",
    };

    private LocationOccupationOptions() {}

    public static void setupLocationDropdown(@NonNull Context ctx, @NonNull AutoCompleteTextView field) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(ctx, android.R.layout.simple_dropdown_item_1line, LOCATIONS);
        field.setAdapter(adapter);
        field.setThreshold(1);
    }

    public static void setupOccupationDropdown(@NonNull Context ctx, @NonNull AutoCompleteTextView field) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(ctx, android.R.layout.simple_dropdown_item_1line, OCCUPATIONS);
        field.setAdapter(adapter);
        field.setThreshold(1);
    }

    /**
     * Returns the canonical list entry if {@code saved} equals one of the options (ignore case), else
     * {@code ""} so the user must pick from the list (avoids legacy free-text mismatches).
     */
    @NonNull
    public static String canonicalLocation(@NonNull String saved) {
        return matchCanonical(saved, LOCATIONS);
    }

    @NonNull
    public static String canonicalOccupation(@NonNull String saved) {
        return matchCanonical(saved, OCCUPATIONS);
    }

    private static String matchCanonical(String saved, String[] pool) {
        if (saved == null) {
            return "";
        }
        String t = saved.trim();
        for (String p : pool) {
            if (p.equalsIgnoreCase(t)) {
                return p;
            }
        }
        return "";
    }

    public static boolean isValidLocation(@NonNull String value) {
        return !canonicalLocation(value).isEmpty();
    }

    public static boolean isValidOccupation(@NonNull String value) {
        return !canonicalOccupation(value).isEmpty();
    }
}
