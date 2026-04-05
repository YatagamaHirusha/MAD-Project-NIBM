package com.mad.cw.profile;

import android.content.Context;

import com.mad.cw.supabase.core.SessionStore;

/**
 * Clears auth session and local user-specific preferences so another account can use the device cleanly.
 */
public final class AppSignOut {

    private AppSignOut() {}

    public static void run(Context context) {
        SessionStore.clear();
        AccountScopedLocalStore.clearForNewAccount(context);
    }
}
