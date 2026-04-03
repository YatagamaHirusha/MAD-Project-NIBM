package com.mad.cw;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mad.cw.supabase.ProfileRemoteRepository;
import com.mad.cw.supabase.SessionStore;
import com.mad.cw.supabase.SupabaseRestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches {@code profiles} from Supabase and mirrors into {@link ProfilePreferences} so dashboard and
 * other screens see the same data as the database after login (local prefs are otherwise empty).
 */
public final class ProfileSync {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "profile-sync");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private ProfileSync() {}

    /**
     * Loads the signed-in user's profile and writes {@link ProfilePreferences}. Runs {@code onDone} on the
     * main thread after the write (or immediately if skipped / error).
     */
    public static void refreshLocalFromServerAsync(Context context, Runnable onDone) {
        Context app = context.getApplicationContext();
        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            runOnMain(onDone);
            return;
        }
        IO.execute(
                () -> {
                    try {
                        ProfileRemoteRepository.FetchedProfile fetched =
                                ProfileRemoteRepository.fetchMyProfile();
                        ProfilePreferences.copyFromRecord(app, fetched.record);
                    } catch (Exception ignored) {
                        // Keep existing prefs; ProfileFragment may show load error separately.
                    }
                    runOnMain(onDone);
                });
    }

    private static void runOnMain(Runnable r) {
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
