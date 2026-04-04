package com.mad.cw.matching;

import com.mad.cw.R;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.mad.cw.supabase.repositories.ProfileRecord;
import com.mad.cw.supabase.repositories.ProfileRemoteRepository;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only profile for a match candidate (opened from the suggestions list). */
public class MatchPeerProfileFragment extends Fragment {

    private static final String ARG_PEER_ID = "peer_id";

    private ExecutorService io;
    private volatile boolean viewDestroyed;

    public MatchPeerProfileFragment() {}

    @NonNull
    public static MatchPeerProfileFragment newInstance(@NonNull String peerId) {
        MatchPeerProfileFragment f = new MatchPeerProfileFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PEER_ID, peerId);
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

        ImageButton back = view.findViewById(R.id.ib_peer_profile_back);
        if (back != null) {
            back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        Bundle args = getArguments();
        String peerId = args != null ? args.getString(ARG_PEER_ID, "") : "";
        if (!UuidValidation.isUuid(peerId)) {
            showError(view);
            return;
        }

        io.execute(
                () -> {
                    try {
                        Map<String, ProfileRecord> map =
                                ProfileRemoteRepository.fetchProfilesByIds(Collections.singleton(peerId));
                        ProfileRecord pr = map.get(peerId);
                        safePost(
                                () -> {
                                    if (pr == null) {
                                        showError(view);
                                        return;
                                    }
                                    bindProfile(view, peerId, pr);
                                });
                    } catch (Exception e) {
                        safePost(() -> showError(view));
                    }
                });
    }

    private void bindProfile(@NonNull View root, @NonNull String peerId, @NonNull ProfileRecord pr) {
        TextView err = root.findViewById(R.id.tv_peer_error);
        if (err != null) {
            err.setVisibility(View.GONE);
        }
        View card = root.findViewById(R.id.card_peer_body);
        if (card != null) {
            card.setVisibility(View.VISIBLE);
        }

        TextView name = root.findViewById(R.id.tv_peer_display_name);
        TextView line = root.findViewById(R.id.tv_peer_location_line);
        TextView bio = root.findViewById(R.id.tv_peer_bio);
        ImageView avatar = root.findViewById(R.id.iv_peer_avatar);

        String display =
                pr.displayName != null && !pr.displayName.trim().isEmpty()
                        ? pr.displayName.trim()
                        : "Member";
        if (name != null) {
            name.setText(display);
        }
        if (line != null) {
            line.setText(
                    getString(
                            R.string.match_line_location_job,
                            pr.location != null ? pr.location : "",
                            pr.occupation != null ? pr.occupation : ""));
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
    }

    private void showError(@NonNull View root) {
        View card = root.findViewById(R.id.card_peer_body);
        if (card != null) {
            card.setVisibility(View.GONE);
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
        super.onDestroyView();
    }
}
