package com.example.lostfoundcampus.util;

import android.content.Context;
import android.util.Patterns;

import androidx.annotation.Nullable;

import com.example.lostfoundcampus.R;

import java.util.Locale;

public final class Validators {

    /** Domaine des adresses universitaires acceptées (configuration, pas un texte affiché). */
    public static final String UNIVERSITY_DOMAIN = "universite.tn";

    private Validators() {
    }

    public static boolean isUniversityEmail(@Nullable CharSequence email) {
        if (email == null) return false;
        String e = email.toString().trim().toLowerCase(Locale.ROOT);
        return Patterns.EMAIL_ADDRESS.matcher(e).matches() && e.endsWith("@" + UNIVERSITY_DOMAIN);
    }

    public static boolean isPasswordLongEnough(Context c, @Nullable CharSequence password) {
        int min = c.getResources().getInteger(R.integer.lf_password_min_length);
        return password != null && password.length() >= min;
    }

    public static boolean isBlank(@Nullable CharSequence s) {
        return s == null || s.toString().trim().isEmpty();
    }
}
