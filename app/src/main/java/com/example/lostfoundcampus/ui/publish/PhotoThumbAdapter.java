package com.example.lostfoundcampus.ui.publish;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.databinding.ItemAddPhotoBinding;
import com.example.lostfoundcampus.databinding.ItemPhotoThumbBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Miniatures horizontales des photos, avec suppression et tuile « Ajouter » tant que la limite n'est pas atteinte. */
class PhotoThumbAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Listener {
        void onRemove(int index);

        /** Photo touchée : ouvrir le cadrage de l'objet. */
        void onOpen(int index);

        void onAdd();
    }

    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_ADD = 1;

    private final List<PublishViewModel.PhotoItem> photos = new ArrayList<>();
    private final Listener listener;
    private boolean showAddTile;
    private boolean cameraMode;

    PhotoThumbAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<PublishViewModel.PhotoItem> data, boolean showAdd, boolean camera) {
        photos.clear();
        photos.addAll(data);
        showAddTile = showAdd;
        cameraMode = camera;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < photos.size() ? TYPE_PHOTO : TYPE_ADD;
    }

    @Override
    public int getItemCount() {
        return photos.size() + (showAddTile ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            return new AddHolder(ItemAddPhotoBinding.inflate(inflater, parent, false));
        }
        return new PhotoHolder(ItemPhotoThumbBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PhotoHolder) {
            ItemPhotoThumbBinding b = ((PhotoHolder) holder).b;
            Context c = b.getRoot().getContext();
            int number = position + 1;
            PublishViewModel.PhotoItem item = photos.get(position);
            if (item.localUri != null) ImageLoader.local(b.imgThumb, item.localUri);
            else ImageLoader.announcementPhoto(b.imgThumb, item.remotePath);
            b.imgThumb.setContentDescription(c.getString(R.string.cd_photo_thumb, number));
            b.btnRemove.setContentDescription(c.getString(R.string.cd_remove_photo, number));
            if (item.localUri != null) {
                b.imgThumb.setContentDescription(c.getString(R.string.cd_crop_photo, number));
                b.imgThumb.setOnClickListener(v -> {
                    int index = holder.getBindingAdapterPosition();
                    if (index != RecyclerView.NO_POSITION) listener.onOpen(index);
                });
            } else {
                b.imgThumb.setOnClickListener(null);
                b.imgThumb.setClickable(false);
            }
            b.btnRemove.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) listener.onRemove(index);
            });
        } else {
            ItemAddPhotoBinding b = ((AddHolder) holder).b;
            Context c = b.getRoot().getContext();
            b.imgAddIcon.setImageResource(cameraMode ? R.drawable.ic_camera : R.drawable.ic_add);
            b.tile.setContentDescription(c.getString(cameraMode
                    ? R.string.cd_add_photo_camera : R.string.cd_add_photo_gallery));
            b.tile.setOnClickListener(v -> listener.onAdd());
        }
    }

    static class PhotoHolder extends RecyclerView.ViewHolder {
        final ItemPhotoThumbBinding b;

        PhotoHolder(ItemPhotoThumbBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static class AddHolder extends RecyclerView.ViewHolder {
        final ItemAddPhotoBinding b;

        AddHolder(ItemAddPhotoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
