package com.example.lostfoundcampus.ui.matches;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.databinding.ItemMatchBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;
import com.example.lostfoundcampus.ui.map.MapHelper;
import com.example.lostfoundcampus.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.Holder> {

    static final class Item {
        final Announcement announcement;
        final int score;

        Item(Announcement announcement, int score) {
            this.announcement = announcement;
            this.score = score;
        }
    }

    interface Listener {
        void onView(Announcement a);

        void onContact(Announcement a, android.view.View button);
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;

    MatchAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<Item> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMatchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item item = items.get(position);
        Announcement a = item.announcement;
        Context c = h.b.getRoot().getContext();
        ImageLoader.announcementPhoto(h.b.imgPhoto, a.coverPath());
        h.b.imgPhoto.setContentDescription(c.getString(R.string.cd_announcement_photo, a.title));
        String kind = c.getString(a.type == AnnouncementType.FOUND ? R.string.matches_object_found : R.string.matches_object_lost);
        h.b.txtKind.setText(c.getString(R.string.matches_other_type, kind, c.getString(a.type.labelRes)));
        h.b.txtTitle.setText(a.title);
        h.b.txtMeta.setText(c.getString(R.string.separator_dot, MapHelper.label(c, a.location),
                DateUtil.shortDate(a.eventDate)));
        h.b.txtScore.setText(c.getString(R.string.matches_score, item.score));
        h.b.scoreRow.setContentDescription(c.getString(R.string.cd_match_score, item.score));
        h.b.progressScore.setProgress(item.score);
        int strong = c.getResources().getInteger(R.integer.lf_match_strong);
        h.b.progressScore.setIndicatorColor(ContextCompat.getColor(c,
                item.score >= strong ? R.color.lf_violet_600 : R.color.lf_mauve));
        h.b.btnView.setOnClickListener(v -> listener.onView(a));
        h.b.btnContact.setOnClickListener(v -> listener.onContact(a, v));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemMatchBinding b;

        Holder(ItemMatchBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
