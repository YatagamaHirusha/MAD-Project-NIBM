package com.mad.cw.supabase.core;

import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Reads unsigned JWT payload fields (no signature verification). Used only to recover {@code sub} when
 * GoTrue JSON omits {@code user.id}.
 */
public final class JwtPayload {

    private JwtPayload() {}

    @Nullable
    public static String optSub(@Nullable String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        String segment = parts[1];
        try {
            int rem = segment.length() % 4;
            if (rem > 0) {
                segment += "====".substring(rem);
            }
            byte[] bytes = Base64.decode(segment, Base64.URL_SAFE | Base64.NO_WRAP);
            JSONObject o = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            String sub = o.optString("sub", "");
            return sub.isEmpty() ? null : sub;
        } catch (Exception ignored) {
            return null;
        }
    }
}
