package com.mad.cw.supabase.repositories;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mad.cw.interests.InterestTaxonomy;
import com.mad.cw.interests.UserInterestStore;
import com.mad.cw.matching.UuidValidation;
import com.mad.cw.supabase.core.PostgrestError;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Upserts {@code public.user_interests} from local {@link UserInterestStore} (same semantics as training
 * CSV / JSON columns).
 */
public final class UserInterestsRepository {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private UserInterestsRepository() {}

    /** One row from {@code user_interests} for display (e.g. peer profile). */
    public static final class PeerInterestRow {
        @NonNull public final String location;
        @NonNull public final String occupation;
        @NonNull public final Map<String, List<String>> byCategory;

        public PeerInterestRow(
                @NonNull String location,
                @NonNull String occupation,
                @NonNull Map<String, List<String>> byCategory) {
            this.location = location;
            this.occupation = occupation;
            this.byCategory = byCategory;
        }

        public boolean hasAnyTags() {
            for (List<String> list : byCategory.values()) {
                if (list != null && !list.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Loads {@code user_interests} for another user (RLS: {@code interests_select_looking_peers}). Returns empty
     * maps when no row or on failure.
     */
    @NonNull
    public static PeerInterestRow fetchPeerRow(@NonNull String userId) throws IOException {
        if (!UuidValidation.isUuid(userId)) {
            return new PeerInterestRow("", "", Collections.emptyMap());
        }
        if (SessionStore.getAccessToken() == null || SessionStore.getAccessToken().isEmpty()) {
            throw new IOException("Not signed in");
        }
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("user_interests");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        String select =
                "location,occupation,lifestyle,arts_creativity,music,movies_shows,intellectual_learning,"
                        + "food_drinks,sports_outdoor,gaming_digital,travel_culture,personality_values,"
                        + "relationship_intent";
        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", select)
                .addQueryParameter("user_id", "eq." + userId)
                .addQueryParameter("limit", "1")
                .build();

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                throw new IOException(
                        response.code() + ": " + PostgrestError.userMessage(response.code(), resp));
            }
            JSONArray arr = new JSONArray(resp);
            if (arr.length() == 0) {
                return new PeerInterestRow("", "", Collections.emptyMap());
            }
            JSONObject row = arr.getJSONObject(0);
            String location = row.optString("location", "");
            String occupation = row.optString("occupation", "");
            Map<String, List<String>> map = new HashMap<>();
            for (InterestTaxonomy.Category cat : InterestTaxonomy.CATEGORIES) {
                String dbCol = taxonomyColumnToDbColumn(cat.columnName);
                if (dbCol.isEmpty()) {
                    continue;
                }
                map.put(cat.columnName, jsonbValueToList(row, dbCol));
            }
            return new PeerInterestRow(
                    location != null ? location : "",
                    occupation != null ? occupation : "",
                    map);
        } catch (JSONException e) {
            throw new IOException("Invalid interests response", e);
        }
    }

    /**
     * Maps {@link InterestTaxonomy.Category#columnName} to PostgREST / DB column names in {@code user_interests}.
     */
    static String taxonomyColumnToDbColumn(String taxonomyColumn) {
        if (taxonomyColumn == null) {
            return "";
        }
        switch (taxonomyColumn) {
            case "Lifestyle":
                return "lifestyle";
            case "Arts & Creativity":
                return "arts_creativity";
            case "Music":
                return "music";
            case "Movies & Shows":
                return "movies_shows";
            case "Intellectual & Learning":
                return "intellectual_learning";
            case "Food & Drinks":
                return "food_drinks";
            case "Sports & Outdoor":
                return "sports_outdoor";
            case "Gaming & Digital":
                return "gaming_digital";
            case "Travel & Culture":
                return "travel_culture";
            case "Personality & Values":
                return "personality_values";
            case "Relationship Intent":
                return "relationship_intent";
            default:
                return "";
        }
    }

    /**
     * Writes current local interests + location + occupation to Supabase (merge on {@code user_id}).
     */
    public static void upsertFromLocal(Context context) throws IOException {
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }

        Context app = context.getApplicationContext();
        String location = UserInterestStore.loadLocation(app);
        String occupation = UserInterestStore.loadOccupation(app);
        Map<String, List<String>> byColumn = UserInterestStore.loadInterestMap(app);

        JSONObject row = new JSONObject();
        try {
            row.put("user_id", userId);
            row.put("location", location != null ? location : "");
            row.put("occupation", occupation != null ? occupation : "");

            for (InterestTaxonomy.Category cat : InterestTaxonomy.CATEGORIES) {
                String dbCol = taxonomyColumnToDbColumn(cat.columnName);
                if (dbCol.isEmpty()) {
                    continue;
                }
                JSONArray arr = new JSONArray();
                List<String> tags = byColumn.get(cat.columnName);
                if (tags != null) {
                    for (String t : tags) {
                        if (t != null && !t.trim().isEmpty()) {
                            arr.put(t.trim());
                        }
                    }
                }
                row.put(dbCol, arr);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }

        HttpUrl url = SupabaseRestClient.getInstance().tableUrl("user_interests");
        if (url == null) {
            throw new IOException("Supabase URL not configured");
        }

        JSONArray bulk = new JSONArray();
        bulk.put(row);

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request =
                new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                        .post(RequestBody.create(bulk.toString(), JSON))
                        .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException(
                        response.code() + ": " + PostgrestError.userMessage(response.code(), err));
            }
        }
    }

