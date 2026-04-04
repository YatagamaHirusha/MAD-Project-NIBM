package com.mad.cw.shell;

import com.mad.cw.R;
import android.os.Bundle;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.mad.cw.supabase.core.SessionStore;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionStore.init(this);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null && SessionStore.isLoggedIn()) {
            AccountSync.refreshLocalFromServerAsync(getApplicationContext(), null);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.nav_hub);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            clearFragmentBackStack();
            Fragment selectedFragment = resolveTab(item.getItemId());
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }

    private void clearFragmentBackStack() {
        FragmentManager fm = getSupportFragmentManager();
        while (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate();
        }
    }

    private Fragment resolveTab(int itemId) {
        if (itemId == R.id.nav_hub) {
            return new DashboardFragment();
        }
        if (itemId == R.id.nav_chatbot) {
            return new ChatBotFragment();
        }
        if (itemId == R.id.nav_inbox) {
            return new InboxFragment();
        }
        if (itemId == R.id.nav_profile) {
            return new ProfileFragment();
        }
        return null;
    }
}
