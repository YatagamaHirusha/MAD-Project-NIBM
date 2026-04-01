package com.mad.cw;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists ECR-RS questionnaire completion and raw answers (1–7 per item) for scoring / backend later.
 */
public final class AssessmentPreferences {

    private static final String PREFS_NAME = "assessment";

    public static final String KEY_ECR_COMPLETE = "ecr_complete";
    public static final String KEY_ECR_ANSWERS_CSV = "ecr_answers_csv";

    private AssessmentPreferences() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Stores nine Likert values in order (questions 1–9). */
    public static void saveEcrAnswers(Context context, int[] scores) {
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
                .apply();
    }

    public static boolean isEcrComplete(Context context) {
        return prefs(context).getBoolean(KEY_ECR_COMPLETE, false);
    }
}
