package com.mad.cw.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import com.google.ai.client.generativeai.Chat;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory chat transcript for the activity-scoped {@link AiCoachViewModel}, plus a Gemini {@link Chat}
 * session that keeps server-side multi-turn history until {@link #clearGeminiSession()} is called.
 */
public final class AiCoachRepository {

    /**
     * Model id for the Gemini Developer API (Google AI Studio). Legacy ids such as {@code
     * gemini-1.5-flash} return 404 on current v1beta — use a stable id from the
     * <a href="https://ai.google.dev/gemini-api/docs/models/gemini">Gemini model list</a> (this app defaults
     * to {@code gemini-2.5-flash}).
     */
    public static final String DEFAULT_MODEL_NAME = "gemini-2.5-flash";

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());

    private final String modelName;

    @Nullable private ChatFutures chatFutures;

    public AiCoachRepository() {
        this(DEFAULT_MODEL_NAME);
    }

    public AiCoachRepository(@NonNull String modelName) {
        this.modelName = modelName;
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public void ensureWelcome(@NonNull String welcomeText) {
        List<ChatMessage> current = messages.getValue();
        if (current == null || current.isEmpty()) {
            ArrayList<ChatMessage> next = new ArrayList<>();
            next.add(new ChatMessage(welcomeText, false));
            messages.setValue(next);
        }
    }

    public void addMessage(@NonNull ChatMessage message) {
        List<ChatMessage> cur = messages.getValue();
        ArrayList<ChatMessage> next = new ArrayList<>(cur != null ? cur : Collections.emptyList());
        next.add(message);
        messages.setValue(next);
    }

    public void resetToWelcome(@NonNull String welcomeText) {
        ArrayList<ChatMessage> next = new ArrayList<>();
        next.add(new ChatMessage(welcomeText, false));
        messages.setValue(next);
    }

    /** Drops the Gemini chat so the next send can recreate the model with a fresh system instruction. */
    public void clearGeminiSession() {
        chatFutures = null;
    }

    public boolean hasGeminiSession() {
        return chatFutures != null;
    }

    /**
     * Creates a {@link GenerativeModel} with the given system instruction and an empty history. Safe to call
     * again after {@link #clearGeminiSession()}.
     */
    public void initGeminiSession(@NonNull String apiKey, @NonNull String systemInstructionText) {
        if (apiKey.trim().isEmpty()) {
            chatFutures = null;
            return;
        }
        Content systemInstruction =
                new Content.Builder().addText(systemInstructionText.trim()).build();
        GenerativeModel model =
                new GenerativeModel(
                        modelName,
                        apiKey.trim(),
                        null,
                        null,
                        new RequestOptions(),
                        null,
                        null,
                        systemInstruction);
        Chat chat = model.startChat(Collections.emptyList());
        chatFutures = ChatFutures.from(chat);
    }

    /**
     * Sends a user turn to Gemini. The SDK appends this message and the model reply to the internal {@link
     * Chat} history. Must be called only when {@link #hasGeminiSession()} is true.
     */
    @NonNull
    public ListenableFuture<GenerateContentResponse> sendUserMessage(@NonNull String userText) {
        if (chatFutures == null) {
            throw new IllegalStateException("Gemini session not initialized");
        }
        Content.Builder userBuilder = new Content.Builder();
        userBuilder.setRole("user");
        userBuilder.addText(userText.trim());
        Content userContent = userBuilder.build();
        return chatFutures.sendMessage(userContent);
    }

    @Nullable
    public static String responseText(@Nullable GenerateContentResponse response) {
        if (response == null) {
            return null;
        }
        try {
            return response.getText();
        } catch (Exception ignored) {
            return null;
        }
    }
}
