package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class AuthUser {
    @SerializedName("id")
    public String id;
    @SerializedName("email")
    public String email;
}
