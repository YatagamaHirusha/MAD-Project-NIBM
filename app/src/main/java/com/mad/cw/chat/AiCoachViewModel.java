package com.mad.cw.chat;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.mad.cw.BuildConfig;
import com.mad.cw.R;
import com.mad.cw.assessment.AssessmentPreferences;
import com.mad.cw.auth.AuthValidation;
import com.mad.cw.profile.ProfilePreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI Coach: builds a personalized system prompt from {@link ProfilePreferences} and {@link
 * AssessmentPreferences}, drives {@link AiCoachRepository} + Gemini {@link com.google.ai.client.generativeai.Chat}
 * for multi-turn dialogue.
 */
public class AiCoachViewModel extends AndroidViewModel {

    public static final String WELCOME_AI =
            "Hi — I'm your MatchMind coach. Ask about dating, attachment, or difficult moments in relationships. "
                    + "I'll tailor ideas to your profile and ECR-RS results when we have them. "
                    + "Tap a suggestion below or type your own message.";

    private static final String CRISIS_BLOCK =
            "\n\nCRISIS AND SAFETY (non-negotiable):\n"
                    + "If the user shows signs of severe emotional crisis, domestic abuse, or self-harm (including "
                    + "suicidal thoughts, intent, or plan), you must:\n"
                    + "1) Respond with empathy and validation immediately.\n"
                    + "2) Encourage them to reach professional or emergency help right away.\n"
                    + "3) Clearly give the Sri Lanka national mental health / emotional support hotline: "
                    + "dial 1333 (Sri Lanka).\n"
                    + "4) Do not pretend you can intervene in an emergency; prioritize their safety.\n";

    private static final String STYLE_BLOCK =
            "\n\nCOACHING STYLE:\n"
                    + "- Be warm, non-judgmental, and grounded in attachment science and healthy relationship skills.\n"
                    + "- Keep replies concise; avoid long essays.\n"
                    + "- Always end with one small, concrete \"micro-step\" the user can try before the next message "
                    + "(a single sentence starting with something like \"Micro-step:\" or \"Try this:\").\n";

    private final AiCoachRepository repository = new AiCoachRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean sendInFlight = new AtomicBoolean(false);

