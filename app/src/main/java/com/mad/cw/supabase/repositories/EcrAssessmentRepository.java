package com.mad.cw.supabase.repositories;

import android.content.Context;

import com.mad.cw.assessment.AssessmentPreferences;
import com.mad.cw.assessment.EcrRsScoring;
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
        //here i change the ercscoring as item count because i added new question for that
        if (rawAnswers == null || rawAnswers.length != EcrRsScoring.ITEM_COUNT) {
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

    /**
     * Loads the most recent assessment for the current user and mirrors it into {@link AssessmentPreferences}.
     * No-op if there is no row.
     */
    public static void fetchLatestIntoLocal(Context context) throws IOException {
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("user_ecr_assessments");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", "raw_answers,anxiety_score,avoidance_score")
                .addQueryParameter("user_id", "eq." + userId)
                .addQueryParameter("order", "completed_at.desc")
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
            JSONObject o = arr.getJSONObject(0);
            JSONArray raw = o.getJSONArray("raw_answers");
            if (raw.length() != EcrRsScoring.ITEM_COUNT) {
                return;
            }
            int[] scores = new int[EcrRsScoring.ITEM_COUNT];
            for (int i = 0; i < EcrRsScoring.ITEM_COUNT; i++) {
                scores[i] = raw.getInt(i);
            }
            double anxiety = o.has("anxiety_score") && !o.isNull("anxiety_score")
                    ? o.getDouble("anxiety_score")
                    : 4.0;
            double avoidance = o.has("avoidance_score") && !o.isNull("avoidance_score")
                    ? o.getDouble("avoidance_score")
                    : 4.0;
            Context app = context.getApplicationContext();
            AssessmentPreferences.saveEcrAnswers(app, scores, anxiety, avoidance, true);
        } catch (JSONException e) {
            throw new IOException("Invalid ECR response", e);
        }
    }
}
