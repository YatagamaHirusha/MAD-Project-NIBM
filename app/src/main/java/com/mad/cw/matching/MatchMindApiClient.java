package com.mad.cw.matching;

import com.mad.cw.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Calls the MatchMind FastAPI service (e.g. on Render): {@code GET /get_matches/{user_id}}.
 */
public final class MatchMindApiClient {

    private static volatile OkHttpClient client;

    private MatchMindApiClient() {}

    public static boolean isConfigured() {
        String b = BuildConfig.ML_API_BASE_URL;
        return b != null && !b.trim().isEmpty();
    }

    private static OkHttpClient httpClient() {
        if (client == null) {
            synchronized (MatchMindApiClient.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(180, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    /**
     * @return {@code matches} array from JSON body (each object has {@code user_id}, {@code match_percent},
     *     {@code hybrid_score}, etc.)
     */
    public static JSONArray fetchMatches(String seekerUserId) throws IOException, JSONException {
        String raw = BuildConfig.ML_API_BASE_URL == null ? "" : BuildConfig.ML_API_BASE_URL.trim();
        if (raw.isEmpty()) {
            throw new IOException("ML API base URL not configured");
        }
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        HttpUrl base = HttpUrl.parse(raw);
        if (base == null) {
            throw new IOException("Invalid ML_API_BASE_URL");
        }
        HttpUrl url = base.newBuilder()
                .addPathSegment("get_matches")
                .addPathSegment(seekerUserId)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient().newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("ML API " + response.code() + ": " + body);
            }
            JSONObject root = new JSONObject(body);
            return root.getJSONArray("matches");
        }
    }
}
