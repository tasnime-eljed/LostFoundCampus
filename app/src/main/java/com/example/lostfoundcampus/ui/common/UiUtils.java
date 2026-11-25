package com.example.lostfoundcampus.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.AnnouncementStatus;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.User;

import java.util.Locale;

/** Liaisons visuelles communes (badges, avatars) pour garder un rendu identique partout. */
public final class UiUtils {

    private static final int[] AVATAR_COLORS = {
            R.color.lf_peach_ink, R.color.lf_mauve, R.color.lf_violet_600
    };

    private UiUtils() {
    }

    public static void bindTypeBadge(TextView badge, AnnouncementType type) {
        badge.setText(type.labelRes);
        tintBadge(badge, type == AnnouncementType.LOST ? R.color.lf_pink_100 : R.color.lf_peach,
                type == AnnouncementType.LOST ? R.color.lf_pink_ink : R.color.lf_peach_ink);
    }

    /** Badge « Perdu · Actif » du détail. */
    public static void bindTypeStatusBadge(TextView badge, AnnouncementType type, AnnouncementStatus status) {
        Context c = badge.getContext();
        badge.setText(c.getString(R.string.badge_type_status, c.getString(type.labelRes),
                c.getString(status.detailLabelRes)));
        if (status == AnnouncementStatus.RETURNED) {
            tintBadge(badge, R.color.lf_returned_bg, R.color.lf_returned_ink);
        } else {
            tintBadge(badge, type == AnnouncementType.LOST ? R.color.lf_pink_100 : R.color.lf_peach,
                    type == AnnouncementType.LOST ? R.color.lf_pink_ink : R.color.lf_peach_ink);
        }
    }

    public static void bindStatusBadge(TextView badge, AnnouncementStatus status) {
        badge.setText(status.labelRes);
        if (status == AnnouncementStatus.ACTIVE) {
            tintBadge(badge, R.color.lf_pink_100, R.color.lf_pink_ink);
        } else {
            tintBadge(badge, R.color.lf_returned_bg, R.color.lf_returned_ink);
        }
    }

    private static void tintBadge(TextView badge, @ColorRes int bg, @ColorRes int fg) {
        Context c = badge.getContext();
        ViewCompat.setBackgroundTintList(badge, ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
        badge.setTextColor(ContextCompat.getColor(c, fg));
    }

    public static String initials(@Nullable String fullName) {
        if (fullName == null) return "";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            char ch = p.charAt(0);
            if (Character.isLetter(ch)) sb.append(ch);
            if (sb.length() == 2) break;
        }
        return sb.toString().toUpperCase(Locale.FRANCE);
    }

    @ColorRes
    public static int avatarColor(@Nullable String uid) {
        if (uid == null) return R.color.lf_violet_600;
        return AVATAR_COLORS[Math.abs(uid.hashCode() % AVATAR_COLORS.length)];
    }

    /** Avatar à initiales ; si l'utilisateur a une photo, elle s'affiche par-dessus. */
    public static void bindAvatar(TextView initials, @Nullable ImageView photo, User user) {
        Context c = initials.getContext();
        initials.setVisibility(View.VISIBLE);
        initials.setText(initials(user.fullName));
        ViewCompat.setBackgroundTintList(initials,
                ColorStateList.valueOf(ContextCompat.getColor(c, avatarColor(user.uid))));
        if (photo == null) return;
        if (user.hasPhoto()) {
            photo.setVisibility(View.VISIBLE);
            ImageLoader.avatar(photo, user.photoPath);
        } else {
            photo.setVisibility(View.GONE);
        }
    }

    public static void bindInitialsAvatar(TextView initials, User user) {
        bindAvatar(initials, null, user);
    }
}
