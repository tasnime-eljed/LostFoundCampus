package com.example.lostfoundcampus.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.data.remote.SupabaseClient;
import com.example.lostfoundcampus.data.remote.dto.ProfileDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Profils (nom, e-mail universitaire, photo), avec un petit cache mémoire. */
public final class ProfileRepository {

    private static ProfileRepository instance;
    private final Context app;
    private final SupabaseClient sb;
    private final Map<String, User> cache = new HashMap<>();

    private ProfileRepository(Context context) {
        app = context.getApplicationContext();
        sb = SupabaseClient.get();
    }

    public static synchronized ProfileRepository get(Context context) {
        if (instance == null) instance = new ProfileRepository(context);
        return instance;
    }

    @Nullable
    public User cachedMe() {
        String id = sb.session.userId();
        return id == null ? null : cache.get(id);
    }

    @Nullable
    public User cached(String uid) {
        return cache.get(uid);
    }

    public void clearCache() {
        cache.clear();
    }

    public void loadMe(ResultCallback<User> cb) {
        String id = sb.session.userId();
        if (id == null) {
            cb.onError(app.getString(R.string.error_session_expired));
            return;
        }
        loadUser(id, true, cb);
    }

    public void loadUser(String uid, boolean forceRefresh, ResultCallback<User> cb) {
        User cached = cache.get(uid);
        if (cached != null && !forceRefresh) {
            cb.onSuccess(cached);
            return;
        }
        Calls.enqueue(app, sb.rest.profiles("eq." + uid), list -> {
            if (list == null || list.isEmpty()) throw new IllegalStateException("Profil introuvable");
            User u = Mappers.user(list.get(0));
            cache.put(u.uid, u);
            return u;
        }, cb);
    }

    /** Charge plusieurs profils en une requête (id=in.(…)), en réutilisant le cache. */
    public void loadUsers(Collection<String> ids, ResultCallback<Map<String, User>> cb) {
        Map<String, User> found = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            if (id == null) continue;
            User u = cache.get(id);
            if (u != null) found.put(id, u);
            else if (!missing.contains(id)) missing.add(id);
        }
        if (missing.isEmpty()) {
            cb.onSuccess(found);
            return;
        }
        Calls.enqueue(app, sb.rest.profiles("in.(" + android.text.TextUtils.join(",", missing) + ")"), list -> {
            if (list != null) {
                for (ProfileDto d : list) {
                    User u = Mappers.user(d);
                    cache.put(u.uid, u);
                    found.put(u.uid, u);
                }
            }
            return found;
        }, cb);
    }

    public void updateMe(@Nullable String fullName, @Nullable String photoPath, boolean setPhoto, ResultCallback<User> cb) {
        String id = sb.session.userId();
        if (id == null) {
            cb.onError(app.getString(R.string.error_session_expired));
            return;
        }
        Map<String, Object> body = new HashMap<>();
        if (fullName != null) body.put("full_name", fullName.trim());
        if (setPhoto) body.put("photo_path", photoPath);
        Calls.enqueue(app, sb.rest.updateProfile("eq." + id, body), list -> {
            if (list == null || list.isEmpty()) throw new IllegalStateException("Profil introuvable");
            User u = Mappers.user(list.get(0));
            cache.put(u.uid, u);
            return u;
        }, cb);
    }
}
