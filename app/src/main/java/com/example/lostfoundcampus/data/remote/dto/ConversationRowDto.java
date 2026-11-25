package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Ligne renvoyée par la fonction SQL my_conversations(). */
public class ConversationRowDto {
    @SerializedName("conversation_id")
    public String conversationId;
    @SerializedName("announcement_id")
    public String announcementId;
    @SerializedName("announcement_title")
    public String announcementTitle;
    @SerializedName("announcement_type")
    public String announcementType;
    @SerializedName("announcement_photo")
    public String announcementPhoto;
    @SerializedName("other_user_id")
    public String otherUserId;
    @SerializedName("other_full_name")
    public String otherFullName;
    @SerializedName("other_photo_path")
    public String otherPhotoPath;
    @SerializedName("last_message_at")
    public String lastMessageAt;
    @SerializedName("last_message_preview")
    public String lastMessagePreview;
    @SerializedName("unread_count")
    public int unreadCount;
}
