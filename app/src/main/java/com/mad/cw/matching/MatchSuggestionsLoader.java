package com.mad.cw.matching;

import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.repositories.ProfileRecord;
import com.mad.cw.supabase.repositories.ProfileRemoteRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * Loads ranked {@link MatchSuggestion}s from the ML HTTP API and enriches rows from Supabase {@code profiles}.
 */
public final class MatchSuggestionsLoader {

    private MatchSuggestionsLoader() {}

    @NonNull
    public static List<MatchSuggestion> loadTopFromMlServer(int limit)
            throws IOException, JSONException {
        String uid = SessionStore.getUserId();
        if (!UuidValidation.isUuid(uid)) {
            throw new IOException("Not signed in");
        }
        JSONArray matches = MatchMindApiClient.fetchMatches(uid);
        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < matches.length() && ordered.size() < limit; i++) {
            JSONObject m = matches.getJSONObject(i);
            if (UuidValidation.isUuid(m.optString("user_id", ""))) {
                ordered.add(m);
            }
        }
        if (ordered.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> idSet = new LinkedHashSet<>();
        for (JSONObject m : ordered) {
            idSet.add(m.getString("user_id"));
        }
        Map<String, ProfileRecord> profiles = ProfileRemoteRepository.fetchProfilesByIds(idSet);

        List<MatchSuggestion> out = new ArrayList<>();
        int rank = 1;
        for (JSONObject m : ordered) {
            String peerId = m.getString("user_id");
            ProfileRecord pr = profiles.get(peerId);
            String name =
                    pr != null && pr.displayName != null && !pr.displayName.trim().isEmpty()
                            ? pr.displayName.trim()
                            : "Member";
            String loc = pr != null ? pr.location : "";
            String occ = pr != null ? pr.occupation : "";
            String photo =
                    pr != null && pr.avatarUrl != null && !pr.avatarUrl.trim().isEmpty()
                            ? pr.avatarUrl.trim()
                            : MatchScoring.demoPhotoUrlForPeerId(peerId);
            int pct = m.optInt("match_percent", 0);
            double score = m.optDouble("hybrid_score", 0.0);
            out.add(
                    new MatchSuggestion(
                            rank++,
                            peerId,
                            name,
                            loc != null ? loc : "",
                            occ != null ? occ : "",
                            Double.NaN,
                            Double.NaN,
                            pct,
                            score,
                            photo));
        }
        return out;
    }
}
