package com.mad.cw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class MatchSuggestionsAdapter extends RecyclerView.Adapter<MatchSuggestionsAdapter.Holder> {

    public interface OnOpenChatListener {
        void onOpenChat(@NonNull MatchSuggestion suggestion);
    }

    private final List<MatchSuggestion> items = new ArrayList<>();
    private final OnOpenChatListener openChatListener;

    public MatchSuggestionsAdapter(@NonNull OnOpenChatListener openChatListener) {
        this.openChatListener = openChatListener;
    }

    public void submitList(@NonNull List<MatchSuggestion> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_match_suggestion, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MatchSuggestion s = items.get(position);
        h.rank.setText(String.valueOf(s.rank));
        h.name.setText(s.displayName);
        h.percent.setText(h.itemView.getContext().getString(R.string.match_percent_label, s.matchPercent));
        h.locationLine.setText(
                h.itemView.getContext()
                        .getString(R.string.match_line_location_job, s.location, s.occupation));
        h.ecr.setText(MatchScoring.formatEcrLine(s.anxietyMean, s.avoidanceMean));
        h.progress.setProgress(s.matchPercent);
        h.sayHello.setOnClickListener(v -> openChatListener.onOpenChat(s));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView rank;
        final TextView name;
        final TextView percent;
        final TextView locationLine;
        final TextView ecr;
        final LinearProgressIndicator progress;
        final MaterialButton sayHello;

        Holder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.tv_match_rank);
            name = itemView.findViewById(R.id.tv_match_name);
            percent = itemView.findViewById(R.id.tv_match_percent);
            locationLine = itemView.findViewById(R.id.tv_match_location_line);
            ecr = itemView.findViewById(R.id.tv_match_ecr);
            progress = itemView.findViewById(R.id.progress_match_strength);
            sayHello = itemView.findViewById(R.id.btn_match_say_hello);
        }
    }
}
