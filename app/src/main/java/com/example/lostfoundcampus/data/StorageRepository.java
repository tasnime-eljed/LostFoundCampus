package com.example.lostfoundcampus.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.lostfoundcampus.data.remote.SupabaseClient;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Supabase Storage : envoi des JPEG compressés et URL authentifiées pour Glide (buckets privés). */
public final class StorageRepository {

    public static final String BUCKET_PHOTOS = "announcement-photos";
    public static final String BUCKET_AVATARS = "avatars";
    private static final MediaType JPEG = MediaType.get("image/jpeg");

    /**
     * En-têtes partagés : GlideUrl compare aussi les en-têtes, une instance par image empêcherait
     * le cache mémoire de fonctionner. Le jeton est lu à chaque téléchargement.
     */
    private static final LazyHeaders HEADERS = new LazyHeaders.Builder()
            .addHeader("apikey", SupabaseClient.apiKey())
            .addHeader("Authorization", () -> "Bearer " + SupabaseClient.get().freshAccessToken())
            .build();

    private static StorageRepository instance;
    private final Context app;
    private final SupabaseClient sb;

    private StorageRepository(Context context) {
        app = context.getApplicationContext();
        sb = SupabaseClient.get();
    }

    public static synchronized StorageRepository get(Context context) {
        if (instance == null) instance = new StorageRepository(context);
        return instance;
    }

    public void upload(String bucket, String path, byte[] jpeg, boolean upsert, ResultCallback<String> cb) {
        RequestBody body = RequestBody.create(jpeg, JPEG);
        Calls.enqueue(app, sb.storage.upload(bucket, path, upsert ? "true" : "false", body), r -> path, cb);
    }

    /** Suppression sans attendre (fichier devenu inutile). */
    public void deleteQuietly(String bucket, String path) {
        sb.storage.delete(bucket, path).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            }
        });
    }

    /** URL de lecture authentifiée : Glide envoie la clé et le jeton de l'utilisateur. */
    @Nullable
    public static GlideUrl glideUrl(String bucket, @Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        String url = SupabaseClient.baseUrl() + "/storage/v1/object/" + bucket + "/" + path;
        return new GlideUrl(url, HEADERS);
    }
}
