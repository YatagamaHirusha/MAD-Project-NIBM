package com.mad.cw.supabase.repositories;

import com.mad.cw.BuildConfig;
import com.mad.cw.supabase.core.PostgrestError;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;
import com.mad.cw.supabase.core.SupabaseUrls;

import java.io.File;
import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads profile JPEGs to Supabase Storage ({@code avatars/{userId}/profile.jpg}) using the signed-in
 * user's JWT. Requires {@code sql/supabase_storage_avatars.sql} on the project.
 */
public final class ProfileAvatarStorage {

    static final String BUCKET = "avatars";
    private static final String OBJECT_FILE = "profile.jpg";
    private static final MediaType JPEG = MediaType.parse("image/jpeg");

    private ProfileAvatarStorage() {}

    /** Public URL for the current user's avatar object (after upload). */
    public static String publicUrlForUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "";
        }
        return SupabaseUrls.projectBase()
                + "/storage/v1/object/public/"
                + BUCKET
                + "/"
                + userId
                + "/"
                + OBJECT_FILE;
    }

    /**
     * POSTs the JPEG bytes to Storage with upsert. Caller must have written {@code jpegFile} already
     * (e.g. {@link com.mad.cw.profile.AvatarStorage}).
     */
    public static String uploadProfileJpeg(File jpegFile) throws IOException {
        if (!SupabaseRestClient.isConfigured()) {
            throw new IOException("Supabase not configured");
        }
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        String token = SessionStore.getAccessToken();
        if (token == null || token.isEmpty()) {
            throw new IOException("Not signed in");
        }
        if (jpegFile == null || !jpegFile.exists() || jpegFile.length() == 0) {
            throw new IOException("No image file to upload");
        }

        String objectPath = userId + "/" + OBJECT_FILE;
        HttpUrl uploadUrl =
                HttpUrl.parse(SupabaseUrls.projectBase() + "/storage/v1/object/" + BUCKET + "/" + objectPath);
        if (uploadUrl == null) {
            throw new IOException("Invalid storage URL");
        }

        String anon = BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY.trim();
        RequestBody body = RequestBody.create(jpegFile, JPEG);
        Request request =
                new Request.Builder()
                        .url(uploadUrl)
                        .addHeader("apikey", anon)
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("x-upsert", "true")
                        .post(body)
                        .build();

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
            }
        }
        return publicUrlForUserId(userId);
    }
}
