package com.mad.cw.welcome;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** Whether the animated intro splash has already been shown (once per install / clear-data). */
public final class SplashPreferences {

    private static final String PREFS = "splash_prefs";
    private static final String KEY_INTRO_ANIMATION_DONE = "intro_animation_done";

    private SplashPreferences() {}

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True after the user has completed the first-launch intro splash at least once. */
    public static boolean isIntroAnimationDone(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_INTRO_ANIMATION_DONE, false);
    }

    public static void markIntroAnimationDone(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_INTRO_ANIMATION_DONE, true).apply();
    }
}
