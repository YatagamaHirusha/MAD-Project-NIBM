package com.mad.cw.matching;

import com.mad.cw.R;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MatchSuggestionsFragment extends Fragment {

    private MatchSuggestionsAdapter adapter;
    private ExecutorService io;
    private volatile boolean viewDestroyed;

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
                                adapter.setPendingPeerId(suggestion.peerId);
                                Toast.makeText(ctx, R.string.match_request_demo_saved, Toast.LENGTH_LONG).show();
                                return;
                            }

                            if (!SupabaseRestClient.isConfigured() || !SessionStore.isLoggedIn()) {
                                MatchRequestLocalStore.setPending(ctx, suggestion.peerId, suggestion.rank);
                                adapter.setPendingPeerId(suggestion.peerId);
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
                                                            adapter.setPendingPeerId(suggestion.peerId);
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
        String pendingId = MatchRequestLocalStore.getPendingPeerId(requireContext());
        adapter.setPendingPeerId(pendingId != null ? pendingId : "");
        rv.setAdapter(adapter);

        List<MatchSuggestion> top = MatchScoring.computeTopMatches(requireContext(), 5);
        adapter.submitList(top);
        boolean has = !top.isEmpty();
        rv.setVisibility(has ? View.VISIBLE : View.GONE);
        if (empty != null) {
            empty.setVisibility(has ? View.GONE : View.VISIBLE);
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
