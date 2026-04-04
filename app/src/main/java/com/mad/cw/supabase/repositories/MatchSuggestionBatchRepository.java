package com.mad.cw.supabase.repositories;

import androidx.annotation.NonNull;

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
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Persists and restores latest ML suggestion IDs via public.match_suggestion_batches. */
public final class MatchSuggestionBatchRepository {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private MatchSuggestionBatchRepository() {}

    @NonNull
    public static List<String> fetchLatestSuggestionUserIds() throws IOException {
        String seekerId = requireUserId();
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("match_suggestion_batches");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", "candidate_user_ids")
                .addQueryParameter("seeker_id", "eq." + seekerId)
                .addQueryParameter("order", "created_at.desc")
                .addQueryParameter("limit", "1")
                .build();

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        String token = requireAccessToken();
        Request request =
                new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + token)
                        .get()
                        .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), body));
            }
            try {
                JSONArray rows = new JSONArray(body);
                if (rows.length() == 0) {
                    return Collections.emptyList();
                }
                JSONArray arr = rows.getJSONObject(0).optJSONArray("candidate_user_ids");
                if (arr == null) {
                    return Collections.emptyList();
                }
                List<String> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, "");
                    if (UuidValidation.isUuid(id)) {
                        out.add(id);
                    }
                }
                return out;
            } catch (JSONException e) {
                throw new IOException("Invalid suggestion batch response", e);
            }
        }
    }

    public static void insertSuggestionBatch(@NonNull List<String> candidateUserIds) throws IOException {
        String seekerId = requireUserId();
        String token = requireAccessToken();
        if (candidateUserIds.isEmpty()) {
            return;
        }
        JSONArray ids = new JSONArray();
        for (String id : candidateUserIds) {
            if (UuidValidation.isUuid(id)) {
                ids.put(id);
            }
        }
        if (ids.length() == 0) {
            return;
        }

        JSONObject row = new JSONObject();
        try {
            row.put("seeker_id", seekerId);
            row.put("candidate_user_ids", ids);
            row.put("model_version", "fastapi-v1");
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        JSONArray body = new JSONArray().put(row);

        HttpUrl url = SupabaseRestClient.getInstance().tableUrl("match_suggestion_batches");
        if (url == null) {
            throw new IOException("Supabase URL not configured");
        }
        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
            }
        }
    }

    private static String requireAccessToken() throws IOException {
        String token = SessionStore.getAccessToken();
        if (token == null || token.isEmpty()) {
            throw new IOException("Not signed in");
        }
        return token;
    }

    private static String requireUserId() throws IOException {
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        return userId;
    }
}
