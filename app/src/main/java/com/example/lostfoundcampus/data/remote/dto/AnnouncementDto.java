package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AnnouncementDto {
    @SerializedName("id")
    public String id;
    @SerializedName("author_id")
    public String authorId;
    @SerializedName("type")
    public String type;
    @SerializedName("status")
    public String status;
    @SerializedName("title")
    public String title;
    @SerializedName("category")
    public String category;
    @SerializedName("color")
    public String color;
    @SerializedName("description")
    public String description;
    @SerializedName("latitude")
    public double latitude;
    @SerializedName("longitude")
    public double longitude;
    @SerializedName("location_precision")
    public String locationPrecision;
    @SerializedName("event_date")
    public String eventDate;
    @SerializedName("photo_paths")
    public List<String> photoPaths;
    @SerializedName("created_at")
    public String createdAt;
}
