package com.mad.cw.supabase.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Row from {@code public.profiles} (editable slice for the profile screen).
 */
public final class ProfileRecord {

    public final String displayName;
    public final String email;
    public final String dateOfBirth;
    public final String bio;
    public final String location;
    public final String occupation;
    public final String gender;
    public final String targetGender;
    /** Optional HTTPS URL for profile photo (e.g. Supabase Storage). */
    public final String avatarUrl;

    public ProfileRecord(
            String displayName,
            String email,
            String dateOfBirth,
            String bio,
            String location,
            String occupation,
            String gender,
            String targetGender,
            String avatarUrl) {
        this.displayName = displayName != null ? displayName : "";
        this.email = email != null ? email : "";
        this.dateOfBirth = dateOfBirth != null ? dateOfBirth : "";
        this.bio = bio != null ? bio : "";
        this.location = location != null ? location : "";
        this.occupation = occupation != null ? occupation : "";
        this.gender = gender != null ? gender : "";
        this.targetGender = targetGender != null ? targetGender : "";
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
    }

    @NonNull
    public static ProfileRecord fromJsonObject(@NonNull JSONObject o) {
        return new ProfileRecord(
                optText(o, "display_name"),
                optText(o, "email"),
                optText(o, "date_of_birth"),
                optText(o, "bio"),
                optText(o, "location"),
                optText(o, "occupation"),
                optText(o, "gender"),
                optText(o, "target_gender"),
                optText(o, "avatar_url"));
    }

    private static String optText(@NonNull JSONObject o, @Nullable String key) {
        if (key == null || !o.has(key) || o.isNull(key)) {
            return "";
        }
        String s = o.optString(key, "");
        return s != null ? s.trim() : "";
    }
}
