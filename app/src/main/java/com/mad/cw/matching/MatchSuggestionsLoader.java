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

    @NonNull
    public static List<MatchSuggestion> fromCachedMatches(@NonNull List<MatchCacheStore.CachedMatch> cached) {
        List<MatchSuggestion> out = new ArrayList<>();
        int rank = 1;
        for (MatchCacheStore.CachedMatch c : cached) {
            if (!UuidValidation.isUuid(c.userId)) {
                continue;
            }
            out.add(
                    new MatchSuggestion(
                            rank++,
                            c.userId,
                            c.displayName,
                            c.location != null ? c.location : "",
                            c.occupation != null ? c.occupation : "",
                            Double.NaN,
                            Double.NaN,
                            c.matchPercent,
                            c.score,
                            c.photoUrl != null ? c.photoUrl : ""));
        }
        return out;
    }

    /**
     * Rebuilds list rows from stored candidate order (e.g. Supabase batch). Percentages are placeholders when ML
     * scores are unknown.
     */
    @NonNull
    public static List<MatchSuggestion> fromOrderedPeerIds(@NonNull List<String> orderedPeerIds)
            throws IOException {
        if (orderedPeerIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> idSet = new LinkedHashSet<>();
        for (String id : orderedPeerIds) {
            if (UuidValidation.isUuid(id)) {
                idSet.add(id);
            }
        }
        Map<String, ProfileRecord> profiles = ProfileRemoteRepository.fetchProfilesByIds(idSet);
        List<MatchSuggestion> out = new ArrayList<>();
        int rank = 1;
        for (String peerId : orderedPeerIds) {
            if (!UuidValidation.isUuid(peerId)) {
                continue;
            }
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
            int pct = Math.max(55, 92 - (rank - 1) * 7);
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
                            0.0,
                            photo));
        }
        return out;
    }

    @NonNull
    public static List<String> peerIdsFromSuggestions(@NonNull List<MatchSuggestion> suggestions) {
        List<String> ids = new ArrayList<>();
        for (MatchSuggestion s : suggestions) {
            if (UuidValidation.isUuid(s.peerId)) {
                ids.add(s.peerId);
            }
        }
        return ids;
    }
}