    public AiCoachViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return repository.getMessages();
    }

    public boolean isSending() {
        return sendInFlight.get();
    }

    public void ensureWelcome() {
        repository.ensureWelcome(WELCOME_AI);
    }

    /**
     * Builds the system instruction from local prefs (name, age from DOB, ECR-RS means) plus safety and style
     * rules.
     */
    @NonNull
    public String buildSystemInstruction() {
        Application app = getApplication();
        String name = ProfilePreferences.get(app).getString(ProfilePreferences.KEY_DISPLAY_NAME, "").trim();
        if (name.isEmpty()) {
            name = "this user";
        }

        String dobRaw = ProfilePreferences.get(app).getString(ProfilePreferences.KEY_DOB, "");
        Calendar dob = AuthValidation.parseDob(dobRaw);
        String agePart;
        if (dob != null) {
            int age = AuthValidation.ageInYears(dob, Calendar.getInstance());
            agePart = age >= 0 ? String.valueOf(age) : "unknown";
        } else {
            agePart = "unknown (date of birth not set)";
        }

        double anxiety = AssessmentPreferences.getLastAnxietyScore(app);
        double avoidance = AssessmentPreferences.getLastAvoidanceScore(app);
        String anxietyPart =
                Double.isNaN(anxiety)
                        ? "not yet assessed (encourage completing the ECR-RS in the app when relevant)"
                        : String.format(Locale.US, "%.2f out of 7", anxiety);
        String avoidancePart =
                Double.isNaN(avoidance)
                        ? "not yet assessed (encourage completing the ECR-RS in the app when relevant)"
                        : String.format(Locale.US, "%.2f out of 7", avoidance);

        return "You are an empathetic relationship counselor for the app MatchMind. You are speaking with "
                + name
                + ", age "
                + agePart
                + ". Their ECR-RS anxiety mean score is "
                + anxietyPart
                + " and avoidance mean score is "
                + avoidancePart
                + " (1–7 scale, higher = more of that dimension). Use this to tailor tone and examples toward "
                + "their attachment-related patterns, without labeling them harshly or diagnosing clinically.\n"
                + CRISIS_BLOCK
                + STYLE_BLOCK;
    }

    public void sendUserMessage(String rawText) {
        if (rawText == null) {
            return;
        }
        final String text = rawText.trim();
        if (text.isEmpty()) {
            return;
        }
        if (!sendInFlight.compareAndSet(false, true)) {
            return;
        }

        repository.addMessage(new ChatMessage(text, true));

        String apiKey = BuildConfig.GEMINI_API_KEY != null ? BuildConfig.GEMINI_API_KEY.trim() : "";
        if (apiKey.isEmpty()) {
            sendInFlight.set(false);
            repository.addMessage(
                    new ChatMessage(
                            getApplication()
                                    .getString(
                                            R.string.ai_coach_missing_api_key,
                                            "Add GEMINI_API_KEY to local.properties and sync Gradle."),
                            false));
            return;
        }

        if (!repository.hasGeminiSession()) {
            repository.initGeminiSession(apiKey, buildSystemInstruction());
        }

        if (!repository.hasGeminiSession()) {
            sendInFlight.set(false);
            repository.addMessage(
                    new ChatMessage(
                            getApplication().getString(R.string.ai_coach_session_failed),
                            false));
            return;
        }

        final ListenableFuture<GenerateContentResponse> future;
        try {
            future = repository.sendUserMessage(text);
        } catch (Exception e) {
            sendInFlight.set(false);
            repository.addMessage(
                    new ChatMessage(
                            getApplication()
                                    .getString(R.string.ai_coach_send_failed, userFacingGeminiError(e)),
                            false));
            return;
        }

        Futures.addCallback(
                future,
                new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        mainHandler.post(
                                () -> {
                                    sendInFlight.set(false);
                                    String reply = AiCoachRepository.responseText(result);
                                    if (reply == null || reply.trim().isEmpty()) {
                                        reply = getApplication().getString(R.string.ai_coach_empty_reply);
                                    }
                                    repository.addMessage(new ChatMessage(reply.trim(), false));
                                });
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        mainHandler.post(
                                () -> {
                                    sendInFlight.set(false);
                                    repository.addMessage(
                                            new ChatMessage(
                                                    getApplication()
                                                            .getString(
                                                                    R.string.ai_coach_send_failed,
                                                                    userFacingGeminiError(t)),
                                                    false));
                                });
                    }
                },
                MoreExecutors.directExecutor());
    }

    /**
     * Strips SDK noise (e.g. kotlinx.serialization tails after HTTP errors) and maps common API failures to a
     * short explanation.
     */
    @NonNull
    private static String userFacingGeminiError(@Nullable Throwable t) {
        if (t == null) {
            return "";
        }
        String m = t.getMessage();
        if (m == null || m.isEmpty()) {
            return t.getClass().getSimpleName();
        }
        if (m.contains("NOT_FOUND")
                && (m.contains("models/") || m.contains("is not found") || m.contains("not supported"))) {
            return "The configured Gemini model is not available for your API key or API version. "
                    + "This build uses a current default; sync the latest app or check Google AI Studio → Models.";
        }
        int kotlinNoise = m.indexOf("kotlinx.serialization");
        if (kotlinNoise > 0) {
            m = m.substring(0, kotlinNoise).trim();
        }
        if (m.length() > 320) {
            m = m.substring(0, 320).trim() + "…";
        }
        return m;
    }

    public void clearChat() {
        mainHandler.removeCallbacksAndMessages(null);
        sendInFlight.set(false);
        repository.clearGeminiSession();
        repository.resetToWelcome(WELCOME_AI);
    }

    @Override
    protected void onCleared() {
        mainHandler.removeCallbacksAndMessages(null);
        sendInFlight.set(false);
        repository.clearGeminiSession();
        super.onCleared();
    }
}
