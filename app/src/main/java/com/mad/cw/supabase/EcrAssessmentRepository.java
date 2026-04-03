package com.mad.cw.supabase;

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
 * Inserts rows into {@code public.user_ecr_assessments}. Each submit creates a new row so users can
 * retake the questionnaire; the latest attempt is the row with the greatest {@code id} /
 * {@code completed_at}.
 */
public final class EcrAssessmentRepository {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private EcrAssessmentRepository() {}

    public static void insertAssessment(int[] rawAnswers, double anxietyScore, double avoidanceScore)
            throws IOException {
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        if (rawAnswers == null || rawAnswers.length != 9) {
            throw new IOException("Invalid answers length");
        }

        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("user_ecr_assessments");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }

        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            JSONArray arr = new JSONArray();
            for (int v : rawAnswers) {
                arr.put(v);
            }
            body.put("raw_answers", arr);
            body.put("anxiety_score", anxietyScore);
            body.put("avoidance_score", avoidanceScore);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request = new Request.Builder()
                .url(base)
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
}
