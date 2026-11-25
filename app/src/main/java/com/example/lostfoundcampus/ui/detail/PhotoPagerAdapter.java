package com.example.lostfoundcampus.ui.detail;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.databinding.ItemPhotoPageBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Pages de la galerie du détail (ViewPager2), photos chargées depuis Supabase Storage par Glide. */
class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.Holder> {

    private final List<String> photos = new ArrayList<>();
    private String title = "";

    void submit(List<String> data, String announcementTitle) {
        photos.clear();
        photos.addAll(data);
        title = announcementTitle;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ImageLoader.announcementPhoto(h.b.imgPage, photos.get(position));
        h.b.imgPage.setContentDescription(h.b.getRoot().getContext()
                .getString(R.string.cd_announcement_photo, title));
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemPhotoPageBinding b;

        Holder(ItemPhotoPageBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