    /**
     * Loads {@code user_interests} for the signed-in user into {@link UserInterestStore}. No-op if no row exists.
     */
    public static void fetchIntoLocal(Context context) throws IOException {
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("user_interests");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        String select =
                "location,occupation,lifestyle,arts_creativity,music,movies_shows,intellectual_learning,"
                        + "food_drinks,sports_outdoor,gaming_digital,travel_culture,personality_values,"
                        + "relationship_intent";
        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", select)
                .addQueryParameter("user_id", "eq." + userId)
                .addQueryParameter("limit", "1")
                .build();

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                throw new IOException(
                        response.code() + ": " + PostgrestError.userMessage(response.code(), resp));
            }
            JSONArray arr = new JSONArray(resp);
            if (arr.length() == 0) {
                return;
            }
            JSONObject row = arr.getJSONObject(0);
            String location = row.optString("location", "");
            String occupation = row.optString("occupation", "");
            Map<String, List<String>> map = new HashMap<>();
            for (InterestTaxonomy.Category cat : InterestTaxonomy.CATEGORIES) {
                String dbCol = taxonomyColumnToDbColumn(cat.columnName);
                if (dbCol.isEmpty()) {
                    continue;
                }
                map.put(cat.columnName, jsonbValueToList(row, dbCol));
            }
            Context app = context.getApplicationContext();
            UserInterestStore.save(app, location, occupation, map, true);
        } catch (JSONException e) {
            throw new IOException("Invalid interests response", e);
        }
    }

    /**
     * PostgREST usually returns jsonb arrays as JSONArray, but older/edge payloads may return
     * encoded strings. This parser accepts both to avoid silent drops on sync.
     */
    private static List<String> jsonbValueToList(JSONObject row, String key) {
        Object raw = row.opt(key);
        if (raw == null || raw == JSONObject.NULL) {
            return new ArrayList<>();
        }
        if (raw instanceof JSONArray) {
            return jsonArrayToList((JSONArray) raw);
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return new ArrayList<>();
            }
            try {
                return jsonArrayToList(new JSONArray(s));
            } catch (JSONException ignored) {
                // Legacy fallback: comma-separated text
                List<String> list = new ArrayList<>();
                for (String part : s.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) {
                        list.add(t);
                    }
                }
                return list;
            }
        }
        return new ArrayList<>();
    }

    private static List<String> jsonArrayToList(JSONArray ja) {
        try {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < ja.length(); i++) {
                list.add(ja.getString(i));
            }
            return list;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }
}
