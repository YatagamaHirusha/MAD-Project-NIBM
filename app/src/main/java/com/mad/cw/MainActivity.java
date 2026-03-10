package com.mad.cw;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // 1. Set the Hub (not the ChatBot) as the default screen on launch
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
            // Make sure the bottom navigation visually selects the Hub icon on startup
            bottomNav.setSelectedItemId(R.id.nav_hub);
        }

        // 2. Handle all Bottom Navigation clicks
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();

            if (itemId == R.id.nav_hub) {
                selectedFragment = new DashboardFragment();
            } else if (itemId == R.id.nav_chatbot) {
                // Here is your AI Chat screen!
                selectedFragment = new ChatBotFragment();
            } else if (itemId == R.id.nav_inbox) {
                // selectedFragment = new InboxFragment();
                return true; // Return true so the icon highlights, even if blank
            } else if (itemId == R.id.nav_profile) {
                // selectedFragment = new ProfileFragment();
                return true;
            }

            // If we have a fragment to show, swap it into the container
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }
}