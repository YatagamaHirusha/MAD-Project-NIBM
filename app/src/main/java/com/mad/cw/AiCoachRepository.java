package com.mad.cw;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds AI Coach chat messages for the lifetime of {@link AiCoachViewModel} (activity-scoped).
 */
public class AiCoachRepository {

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public void ensureWelcome(String welcomeText) {
        List<ChatMessage> current = messages.getValue();
        if (current == null || current.isEmpty()) {
            ArrayList<ChatMessage> next = new ArrayList<>();
            next.add(new ChatMessage(welcomeText, false));
            messages.setValue(next);
        }
    }

    public void addMessage(ChatMessage message) {
        List<ChatMessage> cur = messages.getValue();
        ArrayList<ChatMessage> next = new ArrayList<>(cur != null ? cur : Collections.emptyList());
        next.add(message);
        messages.setValue(next);
    }

    public void resetToWelcome(String welcomeText) {
        ArrayList<ChatMessage> next = new ArrayList<>();
        next.add(new ChatMessage(welcomeText, false));
        messages.setValue(next);
    }
}
