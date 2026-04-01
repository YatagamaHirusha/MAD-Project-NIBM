package com.mad.cw;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_questionnaire);

        Button submit = findViewById(R.id.btn_submit_ecr);
        submit.setOnClickListener(v -> submitAssessment());
    }

    private void submitAssessment() {
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

        AssessmentPreferences.saveEcrAnswers(this, scores);
        Toast.makeText(this, R.string.ecr_saved_returning_home, Toast.LENGTH_SHORT).show();
        finish();
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
}
