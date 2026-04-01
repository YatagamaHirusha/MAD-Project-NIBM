package com.mad.cw;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LifestyleFragment extends Fragment {

    private static final int SECTION_TITLE_MARGIN_TOP_DP = 20;
    private static final int SECTION_TITLE_MARGIN_BOTTOM_DP = 6;
    private static final int CHIP_GROUP_MARGIN_BOTTOM_DP = 8;

    private final String[] LOCATIONS_POOL = {"Colombo", "Gampaha", "Kalutara", "Galle", "Matara", "Hambantota", "Ratnapura", "Kegalle", "Badulla", "Monaragala", "Kandy"};
    private final String[] OCCUPATIONS_POOL = {"Engineer", "Doctor", "Teacher", "Lawyer", "Accountant", "Bank Officer", "Software Developer", "Civil Servant", "Farmer", "Business Owner", "Marketing Executive", "Student", "Nurse", "Tourism Guide", "Driver", "Chef", "Police Officer", "Electrician", "Construction Worker", "Journalist", "Pharmacist"};

    private final Map<String, ChipGroup> chipGroupsByColumn = new HashMap<>();

    public LifestyleFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lifestyle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AutoCompleteTextView dropdownLocation = view.findViewById(R.id.dropdown_location);
        AutoCompleteTextView dropdownOccupation = view.findViewById(R.id.dropdown_occupation);
        LinearLayout sections = view.findViewById(R.id.ll_interest_sections);
        MaterialButton btnSubmit = view.findViewById(R.id.btn_submit_profile);

        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, LOCATIONS_POOL);
        dropdownLocation.setAdapter(locationAdapter);
        ArrayAdapter<String> occupationAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, OCCUPATIONS_POOL);
        dropdownOccupation.setAdapter(occupationAdapter);

        dropdownLocation.setText(UserInterestStore.loadLocation(requireContext()), false);
        dropdownOccupation.setText(UserInterestStore.loadOccupation(requireContext()), false);

        buildInterestSections(sections);
        restoreChipSelections();

        btnSubmit.setOnClickListener(v -> submitProfile(dropdownLocation, dropdownOccupation));
    }

    private void buildInterestSections(LinearLayout container) {
        chipGroupsByColumn.clear();
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        int pink = ContextCompat.getColor(requireContext(), R.color.accent_pink);
        ColorStateList pinkStroke = ColorStateList.valueOf(pink);

        for (int i = 0; i < InterestTaxonomy.CATEGORIES.length; i++) {
            InterestTaxonomy.Category cat = InterestTaxonomy.CATEGORIES[i];

            TextView title = new TextView(requireContext());
            title.setText(cat.columnName);
            title.setTextColor(0xFF1A1A1A);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            int top = i == 0 ? 0 : dp(density, SECTION_TITLE_MARGIN_TOP_DP);
            titleLp.setMargins(0, top, 0, dp(density, SECTION_TITLE_MARGIN_BOTTOM_DP));
            title.setLayoutParams(titleLp);
            container.addView(title);

            ChipGroup cg = new ChipGroup(requireContext());
            cg.setTag(cat.columnName);
            cg.setSingleSelection(cat.singleSelection);
            if (cat.singleSelection) {
                cg.setSelectionRequired(false);
            }
            cg.setChipSpacing(dp(density, 8));
            cg.setChipSpacingHorizontal(dp(density, 8));
            cg.setChipSpacingVertical(dp(density, 8));

            for (String tag : cat.tags) {
                Chip chip = new Chip(requireContext());
                chip.setText(tag);
                chip.setCheckable(true);
                chip.setChipBackgroundColor(ColorStateList.valueOf(0xFFFCE4EC));
                chip.setChipStrokeWidth(dp(density, 1));
                chip.setChipStrokeColor(pinkStroke);
                chip.setTextColor(0xFF333333);
                cg.addView(chip);
            }

            LinearLayout.LayoutParams cgLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cgLp.bottomMargin = dp(density, CHIP_GROUP_MARGIN_BOTTOM_DP);
            cg.setLayoutParams(cgLp);

            container.addView(cg);
            chipGroupsByColumn.put(cat.columnName, cg);
        }
    }

    private static int dp(float density, int d) {
        return (int) (d * density + 0.5f);
    }

    private void restoreChipSelections() {
        Map<String, List<String>> saved = UserInterestStore.loadInterestMap(requireContext());
        for (Map.Entry<String, ChipGroup> e : chipGroupsByColumn.entrySet()) {
            List<String> tags = saved.get(e.getKey());
            if (tags == null || tags.isEmpty()) {
                continue;
            }
            ChipGroup cg = e.getValue();
            for (int i = 0; i < cg.getChildCount(); i++) {
                View child = cg.getChildAt(i);
                if (!(child instanceof Chip)) {
                    continue;
                }
                Chip chip = (Chip) child;
                String label = chip.getText() != null ? chip.getText().toString() : "";
                if (tags.contains(label)) {
                    chip.setChecked(true);
                }
            }
        }
    }

    private void submitProfile(AutoCompleteTextView dropdownLocation, AutoCompleteTextView dropdownOccupation) {
        String location = dropdownLocation.getText() != null ? dropdownLocation.getText().toString().trim() : "";
        String occupation = dropdownOccupation.getText() != null ? dropdownOccupation.getText().toString().trim() : "";

        if (location.isEmpty() || occupation.isEmpty()) {
            Toast.makeText(getContext(), R.string.lifestyle_need_location_occupation, Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, List<String>> interests = new HashMap<>();
        for (InterestTaxonomy.Category cat : InterestTaxonomy.CATEGORIES) {
            ChipGroup cg = chipGroupsByColumn.get(cat.columnName);
            List<String> selected = getSelectedChipTexts(cg);
            if (selected.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.lifestyle_need_category, cat.columnName), Toast.LENGTH_LONG).show();
                return;
            }
            interests.put(cat.columnName, selected);
        }

        UserInterestStore.save(requireContext(), location, occupation, interests);

        Toast.makeText(getContext(), R.string.lifestyle_saved, Toast.LENGTH_SHORT).show();

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment())
                .commit();

        BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_hub);
        }
    }

    private static List<String> getSelectedChipTexts(ChipGroup chipGroup) {
        List<String> selectedTexts = new ArrayList<>();
        if (chipGroup == null) {
            return selectedTexts;
        }
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
