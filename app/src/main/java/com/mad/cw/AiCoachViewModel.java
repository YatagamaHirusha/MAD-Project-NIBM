package com.mad.cw;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class AiCoachViewModel extends ViewModel {

    static final String WELCOME_AI =
            "Hi — I'm your MatchMind coach. Ask about dating, attachment styles, or your ECR-RS scores. "
                    + "Tap a suggestion below or type your own question.";

    private static final String[] MOCK_REPLIES = {
            "When you connect your AI backend, full answers can live here. For now: notice whether you seek closeness "
                    + "or space when stressed — that often maps to attachment style.",
            "A warm, specific opener beats a generic hello. Mention one detail from their profile or a shared interest.",
            "Reflect on what you need from a partner versus what you fear. That balance is core to secure attachment.",
    };

    private final AiCoachRepository repository = new AiCoachRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int mockReplyIndex = 0;

    public LiveData<List<ChatMessage>> getMessages() {
        return repository.getMessages();
    }

    public void ensureWelcome() {
        repository.ensureWelcome(WELCOME_AI);
    }

    public void sendUserMessage(String rawText) {
        if (rawText == null) {
            return;
        }
        String text = rawText.trim();
        if (text.isEmpty()) {
            return;
        }
        repository.addMessage(new ChatMessage(text, true));

        mainHandler.postDelayed(() -> {
            String reply = MOCK_REPLIES[mockReplyIndex % MOCK_REPLIES.length];
            mockReplyIndex++;
            repository.addMessage(new ChatMessage(reply, false));
        }, 650);
    }

    public void clearChat() {
        mainHandler.removeCallbacksAndMessages(null);
        repository.resetToWelcome(WELCOME_AI);
    }

    @Override
    protected void onCleared() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onCleared();
    }
}
