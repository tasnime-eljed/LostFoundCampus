package com.example.lostfoundcampus.ui.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementStatus;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.databinding.ItemMyAnnouncementBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

class MyAnnouncementAdapter extends RecyclerView.Adapter<MyAnnouncementAdapter.Holder> {

    interface Listener {
        void onOpen(Announcement a);

        void onEdit(Announcement a);

        void onMarkReturned(Announcement a);
    }

    private final List<Announcement> items = new ArrayList<>();
    private final Listener listener;

    MyAnnouncementAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<Announcement> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMyAnnouncementBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Announcement a = items.get(position);
        Context c = h.b.getRoot().getContext();
        ImageLoader.announcementPhoto(h.b.imgPhoto, a.coverPath());
        h.b.imgPhoto.setContentDescription(c.getString(R.string.cd_announcement_photo, a.title));
        h.b.txtTitle.setText(a.title);
        @StringRes int metaRes;
        if (a.status == AnnouncementStatus.RETURNED) {
            metaRes = R.string.profile_item_returned;
        } else {
            metaRes = a.type == AnnouncementType.LOST ? R.string.profile_item_lost : R.string.profile_item_found;
        }
        h.b.txtMeta.setText(c.getString(metaRes, DateUtil.shortDate(a.eventDate)));
        UiUtils.bindStatusBadge(h.b.txtStatus, a.status);

        // Modifier / Marquer comme restitué : seulement pour une annonce active (RG02, RG06).
        h.b.groupActions.setVisibility(a.isActive() ? View.VISIBLE : View.GONE);
        h.b.btnEdit.setOnClickListener(v -> listener.onEdit(a));
        h.b.btnReturned.setOnClickListener(v -> listener.onMarkReturned(a));
        h.b.getRoot().setOnClickListener(v -> listener.onOpen(a));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemMyAnnouncementBinding b;

        Holder(ItemMyAnnouncementBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
