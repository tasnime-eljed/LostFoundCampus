package com.example.lostfoundcampus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeNotNull;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.StorageRepository;
import com.example.lostfoundcampus.data.model.Announcement;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Photos privées affichées par Glide. Lancer avec -e email … -e password … (compte de test). */
@RunWith(AndroidJUnit4.class)
public class ImageLoadTest {

    private final Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static <T> ResultCallback<T> into(CompletableFuture<T> f) {
        return new ResultCallback<T>() {
            @Override
            public void onSuccess(T v) {
                f.complete(v);
            }

            @Override
            public void onError(String m) {
                f.completeExceptionally(new AssertionError(m));
            }
        };
    }

    private Drawable load(String path) throws Exception {
        return Glide.with(app).load(StorageRepository.glideUrl(StorageRepository.BUCKET_PHOTOS, path))
                .diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                .submit(300, 300).get(40, TimeUnit.SECONDS);
    }

    @Test
    public void privatePhotosLoad() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String email = args.getString("email"), password = args.getString("password");
        assumeNotNull(email, password);
        CompletableFuture<Void> in = new CompletableFuture<>();
        AuthRepository.get(app).signIn(email, password, into(in));
        in.get(30, TimeUnit.SECONDS);
        CompletableFuture<List<Announcement>> feed = new CompletableFuture<>();
        AnnouncementRepository.get(app).feed(null, null, null, 0, into(feed));
        String path = feed.get(30, TimeUnit.SECONDS).get(0).coverPath();
        assertNotNull(load(path));

        // Jeton périmé (app restée ouverte plus d'une heure) : la photo doit quand même se charger.
        app.getSharedPreferences("supabase_session", Context.MODE_PRIVATE).edit()
                .putLong("expires_at", 1).putString("access_token", "perime").commit();
        assertNotNull(load(path));
    }
}
