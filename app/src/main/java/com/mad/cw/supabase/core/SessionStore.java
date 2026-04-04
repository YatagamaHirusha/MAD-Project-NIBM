package com.mad.cw.supabase.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists Supabase Auth session. Call {@link #init(Context)} from {@link com.mad.cw.welcome.Splash} early.
 */
public final class SessionStore {

    private static final String PREFS = "supabase_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES_AT_MS = "expires_at_ms";
    private static final String KEY_USER_ID = "user_id";

    private static volatile Context appContext;

    private SessionStore() {}

    public static void init(Context context) {
        if (appContext == null) {
            synchronized (SessionStore.class) {
                if (appContext == null) {
                    appContext = context.getApplicationContext();
                }
            }
        }
    }

    private static SharedPreferences prefs() {
        if (appContext == null) {
            return null;
        }
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveSession(String accessToken, String refreshToken, long expiresInSeconds, String userId) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L) - 30_000L;
        p.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken != null ? refreshToken : "")
                .putLong(KEY_EXPIRES_AT_MS, expiresAt)
                .putString(KEY_USER_ID, userId != null ? userId : "")
                .commit();
    }

    public static void clear() {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().clear().apply();
        }
    }

    /** Non-empty access token that is not past expiry; otherwise null. */
    public static String getAccessToken() {
        SharedPreferences p = prefs();
        if (p == null) {
            return null;
        }
        long exp = p.getLong(KEY_EXPIRES_AT_MS, 0);
        if (exp > 0 && System.currentTimeMillis() >= exp) {
            return null;
        }
        String t = p.getString(KEY_ACCESS, "");
        if (t == null || t.isEmpty()) {
            return null;
        }
        return t;
    }

    public static String getUserId() {
        SharedPreferences p = prefs();
        if (p == null) {
            return "";
        }
        String id = p.getString(KEY_USER_ID, "");
        if (id != null && !id.isEmpty()) {
            return id;
        }
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            return "";
        }
        String sub = JwtPayload.optSub(token);
        if (sub != null && !sub.isEmpty()) {
            p.edit().putString(KEY_USER_ID, sub).apply();
            return sub;
        }
        return "";
    }

    public static boolean isLoggedIn() {
        return getAccessToken() != null;
    }
}
