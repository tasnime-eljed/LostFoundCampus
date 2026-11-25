package com.example.lostfoundcampus.ui.publish;

import android.content.Context;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.util.ColorNames;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.StorageRepository;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.ImageAnalysis;
import com.example.lostfoundcampus.util.AppExecutors;
import com.example.lostfoundcampus.util.DateUtil;
import com.example.lostfoundcampus.util.PhotoCompressor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enregistre le brouillon : compression et envoi des nouvelles photos dans Storage,
 * insertion ou mise à jour de l'annonce, puis remplacement des analyses de photos.
 */
final class AnnouncementPublisher {

    interface Listener {
        void onProgress(String message);

        void onDone(@NonNull String announcementId);

        void onError(@NonNull String message);
    }

    private final Context app;
    private final PublishViewModel vm;
    @Nullable
    private Listener listener;
    /** Copie : le formulaire peut changer pendant l'envoi. */
    private final List<PublishViewModel.PhotoItem> photos;
    private final List<String> removedPaths;
    private final boolean editing;
    private String progress = "";
    private final String announcementId;
    private final String userId;
    private final List<String> finalPaths = new ArrayList<>();
    /** Photos envoyées par cette publication : supprimées si un envoi échoue avant l'enregistrement. */
    private final List<PublishViewModel.PhotoItem> uploadedItems = new ArrayList<>();
    private volatile boolean cancelled;

    AnnouncementPublisher(Context context, PublishViewModel vm) {
        this.app = context.getApplicationContext();
        this.vm = vm;
        this.photos = new ArrayList<>(vm.photos);
        this.removedPaths = new ArrayList<>(vm.removedRemotePaths);
        this.editing = vm.isEditing();
        this.announcementId = vm.editingId != null ? vm.editingId : vm.draftId;
        String uid = AuthRepository.get(app).currentUserId();
        this.userId = uid == null ? "" : uid;
    }

    /** Écran qui suit l'envoi (remplacé après une rotation, null s'il n'est plus affiché). */
    void setListener(@Nullable Listener l) {
        listener = l;
        if (l != null) l.onProgress(progress);
    }

    private void progress(String message) {
        progress = message;
        if (listener != null) listener.onProgress(message);
    }

    void start() {
        uploadPhoto(0);
    }

    void cancel() {
        cancelled = true;
    }

    private int localCount() {
        int n = 0;
        for (PublishViewModel.PhotoItem p : photos) if (p.localUri != null) n++;
        return n;
    }

    private int localIndex(int index) {
        int n = 0;
        for (int i = 0; i <= index && i < photos.size(); i++) if (photos.get(i).localUri != null) n++;
        return n;
    }

    /** Envoie les photos une par une, dans l'ordre (le premier chemin est la photo de couverture). */
    private void uploadPhoto(int index) {
        if (cancelled) return;
        if (index >= photos.size()) {
            saveRow();
            return;
        }
        PublishViewModel.PhotoItem item = photos.get(index);
        if (item.localUri == null) {
            finalPaths.add(item.remotePath);
            uploadPhoto(index + 1);
            return;
        }
        if (item.uploadedPath != null) {
            finalPaths.add(item.uploadedPath);
            uploadPhoto(index + 1);
            return;
        }
        progress(app.getString(R.string.publish_uploading, localIndex(index), localCount()));
        AppExecutors.io(() -> {
            byte[] jpeg;
            try {
                jpeg = PhotoCompressor.compress(app, item.localUri, PhotoCompressor.PHOTO_MAX_SIDE,
                        PhotoCompressor.PHOTO_MAX_BYTES);
            } catch (Exception e) {
                AppExecutors.main(() -> {
                    discardUploads();
                    fail(app.getString(R.string.error_photo_read));
                });
                return;
            }
            String path = userId + "/" + announcementId + "/" + UUID.randomUUID() + ".jpg";
            AppExecutors.main(() -> StorageRepository.get(app).upload(StorageRepository.BUCKET_PHOTOS, path, jpeg, false,
                    new ResultCallback<String>() {
                        @Override
                        public void onSuccess(String uploaded) {
                            finalPaths.add(uploaded);
                            item.uploadedPath = uploaded;
                            uploadedItems.add(item);
                            if (cancelled) {
                                discardUploads();
                                return;
                            }
                            uploadPhoto(index + 1);
                        }

                        @Override
                        public void onError(String message) {
                            discardUploads();
                            fail(message);
                        }
                    }));
        });
    }

    private void saveRow() {
        if (cancelled) return;
        progress(app.getString(R.string.publish_saving));
        Map<String, Object> body = new HashMap<>();
        body.put("type", vm.type.name());
        body.put("title", vm.title.trim());
        body.put("category", vm.category == null ? null : vm.category.name());
        body.put("color", ColorNames.normalize(vm.color));
        body.put("description", vm.description.trim());
        body.put("latitude", vm.location == null ? 0 : vm.location.latitude);
        body.put("longitude", vm.location == null ? 0 : vm.location.longitude);
        body.put("location_precision", vm.location != null && vm.location.hasPrecision()
                ? vm.location.precision.trim() : null);
        body.put("event_date", DateUtil.formatDay(vm.date == null ? System.currentTimeMillis() : vm.date));
        body.put("photo_paths", finalPaths);

        ResultCallback<Announcement> saved = new ResultCallback<Announcement>() {
            @Override
            public void onSuccess(Announcement value) {
                saveAnalyses();
            }

            @Override
            public void onError(String message) {
                // Ne pas supprimer les photos : après un délai dépassé, l'annonce a pu être enregistrée.
                fail(message);
            }
        };
        AnnouncementRepository repo = AnnouncementRepository.get(app);
        if (editing) {
            repo.update(announcementId, body, saved);
        } else {
            body.put("id", announcementId);
            repo.insert(body, saved);
        }
    }

    private void saveAnalyses() {
        if (cancelled) return;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            ImageAnalysis a = photos.get(i).analysis;
            if (a == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("announcement_id", announcementId);
            row.put("photo_index", i);
            row.put("labels", a.labels);
            row.put("label_confidence", a.confidence);
            row.put("detected_texts", a.detectedTexts);
            row.put("dominant_color", ColorNames.normalize(a.dominantColor));
            row.put("user_confirmed", true);
            rows.add(row);
        }
        AnnouncementRepository.get(app).replaceAnalyses(announcementId, rows, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                finish();
            }

            @Override
            public void onError(String message) {
                // L'annonce est enregistrée ; seules les étiquettes manquent (correspondances moins précises).
                finish();
            }
        });
    }

    private void finish() {
        if (cancelled) return;
        StorageRepository storage = StorageRepository.get(app);
        for (String path : removedPaths) storage.deleteQuietly(StorageRepository.BUCKET_PHOTOS, path);
        vm.publisher = null;
        vm.publishedId = announcementId;
        if (listener != null) listener.onDone(announcementId);
    }

    private void discardUploads() {
        StorageRepository storage = StorageRepository.get(app);
        for (PublishViewModel.PhotoItem p : uploadedItems) {
            if (p.uploadedPath != null) storage.deleteQuietly(StorageRepository.BUCKET_PHOTOS, p.uploadedPath);
            p.uploadedPath = null;
        }
        uploadedItems.clear();
    }

    private void fail(@Nullable String message) {
        if (cancelled) return;
        vm.publisher = null;
        if (listener != null) listener.onError(message == null ? app.getString(R.string.error_generic) : message);
    }
}
