package com.mad.cw.supabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Parses PostgREST / Supabase error JSON bodies for clearer client messages.
 */
public final class PostgrestError {

    private PostgrestError() {}

    /** True when PostgREST cannot resolve a column (migration not applied on server). */
    public static boolean isUnknownColumnResponse(int httpCode, @Nullable String responseBody) {
        if (httpCode != 400 || responseBody == null || responseBody.isEmpty()) {
            return false;
        }
        return responseBody.contains("PGRST204")
                || (responseBody.contains("Could not find") && responseBody.contains("column"));
    }

    /** Short message for UI; falls back to raw body. */
    @NonNull
    public static String userMessage(int httpCode, @Nullable String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return httpCode + "";
        }
        try {
            JSONObject o = new JSONObject(responseBody);
            String msg = o.optString("message", "");
            if (!msg.isEmpty()) {
                return msg;
            }
            String hint = o.optString("hint", "");
            if (!hint.isEmpty()) {
                return hint;
            }
        } catch (org.json.JSONException ignored) {
            // fall through
        }
        if (responseBody.length() > 280) {
            return responseBody.substring(0, 280) + "…";
        }
        return responseBody;
    }
}
