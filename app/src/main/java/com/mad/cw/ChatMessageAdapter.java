package com.mad.cw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PEER = 0;
    private static final int TYPE_ME = 1;

    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatMessageAdapter() {}

    public void submitList(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).fromUser ? TYPE_ME : TYPE_PEER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ME) {
            View v = inf.inflate(R.layout.item_message_me, parent, false);
            return new BubbleVH(v);
        }
        View v = inf.inflate(R.layout.item_message_peer, parent, false);
        return new BubbleVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((BubbleVH) holder).text.setText(messages.get(position).text);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class BubbleVH extends RecyclerView.ViewHolder {
        final TextView text;

        BubbleVH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_message_text);
        }
    }
}
