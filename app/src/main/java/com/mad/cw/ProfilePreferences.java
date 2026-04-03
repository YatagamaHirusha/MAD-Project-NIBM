package com.mad.cw;

import android.content.Context;
import android.content.SharedPreferences;

import com.mad.cw.supabase.ProfileRecord;

/**
 * Local profile fields until backend persistence is wired. Keys align with registration / lifestyle flows.
 */
public final class ProfilePreferences {

    private static final String PREFS_NAME = "user_profile";

    public static final String KEY_DISPLAY_NAME = "display_name";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_DOB = "dob";
    public static final String KEY_BIO = "bio";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_OCCUPATION = "occupation";
    public static final String KEY_GENDER = "gender";
    public static final String KEY_TARGET_GENDER = "target_gender";
    /** HTTPS URL for profile photo when synced from the server (see {@code profiles.avatar_url}). */
    public static final String KEY_AVATAR_URL = "avatar_url";
    /** True when the user picked a photo stored locally in {@link AvatarStorage}. */
    public static final String KEY_HAS_LOCAL_AVATAR = "has_local_avatar";

    private ProfilePreferences() {}

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Persists a server profile row locally. Uses {@link SharedPreferences.Editor#commit()} so the next
     * screen (e.g. dashboard) never reads stale empties after an async fetch.
     */
    public static void copyFromRecord(Context context, ProfileRecord r) {
        if (r == null) {
            return;
        }
        get(context)
                .edit()
                .putString(KEY_DISPLAY_NAME, r.displayName)
                .putString(KEY_EMAIL, r.email)
                .putString(KEY_DOB, r.dateOfBirth)
                .putString(KEY_LOCATION, r.location)
                .putString(KEY_OCCUPATION, r.occupation)
                .putString(KEY_GENDER, r.gender)
                .putString(KEY_TARGET_GENDER, r.targetGender)
                .putString(KEY_BIO, r.bio)
                .putString(KEY_AVATAR_URL, r.avatarUrl)
                .commit();
    }
}
