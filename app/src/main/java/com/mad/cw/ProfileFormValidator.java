package com.mad.cw;

import com.mad.cw.auth.AuthValidation;

import java.util.Calendar;

/**
 * Validates profile form fields for “basic profile complete” before saving to Supabase.
 */
public final class ProfileFormValidator {

    public static final int MIN_LOCATION_LENGTH = 2;
    public static final int MIN_OCCUPATION_LENGTH = 2;
    public static final int MIN_BIO_LENGTH = 10;

    private ProfileFormValidator() {}

    /** @return 0 if valid; otherwise a {@code R.string.*} resource id */
    public static int validateBasicFields(
            String name,
            String email,
            String dobStr,
            String location,
            String occupation,
            String bio,
            String genderValue,
            String targetGenderValue) {
        if (!AuthValidation.isValidSignupName(name)) {
            return R.string.auth_invalid_name;
        }
        if (!AuthValidation.isValidEmail(email)) {
            return R.string.auth_invalid_email;
        }
        if (dobStr == null || dobStr.trim().isEmpty()) {
            return R.string.auth_dob_required;
        }
        Calendar dob = AuthValidation.parseDob(dobStr.trim());
        if (dob == null) {
            return R.string.auth_dob_invalid;
        }
        if (!AuthValidation.isAtLeastYearsOld(dob, AuthValidation.MIN_AGE_YEARS, Calendar.getInstance())) {
            return R.string.auth_adults_only;
        }
        if (!GenderOptions.isValidGenderValue(genderValue)) {
            return R.string.auth_gender_required;
        }
        if (!GenderOptions.isValidTargetGenderValue(targetGenderValue)) {
            return R.string.profile_target_gender_required;
        }
        if (location == null || location.trim().length() < MIN_LOCATION_LENGTH) {
            return R.string.profile_location_required;
        }
        if (occupation == null || occupation.trim().length() < MIN_OCCUPATION_LENGTH) {
            return R.string.profile_occupation_required;
        }
        if (bio == null || bio.trim().length() < MIN_BIO_LENGTH) {
            return R.string.profile_bio_required;
        }
        return 0;
    }

    public static boolean isBasicProfileComplete(
            String name,
            String email,
            String dobStr,
            String location,
            String occupation,
            String bio,
            String genderValue,
            String targetGenderValue) {
        return validateBasicFields(
                        name, email, dobStr, location, occupation, bio, genderValue, targetGenderValue)
                == 0;
    }
}
