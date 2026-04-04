package com.mad.cw.matching;

import com.mad.cw.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public final class MatchSuggestionsAdapter extends RecyclerView.Adapter<MatchSuggestionsAdapter.Holder> {

    public interface Listener {
        void onOpenProfile(@NonNull MatchSuggestion suggestion);
    }

    private final List<MatchSuggestion> items = new ArrayList<>();
    private final Listener listener;
    @Nullable private String pendingPeerId = "";

    public MatchSuggestionsAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setPendingPeerId(@Nullable String peerId) {
        this.pendingPeerId = peerId != null ? peerId : "";
        notifyDataSetChanged();
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
                .inflate(R.layout.item_match_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MatchSuggestion s = items.get(position);
        boolean pendingThis = pendingPeerId != null && pendingPeerId.equals(s.peerId);

        h.rank.setText(String.valueOf(s.rank));
        h.name.setText(s.displayName);
        h.percent.setText(h.itemView.getContext().getString(R.string.match_percent_label, s.matchPercent));
        h.locationLine.setText(
                h.itemView.getContext()
                        .getString(R.string.match_line_location_job, s.location, s.occupation));

        Glide.with(h.photo).load(s.photoUrl).circleCrop().into(h.photo);

        if (pendingThis) {
            h.pendingBadge.setVisibility(View.VISIBLE);
        } else {
            h.pendingBadge.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> listener.onOpenProfile(s));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView rank;
        final TextView name;
        final TextView percent;
        final TextView locationLine;
        final TextView pendingBadge;

        Holder(@NonNull View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.iv_match_photo);
            rank = itemView.findViewById(R.id.tv_match_rank);
            name = itemView.findViewById(R.id.tv_match_name);
            percent = itemView.findViewById(R.id.tv_match_percent);
            locationLine = itemView.findViewById(R.id.tv_match_location_line);
            pendingBadge = itemView.findViewById(R.id.tv_match_pending_badge);
        }
    }
}
