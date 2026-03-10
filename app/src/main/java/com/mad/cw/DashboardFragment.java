package com.mad.cw;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;

public class DashboardFragment extends Fragment {

    public DashboardFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        MaterialCardView psychologyCard = view.findViewById(R.id.card_module_psychology);

        if (psychologyCard != null) {
            psychologyCard.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), Questionnaire.class);
                startActivity(intent);
            });
        }

        MaterialCardView interestCard = view.findViewById(R.id.card_module_interests);


        if (interestCard != null) {
            interestCard.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LifestyleFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        return view;
    }
}