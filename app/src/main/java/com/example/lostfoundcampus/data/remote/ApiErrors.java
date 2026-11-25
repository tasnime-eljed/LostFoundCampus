package com.example.lostfoundcampus.data.remote;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.remote.dto.ApiErrorBody;

import java.io.IOException;

import retrofit2.Response;

/** Traduit les erreurs réseau, Auth, PostgREST et Storage en messages français actionnables. */
public final class ApiErrors {

    private ApiErrors() {
    }

    @NonNull
    public static String fromThrowable(Context c, Throwable t) {
        if (t instanceof IOException) return c.getString(R.string.error_network);
        return c.getString(R.string.error_generic);
    }

    @NonNull
    public static String fromResponse(Context c, Response<?> response) {
        String text = "";
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                ApiErrorBody body = SupabaseClient.get().gson.fromJson(raw, ApiErrorBody.class);
                text = body == null ? raw.toLowerCase(java.util.Locale.ROOT) : body.text();
            }
        } catch (Exception ignored) {
            // corps illisible : on se rabat sur le code HTTP
        }
        return c.getString(map(response.code(), text));
    }

    @StringRes
    static int map(int http, String t) {
        if (t.contains("invalid_credentials") || t.contains("invalid login credentials")) return R.string.login_error_credentials;
        if (t.contains("email_not_confirmed") || t.contains("email not confirmed")) return R.string.error_email_not_confirmed;
        if (t.contains("user_already_exists") || t.contains("already registered") || t.contains("email_exists")) return R.string.error_user_exists;
        if (t.contains("weak_password") || t.contains("password should be")) return R.string.error_password_short;
        if (t.contains("otp_expired") || t.contains("token has expired") || t.contains("invalid token")) return R.string.error_code_invalid;
        if (t.contains("rate_limit") || t.contains("rate limit") || http == 429) return R.string.error_rate_limit;
        if (t.contains("database error saving new user") || t.contains("university_email")) return R.string.error_email_university;
        if (t.contains("signup_disabled") || t.contains("signups not allowed")) return R.string.error_signup_disabled;
        if (t.contains("dans le futur")) return R.string.error_date_future;
        if (t.contains("payload too large") || t.contains("maximum allowed size") || http == 413) return R.string.error_photo_too_large;
        if (t.contains("row-level security") || http == 403) return R.string.error_forbidden;
        if (http == 401) return R.string.error_session_expired;
        if (http >= 500) return R.string.error_server;
        return R.string.error_generic;
    }

    public static boolean isConflict(Response<?> response) {
        return response.code() == 409;
    }
}
