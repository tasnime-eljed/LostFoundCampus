package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class ProfileDto {
    @SerializedName("id")
    public String id;
    @SerializedName("full_name")
    public String fullName;
    @SerializedName("university_email")
    public String universityEmail;
    @SerializedName("photo_path")
    public String photoPath;
}
