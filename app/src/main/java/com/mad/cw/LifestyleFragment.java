package com.mad.cw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class LifestyleFragment extends Fragment {

    // 1. Define your DRL Model Data Pools
    private final String[] LOCATIONS_POOL = {"Colombo", "Gampaha", "Kalutara", "Galle", "Matara", "Hambantota", "Ratnapura", "Kegalle", "Badulla", "Monaragala", "Kandy"};
    private final String[] OCCUPATIONS_POOL = {"Engineer", "Doctor", "Teacher", "Lawyer", "Accountant", "Bank Officer", "Software Developer", "Civil Servant", "Farmer", "Business Owner", "Marketing Executive", "Student", "Nurse", "Tourism Guide", "Driver", "Chef", "Police Officer", "Electrician", "Construction Worker", "Journalist", "Pharmacist"};

    private final String[] INTENT_POOL = {"Casual Dating", "Long-Term Relationship", "Marriage", "Open Relationship", "Still Figuring It Out"};
    private final String[] LIFESTYLE_POOL = {"Fitness", "Gym", "Yoga", "Meditation", "Vegan", "Vegetarian", "Traveling", "Digital Nomad", "Pet Lover", "Nightlife", "Early Bird", "Night Owl"};
    private final String[] INTELLECTUAL_POOL = {"Philosophy", "Psychology", "Self-Improvement", "History", "Science", "Technology", "Startups", "AI & Machine Learning", "Books & Reading"};

    public LifestyleFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lifestyle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 2. Setup Dropdown Menus
        AutoCompleteTextView dropdownLocation = view.findViewById(R.id.dropdown_location);
        AutoCompleteTextView dropdownOccupation = view.findViewById(R.id.dropdown_occupation);

        // Create an adapter for the dropdowns using Android's built-in layout
        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, LOCATIONS_POOL);
        dropdownLocation.setAdapter(locationAdapter);

        ArrayAdapter<String> occupationAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, OCCUPATIONS_POOL);
        dropdownOccupation.setAdapter(occupationAdapter);

        // 3. Setup Chip Groups
        ChipGroup cgIntent = view.findViewById(R.id.cg_intent);
        ChipGroup cgLifestyle = view.findViewById(R.id.cg_lifestyle);
        ChipGroup cgIntellectual = view.findViewById(R.id.cg_intellectual);

        // Inject the arrays into the UI
        populateChipGroup(INTENT_POOL, cgIntent);
        populateChipGroup(LIFESTYLE_POOL, cgLifestyle);
        populateChipGroup(INTELLECTUAL_POOL, cgIntellectual);

        // 4. Handle Submit Button
        Button btnSubmit = view.findViewById(R.id.btn_submit_profile);
        btnSubmit.setOnClickListener(v -> {

            // Example of how to get the selected dropdown values:
            String selectedLocation = dropdownLocation.getText().toString();
            String selectedOccupation = dropdownOccupation.getText().toString();

            // Example of how to get all checked chips from a multi-select group:
            List<String> selectedLifestyle = getSelectedChipTexts(cgLifestyle);

            if (selectedLocation.isEmpty() || selectedOccupation.isEmpty()) {
                Toast.makeText(getContext(), "Please select your location and occupation", Toast.LENGTH_SHORT).show();
                return;
            }

            // pass data to DRL model
            Toast.makeText(getContext(), "Profile Saved!", Toast.LENGTH_SHORT).show();

            // Navigate back to Hub (DashboardFragment)
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();

            // Sync the bottom nav selection to Hub
            com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                    requireActivity().findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_hub);
            }

        });
    }

    /**
     * Helper Method: Creates Chips programmatically and adds them to a group
     */
    private void populateChipGroup(String[] items, ChipGroup chipGroup) {
        for (String item : items) {
            Chip chip = new Chip(requireContext());
            chip.setText(item);
            chip.setCheckable(true);
            chip.setClickable(true);

            // Optional styling: making the chip pink when selected
            // chip.setCheckedIconTintResource(R.color.pink_accent);

            chipGroup.addView(chip);
        }
    }

    /**
     * Helper Method: Extracts a list of strings from whatever the user tapped
     */
    private List<String> getSelectedChipTexts(ChipGroup chipGroup) {
        List<String> selectedTexts = new ArrayList<>();
        List<Integer> checkedIds = chipGroup.getCheckedChipIds();

        for (int id : checkedIds) {
            Chip chip = chipGroup.findViewById(id);
            if (chip != null) {
                selectedTexts.add(chip.getText().toString());
            }
        }
        return selectedTexts;
    }
}