package com.mad.cw.supabase;

import android.content.Context;

import com.mad.cw.InterestTaxonomy;
import com.mad.cw.UserInterestStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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
}
