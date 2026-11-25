package com.example.lostfoundcampus.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Announcement {

    public final String id;
    public AnnouncementType type;
    public AnnouncementStatus status;
    public String title;
    public Category category;
    public String description;
    @Nullable
    /** Couleur saisie librement (ex. « Bleu marine »). */
    public String color;
    public CampusLocation location;
    public long eventDate;
    public final long createdAt;
    /** Chemins dans le bucket « announcement-photos » (1 à 5). */
    public final List<String> photoPaths = new ArrayList<>();
    public final String authorId;

    public Announcement(String id, AnnouncementType type, AnnouncementStatus status, String title,
                        Category category, String description, @Nullable String color,
                        CampusLocation location, long eventDate, long createdAt,
                        List<String> photos, String authorId) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.title = title;
        this.category = category;
        this.description = description;
        this.color = color;
        this.location = location;
        this.eventDate = eventDate;
        this.createdAt = createdAt;
        if (photos != null) this.photoPaths.addAll(photos);
        this.authorId = authorId;
    }

    public boolean isActive() {
        return status == AnnouncementStatus.ACTIVE;
    }

    @Nullable
    public String coverPath() {
        return photoPaths.isEmpty() ? null : photoPaths.get(0);
    }

    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }
}
