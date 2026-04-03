package com.mad.cw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MatchSuggestionsFragment extends Fragment {

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

        ImageButton back = view.findViewById(R.id.ib_match_suggestions_back);
        if (back != null) {
            back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        RecyclerView rv = view.findViewById(R.id.rv_match_suggestions);
        LinearLayout empty = view.findViewById(R.id.layout_match_suggestions_empty);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        MatchSuggestionsAdapter adapter =
                new MatchSuggestionsAdapter(
                        suggestion ->
                                requireActivity()
                                        .getSupportFragmentManager()
                                        .beginTransaction()
                                        .replace(
                                                R.id.fragment_container,
                                                MatchChatFragment.newInstance(
                                                        suggestion.peerId, suggestion.displayName))
                                        .addToBackStack(null)
                                        .commit());
        rv.setAdapter(adapter);

        List<MatchSuggestion> top = MatchScoring.computeTopMatches(requireContext(), 5);
        adapter.submitList(top);
        boolean has = !top.isEmpty();
        rv.setVisibility(has ? View.VISIBLE : View.GONE);
        if (empty != null) {
            empty.setVisibility(has ? View.GONE : View.VISIBLE);
        }
    }
}
