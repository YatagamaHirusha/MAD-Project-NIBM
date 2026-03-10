package com.mad.cw;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class lifestyle extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.fragment_lifestyle); // a simple layout with just a FrameContainer
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new LifestyleFragment())
                .commit();

    }
}