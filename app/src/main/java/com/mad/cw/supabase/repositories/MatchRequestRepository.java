package com.mad.cw.supabase.repositories;

import com.mad.cw.matching.UuidValidation;
import com.mad.cw.supabase.core.PostgrestError;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import androidx.annotation.Nullable;

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

    /**
     * Thrown when the DB already has a <strong>pending</strong> outbound request to a <strong>different</strong>
     * user ({@code match_requests_one_pending_outbound}).
     */
    public static final class OtherPendingRequestException extends IOException {
        public OtherPendingRequestException() {
            super("other_pending");
        }
    }

    private MatchRequestRepository() {}

    /** Current user's single pending outbound row, if any (RLS: sender can read own requests). */
    @Nullable
    public static PendingOutbound fetchPendingOutbound() throws IOException {
        String fromUserId = SessionStore.getUserId();
        if (fromUserId == null || fromUserId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        if (SessionStore.getAccessToken() == null || SessionStore.getAccessToken().isEmpty()) {
            throw new IOException("Not signed in");
        }
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("match_requests");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", "id,to_user_id,ml_rank")
                .addQueryParameter("from_user_id", "eq." + fromUserId)
                .addQueryParameter("status", "eq.pending")
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
                return null;
            }
            JSONObject o = arr.getJSONObject(0);
            String id = o.optString("id", "");
            String to = o.optString("to_user_id", "");
            int rank = o.optInt("ml_rank", 0);
            if (!UuidValidation.isUuid(to)) {
                return null;
            }
            return new PendingOutbound(id, to, rank);
        } catch (JSONException e) {
            throw new IOException("Invalid match_requests response", e);
        }
    }

    public static final class PendingOutbound {
        @Nullable public final String id;
        @Nullable public final String toUserId;
        public final int mlRank;

        public PendingOutbound(@Nullable String id, @Nullable String toUserId, int mlRank) {
            this.id = id;
            this.toUserId = toUserId;
            this.mlRank = mlRank;
        }
    }

    private static boolean samePeer(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Creates a pending row. {@code toUserId} must be a real {@code auth.users} id (UUID).
     *
     * <p>Idempotent: if a pending row to the same {@code toUserId} already exists (e.g. double tap, or local prefs
     * out of sync), returns without error.
     *
     * @throws OtherPendingRequestException if there is already a pending request to someone else
     * @throws IOException on other network / PostgREST failures
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

        PendingOutbound existing = fetchPendingOutbound();
        if (existing != null && existing.toUserId != null) {
            if (samePeer(existing.toUserId, toUserId)) {
                return;
            }
            throw new OtherPendingRequestException();
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
            if (response.code() == 409) {
                PendingOutbound after = fetchPendingOutbound();
                if (after != null && after.toUserId != null && samePeer(after.toUserId, toUserId)) {
                    return;
                }
                throw new OtherPendingRequestException();
            }
            throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
        }
    }
}
