package com.mad.cw.profile;

import com.mad.cw.R;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Stored values match {@code profiles.gender} / {@code profiles.target_gender} (text).
 */
public final class GenderOptions {

    private GenderOptions() {}

    public static boolean isValidGenderValue(String v) {
        if (v == null || v.isEmpty()) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.US);
        switch (s) {
            case "female":
            case "male":
            case "non_binary":
            case "other":
                return true;
            default:
                return false;
        }
    }

    public static boolean isValidTargetGenderValue(String v) {
        if (v == null || v.isEmpty()) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.US);
        switch (s) {
            case "female":
            case "male":
            case "non_binary":
            case "any":
            case "other":
                return true;
            default:
                return false;
        }
    }

    public static void setupGenderDropdown(@NonNull Context ctx, @NonNull AutoCompleteTextView field) {
        String[] labels = ctx.getResources().getStringArray(R.array.gender_labels);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(ctx, android.R.layout.simple_dropdown_item_1line, labels);
        field.setAdapter(adapter);
        field.setThreshold(1);
    }

    public static void setupTargetGenderDropdown(@NonNull Context ctx, @NonNull AutoCompleteTextView field) {
        String[] labels = ctx.getResources().getStringArray(R.array.target_gender_labels);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(ctx, android.R.layout.simple_dropdown_item_1line, labels);
        field.setAdapter(adapter);
        field.setThreshold(1);
    }

    /** Maps visible label or stored value to canonical {@code gender_values} entry. */
    @NonNull
    public static String genderValueFromInput(@NonNull Context ctx, @NonNull String shownText) {
        return valueFromArrays(ctx, R.array.gender_labels, R.array.gender_values, shownText);
    }

    @NonNull
    public static String targetGenderValueFromInput(@NonNull Context ctx, @NonNull String shownText) {
        return valueFromArrays(ctx, R.array.target_gender_labels, R.array.target_gender_values, shownText);
    }

    @NonNull
    public static String labelForGenderValue(@NonNull Context ctx, @NonNull String value) {
        return labelForValue(ctx, R.array.gender_labels, R.array.gender_values, value);
    }

    @NonNull
    public static String labelForTargetGenderValue(@NonNull Context ctx, @NonNull String value) {
        return labelForValue(ctx, R.array.target_gender_labels, R.array.target_gender_values, value);
    }

    @NonNull
    private static String valueFromArrays(
            @NonNull Context ctx, int labelsRes, int valuesRes, @NonNull String input) {
        String[] labels = ctx.getResources().getStringArray(labelsRes);
        String[] values = ctx.getResources().getStringArray(valuesRes);
        String t = input.trim();
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equalsIgnoreCase(t)) {
                return values[i];
            }
        }
        for (String v : values) {
            if (v.equalsIgnoreCase(t)) {
                return v;
            }
        }
        return "";
    }

    @NonNull
    private static String labelForValue(
            @NonNull Context ctx, int labelsRes, int valuesRes, @NonNull String value) {
        String[] labels = ctx.getResources().getStringArray(labelsRes);
        String[] values = ctx.getResources().getStringArray(valuesRes);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(value.trim())) {
                return labels[i];
            }
        }
        return value.trim();
    }
}
