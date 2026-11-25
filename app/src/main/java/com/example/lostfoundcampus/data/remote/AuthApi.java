package com.example.lostfoundcampus.data.remote;

import com.example.lostfoundcampus.data.remote.dto.AuthSession;
import com.example.lostfoundcampus.data.remote.dto.AuthUser;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;

/** Supabase Auth (GoTrue) : SUPABASE_URL/auth/v1/ */
public interface AuthApi {

    @POST("signup")
    Call<AuthSession> signUp(@Body Map<String, Object> body);

    @POST("token?grant_type=password")
    Call<AuthSession> signIn(@Body Map<String, Object> body);

    @POST("token?grant_type=refresh_token")
    Call<AuthSession> refresh(@Body Map<String, Object> body);

    /** Envoie l'e-mail de réinitialisation (le modèle d'e-mail contient le code {{ .Token }}). */
    @POST("recover")
    Call<Void> recover(@Body Map<String, Object> body);

    /** Vérifie le code de réinitialisation et ouvre une session. */
    @POST("verify")
    Call<AuthSession> verify(@Body Map<String, Object> body);

    @PUT("user")
    Call<AuthUser> updateUser(@Header("Authorization") String bearer, @Body Map<String, Object> body);

    @POST("logout")
    Call<Void> logout(@Header("Authorization") String bearer);
}
