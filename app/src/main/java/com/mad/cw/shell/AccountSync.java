package com.mad.cw.shell;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.repositories.EcrAssessmentRepository;
import com.mad.cw.supabase.repositories.ProfileRecord;
import com.mad.cw.supabase.repositories.ProfileRemoteRepository;
import com.mad.cw.supabase.repositories.UserInterestsRepository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mirrors the signed-in user's Supabase data into local stores so screens work offline and after clearing
 * app data (profile, latest ECR assessment, and lifestyle interests).
 */
public final class AccountSync {

    private static final String TAG = "AccountSync";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "account-sync");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final MutableLiveData<SyncState> SYNC_STATE =
            new MutableLiveData<>(new SyncState(0L, false, false, false));

    private AccountSync() {}

    /**
     * Fetches profile, latest ECR row, and {@code user_interests} for the current session. Safe to call from a
     * background thread; each step is isolated so one failure does not block the others.
     */
    public static void syncFromServer(Context context) {
        Context app = context.getApplicationContext();
        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            return;
        }
        boolean profileOk = false;
        boolean ecrOk = false;
        boolean interestsOk = false;
        try {
            ProfileRemoteRepository.FetchedProfile fetched = ProfileRemoteRepository.fetchMyProfile();
            if (fetched.rowExists || hasAnyProfileCoreData(fetched.record)) {
                ProfilePreferences.copyFromRecord(app, fetched.record);
                profileOk = true;
            } else {
                Log.w(TAG, "Profile row missing; keeping existing local profile cache");
            }
        } catch (Exception e) {
            Log.w(TAG, "Profile sync failed", e);
        }
        try {
            EcrAssessmentRepository.fetchLatestIntoLocal(app);
            ecrOk = true;
        } catch (Exception e) {
            Log.w(TAG, "ECR sync failed", e);
        }
        try {
            UserInterestsRepository.fetchIntoLocal(app);
            mergeProfileFallbackFromInterests(app);
            interestsOk = true;
        } catch (Exception e) {
            Log.w(TAG, "Interests sync failed", e);
        }
        SYNC_STATE.postValue(new SyncState(System.currentTimeMillis(), profileOk, ecrOk, interestsOk));
    }

    private static boolean hasAnyProfileCoreData(ProfileRecord r) {
        if (r == null) {
            return false;
        }
        return !isBlank(r.displayName)
                || !isBlank(r.email)
                || !isBlank(r.dateOfBirth)
                || !isBlank(r.bio)
                || !isBlank(r.gender)
                || !isBlank(r.targetGender)
                || !isBlank(r.location)
                || !isBlank(r.occupation);
    }

    /**
     * Older data may have location/occupation only in {@code user_interests}. Merge those into profile
     * prefs when profile cache is missing those fields.
     */
    private static void mergeProfileFallbackFromInterests(Context app) {
        String loc = UserInterestStore.loadLocation(app);
        String occ = UserInterestStore.loadOccupation(app);
        if (isBlank(loc) && isBlank(occ)) {
            return;
        }
        SharedPreferences p = ProfilePreferences.get(app);
        String curLoc = p.getString(ProfilePreferences.KEY_LOCATION, "");
        String curOcc = p.getString(ProfilePreferences.KEY_OCCUPATION, "");
        SharedPreferences.Editor e = p.edit();
        boolean changed = false;
        if (isBlank(curLoc) && !isBlank(loc)) {
            e.putString(ProfilePreferences.KEY_LOCATION, loc.trim());
            changed = true;
        }
        if (isBlank(curOcc) && !isBlank(occ)) {
            e.putString(ProfilePreferences.KEY_OCCUPATION, occ.trim());
            changed = true;
        }
        if (changed) {
            e.commit();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Runs {@link #syncFromServer(Context)} on a worker thread, then {@code onDone} on the main thread (or
     * immediately if not signed in / not configured).
     */
    public static void refreshLocalFromServerAsync(Context context, Runnable onDone) {
        Context app = context.getApplicationContext();
        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            SYNC_STATE.postValue(new SyncState(System.currentTimeMillis(), false, false, false));
            runOnMain(onDone);
            return;
        }
        IO.execute(
                () -> {
                    syncFromServer(app);
                    runOnMain(onDone);
                });
    }

    /** Fragments can observe this to refresh exactly when sync writes complete. */
    public static LiveData<SyncState> syncState() {
        return SYNC_STATE;
    }

    public static final class SyncState {
        public final long completedAtMs;
        public final boolean profileSynced;
        public final boolean ecrSynced;
        public final boolean interestsSynced;

        public SyncState(long completedAtMs, boolean profileSynced, boolean ecrSynced, boolean interestsSynced) {
            this.completedAtMs = completedAtMs;
            this.profileSynced = profileSynced;
            this.ecrSynced = ecrSynced;
            this.interestsSynced = interestsSynced;
        }
    }

    static void runOnMain(Runnable r) {
        if (r == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }
}
