package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PhotoAnalysisDto {
    @SerializedName("photo_index")
    public int photoIndex;
    @SerializedName("labels")
    public List<String> labels;
    @SerializedName("label_confidence")
    public Integer labelConfidence;
    @SerializedName("detected_texts")
    public List<String> detectedTexts;
    @SerializedName("dominant_color")
    public String dominantColor;
}
