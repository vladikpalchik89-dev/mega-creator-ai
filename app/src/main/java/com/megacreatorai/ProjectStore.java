package com.megacreatorai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Локальное хранилище профиля, проектов, истории и дневных лимитов. */
public final class ProjectStore {

    private static final String PREF = "mega_creator_ai_v1";
    private static final String KEY_PROJECTS = "projects";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_USAGE = "usage";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_TARIFF = "tariff";
    private static final String KEY_DEMO = "demo_unlocked";
    private static final String KEY_NIGHT = "night";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_TOKEN = "server_token";

    private final SharedPreferences prefs;

    public ProjectStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------- профиль ----------

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getPhone() {
        return prefs.getString(KEY_PHONE, "");
    }

    public void saveProfile(String name, String email, String phone) {
        prefs.edit()
                .putString(KEY_NAME, name == null ? "" : name.trim())
                .putString(KEY_EMAIL, email == null ? "" : email.trim())
                .putString(KEY_PHONE, phone == null ? "" : phone.trim())
                .apply();
    }

    public Limits.Tariff getTariff() {
        // По умолчанию открыт максимум: всё бесплатно, без оплаты и демо-режима.
        return Limits.fromName(prefs.getString(KEY_TARIFF, "PREMIUM"), Limits.Tariff.PREMIUM);
    }

    public void setTariff(Limits.Tariff tariff) {
        prefs.edit().putString(KEY_TARIFF, tariff.name()).apply();
    }

    public boolean isDemoUnlocked() {
        return prefs.getBoolean(KEY_DEMO, false);
    }

    public void setDemoUnlocked(boolean value) {
        prefs.edit().putBoolean(KEY_DEMO, value).apply();
    }

    public boolean isNight() {
        return prefs.getBoolean(KEY_NIGHT, true);
    }

    public void setNight(boolean value) {
        prefs.edit().putBoolean(KEY_NIGHT, value).apply();
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER, "");
    }

    public void setServerUrl(String value) {
        prefs.edit().putString(KEY_SERVER, value == null ? "" : value.trim()).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public void setToken(String value) {
        prefs.edit().putString(KEY_TOKEN, value == null ? "" : value.trim()).apply();
    }

    /** Тариф, подтверждённый сервером (например, после вебхука оплаты). */
    public void applyServerTariff(String name) {
        Limits.Tariff parsed = Limits.fromName(name, getTariff());
        setTariff(parsed);
    }

    // ---------- проекты ----------

    public JSONArray listProjects() {
        return parseArray(prefs.getString(KEY_PROJECTS, "[]"));
    }

    public String addProject(String title, String category, String content) {
        String id = String.valueOf(System.currentTimeMillis())
                + "-" + (int) (Math.random() * 10000);
        JSONObject item = new JSONObject();
        try {
            item.put("id", id);
            item.put("title", title == null ? "Без названия" : title);
            item.put("category", category == null ? "Тексты" : category);
            item.put("content", content == null ? "" : content);
            item.put("favorite", false);
            item.put("created", System.currentTimeMillis());
        } catch (JSONException ignored) {
            return id;
        }
        JSONArray items = listProjects();
        items.put(item);
        if (items.length() > 200) {
            JSONArray trimmed = new JSONArray();
            for (int i = items.length() - 200; i < items.length(); i++) {
                trimmed.put(items.opt(i));
            }
            items = trimmed;
        }
        prefs.edit().putString(KEY_PROJECTS, items.toString()).apply();
        return id;
    }

    public void deleteProject(String id) {
        JSONArray source = listProjects();
        JSONArray kept = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) {
                kept.put(item);
            }
        }
        prefs.edit().putString(KEY_PROJECTS, kept.toString()).apply();
    }

    /** Заменяет проект с указанным id (например, после ответа сервера). */
    public void updateProject(String id, JSONObject updated) {
        JSONArray items = listProjects();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                try {
                    items.put(i, updated);
                    prefs.edit().putString(KEY_PROJECTS, items.toString()).apply();
                } catch (Exception ignored) {
                    // оставляем прежнюю запись
                }
                return;
            }
        }
    }

    public void toggleFavorite(String id) {
        JSONArray items = listProjects();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                try {
                    item.put("favorite", !item.optBoolean("favorite"));
                } catch (JSONException ignored) {
                    // оставляем как есть
                }
            }
        }
        prefs.edit().putString(KEY_PROJECTS, items.toString()).apply();
    }

    public JSONArray listHistory() {
        return parseArray(prefs.getString(KEY_HISTORY, "[]"));
    }

    public void addHistory(String title, String category) {
        JSONObject item = new JSONObject();
        try {
            item.put("title", title == null ? "Генерация" : title);
            item.put("category", category == null ? "Тексты" : category);
            item.put("created", System.currentTimeMillis());
        } catch (JSONException ignored) {
            return;
        }
        JSONArray items = listHistory();
        items.put(item);
        if (items.length() > 50) {
            JSONArray trimmed = new JSONArray();
            for (int i = items.length() - 50; i < items.length(); i++) {
                trimmed.put(items.opt(i));
            }
            items = trimmed;
        }
        prefs.edit().putString(KEY_HISTORY, items.toString()).apply();
    }

    public static String formatDate(long millis) {
        return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    // ---------- дневные лимиты ----------

    public int remaining(Limits.Feature feature) {
        int limit = Limits.dailyLimit(getTariff(), feature);
        return Math.max(0, limit - used(feature));
    }

    public int used(Limits.Feature feature) {
        JSONObject counts = usage().optJSONObject("counts");
        return counts == null ? 0 : counts.optInt(feature.name(), 0);
    }

    public boolean canGenerate(Limits.Feature feature) {
        return remaining(feature) > 0;
    }

    /** Списывает одну генерацию. Возвращает false, если лимит исчерпан. */
    public boolean consume(Limits.Feature feature) {
        JSONObject state = usage();
        JSONObject counts = state.optJSONObject("counts");
        if (counts == null) {
            counts = new JSONObject();
        }
        int limit = Limits.dailyLimit(getTariff(), feature);
        int current = counts.optInt(feature.name(), 0);
        if (current >= limit) {
            return false;
        }
        try {
            counts.put(feature.name(), current + 1);
            state.put("counts", counts);
            prefs.edit().putString(KEY_USAGE, state.toString()).apply();
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    /** Приводит локальный счётчик к значению, полученному от сервера. */
    public void syncRemaining(Limits.Feature feature, int remaining) {
        JSONObject state = usage();
        JSONObject counts = state.optJSONObject("counts");
        if (counts == null) {
            counts = new JSONObject();
        }
        int used = Limits.dailyLimit(getTariff(), feature) - Math.max(0, remaining);
        try {
            counts.put(feature.name(), Math.max(0, used));
            state.put("counts", counts);
            prefs.edit().putString(KEY_USAGE, state.toString()).apply();
        } catch (JSONException ignored) {
            // счётчик останется прежним
        }
    }

    private JSONObject usage() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        JSONObject state = parseObject(prefs.getString(KEY_USAGE, "{}"));
        if (!today.equals(state.optString("date"))) {
            state = new JSONObject();
            try {
                state.put("date", today);
                state.put("counts", new JSONObject());
            } catch (JSONException ignored) {
                // вернём пустой объект
            }
            prefs.edit().putString(KEY_USAGE, state.toString()).apply();
        }
        return state;
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    private static JSONArray parseArray(String raw) {
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static JSONObject parseObject(String raw) {
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
