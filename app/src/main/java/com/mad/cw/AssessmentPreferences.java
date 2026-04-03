package com.mad.cw;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists ECR-RS questionnaire completion, raw answers (1–7 per item), and last computed subscale means.
 */
public final class AssessmentPreferences {

    private static final String PREFS_NAME = "assessment";

    public static final String KEY_ECR_COMPLETE = "ecr_complete";
    public static final String KEY_ECR_ANSWERS_CSV = "ecr_answers_csv";
    public static final String KEY_ECR_ANXIETY = "ecr_anxiety_score";
    public static final String KEY_ECR_AVOIDANCE = "ecr_avoidance_score";

    private AssessmentPreferences() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Stores nine Likert values (questions 1–9) and computed anxiety / avoidance means. */
    public static void saveEcrAnswers(Context context, int[] scores, double anxietyScore, double avoidanceScore) {
        if (scores == null || scores.length != 9) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(scores[i]);
        }
        prefs(context).edit()
                .putBoolean(KEY_ECR_COMPLETE, true)
                .putString(KEY_ECR_ANSWERS_CSV, sb.toString())
                .putFloat(KEY_ECR_ANXIETY, (float) anxietyScore)
                .putFloat(KEY_ECR_AVOIDANCE, (float) avoidanceScore)
                .apply();
    }

    public static boolean isEcrComplete(Context context) {
        return prefs(context).getBoolean(KEY_ECR_COMPLETE, false);
    }

    /** @return last saved mean anxiety, or {@link Double#NaN} if missing */
    public static double getLastAnxietyScore(Context context) {
        if (!prefs(context).contains(KEY_ECR_ANXIETY)) {
            return Double.NaN;
        }
        return prefs(context).getFloat(KEY_ECR_ANXIETY, Float.NaN);
    }

    /** @return last saved mean avoidance, or {@link Double#NaN} if missing */
    public static double getLastAvoidanceScore(Context context) {
        if (!prefs(context).contains(KEY_ECR_AVOIDANCE)) {
            return Double.NaN;
        }
        return prefs(context).getFloat(KEY_ECR_AVOIDANCE, Float.NaN);
    }
}
