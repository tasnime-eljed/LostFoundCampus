package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Réponse de /auth/v1/token, /signup ou /verify. Sans access_token si l'e-mail doit être confirmé. */
public class AuthSession {
    @SerializedName("access_token")
    public String accessToken;
    @SerializedName("refresh_token")
    public String refreshToken;
    @SerializedName("expires_in")
    public long expiresIn;
    @SerializedName("expires_at")
    public long expiresAt;
    @SerializedName("user")
    public AuthUser user;

    public boolean hasTokens() {
        return accessToken != null && !accessToken.isEmpty() && refreshToken != null && user != null;
    }
}
