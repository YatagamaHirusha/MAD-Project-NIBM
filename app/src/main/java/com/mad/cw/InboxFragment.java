package com.mad.cw;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mad.cw.adapters.MessageAdapter;
import com.mad.cw.models.Message;

import java.util.ArrayList;
import java.util.List;

public class InboxFragment extends Fragment {

    private RecyclerView rvChatMessages;
    private MessageAdapter adapter;
    private List<Message> chatMessages;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);

        rvChatMessages = view.findViewById(R.id.rv_chat_messages);
        EditText etMessage = view.findViewById(R.id.et_message);
        FloatingActionButton fabSend = view.findViewById(R.id.fab_send);
        ImageView ivCall = view.findViewById(R.id.iv_call);
        ImageView ivMore = view.findViewById(R.id.iv_more);
        ImageView ivHeaderAvatar = view.findViewById(R.id.iv_header_avatar);

        // Mock Chat Data (Focused on a single person: Tharushi)
        chatMessages = new ArrayList<>();
        chatMessages.add(new Message("Hi! I saw your profile.", "10:00 AM", false, android.R.drawable.ic_menu_myplaces, "Tharushi"));
        chatMessages.add(new Message("Oh hey! Thanks for reaching out. How are you?", "10:02 AM", true, 0, "Me"));
        chatMessages.add(new Message("මම හොඳින් ඉන්නවා. ඔයා මොකද කරන්නේ?", "10:05 AM", false, android.R.drawable.ic_menu_myplaces, "Tharushi"));
        chatMessages.add(new Message("ඔයාගේ විනෝදාංශ මොනවද?", "10:06 AM", false, android.R.drawable.ic_menu_myplaces, "Tharushi"));
        chatMessages.add(new Message("මම සින්දු අහන්න වගේම පොත් කියවන්න කැමතියි.", "10:10 AM", true, 0, "Me"));

        adapter = new MessageAdapter(chatMessages);
        rvChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChatMessages.setAdapter(adapter);
        
        // Scroll to the bottom of the chat
        rvChatMessages.scrollToPosition(chatMessages.size() - 1);

        // Send Message Functionality
        fabSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                addMessage(text, true);
                etMessage.setText("");
                
                // Professional Touch: Auto-reply mock
                new Handler().postDelayed(() -> {
                    addMessage("නියමයි! මමත් පොත් කියවන්න ගොඩක් කැමතියි.", false);
                }, 1500);
            }
        });

        // Mocking Header Actions
        ivCall.setOnClickListener(v -> Toast.makeText(getContext(), "Calling Tharushi...", Toast.LENGTH_SHORT).show());
        ivMore.setOnClickListener(v -> Toast.makeText(getContext(), "Opening settings...", Toast.LENGTH_SHORT).show());
        ivHeaderAvatar.setOnClickListener(v -> Toast.makeText(getContext(), "Viewing Tharushi's Profile", Toast.LENGTH_SHORT).show());

        return view;
    }

    private void addMessage(String text, boolean isSent) {
        chatMessages.add(new Message(text, "Just now", isSent, android.R.drawable.ic_menu_myplaces, isSent ? "Me" : "Tharushi"));
        adapter.notifyItemInserted(chatMessages.size() - 1);
        rvChatMessages.smoothScrollToPosition(chatMessages.size() - 1);
    }
}
