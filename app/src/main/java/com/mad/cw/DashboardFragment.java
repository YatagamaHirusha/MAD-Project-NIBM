package com.mad.cw;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class DashboardFragment extends Fragment {

    public DashboardFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        ImageView profileShortcut = view.findViewById(R.id.iv_profile);
        if (profileShortcut != null) {
            profileShortcut.setOnClickListener(v -> navigateToProfileTab());
        }

        View basicRow = view.findViewById(R.id.card_module_basic);
        if (basicRow != null) {
            basicRow.setOnClickListener(v -> navigateToProfileTab());
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
                if (!AssessmentPreferences.isEcrComplete(requireContext())) {
                    Toast.makeText(
                                    requireContext(),
                                    R.string.dashboard_complete_ecr_first,
                                    Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LifestyleFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        MaterialButton btnMatches = view.findViewById(R.id.btn_generate_matches);
        if (btnMatches != null) {
            btnMatches.setOnClickListener(
                    v ->
                            requireActivity()
                                    .getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.fragment_container, new MatchSuggestionsFragment())
                                    .addToBackStack(null)
                                    .commit());
        }

        refreshJourneyUi(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) {
            refreshJourneyUi(v);
        }
        ProfileSync.refreshLocalFromServerAsync(
                requireContext(),
                () -> {
                    View root = getView();
                    if (root != null && isAdded()) {
                        refreshJourneyUi(root);
                    }
                });
    }

    private void navigateToProfileTab() {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        while (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate();
        }
        BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
        }
    }

    private void refreshJourneyUi(View root) {
        Context ctx = requireContext();
        boolean basic = isBasicProfileCompleteFromPrefs(ctx);
        boolean ecr = AssessmentPreferences.isEcrComplete(ctx);
        boolean lifestyle = UserInterestStore.isLifestyleComplete(ctx);

        int doneCount = (basic ? 1 : 0) + (ecr ? 1 : 0) + (lifestyle ? 1 : 0);
        int pct = Math.round(100f * doneCount / 3f);

        TextView tvPct = root.findViewById(R.id.tv_readiness_value);
        LinearProgressIndicator bar = root.findViewById(R.id.progress_readiness_bar);
        if (tvPct != null) {
            tvPct.setText(pct + "%");
        }
        if (bar != null) {
            bar.setProgress(pct);
        }

        TextView tvBasicSubtitle = root.findViewById(R.id.tv_basic_step_subtitle);
        ImageView ivBasicIcon = root.findViewById(R.id.iv_basic_step_icon);
        TextView tvBasicDone = root.findViewById(R.id.tv_basic_done);
        ImageView ivBasicChevron = root.findViewById(R.id.iv_basic_chevron);
        if (tvBasicSubtitle != null) {
            tvBasicSubtitle.setText(
                    basic
                            ? getString(R.string.dashboard_basic_subtitle_done)
                            : getString(R.string.dashboard_basic_subtitle_todo));
        }
        if (ivBasicIcon != null) {
            if (basic) {
                ivBasicIcon.setImageResource(R.drawable.ic_step_done);
                ivBasicIcon.setImageTintList(null);
            } else {
                ivBasicIcon.setImageResource(R.drawable.ic_nav_profile);
                ivBasicIcon.setImageTintList(ColorStateList.valueOf(0xFFBDBDBD));
            }
        }
        if (tvBasicDone != null && ivBasicChevron != null) {
            if (basic) {
                tvBasicDone.setVisibility(View.VISIBLE);
                ivBasicChevron.setVisibility(View.GONE);
            } else {
                tvBasicDone.setVisibility(View.GONE);
                ivBasicChevron.setVisibility(View.VISIBLE);
            }
        }

        ImageView ivPsychIcon = root.findViewById(R.id.iv_psychology_step_icon);
        TextView ecrDoneBadge = root.findViewById(R.id.tv_psychology_done);
        ImageView ecrChevron = root.findViewById(R.id.iv_psychology_chevron);
        if (ivPsychIcon != null) {
            if (ecr) {
                ivPsychIcon.setImageResource(R.drawable.ic_step_done);
                ivPsychIcon.setImageTintList(null);
            } else {
                ivPsychIcon.setImageResource(R.drawable.ic_step_psychology);
                ivPsychIcon.setImageTintList(null);
            }
        }
        if (ecrDoneBadge != null && ecrChevron != null) {
            if (ecr) {
                ecrDoneBadge.setVisibility(View.VISIBLE);
                ecrChevron.setVisibility(View.GONE);
            } else {
                ecrDoneBadge.setVisibility(View.GONE);
                ecrChevron.setVisibility(View.VISIBLE);
            }
        }

        View llInterests = root.findViewById(R.id.ll_interests_row);
        ImageView ivIntIcon = root.findViewById(R.id.iv_interests_step_icon);
        TextView tvIntTitle = root.findViewById(R.id.tv_interests_title);
        TextView tvIntSub = root.findViewById(R.id.tv_interests_subtitle);
        TextView tvLock = root.findViewById(R.id.tv_interests_locked);
        ImageView ivIntChev = root.findViewById(R.id.iv_interests_chevron);
        TextView tvIntDone = root.findViewById(R.id.tv_interests_done);

        if (llInterests != null) {
            llInterests.setAlpha(!ecr ? 0.72f : 1f);
        }
        if (tvIntTitle != null) {
            tvIntTitle.setTextColor(!ecr ? 0xFF616161 : 0xFF1A1A1A);
        }
        if (tvIntSub != null) {
            tvIntSub.setTextColor(!ecr ? 0xFF9E9E9E : 0xFF757575);
        }
        if (ivIntIcon != null) {
            if (lifestyle) {
                ivIntIcon.setImageResource(R.drawable.ic_step_done);
                ivIntIcon.setImageTintList(null);
            } else {
                ivIntIcon.setImageResource(R.drawable.ic_step_interests);
                ivIntIcon.setImageTintList(!ecr ? ColorStateList.valueOf(0xFF9E9E9E) : null);
            }
        }
        if (tvLock != null && ivIntChev != null && tvIntDone != null) {
            if (!ecr) {
                tvLock.setVisibility(View.VISIBLE);
                ivIntChev.setVisibility(View.GONE);
                tvIntDone.setVisibility(View.GONE);
            } else if (!lifestyle) {
                tvLock.setVisibility(View.GONE);
                ivIntChev.setVisibility(View.VISIBLE);
                tvIntDone.setVisibility(View.GONE);
            } else {
                tvLock.setVisibility(View.GONE);
                ivIntChev.setVisibility(View.GONE);
                tvIntDone.setVisibility(View.VISIBLE);
            }
        }

        boolean allDone = basic && ecr && lifestyle;
        MaterialButton btnMatches = root.findViewById(R.id.btn_generate_matches);
        TextView footer = root.findViewById(R.id.tv_dashboard_footer);
        if (footer != null) {
            footer.setText(
                    allDone
                            ? getString(R.string.dashboard_footer_ready)
                            : getString(R.string.dashboard_footer_progress));
        }
        if (btnMatches != null) {
            if (allDone) {
                btnMatches.setEnabled(true);
                btnMatches.setBackgroundTintList(ColorStateList.valueOf(0xFFE91E63));
                btnMatches.setTextColor(ColorStateList.valueOf(0xFFFFFFFF));
            } else {
                btnMatches.setEnabled(false);
                btnMatches.setBackgroundTintList(ColorStateList.valueOf(0xFFE8E8E8));
                btnMatches.setTextColor(ColorStateList.valueOf(0xFF9E9E9E));
            }
        }
    }

    private static boolean isBasicProfileCompleteFromPrefs(Context ctx) {
        SharedPreferences p = ProfilePreferences.get(ctx);
        return ProfileFormValidator.isBasicProfileComplete(
                p.getString(ProfilePreferences.KEY_DISPLAY_NAME, ""),
                p.getString(ProfilePreferences.KEY_EMAIL, ""),
                p.getString(ProfilePreferences.KEY_DOB, ""),
                p.getString(ProfilePreferences.KEY_LOCATION, ""),
                p.getString(ProfilePreferences.KEY_OCCUPATION, ""),
                p.getString(ProfilePreferences.KEY_BIO, ""),
                p.getString(ProfilePreferences.KEY_GENDER, ""),
                p.getString(ProfilePreferences.KEY_TARGET_GENDER, ""));
    }
}
