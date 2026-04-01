package com.mad.cw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class ChatBotFragment extends Fragment {

    private RecyclerView rvAiChat;
    private EditText etAiMessage;
    private FloatingActionButton fabSend;
    private ImageView ivClearChat;

    private ChatMessageAdapter adapter;
    private AiCoachViewModel viewModel;

    public ChatBotFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chatbot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AiCoachViewModel.class);
        viewModel.ensureWelcome();

        rvAiChat = view.findViewById(R.id.rv_ai_chat);
        etAiMessage = view.findViewById(R.id.et_ai_message);
        fabSend = view.findViewById(R.id.fab_send);
        ivClearChat = view.findViewById(R.id.iv_clear_chat);

        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        rvAiChat.setLayoutManager(lm);
        rvAiChat.setItemAnimator(null);

        adapter = new ChatMessageAdapter();
        rvAiChat.setAdapter(adapter);

        viewModel.getMessages().observe(getViewLifecycleOwner(), this::onMessagesChanged);

        fabSend.setOnClickListener(v -> {
            String text = etAiMessage.getText() != null ? etAiMessage.getText().toString() : "";
            viewModel.sendUserMessage(text);
            etAiMessage.setText("");
        });

        ivClearChat.setOnClickListener(v -> {
            viewModel.clearChat();
            Toast.makeText(requireContext(), "Chat cleared", Toast.LENGTH_SHORT).show();
        });

        Chip chip1 = view.findViewById(R.id.chip_prompt_1);
        Chip chip2 = view.findViewById(R.id.chip_prompt_2);
        Chip chip3 = view.findViewById(R.id.chip_prompt_3);
        chip1.setOnClickListener(v -> submitChipPrompt(chip1.getText().toString()));
        chip2.setOnClickListener(v -> submitChipPrompt(chip2.getText().toString()));
        chip3.setOnClickListener(v -> submitChipPrompt(chip3.getText().toString()));
    }

    private void onMessagesChanged(List<ChatMessage> messages) {
        if (messages == null) {
            return;
        }
        adapter.submitList(messages);
        if (!messages.isEmpty()) {
            rvAiChat.smoothScrollToPosition(messages.size() - 1);
        }
    }

    private void submitChipPrompt(String prompt) {
        etAiMessage.setText(prompt);
        viewModel.sendUserMessage(prompt);
        etAiMessage.setText("");
    }
}
