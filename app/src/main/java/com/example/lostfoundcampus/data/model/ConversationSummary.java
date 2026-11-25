package com.example.lostfoundcampus.data.model;

import androidx.annotation.Nullable;

/** Une ligne de l'écran Messages (fonction SQL my_conversations). */
public class ConversationSummary {

    public final String id;
    @Nullable
    public final String announcementId;
    @Nullable
    public final String announcementTitle;
    @Nullable
    public final AnnouncementType announcementType;
    @Nullable
    public final String announcementPhoto;
    public final User other;
    public final long lastMessageAt;
    @Nullable
    public final String lastMessagePreview;
    public final int unreadCount;

    public ConversationSummary(String id, @Nullable String announcementId, @Nullable String announcementTitle,
                               @Nullable AnnouncementType announcementType, @Nullable String announcementPhoto,
                               User other, long lastMessageAt, @Nullable String lastMessagePreview, int unreadCount) {
        this.id = id;
        this.announcementId = announcementId;
        this.announcementTitle = announcementTitle;
        this.announcementType = announcementType;
        this.announcementPhoto = announcementPhoto;
        this.other = other;
        this.lastMessageAt = lastMessageAt;
        this.lastMessagePreview = lastMessagePreview;
        this.unreadCount = unreadCount;
    }

    public boolean hasMessages() {
        return lastMessagePreview != null;
    }
}
