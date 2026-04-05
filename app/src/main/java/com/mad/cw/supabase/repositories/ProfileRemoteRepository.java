package com.mad.cw.supabase.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mad.cw.matching.UuidValidation;
import com.mad.cw.supabase.core.PostgrestError;
import com.mad.cw.supabase.core.SessionStore;
import com.mad.cw.supabase.core.SupabaseRestClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Reads and writes {@code public.profiles} via PostgREST.
 */
public final class ProfileRemoteRepository {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** All profile form columns including {@code avatar_url} (requires migrations). */
    private static final String SELECT_FULL_WITH_AVATAR =
            "display_name,email,date_of_birth,bio,location,occupation,gender,target_gender,avatar_url";

    /** DBs with location & occupation but no {@code avatar_url} yet. */
    private static final String SELECT_FULL_NO_AVATAR =
            "display_name,email,date_of_birth,bio,location,occupation,gender,target_gender";

    /** Older DBs without {@code location} / {@code occupation}; gender columns always expected. */
    private static final String SELECT_SLIM =
            "display_name,email,date_of_birth,bio,gender,target_gender";

    private ProfileRemoteRepository() {}

    public static final class FetchedProfile {
        public final ProfileRecord record;
        /** {@code false} when no row exists yet for this user (empty GET result). */
        public final boolean rowExists;

        public FetchedProfile(ProfileRecord record, boolean rowExists) {
            this.record = record;
            this.rowExists = rowExists;
        }
    }

