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

import com.google.android.material.button.MaterialButton;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.repositories.MatchRequestRepository;
import com.mad.cw.supabase.repositories.MatchSuggestionBatchRepository;

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
    @Nullable private MaterialButton btnMatchAction;
    private volatile boolean mlFetchInProgress;

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
        btnMatchAction = view.findViewById(R.id.btn_match_action);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter =
                new MatchSuggestionsAdapter(
                        new MatchSuggestionsAdapter.Listener() {
                            @Override
                            public void onOpenProfile(@NonNull MatchSuggestion suggestion) {
                                if (!isAdded()) {
                                    return;
                                }
                                requireActivity()
                                        .getSupportFragmentManager()
                                        .beginTransaction()
                                        .replace(
                                                R.id.fragment_container,
                                                MatchPeerProfileFragment.newInstance(suggestion.peerId))
                                        .addToBackStack(null)
                                        .commit();
                            }

                            @Override
                            public void onSendRequest(@NonNull MatchSuggestion suggestion) {
                                Context ctx = requireContext();
                                String pending = MatchRequestLocalStore.getPendingPeerId(ctx);
                                if (pending != null && !pending.isEmpty() && !pending.equals(suggestion.peerId)) {
                                    Toast.makeText(ctx, R.string.match_request_already_pending, Toast.LENGTH_LONG)
                                            .show();
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
                            }
                        });
        rv.setAdapter(adapter);

        TextView subtitle = view.findViewById(R.id.tv_match_suggestions_subtitle);
        boolean useMl = useMl();
        if (subtitle != null) {
            subtitle.setText(
                    useMl
                            ? getString(R.string.match_suggestions_subtitle_ml)
                            : getString(R.string.match_suggestions_subtitle));
        }

        if (btnMatchAction != null) {
            btnMatchAction.setVisibility(useMl ? View.VISIBLE : View.GONE);
            if (useMl) {
                btnMatchAction.setOnClickListener(v -> runMlFetchReplace());
            }
        }

        if (useMl) {
            List<MatchCacheStore.CachedMatch> cached = MatchCacheStore.load(requireContext());
            if (!cached.isEmpty()) {
                applyLoadedMatches(MatchSuggestionsLoader.fromCachedMatches(cached), rv, empty);
                updateActionButton();
            } else {
                rv.setVisibility(View.GONE);
                if (empty != null) {
                    empty.setVisibility(View.VISIBLE);
                    TextView t1 = empty.findViewById(R.id.tv_match_empty_title);
                    TextView t2 = empty.findViewById(R.id.tv_match_empty_body);
                    if (t1 != null) {
                        t1.setText(R.string.match_suggestions_empty_title);
                    }
                    if (t2 != null) {
                        t2.setText(R.string.match_suggestions_empty_body);
                    }
                }
                updateActionButton();
                final Context appCtx = requireContext().getApplicationContext();
                io.execute(
                        () -> {
                            try {
                                List<String> remote = MatchSuggestionBatchRepository.fetchLatestSuggestionUserIds();
                                if (remote.isEmpty()) {
                                    return;
                                }
                                List<MatchSuggestion> restored =
                                        MatchSuggestionsLoader.fromOrderedPeerIds(remote);
                                if (restored.isEmpty()) {
                                    return;
                                }
                                MatchCacheStore.saveFromMatchSuggestions(appCtx, restored);
                                safePost(() -> applyLoadedMatches(restored, rv, empty));
                            } catch (Exception e) {
                                Log.d(TAG, "Remote suggestion batch restore skipped", e);
                            } finally {
                                safePost(() -> updateActionButton());
                            }
                        });
            }
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

    private boolean useMl() {
        return MatchMindApiClient.isConfigured()
                && SupabaseRestClient.isConfigured()
                && SessionStore.isLoggedIn()
                && UuidValidation.isUuid(SessionStore.getUserId());
    }

    private void updateActionButton() {
        if (btnMatchAction == null || !useMl()) {
            return;
        }
        boolean has = latestTop != null && !latestTop.isEmpty();
        btnMatchAction.setText(has ? R.string.match_action_refresh : R.string.match_action_find);
        btnMatchAction.setEnabled(!mlFetchInProgress);
    }

    private void runMlFetchReplace() {
        if (!useMl() || getView() == null || io == null) {
            return;
        }
        if (mlFetchInProgress) {
            return;
        }
        mlFetchInProgress = true;
        updateActionButton();

        View view = getView();
        RecyclerView rv = view.findViewById(R.id.rv_match_suggestions);
        LinearLayout empty = view.findViewById(R.id.layout_match_suggestions_empty);

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
                        List<String> ids = MatchSuggestionsLoader.peerIdsFromSuggestions(list);
                        try {
                            MatchCacheStore.saveFromMatchSuggestions(appCtx, list);
                        } catch (Exception e) {
                            Log.w(TAG, "Local match cache save failed", e);
                        }
                        if (!ids.isEmpty()) {
                            try {
                                MatchSuggestionBatchRepository.insertSuggestionBatch(ids);
                            } catch (Exception e) {
                                Log.w(TAG, "Suggestion batch sync failed", e);
                            }
                        }
                        safePost(
                                () -> {
                                    mlFetchInProgress = false;
                                    updateActionButton();
                                    applyLoadedMatches(list, rv, empty);
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "ML match fetch failed", e);
                        safePost(
                                () -> {
                                    mlFetchInProgress = false;
                                    updateActionButton();
                                    String reason = e.getMessage() != null ? e.getMessage() : "unknown";
                                    Toast.makeText(
                                                    appCtx,
                                                    getString(R.string.match_ml_fallback) + " (" + reason + ")",
                                                    Toast.LENGTH_LONG)
                                            .show();
                                    TextView subtitle = view.findViewById(R.id.tv_match_suggestions_subtitle);
                                    if (subtitle != null) {
                                        subtitle.setText(R.string.match_suggestions_subtitle);
                                    }
                                    List<MatchSuggestion> fallback =
                                            MatchScoring.computeTopMatches(requireContext(), 5);
                                    applyLoadedMatches(fallback, rv, empty);
                                });
                    }
                });
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
    public void onResume() {
        super.onResume();
        applyListState();
    }

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        if (io != null) {
            io.shutdown();
            io = null;
        }
        btnMatchAction = null;
        super.onDestroyView();
    }
}
