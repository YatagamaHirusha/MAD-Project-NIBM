package com.mad.cw.supabase;

import com.mad.cw.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase GoTrue REST (signup / password login). Uses anon key only — no user JWT required.
 */
public final class SupabaseAuthApi {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                String anon = BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY.trim();
                Request req = chain.request().newBuilder()
                        .header("apikey", anon)
                        .header("Authorization", "Bearer " + anon)
                        .build();
                return chain.proceed(req);
            })
            .build();

    public AuthResponse signUp(String email, String password, String displayName) throws IOException {
        if (!SupabaseRestClient.isConfigured()) {
            return AuthResponse.fail("Supabase is not configured (check local.properties).");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            JSONObject data = new JSONObject();
            data.put("display_name", displayName);
            body.put("data", data);
        } catch (org.json.JSONException e) {
            return AuthResponse.fail(e.getMessage());
        }

        Request request = new Request.Builder()
                .url(SupabaseUrls.authBase() + "signup")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            return parseAuthResponse(response.code(), respBody);
        }
    }

    public AuthResponse signInWithPassword(String email, String password) throws IOException {
        if (!SupabaseRestClient.isConfigured()) {
            return AuthResponse.fail("Supabase is not configured (check local.properties).");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
        } catch (org.json.JSONException e) {
            return AuthResponse.fail(e.getMessage());
        }

        Request request = new Request.Builder()
                .url(SupabaseUrls.authBase() + "token?grant_type=password")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            return parseAuthResponse(response.code(), respBody);
        }
    }

    private static AuthResponse parseAuthResponse(int code, String respBody) {
        try {
            JSONObject json = new JSONObject(respBody);
            if (!responseOk(code)) {
                String msg = json.optString("error_description", json.optString("msg", json.optString("error", "Auth failed")));
                return AuthResponse.fail(msg);
            }
            String access = json.optString("access_token", "");
            if (access.isEmpty()) {
                return AuthResponse.needsEmailConfirmation();
            }
            String refresh = json.optString("refresh_token", "");
            long expiresIn = json.optLong("expires_in", 3600);
            JSONObject user = json.optJSONObject("user");
            String userId = user != null ? user.optString("id", "") : json.optString("user_id", "");
            return AuthResponse.ok(access, refresh, expiresIn, userId);
        } catch (org.json.JSONException e) {
            return AuthResponse.fail(respBody.isEmpty() ? "Invalid server response" : respBody);
        }
    }

    private static boolean responseOk(int code) {
        return code >= 200 && code < 300;
    }

    public static final class AuthResponse {
        public final boolean success;
        public final boolean needsEmailConfirmation;
        public final String errorMessage;
        public final String accessToken;
        public final String refreshToken;
        public final long expiresInSeconds;
        public final String userId;

        private AuthResponse(boolean success, boolean needsEmail, String err, String access, String refresh, long exp, String uid) {
            this.success = success;
            this.needsEmailConfirmation = needsEmail;
            this.errorMessage = err;
            this.accessToken = access;
            this.refreshToken = refresh;
            this.expiresInSeconds = exp;
            this.userId = uid;
        }

        static AuthResponse ok(String access, String refresh, long exp, String uid) {
            return new AuthResponse(true, false, null, access, refresh, exp, uid);
        }

        static AuthResponse fail(String msg) {
            return new AuthResponse(false, false, msg, null, null, 0, null);
        }

        static AuthResponse needsEmailConfirmation() {
            return new AuthResponse(false, true, null, null, null, 0, null);
        }
    }
}
