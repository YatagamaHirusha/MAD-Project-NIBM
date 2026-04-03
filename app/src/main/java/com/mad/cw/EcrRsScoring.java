package com.mad.cw;

import androidx.annotation.NonNull;

/**
 * Computes ECR-RS–style subscale means from the app’s nine 1–7 Likert items (questions 1–9 in order).
 * <p>
 * Mapping (by item content): <strong>Anxiety</strong> — worry about partner’s caring / abandonment (Q7–Q9).
 * <strong>Avoidance</strong> — discomfort with closeness / dependence (Q5–Q6 direct), comfort with relying
 * and sharing (Q1–Q4 reverse-scored: 8 − score). Each subscale is the mean of its items (standard ECR
 * practice). This matches the wording in {@code activity_questionnaire.xml}, not necessarily every
 * published short-form item order.
 */
public final class EcrRsScoring {

    public static final int ITEM_COUNT = 9;
    public static final int LIKERT_MIN = 1;
    public static final int LIKERT_MAX = 7;

    private EcrRsScoring() {}

    public static final class Result {
        public final double anxiety;
        public final double avoidance;

        public Result(double anxiety, double avoidance) {
            this.anxiety = anxiety;
            this.avoidance = avoidance;
        }
    }

    @NonNull
    public static Result compute(@NonNull int[] answers) {
        if (answers.length != ITEM_COUNT) {
            throw new IllegalArgumentException("Expected " + ITEM_COUNT + " answers");
        }
        for (int v : answers) {
            if (v < LIKERT_MIN || v > LIKERT_MAX) {
                throw new IllegalArgumentException("Each answer must be " + LIKERT_MIN + "–" + LIKERT_MAX);
            }
        }
        // Q1–Q4: higher agreement = more secure / less avoidant → reverse for avoidance scale
        double rev1 = reverse(answers[0]);
        double rev2 = reverse(answers[1]);
        double rev3 = reverse(answers[2]);
        double rev4 = reverse(answers[3]);
        // Q5–Q6: higher = more avoidant
        double av5 = answers[4];
        double av6 = answers[5];
        double avoidance = (rev1 + rev2 + rev3 + rev4 + av5 + av6) / 6.0;

        // Q7–Q9: anxiety
        double anxiety = (answers[6] + answers[7] + answers[8]) / 3.0;

        return new Result(anxiety, avoidance);
    }

    private static double reverse(int likert) {
        return (LIKERT_MAX + 1) - likert;
    }
}
