package com.example.lostfoundcampus.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.databinding.ItemAnnouncementBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.ui.map.MapHelper;
import com.example.lostfoundcampus.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

/** Cartes d'annonces du fil (photo, badge, date, titre, catégorie · lieu, description). */
public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.Holder> {

    public interface OnClick {
        void onAnnouncement(Announcement announcement);
    }

    private final List<Announcement> items = new ArrayList<>();
    private final OnClick onClick;

    public AnnouncementAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<Announcement> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    /** Ajoute la page suivante du fil. */
    public void append(List<Announcement> more) {
        int start = items.size();
        items.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Announcement a = items.get(position);
        Context c = h.b.getRoot().getContext();
        ImageLoader.announcementPhoto(h.b.imgPhoto, a.coverPath());
        h.b.imgPhoto.setContentDescription(c.getString(R.string.cd_announcement_photo, a.title));
        UiUtils.bindTypeBadge(h.b.txtBadge, a.type);
        h.b.txtDate.setText(DateUtil.relativeShort(c, a.eventDate));
        h.b.txtTitle.setText(a.title);
        h.b.txtMeta.setText(c.getString(R.string.separator_dot, c.getString(a.category.labelRes),
                MapHelper.label(c, a.location)));
        h.b.txtDescription.setText(a.description);
        h.b.txtDescription.setVisibility(a.hasDescription() ? View.VISIBLE : View.GONE);
        h.b.getRoot().setOnClickListener(v -> onClick.onAnnouncement(a));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemAnnouncementBinding b;

        Holder(ItemAnnouncementBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
