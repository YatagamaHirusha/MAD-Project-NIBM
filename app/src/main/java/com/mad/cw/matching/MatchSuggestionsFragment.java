package com.mad.cw.matching;

import com.mad.cw.R;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.repositories.MatchRequestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MatchSuggestionsFragment extends Fragment {

    private static final String TAG = "MatchSuggestions";
    private MatchSuggestionsAdapter adapter;
    private List<MatchSuggestion> latestTop = new ArrayList<>();
    private ExecutorService io;
    private volatile boolean viewDestroyed;
    private TextView tvOtherMatchesLabel;

    public MatchSuggestionsFragment() {}

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_match_suggestions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewDestroyed = false;
        io = Executors.newSingleThreadExecutor();

        ImageButton back = view.findViewById(R.id.ib_match_suggestions_back);
        if (back != null) {
            back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        RecyclerView rv = view.findViewById(R.id.rv_match_suggestions);
        LinearLayout empty = view.findViewById(R.id.layout_match_suggestions_empty);
        tvOtherMatchesLabel = view.findViewById(R.id.tv_other_matches_label);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter =
                new MatchSuggestionsAdapter(
                        suggestion -> {
                            Context ctx = requireContext();
                            String pending = MatchRequestLocalStore.getPendingPeerId(ctx);
                            if (pending != null && !pending.isEmpty() && !pending.equals(suggestion.peerId)) {
                                Toast.makeText(ctx, R.string.match_request_already_pending, Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (pending != null && pending.equals(suggestion.peerId)) {
                                return;
                            }

                            if (!UuidValidation.isUuid(suggestion.peerId)) {
                                MatchRequestLocalStore.setPending(ctx, suggestion.peerId, suggestion.rank);
                                applyListState();
                                Toast.makeText(ctx, R.string.match_request_demo_saved, Toast.LENGTH_LONG).show();
                                return;
                            }

                            if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
                                MatchRequestLocalStore.setPending(ctx, suggestion.peerId, suggestion.rank);
                                applyListState();
                                Toast.makeText(ctx, R.string.match_request_demo_saved, Toast.LENGTH_LONG).show();
                                return;
                            }

                            try {
                                io.execute(
                                        () -> {
                                            try {
                                                MatchRequestRepository.insertPending(
                                                        suggestion.peerId, suggestion.rank);
                                                safePost(
                                                        () -> {
                                                            MatchRequestLocalStore.setPending(
                                                                    ctx, suggestion.peerId, suggestion.rank);
                                                            applyListState();
                                                            Toast.makeText(
                                                                            ctx,
                                                                            R.string.match_request_sent_server,
                                                                            Toast.LENGTH_SHORT)
                                                                    .show();
                                                        });
                                            } catch (Exception e) {
                                                safePost(
                                                        () ->
                                                                Toast.makeText(
                                                                                ctx,
                                                                                getString(R.string.match_request_failed)
                                                                                        + (e.getMessage() != null
                                                                                                ? " "
                                                                                                        + e.getMessage()
                                                                                                : ""),
                                                                                Toast.LENGTH_LONG)
                                                                        .show());
                                            }
                                        });
                            } catch (RejectedExecutionException ignored) {
                                Toast.makeText(ctx, R.string.match_request_failed, Toast.LENGTH_SHORT).show();
                            }
                        });
        rv.setAdapter(adapter);

        TextView subtitle = view.findViewById(R.id.tv_match_suggestions_subtitle);
        boolean useMl =
                MatchMindApiClient.isConfigured()
                        && SupabaseRestClient.isConfigured()
                        && SessionStore.isLoggedIn()
                        && UuidValidation.isUuid(SessionStore.getUserId());
        if (subtitle != null) {
            subtitle.setText(
                    useMl
                            ? getString(R.string.match_suggestions_subtitle_ml)
                            : getString(R.string.match_suggestions_subtitle));
        }

        if (useMl) {
            rv.setVisibility(View.GONE);
            if (empty != null) {
                empty.setVisibility(View.VISIBLE);
                TextView t1 = empty.findViewById(R.id.tv_match_empty_title);
                TextView t2 = empty.findViewById(R.id.tv_match_empty_body);
                if (t1 != null) {
                    t1.setText(R.string.match_suggestions_loading_title);
                }
                if (t2 != null) {
                    t2.setText(R.string.match_suggestions_loading_body);
                }
            }
            final Context appCtx = requireContext().getApplicationContext();
            io.execute(
                    () -> {
                        try {
                            List<MatchSuggestion> list = MatchSuggestionsLoader.loadTopFromMlServer(5);
                            safePost(() -> applyLoadedMatches(list, rv, empty));
                        } catch (Exception e) {
                            Log.e(TAG, "ML match fetch failed", e);
                            safePost(
                                    () -> {
                                        String reason = e.getMessage() != null ? e.getMessage() : "unknown";
                                        Toast.makeText(
                                                        appCtx,
                                                        getString(R.string.match_ml_fallback) + " (" + reason + ")",
                                                        Toast.LENGTH_LONG)
                                                .show();
                                        List<MatchSuggestion> fallback =
                                                MatchScoring.computeTopMatches(appCtx, 5);
                                        if (subtitle != null) {
                                            subtitle.setText(R.string.match_suggestions_subtitle);
                                        }
                                        applyLoadedMatches(fallback, rv, empty);
                                    });
                        }
                    });
        } else {
            latestTop = MatchScoring.computeTopMatches(requireContext(), 5);
            boolean has = !latestTop.isEmpty();
            rv.setVisibility(has ? View.VISIBLE : View.GONE);
            if (empty != null) {
                empty.setVisibility(has ? View.GONE : View.VISIBLE);
            }
            applyListState();
        }
    }

    private void applyLoadedMatches(
            @NonNull List<MatchSuggestion> list, RecyclerView rv, @Nullable LinearLayout empty) {
        latestTop = list;
        boolean has = !list.isEmpty();
        rv.setVisibility(has ? View.VISIBLE : View.GONE);
        if (empty != null) {
            empty.setVisibility(has ? View.GONE : View.VISIBLE);
            if (!has) {
                TextView t1 = empty.findViewById(R.id.tv_match_empty_title);
                TextView t2 = empty.findViewById(R.id.tv_match_empty_body);
                if (t1 != null) {
                    t1.setText(R.string.match_suggestions_empty_title);
                }
                if (t2 != null) {
                    t2.setText(R.string.match_suggestions_empty_body);
                }
            }
        }
        applyListState();
    }

    private void applyListState() {
        if (adapter == null || getContext() == null) {
            return;
        }
        String pendingId = MatchRequestLocalStore.getPendingPeerId(requireContext());
        List<MatchSuggestion> ordered = new ArrayList<>();
        if (pendingId != null && !pendingId.isEmpty()) {
            MatchSuggestion pending = null;
            for (MatchSuggestion s : latestTop) {
                if (pendingId.equals(s.peerId)) {
                    pending = s;
                    break;
                }
            }
            if (pending != null) {
                ordered.add(pending);
            }
            for (MatchSuggestion s : latestTop) {
                if (pendingId.equals(s.peerId)) {
                    continue;
                }
                ordered.add(s);
            }
        } else {
            ordered.addAll(latestTop);
        }
        adapter.setPendingPeerId(pendingId != null ? pendingId : "");
        adapter.submitList(ordered);
        if (tvOtherMatchesLabel != null) {
            boolean showOther = pendingId != null && !pendingId.isEmpty() && ordered.size() > 1;
            tvOtherMatchesLabel.setVisibility(showOther ? View.VISIBLE : View.GONE);
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
