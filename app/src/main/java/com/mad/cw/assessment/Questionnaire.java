package com.mad.cw.assessment;

import com.mad.cw.R;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.repositories.EcrAssessmentRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class Questionnaire extends AppCompatActivity {

    private static final int[] RADIO_GROUP_IDS = {
            R.id.rg_q1,
            R.id.rg_q2,
            R.id.rg_q3,
            R.id.rg_q4,
            R.id.rg_q5,
            R.id.rg_q6,
            R.id.rg_q7,
            R.id.rg_q8,
            R.id.rg_q9,
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SessionStore.init(this);
        setContentView(R.layout.activity_questionnaire);

        Button submit = findViewById(R.id.btn_submit_ecr);
        submit.setOnClickListener(v -> submitAssessment(submit));
    }

    private void submitAssessment(Button submitBtn) {
        int[] scores = new int[RADIO_GROUP_IDS.length];
        for (int i = 0; i < RADIO_GROUP_IDS.length; i++) {
            RadioGroup rg = findViewById(RADIO_GROUP_IDS[i]);
            int value = getSelectedLikert(rg);
            if (value < 1) {
                Toast.makeText(this, R.string.ecr_answer_all, Toast.LENGTH_SHORT).show();
                return;
            }
            scores[i] = value;
        }

        final EcrRsScoring.Result computed;
        try {
            computed = EcrRsScoring.compute(scores);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage() != null ? e.getMessage() : getString(R.string.ecr_answer_all), Toast.LENGTH_LONG).show();
            return;
        }

        AssessmentPreferences.saveEcrAnswers(this, scores, computed.anxiety, computed.avoidance);

        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            Toast.makeText(this, R.string.ecr_saved_offline, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        submitBtn.setEnabled(false);
        try {
            io.execute(() -> {
                try {
                    EcrAssessmentRepository.insertAssessment(scores, computed.anxiety, computed.avoidance);
                    safePost(() -> Toast.makeText(
                                    Questionnaire.this,
                                    getString(R.string.ecr_saved_synced, computed.anxiety, computed.avoidance),
                                    Toast.LENGTH_LONG)
                            .show());
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    safePost(() -> Toast.makeText(
                                    Questionnaire.this,
                                    getString(R.string.ecr_sync_failed, msg),
                                    Toast.LENGTH_LONG)
                            .show());
                }
                safePost(() -> {
                    submitBtn.setEnabled(true);
                    finish();
                });
            });
        } catch (RejectedExecutionException e) {
            submitBtn.setEnabled(true);
            Toast.makeText(this, R.string.ecr_saved_offline, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void safePost(Runnable action) {
        runOnUiThread(() -> {
            if (destroyed || isFinishing() || isDestroyed()) {
                return;
            }
            action.run();
        });
    }

    /** Returns 1–7 from the selected radio, or -1 if none selected. */
    private static int getSelectedLikert(RadioGroup rg) {
        if (rg == null) {
            return -1;
        }
        int checkedId = rg.getCheckedRadioButtonId();
        if (checkedId == -1) {
            return -1;
        }
        RadioButton rb = rg.findViewById(checkedId);
        if (rb == null) {
            return -1;
        }
        try {
            return Integer.parseInt(rb.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        io.shutdown();
        super.onDestroy();
    }
}
