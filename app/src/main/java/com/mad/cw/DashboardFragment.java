package com.mad.cw;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class DashboardFragment extends Fragment {

    public DashboardFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        ImageView profileShortcut = view.findViewById(R.id.iv_profile);
        if (profileShortcut != null) {
            profileShortcut.setOnClickListener(v -> {
                FragmentManager fm = requireActivity().getSupportFragmentManager();
                while (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStackImmediate();
                }
                BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_profile);
                }
            });
        }

        View psychologyRow = view.findViewById(R.id.card_module_psychology);
        if (psychologyRow != null) {
            psychologyRow.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), Questionnaire.class);
                startActivity(intent);
            });
        }

        View interestRow = view.findViewById(R.id.card_module_interests);
        if (interestRow != null) {
            interestRow.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LifestyleFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        MaterialButton btnMatches = view.findViewById(R.id.btn_generate_matches);
        if (btnMatches != null) {
            btnMatches.setOnClickListener(v ->
                    Toast.makeText(
                            requireContext(),
                            "Complete psychology and lifestyle steps to unlock matching.",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }

        return view;
    }
}