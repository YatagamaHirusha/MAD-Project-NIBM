package com.mad.cw.supabase;

import com.mad.cw.BuildConfig;

public final class SupabaseUrls {

    private SupabaseUrls() {}

    public static String projectBase() {
        String base = BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    public static String authBase() {
        return projectBase() + "/auth/v1/";
    }
}
