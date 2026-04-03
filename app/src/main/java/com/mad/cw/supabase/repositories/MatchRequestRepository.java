package com.mad.cw.supabase.repositories;

import com.mad.cw.supabase.core.PostgrestError;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Inserts into {@code public.match_requests} via PostgREST (sender must be the signed-in user).
 */
public final class MatchRequestRepository {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private MatchRequestRepository() {}

    /**
     * Creates a pending row. {@code toUserId} must be a real {@code auth.users} id (UUID).
     *
     * @throws IOException on network errors or PostgREST failures (e.g. duplicate pending outbound).
     */
    public static void insertPending(String toUserId, int mlRank) throws IOException {
        String fromUserId = SessionStore.getUserId();
        if (fromUserId == null || fromUserId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        if (toUserId == null || toUserId.isEmpty()) {
            throw new IOException("Missing recipient");
        }
        String token = SessionStore.getAccessToken();
        if (token == null || token.isEmpty()) {
            throw new IOException("Not signed in");
        }
        HttpUrl url = SupabaseRestClient.getInstance().tableUrl("match_requests");
        if (url == null) {
            throw new IOException("Supabase URL not configured");
        }
        JSONObject row = new JSONObject();
        try {
            row.put("from_user_id", fromUserId);
            row.put("to_user_id", toUserId);
            row.put("status", "pending");
            row.put("ml_rank", mlRank);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        JSONArray body = new JSONArray();
        body.put(row);

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return;
            }
            String err = response.body() != null ? response.body().string() : "";
            throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
        }
    }
}
