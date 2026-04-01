package com.mad.cw;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MatchChatFragment extends Fragment {

    private static final String ARG_PEER_ID = "peer_id";
    private static final String ARG_PEER_NAME = "peer_name";

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatMessageAdapter adapter;
    private RecyclerView rv;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MatchChatFragment() {}

    public static MatchChatFragment newInstance(String peerId, String peerName) {
        MatchChatFragment f = new MatchChatFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PEER_ID, peerId);
        b.putString(ARG_PEER_NAME, peerName);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_match_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String peerName = getArguments() != null ? getArguments().getString(ARG_PEER_NAME, "Match") : "Match";

        TextView title = view.findViewById(R.id.tv_match_chat_title);
        title.setText(peerName);

        ImageView back = view.findViewById(R.id.iv_match_chat_back);
        back.setOnClickListener(v -> {
            if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        rv = view.findViewById(R.id.rv_match_messages);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatMessageAdapter();
        rv.setAdapter(adapter);
        seedMessages(peerName);
        adapter.submitList(new ArrayList<>(messages));

        EditText input = view.findViewById(R.id.et_match_message);
        FloatingActionButton send = view.findViewById(R.id.fab_match_send);
        send.setOnClickListener(v -> {
            String text = input.getText() != null ? input.getText().toString().trim() : "";
            if (text.isEmpty()) return;
            messages.add(new ChatMessage(text, true));
            adapter.submitList(new ArrayList<>(messages));
            rv.smoothScrollToPosition(messages.size() - 1);
            input.setText("");

            mainHandler.postDelayed(() -> {
                if (!isAdded()) return;
                messages.add(new ChatMessage(mockReply(), false));
                adapter.submitList(new ArrayList<>(messages));
                rv.smoothScrollToPosition(messages.size() - 1);
            }, 900);
        });
    }

    private void seedMessages(String peerName) {
        messages.clear();
        messages.add(new ChatMessage("Hi — I’m " + peerName + ". Nice to match with you!", false));
        messages.add(new ChatMessage("Hey! Good to meet you too.", true));
    }

    private static String mockReply() {
        return "Sounds good — let’s pick a time that works for both of us.";
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
