package com.mad.cw.profile;

import android.content.Context;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import com.mad.cw.supabase.core.SessionStore;

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
        MatchRequestLocalStore.clear(app);
        MatchCacheStore.clear(app);
        AvatarStorage.clear(app);
    }
}
