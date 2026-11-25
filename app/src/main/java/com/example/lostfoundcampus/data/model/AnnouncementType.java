package com.example.lostfoundcampus.data.model;

import androidx.annotation.StringRes;

import com.example.lostfoundcampus.R;

public enum AnnouncementType {
    LOST(R.string.badge_lost),
    FOUND(R.string.badge_found);

    @StringRes
    public final int labelRes;

    AnnouncementType(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }
}
