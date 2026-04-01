package com.mad.cw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {

    public interface OnConversationClickListener {
        void onConversationClick(ConversationPreview item);
    }

    private final List<ConversationPreview> items;
    private final OnConversationClickListener listener;

    public ConversationAdapter(List<ConversationPreview> items, OnConversationClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ConversationPreview c = items.get(position);
        holder.name.setText(c.peerName);
        holder.preview.setText(c.lastMessagePreview);
        holder.time.setText(c.timeLabel);
        String initial = c.peerName != null && !c.peerName.isEmpty()
                ? c.peerName.substring(0, 1).toUpperCase()
                : "?";
        holder.initial.setText(initial);
        holder.itemView.setOnClickListener(v -> listener.onConversationClick(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView initial;
        final TextView name;
        final TextView preview;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            initial = itemView.findViewById(R.id.tv_conversation_initial);
            name = itemView.findViewById(R.id.tv_conversation_name);
            preview = itemView.findViewById(R.id.tv_conversation_preview);
            time = itemView.findViewById(R.id.tv_conversation_time);
        }
    }
}
