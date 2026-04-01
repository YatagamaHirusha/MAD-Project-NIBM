package com.mad.cw;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local storage for lifestyle / interest vectors (same column names and JSON-array encoding as training CSV).
 */
public final class UserInterestStore {

    private static final String PREFS_NAME = "user_interests";

    public static final String KEY_LOCATION = "location";
    public static final String KEY_OCCUPATION = "occupation";

    private UserInterestStore() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String location, String occupation, Map<String, List<String>> interestsByColumn) {
        SharedPreferences.Editor e = prefs(context).edit();
        e.putString(KEY_LOCATION, location != null ? location.trim() : "");
        e.putString(KEY_OCCUPATION, occupation != null ? occupation.trim() : "");
        for (Map.Entry<String, List<String>> en : interestsByColumn.entrySet()) {
            JSONArray arr = new JSONArray();
            List<String> list = en.getValue();
            if (list != null) {
                for (String s : list) {
                    if (s != null && !s.isEmpty()) {
                        arr.put(s);
                    }
                }
            }
            e.putString(en.getKey(), arr.toString());
        }
        e.apply();
    }

    /** Returns stored JSON array strings keyed by column name (empty string if missing). */
    public static Map<String, List<String>> loadInterestMap(Context context) {
        Map<String, List<String>> out = new HashMap<>();
        SharedPreferences p = prefs(context);
        for (InterestTaxonomy.Category cat : InterestTaxonomy.CATEGORIES) {
            out.put(cat.columnName, readJsonArray(p.getString(cat.columnName, null)));
        }
        return out;
    }

    public static String loadLocation(Context context) {
        return prefs(context).getString(KEY_LOCATION, "");
    }

    public static String loadOccupation(Context context) {
        return prefs(context).getString(KEY_OCCUPATION, "");
    }

    private static List<String> readJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = new JSONArray(json);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
            return list;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public static boolean hasSavedProfile(Context context) {
        return prefs(context).contains(KEY_LOCATION) && prefs(context).contains(KEY_OCCUPATION);
    }
}