    /**
     * Loads {@code profiles} rows for the given ids (e.g. ML match candidates). Uses the signed-in user’s JWT;
     * RLS must allow reading peers who are {@code looking_for_partner} ({@code profiles_select_looking_others}).
     */
    @NonNull
    public static Map<String, ProfileRecord> fetchProfilesByIds(@NonNull Collection<String> ids)
            throws IOException {
        requireAccessToken();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String id : ids) {
            if (UuidValidation.isUuid(id)) {
                clean.add(id);
            }
        }
        if (clean.isEmpty()) {
            return Collections.emptyMap();
        }
        String inCsv = String.join(",", clean);
        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Map<String, ProfileRecord> a = fetchProfilesByIdsTrySelect(inCsv, "id," + SELECT_FULL_WITH_AVATAR, client);
        if (a != null) {
            return a;
        }
        Map<String, ProfileRecord> b = fetchProfilesByIdsTrySelect(inCsv, "id," + SELECT_FULL_NO_AVATAR, client);
        if (b != null) {
            return b;
        }
        Map<String, ProfileRecord> c = fetchProfilesByIdsTrySelect(inCsv, "id," + SELECT_SLIM, client);
        if (c == null) {
            throw new IOException("Could not query profiles for match candidates (check database schema).");
        }
        return c;
    }

    /**
     * @return map of id → profile, or {@code null} if PostgREST reports an unknown column (caller retries slimmer
     *     select).
     */
    @Nullable
    private static Map<String, ProfileRecord> fetchProfilesByIdsTrySelect(
            String commaSeparatedIds, String select, OkHttpClient client) throws IOException {
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("profiles");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("select", select)
                .addQueryParameter("id", "in.(" + commaSeparatedIds + ")")
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                int code = response.code();
                if (code == 400 && PostgrestError.isUnknownColumnResponse(code, resp)) {
                    return null;
                }
                throw new IOException(code + ": " + PostgrestError.userMessage(code, resp));
            }
            try {
                JSONArray arr = new JSONArray(resp);
                Map<String, ProfileRecord> map = new HashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String id = o.optString("id", "");
                    if (id.isEmpty()) {
                        continue;
                    }
                    map.put(id, ProfileRecord.fromJsonObject(o));
                }
                return map;
            } catch (JSONException e) {
                throw new IOException("Invalid profiles response", e);
            }
        }
    }

    /** Uses {@link SessionStore#getAccessToken()} for the current user. */
    public static FetchedProfile fetchMyProfile() throws IOException {
        String userId = requireUserId();
        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        FetchedProfile first = fetchProfilesGet(userId, SELECT_FULL_WITH_AVATAR, client);
        if (first != null) {
            return first;
        }
        FetchedProfile legacyFull = fetchProfilesGet(userId, SELECT_FULL_NO_AVATAR, client);
        if (legacyFull != null) {
            return legacyFull;
        }
        FetchedProfile second = fetchProfilesGet(userId, SELECT_SLIM, client);
        if (second != null) {
            return second;
        }
        throw new IOException(
                "Could not load profile: database schema may be outdated. Run sql migrations from the project.");
    }

    /**
     * @return null if the server rejected the request in a way that suggests unknown columns (retry with slim
     *     select).
     */
    private static FetchedProfile fetchProfilesGet(String userId, String select, OkHttpClient client)
            throws IOException {
        HttpUrl url = profilesUrlForGet(userId, select);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                int code = response.code();
                if (code == 400 && PostgrestError.isUnknownColumnResponse(code, resp)) {
                    return null;
                }
                throw new IOException(code + ": " + PostgrestError.userMessage(code, resp));
            }
            try {
                JSONArray arr = new JSONArray(resp);
                if (arr.length() == 0) {
                    return new FetchedProfile(
                            new ProfileRecord("", "", "", "", "", "", "", "", ""), false);
                }
                return new FetchedProfile(ProfileRecord.fromJsonObject(arr.getJSONObject(0)), true);
            } catch (JSONException e) {
                throw new IOException("Invalid profile response", e);
            }
        }
    }

    /**
     * Persists profile form fields. Uses PATCH when a row exists; otherwise POST upsert so a first save
     * still works if signup did not create a row.
     */
    public static void saveMyProfileForm(
            String displayName,
            String email,
            String dateOfBirth,
            String bio,
            String location,
            String occupation,
            String gender,
            String targetGender,
            String avatarUrl,
            boolean rowExists)
            throws IOException {
        String userId = requireUserId();
        String token = requireAccessToken();
        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();

        JSONObject full =
                profileFieldsJsonFull(
                        displayName,
                        email,
                        dateOfBirth,
                        bio,
                        location,
                        occupation,
                        gender,
                        targetGender,
                        avatarUrl);

        if (rowExists) {
            if (!tryPatch(userId, full, client)) {
                JSONObject noAvatar = profileFieldsJsonWithoutAvatar(full);
                if (!tryPatch(userId, noAvatar, client)) {
                    JSONObject slim = profileFieldsJsonWithoutLocationOccupation(noAvatar);
                    patchOrThrow(userId, slim, client);
                }
            }
        } else {
            if (!tryPostUpsert(userId, token, full, client)) {
                JSONObject noAvatar = profileFieldsJsonWithoutAvatar(full);
                if (!tryPostUpsert(userId, token, noAvatar, client)) {
                    JSONObject slim = profileFieldsJsonWithoutLocationOccupation(noAvatar);
                    postUpsertOrThrow(userId, token, slim, client);
                }
            }
        }
    }

    /**
     * Saves {@code profiles.avatar_url} after uploading the file to Storage. Requires column {@code
     * avatar_url} (see {@code sql/supabase_profiles_avatar_url.sql}).
     */
    public static void persistAvatarUrlToProfile(String avatarUrl, boolean rowExists) throws IOException {
        String userId = requireUserId();
        String token = requireAccessToken();
        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        String url = avatarUrl != null ? avatarUrl.trim() : "";
        JSONObject patch = new JSONObject();
        try {
            patch.put("avatar_url", url);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        if (rowExists) {
            if (!tryPatch(userId, patch, client)) {
                throw new IOException(
                        "Could not save photo URL: add column profiles.avatar_url "
                                + "(sql/supabase_profiles_avatar_url.sql).");
            }
        } else {
            if (!tryPostUpsert(userId, token, patch, client)) {
                throw new IOException(
                        "Could not save photo URL: add column profiles.avatar_url "
                                + "(sql/supabase_profiles_avatar_url.sql).");
            }
        }
    }

    /** @return true if successful */
    private static boolean tryPatch(String userId, JSONObject body, OkHttpClient client) throws IOException {
        HttpUrl url = profilesUrlForPatch(userId);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            }
            String err = response.body() != null ? response.body().string() : "";
            int code = response.code();
            if (code == 400 && PostgrestError.isUnknownColumnResponse(code, err)) {
                return false;
            }
            throw new IOException(code + ": " + PostgrestError.userMessage(code, err));
        }
    }

    private static void patchOrThrow(String userId, JSONObject body, OkHttpClient client) throws IOException {
        HttpUrl url = profilesUrlForPatch(userId);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return;
            }
            String err = response.body() != null ? response.body().string() : "";
            throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
        }
    }

    /** @return true if successful */
    private static boolean tryPostUpsert(String userId, String token, JSONObject body, OkHttpClient client)
            throws IOException {
        HttpUrl url = SupabaseRestClient.getInstance().tableUrl("profiles");
        if (url == null) {
            throw new IOException("Supabase URL not configured");
        }
        JSONObject row;
        try {
            row = new JSONObject(body.toString());
            row.put("id", userId);
            row.put("current_status", "looking_for_partner");
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        JSONArray arr = new JSONArray();
        arr.put(row);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(arr.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            }
            String err = response.body() != null ? response.body().string() : "";
            int code = response.code();
            if (code == 400 && PostgrestError.isUnknownColumnResponse(code, err)) {
                return false;
            }
            throw new IOException(code + ": " + PostgrestError.userMessage(code, err));
        }
    }

    private static void postUpsertOrThrow(String userId, String token, JSONObject body, OkHttpClient client)
            throws IOException {
        HttpUrl url = SupabaseRestClient.getInstance().tableUrl("profiles");
        if (url == null) {
            throw new IOException("Supabase URL not configured");
        }
        JSONObject row;
        try {
            row = new JSONObject(body.toString());
            row.put("id", userId);
            row.put("current_status", "looking_for_partner");
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        JSONArray arr = new JSONArray();
        arr.put(row);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(arr.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return;
            }
            String err = response.body() != null ? response.body().string() : "";
            throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
        }
    }

    private static JSONObject profileFieldsJsonFull(
            String displayName,
            String email,
            String dateOfBirth,
            String bio,
            String location,
            String occupation,
            String gender,
            String targetGender,
            String avatarUrl)
            throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("display_name", displayName != null ? displayName.trim() : "");
            o.put("email", email != null ? email.trim() : "");
            o.put("date_of_birth", dateOfBirth != null ? dateOfBirth.trim() : "");
            o.put("bio", bio != null ? bio.trim() : "");
            o.put("location", location != null ? location.trim() : "");
            o.put("occupation", occupation != null ? occupation.trim() : "");
            o.put("gender", gender != null ? gender.trim() : "");
            o.put("target_gender", targetGender != null ? targetGender.trim() : "");
            o.put("avatar_url", avatarUrl != null ? avatarUrl.trim() : "");
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        return o;
    }

    private static JSONObject profileFieldsJsonWithoutAvatar(JSONObject full) throws IOException {
        try {
            JSONObject o = new JSONObject(full.toString());
            o.remove("avatar_url");
            return o;
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static JSONObject profileFieldsJsonWithoutLocationOccupation(JSONObject full) throws IOException {
        try {
            JSONObject o = new JSONObject(full.toString());
            o.remove("location");
            o.remove("occupation");
            return o;
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static HttpUrl profilesUrlForGet(String userId, String select) throws IOException {
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("profiles");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        return base.newBuilder()
                .addQueryParameter("select", select)
                .addQueryParameter("id", "eq." + userId)
                .build();
    }

    private static HttpUrl profilesUrlForPatch(String userId) throws IOException {
        HttpUrl base = SupabaseRestClient.getInstance().tableUrl("profiles");
        if (base == null) {
            throw new IOException("Supabase URL not configured");
        }
        return base.newBuilder().addQueryParameter("id", "eq." + userId).build();
    }

    private static String requireUserId() throws IOException {
        String userId = SessionStore.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new IOException("Not signed in");
        }
        return userId;
    }

    private static String requireAccessToken() throws IOException {
        String token = SessionStore.getAccessToken();
        if (token == null || token.isEmpty()) {
            throw new IOException("Not signed in");
        }
        return token;
    }

    /** Uses {@link SessionStore#getAccessToken()} — must run after session is saved. */
    public static void upsertMyProfile(String userId, String displayName, String email) throws IOException {
        String token = requireAccessToken();
        upsertMyProfile(token, userId, displayName, email, null, null, null);
    }

    /** 4-arg upsert; same as full upsert with no DOB or gender. */
    public static void upsertMyProfile(String accessToken, String userId, String displayName, String email)
            throws IOException {
        upsertMyProfile(accessToken, userId, displayName, email, null, null, null);
    }

    /** 6-arg upsert without location (same as 7-arg with {@code null} location). */
    public static void upsertMyProfile(
            String accessToken,
            String userId,
            String displayName,
            String email,
            String dateOfBirthMmDdYyyy,
            String gender)
            throws IOException {
        upsertMyProfile(accessToken, userId, displayName, email, dateOfBirthMmDdYyyy, gender, null);
    }

    /**
     * Upsert with an explicit JWT (e.g. right after signup) so the request does not depend on prefs timing
     * and can run on a background thread.
     *
     * @param dateOfBirthMmDdYyyy optional {@code MM/dd/yyyy}
     * @param gender optional stored gender value ({@code female}, {@code male}, …)
     * @param location optional city (same canonical values as profile / matching)
     */
    public static void upsertMyProfile(
            String accessToken,
            String userId,
            String displayName,
            String email,
            String dateOfBirthMmDdYyyy,
            String gender,
            @Nullable String location)
            throws IOException {
        HttpUrl url = SupabaseRestClient.getInstance().tableUrl("profiles");
        if (url == null) {
            throw new IOException("Supabase URL not configured");
        }
        JSONObject row = new JSONObject();
        try {
            row.put("id", userId);
            row.put("display_name", displayName);
            row.put("email", email);
            row.put("current_status", "looking_for_partner");
            if (dateOfBirthMmDdYyyy != null && !dateOfBirthMmDdYyyy.trim().isEmpty()) {
                row.put("date_of_birth", dateOfBirthMmDdYyyy.trim());
            }
            if (gender != null && !gender.trim().isEmpty()) {
                row.put("gender", gender.trim());
            }
            if (location != null && !location.trim().isEmpty()) {
                row.put("location", location.trim());
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        JSONArray body = new JSONArray();
        body.put(row);

        OkHttpClient client = SupabaseRestClient.getInstance().httpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException(response.code() + ": " + PostgrestError.userMessage(response.code(), err));
            }
        }
    }
}
