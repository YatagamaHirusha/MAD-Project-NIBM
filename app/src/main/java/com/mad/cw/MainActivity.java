package com.mad.cw;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // we can use sharedPreference to save states of the user and check if the user is logged in or not.
        // for now, we will just use a boolean variable to check if the user is logged in or not.
        boolean isLoggedIn = false;

        if(isLoggedIn){
            Intent intent = new Intent(MainActivity.this, Welcome.class);
            startActivity(intent);
            finish();
        }
        else {
            long splashTime = 4500L;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(MainActivity.this, Welcome.class);
                    startActivity(intent);
                    finish();
                }
            }, splashTime);
        }
    }
}