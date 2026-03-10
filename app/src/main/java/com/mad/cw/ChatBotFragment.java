package com.mad.cw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ChatBotFragment extends Fragment {

    // Declare your UI variables here
    private RecyclerView rvAiChat;
    private EditText etAiMessage;
    private FloatingActionButton fabSend;

    public ChatBotFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 1. Inflate the layout for this fragment (This replaces setContentView!)
        return inflater.inflate(R.layout.fragment_chatbot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 2. IMPORTANT: Notice how we use 'view.findViewById' instead of just 'findViewById'
        rvAiChat = view.findViewById(R.id.rv_ai_chat);
        etAiMessage = view.findViewById(R.id.et_ai_message);
        fabSend = view.findViewById(R.id.fab_send);

        // 3. Set up your click listeners
        fabSend.setOnClickListener(v -> {
            String message = etAiMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                // TODO: Send message to your AI backend
                etAiMessage.setText(""); // Clear the input field
            }
        });
    }
}