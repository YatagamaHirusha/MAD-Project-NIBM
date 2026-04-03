package com.mad.cw.supabase;

import com.mad.cw.BuildConfig;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Minimal PostgREST client for Supabase (Java-friendly).
 * <p>
 * Uses the <strong>anon key</strong> only. Enable <strong>Row Level Security</strong> and policies on
 * every user table so the public key cannot read or write other users' rows.
 * </p>
 */
public final class SupabaseRestClient {

    private static volatile SupabaseRestClient instance;

    private final OkHttpClient httpClient;
    private final String restBaseUrl;

    private SupabaseRestClient() {
        String base = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
        String key = BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY.trim();

        if (base.endsWith("/")) {
            this.restBaseUrl = base + "rest/v1/";
        } else if (base.isEmpty()) {
            this.restBaseUrl = "";
        } else {
            this.restBaseUrl = base + "/rest/v1/";
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (!key.isEmpty()) {
            final String anon = key;
            Interceptor auth = chain -> {
                Request original = chain.request();
                Request.Builder b = original.newBuilder().header("apikey", anon);
                String existingAuth = original.header("Authorization");
                if (existingAuth != null && !existingAuth.isEmpty()) {
                    return chain.proceed(b.build());
                }
                String userJwt = SessionStore.getAccessToken();
                String bearer = (userJwt != null && !userJwt.isEmpty()) ? userJwt : anon;
                return chain.proceed(b.header("Authorization", "Bearer " + bearer).build());
            };
            builder.addInterceptor(auth);
        }
        this.httpClient = builder.build();
    }

    public static SupabaseRestClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseRestClient.class) {
                if (instance == null) {
                    instance = new SupabaseRestClient();
                }
            }
        }
        return instance;
    }

    public static boolean isConfigured() {
        String u = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
        String k = BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY.trim();
        return !u.isEmpty() && !k.isEmpty();
    }

    public OkHttpClient httpClient() {
        return httpClient;
    }

    /**
     * Base URL for PostgREST, e.g. {@code https://xxx.supabase.co/rest/v1/}
     */
    public String restBaseUrl() {
        return restBaseUrl;
    }

    /**
     * Build URL for a table name (no leading slash), e.g. {@code tableUrl("profiles")}.
     */
    public HttpUrl tableUrl(String table) {
        if (restBaseUrl.isEmpty() || table == null || table.isEmpty()) {
            return null;
        }
        return HttpUrl.parse(restBaseUrl + table.trim());
    }
}
