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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public final class MatchSuggestionsAdapter extends RecyclerView.Adapter<MatchSuggestionsAdapter.Holder> {

    public interface OnSendRequestListener {
        void onSendRequest(@NonNull MatchSuggestion suggestion);
    }

    private final List<MatchSuggestion> items = new ArrayList<>();
    private final OnSendRequestListener sendRequestListener;
    @Nullable private String pendingPeerId = "";

    public MatchSuggestionsAdapter(@NonNull OnSendRequestListener sendRequestListener) {
        this.sendRequestListener = sendRequestListener;
    }

    /** Who currently has an outbound request (local mirror or demo). Empty means none. */
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

        Glide.with(h.photo).load(s.photoUrl).circleCrop().into(h.photo);

        boolean hasPending = pendingPeerId != null && !pendingPeerId.isEmpty();
        boolean thisRow = pendingPeerId != null && pendingPeerId.equals(s.peerId);
        if (hasPending && thisRow) {
            h.sendRequest.setEnabled(false);
            h.sendRequest.setText(R.string.match_request_sent);
        } else if (hasPending) {
            h.sendRequest.setEnabled(false);
            h.sendRequest.setText(R.string.match_request_send);
        } else {
            h.sendRequest.setEnabled(true);
            h.sendRequest.setText(R.string.match_request_send);
        }

        h.sendRequest.setOnClickListener(
                v -> {
                    if (!h.sendRequest.isEnabled()) {
                        return;
                    }
                    sendRequestListener.onSendRequest(s);
                });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView rank;
        final TextView name;
        final TextView percent;
        final TextView locationLine;
        final TextView ecr;
        final LinearProgressIndicator progress;
        final MaterialButton sendRequest;

        Holder(@NonNull View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.iv_match_photo);
            rank = itemView.findViewById(R.id.tv_match_rank);
            name = itemView.findViewById(R.id.tv_match_name);
            percent = itemView.findViewById(R.id.tv_match_percent);
            locationLine = itemView.findViewById(R.id.tv_match_location_line);
            ecr = itemView.findViewById(R.id.tv_match_ecr);
            progress = itemView.findViewById(R.id.progress_match_strength);
            sendRequest = itemView.findViewById(R.id.btn_match_send_request);
        }
    }
}
