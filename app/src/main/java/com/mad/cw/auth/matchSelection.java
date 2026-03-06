package com.mad.cw.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.R;

public class matchSelection extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_match_selection);

        Button continueBtn = findViewById(R.id.btn_save_preferences);
        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to ECR-RS Assessment after selection
                Intent intent = new Intent(matchSelection.this, ecrquestions.class);
                startActivity(intent);
                finish();
            }
        });
    }
}