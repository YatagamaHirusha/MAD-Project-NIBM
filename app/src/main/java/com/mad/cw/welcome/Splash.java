package com.mad.cw.welcome;

import com.mad.cw.R;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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