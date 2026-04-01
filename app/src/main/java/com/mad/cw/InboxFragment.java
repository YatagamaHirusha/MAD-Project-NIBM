package com.mad.cw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class InboxFragment extends Fragment {

    public InboxFragment() {}

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

        List<ConversationPreview> mock = new ArrayList<>();
        mock.add(new ConversationPreview("1", "Alex", "Hey! Want to compare our ECR-RS results over coffee?", "2m"));
        mock.add(new ConversationPreview("2", "Sam", "That intro you sent made me smile — free this weekend?", "1h"));
        mock.add(new ConversationPreview("3", "Jordan", "Photos don’t do your hiking pics justice 😊", "Yesterday"));

        ConversationAdapter adapter = new ConversationAdapter(mock, item ->
                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, MatchChatFragment.newInstance(item.peerId, item.peerName))
                        .addToBackStack(null)
                        .commit()
        );
        rv.setAdapter(adapter);
    }
}
