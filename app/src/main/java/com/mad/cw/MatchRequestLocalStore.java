package com.mad.cw.matching;

import android.content.Context;
import android.content.SharedPreferences;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.annotation.Nullable;

/**
 * Tracks a single outbound match request (demo IDs or synced state) so the UI can enforce one pending
 * request at a time, aligned with {@code match_requests_one_pending_outbound} on the server.
 */
public final class MatchRequestLocalStore {

    private static final String PREFS = "match_request_local";
    private static final String KEY_PENDING_PEER_ID = "pending_peer_id";
    private static final String KEY_PENDING_ML_RANK = "pending_ml_rank";

    private MatchRequestLocalStore() {}

    public static void clear(Context context) {
        get(context).edit().clear().apply();
    }

    private static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasPending(Context context) {
        String id = getPendingPeerId(context);
        return id != null && !id.isEmpty();
    }

    @Nullable
    public static String getPendingPeerId(Context context) {
        String s = get(context).getString(KEY_PENDING_PEER_ID, "");
        return s != null && !s.isEmpty() ? s : null;
    }

    public static int getPendingMlRank(Context context) {
        return get(context).getInt(KEY_PENDING_ML_RANK, 0);
    }

    public static void setPending(Context context, String peerId, int mlRank) {
        get(context).edit().putString(KEY_PENDING_PEER_ID, peerId).putInt(KEY_PENDING_ML_RANK, mlRank).apply();
    }
}
