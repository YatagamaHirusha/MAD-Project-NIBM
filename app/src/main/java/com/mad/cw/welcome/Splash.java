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
import androidx.core.splashscreen.SplashScreen;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.RenderMode;
import com.mad.cw.supabase.core.SessionStore;

public class Splash extends AppCompatActivity {

    private static final long INTRO_MIN_DISPLAY_MS = 3200L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingNavigate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SessionStore.init(getApplicationContext());

        boolean introDone = SplashPreferences.isIntroAnimationDone(this);
        setTheme(introDone ? R.style.Theme_MindMatch : R.style.Theme_MindMatch_Splash);
        if (!introDone) {
            SplashScreen.installSplashScreen(this);
        }

        super.onCreate(savedInstanceState);

        if (introDone) {
            goToNextAndFinish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        LottieAnimationView lottie = findViewById(R.id.lottie_splash_animation);
        if (lottie != null) {
            lottie.setRepeatCount(LottieDrawable.INFINITE);
            lottie.setRenderMode(RenderMode.HARDWARE);
            lottie.addLottieOnCompositionLoadedListener(
                    composition -> {
                        if (!isFinishing()) {
                            lottie.playAnimation();
                        }
                    });
        }

        pendingNavigate =
                () -> {
                    if (isFinishing()) {
                        return;
                    }
                    SplashPreferences.markIntroAnimationDone(Splash.this);
                    goToNextAndFinish();
                };
        mainHandler.postDelayed(pendingNavigate, INTRO_MIN_DISPLAY_MS);
    }

    @Override
    protected void onDestroy() {
        if (pendingNavigate != null) {
            mainHandler.removeCallbacks(pendingNavigate);
            pendingNavigate = null;
        }
        super.onDestroy();
    }

    private void goToNextAndFinish() {
        Class<?> next = SessionStore.isLoggedIn() ? MainActivity.class : Welcome.class;
        startActivity(new Intent(Splash.this, next));
        finish();
    }
}
