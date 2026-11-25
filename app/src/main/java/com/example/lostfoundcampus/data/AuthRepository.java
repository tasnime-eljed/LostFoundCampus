package com.example.lostfoundcampus.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.data.remote.ApiErrors;
import com.example.lostfoundcampus.data.remote.SessionStore;
import com.example.lostfoundcampus.data.remote.SupabaseClient;
import com.example.lostfoundcampus.data.remote.dto.AuthSession;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Comptes : inscription, connexion, réinitialisation par code, changement de mot de passe. */
public final class AuthRepository {

    private static AuthRepository instance;
    private final Context app;
    private final SupabaseClient sb;
    /** E-mail et code déjà vérifiés : le code ne sert qu'une fois, un nouvel essai ne le revérifie pas. */
    @Nullable
    private String verifiedRecovery;

    private AuthRepository(Context context) {
        app = context.getApplicationContext();
        sb = SupabaseClient.get();
    }

    public static synchronized AuthRepository get(Context context) {
        if (instance == null) instance = new AuthRepository(context);
        return instance;
    }

    public boolean isSignedIn() {
        return sb.session.isSignedIn();
    }

    @Nullable
    public String currentUserId() {
        return sb.session.userId();
    }

    @Nullable
    public String currentEmail() {
        return sb.session.email();
    }

    public SessionStore session() {
        return sb.session;
    }

    public void signIn(String email, String password, ResultCallback<Void> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", normalize(email));
        body.put("password", password);
        enqueueSession(sb.auth.signIn(body), cb);
    }

    /** @return true dans le callback si l'adresse doit d'abord être confirmée par e-mail. */
    public void signUp(String fullName, String email, String password, ResultCallback<Boolean> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("full_name", fullName.trim());
        Map<String, Object> body = new HashMap<>();
        body.put("email", normalize(email));
        body.put("password", password);
        body.put("data", data);
        sb.auth.signUp(body).enqueue(new Callback<AuthSession>() {
            @Override
            public void onResponse(@NonNull Call<AuthSession> call, @NonNull Response<AuthSession> r) {
                if (!r.isSuccessful()) {
                    cb.onError(ApiErrors.fromResponse(app, r));
                    return;
                }
                AuthSession s = r.body();
                if (s != null && s.hasTokens()) {
                    sb.session.save(s);
                    cb.onSuccess(false);
                } else {
                    cb.onSuccess(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthSession> call, @NonNull Throwable t) {
                cb.onError(ApiErrors.fromThrowable(app, t));
            }
        });
    }

    public void sendRecoveryCode(String email, ResultCallback<Void> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", normalize(email));
        Calls.enqueue(app, sb.auth.recover(body), v -> null, cb);
    }

    /** Vérifie le code reçu par e-mail (ouvre une session), puis enregistre le nouveau mot de passe. */
    public void resetPassword(String email, String code, String newPassword, ResultCallback<Void> cb) {
        final String recovery = normalize(email) + "|" + code.trim();
        ResultCallback<Void> save = new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
                verifiedRecovery = null;
                cb.onSuccess(null);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        };
        if (recovery.equals(verifiedRecovery) && normalize(email).equals(currentEmail()) && isSignedIn()) {
            updatePassword(newPassword, save);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("type", "recovery");
        body.put("email", normalize(email));
        body.put("token", code.trim());
        enqueueSession(sb.auth.verify(body), new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
                verifiedRecovery = recovery;
                updatePassword(newPassword, save);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** Vérifie le mot de passe actuel (nouvelle connexion), puis enregistre le nouveau. */
    public void changePassword(String currentPassword, String newPassword, ResultCallback<Void> cb) {
        String email = currentEmail();
        if (email == null) {
            cb.onError(app.getString(com.example.lostfoundcampus.R.string.error_session_expired));
            return;
        }
        signIn(email, currentPassword, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
                updatePassword(newPassword, cb);
            }

            @Override
            public void onError(String message) {
                String wrong = app.getString(com.example.lostfoundcampus.R.string.login_error_credentials);
                cb.onError(message.equals(wrong)
                        ? app.getString(com.example.lostfoundcampus.R.string.error_password_current) : message);
            }
        });
    }

    private void updatePassword(String newPassword, ResultCallback<Void> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("password", newPassword);
        Calls.enqueue(app, sb.auth.updateUser("Bearer " + sb.session.accessToken(), body), u -> null, cb);
    }

    public void signOut() {
        String token = sb.session.accessToken();
        if (token != null) {
            sb.auth.logout("Bearer " + token).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                }
            });
        }
        verifiedRecovery = null;
        sb.session.clear();
        ProfileRepository.get(app).clearCache();
    }

    private void enqueueSession(Call<AuthSession> call, ResultCallback<Void> cb) {
        call.enqueue(new Callback<AuthSession>() {
            @Override
            public void onResponse(@NonNull Call<AuthSession> c, @NonNull Response<AuthSession> r) {
                if (r.isSuccessful() && r.body() != null && r.body().hasTokens()) {
                    sb.session.save(r.body());
                    cb.onSuccess(null);
                } else {
                    cb.onError(ApiErrors.fromResponse(app, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthSession> c, @NonNull Throwable t) {
                cb.onError(ApiErrors.fromThrowable(app, t));
            }
        });
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
