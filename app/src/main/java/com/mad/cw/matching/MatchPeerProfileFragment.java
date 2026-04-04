package com.mad.cw.matching;

import com.mad.cw.R;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.mad.cw.assessment.*;
import com.mad.cw.auth.AuthValidation;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.InterestTaxonomy;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.GenderOptions;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.repositories.MatchRequestRepository;
import com.mad.cw.supabase.repositories.MatchRequestRepository.OtherPendingRequestException;
import com.mad.cw.supabase.repositories.ProfileRecord;
import com.mad.cw.supabase.repositories.ProfileRemoteRepository;
import com.mad.cw.supabase.repositories.UserInterestsRepository;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Read-only profile for a match candidate; send match request from here. */
public class MatchPeerProfileFragment extends Fragment {

    private static final String ARG_PEER_ID = "peer_id";
    private static final String ARG_ML_RANK = "ml_rank";

    private ExecutorService io;
    private volatile boolean viewDestroyed;
    @Nullable private MaterialButton btnSend;
    private String peerId = "";
    private int mlRank = 1;

    public MatchPeerProfileFragment() {}

    @NonNull
    public static MatchPeerProfileFragment newInstance(@NonNull String peerId, int mlRank) {
        MatchPeerProfileFragment f = new MatchPeerProfileFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PEER_ID, peerId);
        b.putInt(ARG_ML_RANK, mlRank);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_match_peer_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewDestroyed = false;
        io = Executors.newSingleThreadExecutor();

        Bundle args = getArguments();
        peerId = args != null ? args.getString(ARG_PEER_ID, "") : "";
        mlRank = args != null ? args.getInt(ARG_ML_RANK, 1) : 1;
        if (mlRank < 1) {
            mlRank = 1;
        }

        ImageButton back = view.findViewById(R.id.ib_peer_profile_back);
        if (back != null) {
            back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        btnSend = view.findViewById(R.id.btn_peer_send_request);

        if (!UuidValidation.isUuid(peerId)) {
            showError(view);
            return;
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> onSendRequestClicked());
        }

