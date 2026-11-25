package com.example.lostfoundcampus.data.remote;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.BuildConfig;
import com.example.lostfoundcampus.data.remote.dto.AuthSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Point d'entrée réseau vers Supabase.
 * - Chaque requête porte l'en-tête « apikey » (clé publishable, lue dans BuildConfig).
 * - Les requêtes REST et Storage portent en plus « Authorization: Bearer <jeton de l'utilisateur> » :
 *   c'est ce jeton qui fait appliquer les règles RLS.
 * - Le jeton est rafraîchi avant expiration, et après une réponse 401.
 */
public final class SupabaseClient {

    private static SupabaseClient instance;

    public final SessionStore session;
    public final AuthApi auth;
    public final RestApi rest;
    public final StorageApi storage;
    public final Gson gson;
    private final Object refreshLock = new Object();

    private SupabaseClient(Context context) {
        session = new SessionStore(context);
        gson = new GsonBuilder().serializeNulls().create();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);

        Interceptor apiKey = chain -> chain.proceed(chain.request().newBuilder()
                .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .build());

        OkHttpClient authHttp = new OkHttpClient.Builder()
                .addInterceptor(apiKey)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

        OkHttpClient userHttp = authHttp.newBuilder()
                .addInterceptor(this::addBearer)
                .authenticator(new TokenAuthenticator())
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        auth = retrofit(authHttp, "/auth/v1/").create(AuthApi.class);
        rest = retrofit(userHttp, "/rest/v1/").create(RestApi.class);
        storage = retrofit(userHttp, "/storage/v1/").create(StorageApi.class);
    }

    public static synchronized void init(Context context) {
        if (instance == null) instance = new SupabaseClient(context.getApplicationContext());
    }

    @NonNull
    public static SupabaseClient get() {
        if (instance == null) throw new IllegalStateException("SupabaseClient.init() non appelé");
        return instance;
    }

    /** Les clés sont-elles présentes dans local.properties ? */
    public static boolean isConfigured() {
        return BuildConfig.SUPABASE_URL.startsWith("https://") && !BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty();
    }

    public static String baseUrl() {
        return BuildConfig.SUPABASE_URL;
    }

    public static String apiKey() {
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    }

    private Retrofit retrofit(OkHttpClient client, String path) {
        String base = isConfigured() ? BuildConfig.SUPABASE_URL : "https://configuration-manquante.invalid";
        return new Retrofit.Builder()
                .baseUrl(base + path)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    // ------------------------------------------------------------------ Jeton

    /**
     * Jeton valide, rafraîchi si besoin (bloquant, hors du fil principal). Pour Glide, qui ne passe pas
     * par OkHttp : Storage répond 400 (et non 401) à un jeton périmé, il faut donc agir avant.
     */
    @Nullable
    public String freshAccessToken() {
        if (Looper.myLooper() != Looper.getMainLooper() && session.isSignedIn() && session.isExpiringSoon()) {
            refreshBlocking(session.accessToken());
        }
        return session.accessToken();
    }

    private Response addBearer(Interceptor.Chain chain) throws IOException {
        if (session.isSignedIn() && session.isExpiringSoon()) refreshBlocking(session.accessToken());
        String token = session.accessToken();
        Request.Builder builder = chain.request().newBuilder();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return chain.proceed(builder.build());
    }

    /**
     * Rafraîchit la session de façon synchrone (appelé sur un fil OkHttp).
     * @param staleToken jeton utilisé par la requête ; si un autre fil l'a déjà remplacé, on ne refait rien.
     */
    boolean refreshBlocking(@Nullable String staleToken) {
        synchronized (refreshLock) {
            String current = session.accessToken();
            if (current != null && !current.equals(staleToken) && !session.isExpiringSoon()) return true;
            String refresh = session.refreshToken();
            if (refresh == null) return false;
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("refresh_token", refresh);
                retrofit2.Response<AuthSession> r = auth.refresh(body).execute();
                // Déconnexion (ou autre compte) pendant l'appel : ne pas rouvrir l'ancienne session.
                if (!refresh.equals(session.refreshToken())) return false;
                if (r.isSuccessful() && r.body() != null && r.body().hasTokens()) {
                    session.save(r.body());
                    return true;
                }
                if (r.code() == 400 || r.code() == 401) {
                    session.clear();
                    session.notifyExpired();
                }
                return false;
            } catch (IOException | RuntimeException e) {
                return false;   // hors ligne : on garde la session, la requête échouera proprement
            }
        }
    }

    /** Après une réponse 401 : rafraîchit puis rejoue la requête une seule fois. */
    private final class TokenAuthenticator implements Authenticator {
        @Nullable
        @Override
        public Request authenticate(@Nullable Route route, @NonNull Response response) {
            if (response.priorResponse() != null) return null;
            String header = response.request().header("Authorization");
            String used = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
            if (!refreshBlocking(used)) return null;
            String token = session.accessToken();
            if (token == null) return null;
            return response.request().newBuilder().header("Authorization", "Bearer " + token).build();
        }
    }
}
