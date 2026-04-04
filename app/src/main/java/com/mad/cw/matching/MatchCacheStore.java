package com.mad.cw.matching;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local cache for the latest ML suggestions so the list survives navigation/restart.
 */
public final class MatchCacheStore {

    private static final String PREFS = "match_cache_store";
    private static final String KEY_SUGGESTIONS_JSON = "latest_suggestions_json";
    private static final String KEY_UPDATED_AT_MS = "latest_updated_at_ms";

    private MatchCacheStore() {}

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class CachedMatch {
        @NonNull public final String userId;
        public final int matchPercent;
        public final double score;
        @NonNull public final String displayName;
        @NonNull public final String location;
        @NonNull public final String occupation;
        @NonNull public final String photoUrl;

        public CachedMatch(
                @NonNull String userId,
                int matchPercent,
                double score,
                @NonNull String displayName,
                @NonNull String location,
                @NonNull String occupation,
                @NonNull String photoUrl) {
            this.userId = userId;
            this.matchPercent = matchPercent;
            this.score = score;
            this.displayName = displayName;
            this.location = location;
            this.occupation = occupation;
            this.photoUrl = photoUrl;
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("user_id", userId);
            o.put("match_percent", matchPercent);
            o.put("score", score);
            o.put("display_name", displayName);
            o.put("location", location);
            o.put("occupation", occupation);
            o.put("photo_url", photoUrl);
            return o;
        }

        @NonNull
        static CachedMatch fromJson(@NonNull JSONObject o) {
            return new CachedMatch(
                    o.optString("user_id", ""),
                    o.optInt("match_percent", 0),
                    o.optDouble("score", 0.0),
                    o.optString("display_name", ""),
                    o.optString("location", ""),
                    o.optString("occupation", ""),
                    o.optString("photo_url", ""));
        }
    }

    public static void save(@NonNull Context context, @NonNull List<CachedMatch> matches) {
        JSONArray arr = new JSONArray();
        int max = Math.min(5, matches.size());
        for (int i = 0; i < max; i++) {
            try {
                arr.put(matches.get(i).toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit()
                .putString(KEY_SUGGESTIONS_JSON, arr.toString())
                .putLong(KEY_UPDATED_AT_MS, System.currentTimeMillis())
                .apply();
    }

    @NonNull
    public static List<CachedMatch> load(@NonNull Context context) {
        String raw = prefs(context).getString(KEY_SUGGESTIONS_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = new JSONArray(raw);
            List<CachedMatch> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                CachedMatch c = CachedMatch.fromJson(o);
                if (UuidValidation.isUuid(c.userId)) {
                    out.add(c);
                }
            }
            return out;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    public static List<String> loadUserIds(@NonNull Context context) {
        List<CachedMatch> rows = load(context);
        List<String> out = new ArrayList<>(rows.size());
        for (CachedMatch r : rows) {
            out.add(r.userId);
        }
        return out;
    }

    public static boolean hasAny(@NonNull Context context) {
        return !load(context).isEmpty();
    }

    public static long updatedAtMs(@NonNull Context context) {
        return prefs(context).getLong(KEY_UPDATED_AT_MS, 0L);
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().remove(KEY_SUGGESTIONS_JSON).remove(KEY_UPDATED_AT_MS).apply();
    }

    /** Snapshot of {@link MatchSuggestion} rows for offline replay (same field order as ML path). */
    public static void saveFromMatchSuggestions(
            @NonNull Context context, @NonNull List<MatchSuggestion> suggestions) {
        List<CachedMatch> rows = new ArrayList<>();
        for (MatchSuggestion s : suggestions) {
            if (!UuidValidation.isUuid(s.peerId)) {
                continue;
            }
            rows.add(
                    new CachedMatch(
                            s.peerId,
                            s.matchPercent,
                            s.score,
                            s.displayName,
                            s.location,
                            s.occupation,
                            s.photoUrl != null ? s.photoUrl : ""));
        }
        save(context, rows);
    }
}