        io.execute(
                () -> {
                    try {
                        Map<String, ProfileRecord> map =
                                ProfileRemoteRepository.fetchProfilesByIds(Collections.singleton(peerId));
                        ProfileRecord pr = map.get(peerId);
                        UserInterestsRepository.PeerInterestRow interests;
                        try {
                            interests = UserInterestsRepository.fetchPeerRow(peerId);
                        } catch (Exception e) {
                            interests =
                                    new UserInterestsRepository.PeerInterestRow(
                                            "", "", Collections.emptyMap());
                        }
                        final ProfileRecord record = pr;
                        final UserInterestsRepository.PeerInterestRow interestRow = interests;
                        safePost(
                                () -> {
                                    if (record == null) {
                                        showError(view);
                                        return;
                                    }
                                    bindProfile(view, peerId, record, interestRow);
                                    refreshSendButtonState();
                                });
                    } catch (Exception e) {
                        safePost(() -> showError(view));
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null && UuidValidation.isUuid(peerId)) {
            refreshSendButtonState();
        }
    }

    private void refreshSendButtonState() {
        if (btnSend == null || getContext() == null) {
            return;
        }
        String pending = MatchRequestLocalStore.getPendingPeerId(requireContext());
        boolean pendingThis = pending != null && pending.equals(peerId);
        boolean blockedOther = pending != null && !pending.isEmpty() && !pendingThis;
        if (pendingThis) {
            btnSend.setEnabled(false);
            btnSend.setText(R.string.match_pending_response);
        } else if (blockedOther) {
            btnSend.setEnabled(false);
            btnSend.setText(R.string.match_request_send);
        } else {
            btnSend.setEnabled(true);
            btnSend.setText(R.string.match_request_send);
        }
    }

    private void onSendRequestClicked() {
        if (getContext() == null || io == null) {
            return;
        }
        Context ctx = requireContext();
        String pending = MatchRequestLocalStore.getPendingPeerId(ctx);
        if (pending != null && !pending.isEmpty() && !pending.equals(peerId)) {
            Toast.makeText(ctx, R.string.match_request_already_pending, Toast.LENGTH_LONG).show();
            return;
        }
        if (pending != null && pending.equals(peerId)) {
            return;
        }

        if (!UuidValidation.isUuid(peerId)) {
            MatchRequestLocalStore.setPending(ctx, peerId, mlRank);
            refreshSendButtonState();
            Toast.makeText(ctx, R.string.match_request_demo_saved, Toast.LENGTH_LONG).show();
            return;
        }

        if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
            MatchRequestLocalStore.setPending(ctx, peerId, mlRank);
            refreshSendButtonState();
            Toast.makeText(ctx, R.string.match_request_demo_saved, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            io.execute(
                    () -> {
                        try {
                            MatchRequestRepository.insertPending(peerId, mlRank);
                            safePost(
                                    () -> {
                                        MatchRequestLocalStore.setPending(ctx, peerId, mlRank);
                                        refreshSendButtonState();
                                        Toast.makeText(ctx, R.string.match_request_sent_server, Toast.LENGTH_SHORT)
                                                .show();
                                    });
                        } catch (OtherPendingRequestException e) {
                            try {
                                MatchRequestRepository.PendingOutbound sync =
                                        MatchRequestRepository.fetchPendingOutbound();
                                safePost(
                                        () -> {
                                            if (sync != null
                                                    && sync.toUserId != null
                                                    && !sync.toUserId.isEmpty()) {
                                                int r = sync.mlRank > 0 ? sync.mlRank : 1;
                                                MatchRequestLocalStore.setPending(ctx, sync.toUserId, r);
                                                refreshSendButtonState();
                                            }
                                            Toast.makeText(
                                                            ctx,
                                                            R.string.match_request_blocked_other_pending,
                                                            Toast.LENGTH_LONG)
                                                    .show();
                                        });
                            } catch (IOException ignored) {
                                safePost(
                                        () ->
                                                Toast.makeText(
                                                                ctx,
                                                                R.string.match_request_blocked_other_pending,
                                                                Toast.LENGTH_LONG)
                                                        .show());
                            }
                        } catch (Exception e) {
                            safePost(
                                    () ->
                                            Toast.makeText(
                                                            ctx,
                                                            getString(R.string.match_request_failed)
                                                                    + (e.getMessage() != null ? " " + e.getMessage() : ""),
                                                            Toast.LENGTH_LONG)
                                                    .show());
                        }
                    });
        } catch (RejectedExecutionException ignored) {
            Toast.makeText(ctx, R.string.match_request_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void bindProfile(
            @NonNull View root,
            @NonNull String peerId,
            @NonNull ProfileRecord pr,
            @NonNull UserInterestsRepository.PeerInterestRow interests) {
        root.findViewById(R.id.tv_peer_error).setVisibility(View.GONE);
        root.findViewById(R.id.scroll_peer_content).setVisibility(View.VISIBLE);
        if (btnSend != null) {
            btnSend.setVisibility(View.VISIBLE);
        }

        TextView name = root.findViewById(R.id.tv_peer_display_name);
        TextView age = root.findViewById(R.id.tv_peer_age);
        TextView bio = root.findViewById(R.id.tv_peer_bio);
        ImageView avatar = root.findViewById(R.id.iv_peer_avatar);

        String display =
                pr.displayName != null && !pr.displayName.trim().isEmpty()
                        ? pr.displayName.trim()
                        : "Member";
        if (name != null) {
            name.setText(display);
        }

        if (age != null) {
            Calendar dob = AuthValidation.parseDob(pr.dateOfBirth);
            if (dob != null) {
                int years = AuthValidation.ageInYears(dob, Calendar.getInstance());
                if (years >= 0 && years < 130) {
                    age.setText(getString(R.string.match_peer_age_years, years));
                } else {
                    age.setText(R.string.match_peer_age_unknown);
                }
            } else {
                age.setText(R.string.match_peer_age_unknown);
            }
        }

        if (bio != null) {
            String b = pr.bio != null ? pr.bio.trim() : "";
            bio.setText(b.isEmpty() ? getString(R.string.match_peer_bio_empty) : b);
        }

        if (avatar != null) {
            String url =
                    pr.avatarUrl != null && !pr.avatarUrl.trim().isEmpty()
                            ? pr.avatarUrl.trim()
                            : MatchScoring.demoPhotoUrlForPeerId(peerId);
            Glide.with(avatar).load(url).circleCrop().into(avatar);
        }

        TextView tvGender = root.findViewById(R.id.tv_peer_detail_gender);
        TextView tvTarget = root.findViewById(R.id.tv_peer_detail_target);
        TextView tvLoc = root.findViewById(R.id.tv_peer_detail_location);
        TextView tvOcc = root.findViewById(R.id.tv_peer_detail_occupation);

        setDetailLine(
                tvGender,
                R.string.match_peer_label_gender,
                GenderOptions.isValidGenderValue(pr.gender)
                        ? GenderOptions.labelForGenderValue(requireContext(), pr.gender)
                        : (pr.gender != null ? pr.gender.trim() : ""));
        setDetailLine(
                tvTarget,
                R.string.match_peer_label_looking_for,
                GenderOptions.isValidTargetGenderValue(pr.targetGender)
                        ? GenderOptions.labelForTargetGenderValue(requireContext(), pr.targetGender)
                        : (pr.targetGender != null ? pr.targetGender.trim() : ""));
        setDetailLine(
                tvLoc, R.string.match_peer_label_location, pr.location != null ? pr.location.trim() : "");
        setDetailLine(
                tvOcc,
                R.string.match_peer_label_occupation,
                pr.occupation != null ? pr.occupation.trim() : "");

        TextView meta = root.findViewById(R.id.tv_peer_interests_meta);
        LinearLayout sections = root.findViewById(R.id.ll_peer_interest_sections);
        TextView emptyInterests = root.findViewById(R.id.tv_peer_interests_empty);

        String pLoc = pr.location != null ? pr.location.trim() : "";
        String pOcc = pr.occupation != null ? pr.occupation.trim() : "";
        String iLoc = interests.location != null ? interests.location.trim() : "";
        String iOcc = interests.occupation != null ? interests.occupation.trim() : "";

        boolean locDiff = !iLoc.isEmpty() && !iLoc.equalsIgnoreCase(pLoc);
        boolean occDiff = !iOcc.isEmpty() && !iOcc.equalsIgnoreCase(pOcc);
        if (meta != null) {
            if (locDiff || occDiff) {
                meta.setVisibility(View.VISIBLE);
                meta.setText(
                        getString(R.string.match_peer_interests_from_profile)
                                + "\n"
                                + getString(R.string.match_line_location_job, iLoc, iOcc));
            } else if (!iLoc.isEmpty() || !iOcc.isEmpty()) {
                meta.setVisibility(View.VISIBLE);
                meta.setText(getString(R.string.match_line_location_job, iLoc, iOcc));
            } else {
                meta.setVisibility(View.GONE);
            }
        }

        populateInterestSections(sections, interests);

        boolean hasTags = interests.hasAnyTags();
        if (emptyInterests != null) {
            emptyInterests.setVisibility(hasTags ? View.GONE : View.VISIBLE);
        }
    }

    private void setDetailLine(@Nullable TextView tv, int labelRes, @NonNull String value) {
        if (tv == null) {
            return;
        }
        if (value.isEmpty()) {
            tv.setVisibility(View.GONE);
            return;
        }
        tv.setVisibility(View.VISIBLE);
        tv.setText(getString(R.string.match_peer_detail_pair, getString(labelRes), value));
    }

    private void populateInterestSections(
            @Nullable LinearLayout container, @NonNull UserInterestsRepository.PeerInterestRow row) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int topFirst = (int) (4 * density);
        int topNext = (int) (14 * density);

        boolean first = true;
        for (InterestTaxonomy.Category cat : InterestTaxonomy.CATEGORIES) {
            List<String> tags = row.byCategory.get(cat.columnName);
            if (tags == null || tags.isEmpty()) {
                continue;
            }
            TextView title = new TextView(requireContext());
            title.setText(cat.columnName);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFF424242);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = first ? topFirst : topNext;
            first = false;
            container.addView(title, lp);

            ChipGroup group = new ChipGroup(requireContext());
            group.setSingleSelection(false);
            for (String t : tags) {
                if (t == null || t.trim().isEmpty()) {
                    continue;
                }
                Chip chip = new Chip(requireContext());
                chip.setText(t.trim());
                chip.setCheckable(false);
                chip.setClickable(false);
                chip.setFocusable(false);
                group.addView(chip);
            }
            container.addView(
                    group,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showError(@NonNull View root) {
        root.findViewById(R.id.scroll_peer_content).setVisibility(View.GONE);
        if (btnSend != null) {
            btnSend.setVisibility(View.GONE);
        }
        TextView err = root.findViewById(R.id.tv_peer_error);
        if (err != null) {
            err.setVisibility(View.VISIBLE);
        }
    }

    private void safePost(Runnable action) {
        if (getActivity() == null) {
            return;
        }
        requireActivity()
                .runOnUiThread(
                        () -> {
                            if (viewDestroyed || !isAdded()) {
                                return;
                            }
                            action.run();
                        });
    }

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        if (io != null) {
            io.shutdown();
            io = null;
        }
        btnSend = null;
        super.onDestroyView();
    }
}
