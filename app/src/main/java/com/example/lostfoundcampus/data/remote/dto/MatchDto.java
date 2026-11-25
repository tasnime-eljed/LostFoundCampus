package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class MatchDto {
    @SerializedName("announcement_id")
    public String announcementId;
    @SerializedName("score")
    public int score;
}
