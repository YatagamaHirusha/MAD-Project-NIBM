package com.mad.cw.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.MainActivity;
import com.mad.cw.R;

public class ecrquestions extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ecrquestions);

        Button submitBtn = findViewById(R.id.btn_submit_ecr);
        submitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // In a real app, you would calculate the score here
                Toast.makeText(ecrquestions.this, "Assessment Complete", Toast.LENGTH_SHORT).show();
                
                Intent intent = new Intent(ecrquestions.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}