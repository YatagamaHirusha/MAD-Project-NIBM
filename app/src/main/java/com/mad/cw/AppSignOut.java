package com.mad.cw;

import android.content.Context;

import com.mad.cw.supabase.SessionStore;

/**
 * Clears auth session and local user-specific preferences so another account can use the device cleanly.
 */
public final class AppSignOut {

    private AppSignOut() {}

    public static void run(Context context) {
        SessionStore.clear();
        Context app = context.getApplicationContext();
        ProfilePreferences.get(app).edit().clear().apply();
        UserInterestStore.prefs(app).edit().clear().apply();
        AssessmentPreferences.prefs(app).edit().clear().apply();
    }
}
