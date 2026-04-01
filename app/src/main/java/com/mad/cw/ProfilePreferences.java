package com.mad.cw;

import android.content.Context;
import android.content.SharedPreferences;

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

    private ProfilePreferences() {}

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
