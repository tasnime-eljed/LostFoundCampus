package com.example.lostfoundcampus.ui.common;

import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.StorageRepository;

/** Chargement des photos avec Glide (cache mémoire et disque). */
public final class ImageLoader {

    private ImageLoader() {
    }

    public static void announcementPhoto(ImageView view, @Nullable String path) {
        Glide.with(view)
                .load(StorageRepository.glideUrl(StorageRepository.BUCKET_PHOTOS, path))
                .placeholder(R.drawable.bg_photo_placeholder)
                .error(R.drawable.bg_photo_placeholder)
                .centerCrop()
                .into(view);
    }

    public static void avatar(ImageView view, @Nullable String path) {
        Glide.with(view)
                .load(StorageRepository.glideUrl(StorageRepository.BUCKET_AVATARS, path))
                .placeholder(R.color.lf_sand)
                .circleCrop()
                .into(view);
    }

    public static void local(ImageView view, Uri uri) {
        Glide.with(view)
                .load(uri)
                .placeholder(R.drawable.bg_photo_placeholder)
                .error(R.drawable.bg_photo_placeholder)
                .centerCrop()
                .into(view);
    }

    public static void localAvatar(ImageView view, Uri uri) {
        Glide.with(view).load(uri).circleCrop().into(view);
    }
}
