package com.mad.cw.inbox;

import com.mad.cw.R;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;

public class InboxFragment extends Fragment {

    /** {@link MatchChatFragment} posts this so we remove the row and return to the list. */
    public static final String REQUEST_REMOVE_CONVERSATION = "inbox_remove_conversation";
    public static final String BUNDLE_PEER_ID = "peer_id";

    private final List<ConversationPreview> conversations = new ArrayList<>();
    private boolean mockSeeded;
    private ConversationAdapter conversationAdapter;

    public InboxFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity()
                .getSupportFragmentManager()
                .setFragmentResultListener(REQUEST_REMOVE_CONVERSATION, this, (requestKey, bundle) -> {
                    String peerId = bundle.getString(BUNDLE_PEER_ID);
                    if (peerId == null) {
                        return;
                    }
                    int removedIndex = -1;
                    for (int i = 0; i < conversations.size(); i++) {
                        if (peerId.equals(conversations.get(i).peerId)) {
                            removedIndex = i;
                            break;
                        }
                    }
                    if (removedIndex >= 0) {
                        conversations.remove(removedIndex);
                        if (conversationAdapter != null) {
                            conversationAdapter.notifyItemRemoved(removedIndex);
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inbox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rv_conversations);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        if (!mockSeeded) {
            mockSeeded = true;
            conversations.add(
                    new ConversationPreview(
                            "1",
                            "Alex",
                            "Hey! Want to compare our ECR-RS results over coffee?",
                            "2m",
                            "https://picsum.photos/seed/inboxalex/200/200"));
            conversations.add(
                    new ConversationPreview(
                            "2",
                            "Sam",
                            "That intro you sent made me smile — free this weekend?",
                            "1h",
                            "https://picsum.photos/seed/inboxsam/200/200"));
            conversations.add(
                    new ConversationPreview(
                            "3",
                            "Jordan",
                            "Photos don’t do your hiking pics justice 😊",
                            "Yesterday",
                            "https://picsum.photos/seed/inboxjordan/200/200"));
        }

        conversationAdapter =
                new ConversationAdapter(
                        conversations,
                        item ->
                                requireActivity()
                                        .getSupportFragmentManager()
                                        .beginTransaction()
                                        .replace(
                                                R.id.fragment_container,
                                                MatchChatFragment.newInstance(item.peerId, item.peerName))
                                        .addToBackStack(null)
                                        .commit());
        rv.setAdapter(conversationAdapter);
    }

    @Override
    public void onDestroyView() {
        conversationAdapter = null;
        super.onDestroyView();
    }
}
