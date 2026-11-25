package com.example.lostfoundcampus.data.model;

import androidx.annotation.StringRes;

import com.example.lostfoundcampus.R;

public enum AnnouncementStatus {
    ACTIVE(R.string.status_active, R.string.status_detail_active),
    RETURNED(R.string.status_returned, R.string.status_detail_returned),
    ARCHIVED(R.string.status_archived, R.string.status_archived);

    @StringRes
    public final int labelRes;
    @StringRes
    public final int detailLabelRes;

    AnnouncementStatus(@StringRes int labelRes, @StringRes int detailLabelRes) {
        this.labelRes = labelRes;
        this.detailLabelRes = detailLabelRes;
    }
}
