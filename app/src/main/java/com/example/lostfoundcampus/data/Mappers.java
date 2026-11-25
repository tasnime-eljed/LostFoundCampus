package com.example.lostfoundcampus.data;

import androidx.annotation.Nullable;

import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementStatus;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.CampusLocation;
import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.data.model.ConversationSummary;
import com.example.lostfoundcampus.data.model.Message;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.data.remote.dto.AnnouncementDto;
import com.example.lostfoundcampus.data.remote.dto.ConversationRowDto;
import com.example.lostfoundcampus.data.remote.dto.MessageDto;
import com.example.lostfoundcampus.data.remote.dto.ProfileDto;
import com.example.lostfoundcampus.util.DateUtil;

import com.example.lostfoundcampus.util.ColorNames;

import java.util.ArrayList;
import java.util.List;

/** Conversion des lignes Supabase (DTO) vers le modèle de l'app. */
public final class Mappers {

    private Mappers() {
    }

    public static Announcement announcement(AnnouncementDto d) {
        return new Announcement(d.id,
                enumOr(AnnouncementType.class, d.type, AnnouncementType.LOST),
                enumOr(AnnouncementStatus.class, d.status, AnnouncementStatus.ACTIVE),
                d.title == null ? "" : d.title,
                enumOr(Category.class, d.category, Category.OTHER),
                d.description == null ? "" : d.description,
                ColorNames.display(d.color),
                new CampusLocation(d.latitude, d.longitude, d.locationPrecision),
                DateUtil.parseDay(d.eventDate),
                DateUtil.parseIso(d.createdAt),
                d.photoPaths,
                d.authorId);
    }

    public static List<Announcement> announcements(@Nullable List<AnnouncementDto> list) {
        List<Announcement> out = new ArrayList<>();
        if (list != null) for (AnnouncementDto d : list) out.add(announcement(d));
        return out;
    }

    public static User user(ProfileDto d) {
        return new User(d.id, d.fullName == null ? "" : d.fullName,
                d.universityEmail == null ? "" : d.universityEmail, d.photoPath);
    }

    public static Message message(MessageDto d) {
        return new Message(d.id, d.body == null ? "" : d.body, DateUtil.parseIso(d.sentAt), d.senderId,
                Message.State.SENT, d.readAt != null);
    }

    public static ConversationSummary conversation(ConversationRowDto d) {
        User other = new User(d.otherUserId, d.otherFullName == null ? "" : d.otherFullName, "", d.otherPhotoPath);
        return new ConversationSummary(d.conversationId, d.announcementId, d.announcementTitle,
                d.announcementType == null ? null : enumOr(AnnouncementType.class, d.announcementType, null),
                d.announcementPhoto, other, DateUtil.parseIso(d.lastMessageAt), d.lastMessagePreview, d.unreadCount);
    }

    @Nullable
    public static <E extends Enum<E>> E enumOr(Class<E> type, @Nullable String value, @Nullable E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
