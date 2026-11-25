package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class MessageDto {
    @SerializedName("id")
    public long id;
    @SerializedName("conversation_id")
    public String conversationId;
    @SerializedName("sender_id")
    public String senderId;
    @SerializedName("body")
    public String body;
    @SerializedName("sent_at")
    public String sentAt;
    @SerializedName("read_at")
    public String readAt;
}
