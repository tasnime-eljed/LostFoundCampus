package com.example.lostfoundcampus.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.lostfoundcampus.data.remote.dto.AuthSession;

/**
 * Session Supabase (jetons d'accès et de rafraîchissement) conservée dans les préférences
 * privées de l'app, inaccessibles aux autres applications.
 */
public final class SessionStore {

    public interface ExpiredListener {
        void onSessionExpired();
    }

    private static final String PREFS = "supabase_session";
    private static final String K_ACCESS = "access_token";
    private static final String K_REFRESH = "refresh_token";
    private static final String K_EXPIRES_AT = "expires_at";
    private static final String K_USER_ID = "user_id";
    private static final String K_EMAIL = "email";
    /** Marge avant expiration pour rafraîchir le jeton par anticipation. */
    private static final long REFRESH_MARGIN_S = 60;

    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable
    private ExpiredListener expiredListener;

    SessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void save(AuthSession s) {
        long expiresAt = s.expiresAt > 0 ? s.expiresAt : System.currentTimeMillis() / 1000 + s.expiresIn;
        SharedPreferences.Editor e = prefs.edit()
                .putString(K_ACCESS, s.accessToken)
                .putString(K_REFRESH, s.refreshToken)
                .putLong(K_EXPIRES_AT, expiresAt);
        if (s.user != null) {
            e.putString(K_USER_ID, s.user.id);
            if (s.user.email != null) e.putString(K_EMAIL, s.user.email);
        }
        e.apply();
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }

    public synchronized boolean isSignedIn() {
        return refreshToken() != null && userId() != null;
    }

    @Nullable
    public synchronized String accessToken() {
        return prefs.getString(K_ACCESS, null);
    }

    @Nullable
    public synchronized String refreshToken() {
        return prefs.getString(K_REFRESH, null);
    }

    @Nullable
    public synchronized String userId() {
        return prefs.getString(K_USER_ID, null);
    }

    @Nullable
    public synchronized String email() {
        return prefs.getString(K_EMAIL, null);
    }

    public synchronized boolean isExpiringSoon() {
        long expiresAt = prefs.getLong(K_EXPIRES_AT, 0);
        return expiresAt > 0 && System.currentTimeMillis() / 1000 >= expiresAt - REFRESH_MARGIN_S;
    }

    public void setExpiredListener(@Nullable ExpiredListener listener) {
        expiredListener = listener;
    }

    /** Appelé quand le rafraîchissement échoue : la session est perdue, il faut se reconnecter. */
    void notifyExpired() {
        main.post(() -> {
            if (expiredListener != null) expiredListener.onSessionExpired();
        });
    }
}
