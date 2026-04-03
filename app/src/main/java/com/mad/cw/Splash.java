package com.mad.cw;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.supabase.SessionStore;

public class Splash extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        SessionStore.init(this);

        // Same delay for everyone: a sub-second splash is easy to miss during cold start.
        long splashMs = 2000L;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> next = SessionStore.isLoggedIn() ? MainActivity.class : Welcome.class;
            startActivity(new Intent(Splash.this, next));
            finish();
        }, splashMs);
    }
}