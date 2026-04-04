package com.mad.cw.welcome;

import com.mad.cw.R;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import com.mad.cw.shell.MainActivity;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mad.cw.auth.login;
import com.mad.cw.auth.register;
import com.mad.cw.supabase.core.SessionStore;

public class Welcome extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SessionStore.init(this);
        if (SessionStore.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_welcome);

        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> {
            Intent loginIntent = new Intent(Welcome.this, login.class);
            startActivity(loginIntent);
        });

        Button btnRegister = findViewById(R.id.btnRegister);
        btnRegister.setOnClickListener(v -> {
            Intent registerIntent = new Intent(Welcome.this, register.class);
            startActivity(registerIntent);
        });
    }
}