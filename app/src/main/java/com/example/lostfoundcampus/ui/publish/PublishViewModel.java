package com.example.lostfoundcampus.ui.publish;

import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.example.lostfoundcampus.data.Mappers;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.CampusLocation;
import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.data.model.DetectedColor;
import com.example.lostfoundcampus.data.model.ImageAnalysis;
import com.example.lostfoundcampus.data.remote.dto.PhotoAnalysisDto;

import com.example.lostfoundcampus.util.ColorNames;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Brouillon de l'annonce, partagé (portée activité) entre Publier, l'aperçu photo,
 * l'analyse IA et la sélection du lieu, pour survivre aux allers-retours de navigation.
 */
public class PublishViewModel extends ViewModel {

    /** Une photo du brouillon : locale (à envoyer) ou déjà dans Supabase Storage (modification). */
    public static final class PhotoItem {
        @Nullable
        public final Uri localUri;
        @Nullable
        public final String remotePath;
        @Nullable
        public ImageAnalysis analysis;
        /** Photo locale déjà envoyée par un essai précédent : pas renvoyée au suivant. */
        @Nullable
        public String uploadedPath;
        /** Zone de l'objet choisie par l'utilisateur (relative 0..1), utilisée seulement pour l'analyse. */
        @Nullable
        public RectF crop;

        PhotoItem(@Nullable Uri localUri, @Nullable String remotePath) {
            this.localUri = localUri;
            this.remotePath = remotePath;
        }
    }

    public AnnouncementType type = AnnouncementType.LOST;
    @Nullable
    public String editingId;
    /** L'annonce à modifier a été chargée dans le brouillon. */
    public boolean editLoaded;
    /** Id de la nouvelle annonce, le même à chaque essai de publication. */
    @NonNull
    public String draftId = UUID.randomUUID().toString();
    /** Publication en cours : gardée ici pour survivre à une rotation. */
    @Nullable
    AnnouncementPublisher publisher;
    /** Publication terminée, écran pas encore fermé. */
    @Nullable
    String publishedId;

    public final List<PhotoItem> photos = new ArrayList<>();
    /** Photos déjà en ligne retirées pendant la modification (supprimées après enregistrement). */
    public final List<String> removedRemotePaths = new ArrayList<>();
    /** Fichier cible de l'appareil photo en cours. */
    @Nullable
    public Uri cameraTarget;
    /** Photo prise, en attente dans l'aperçu (onglet « J'ai trouvé »). */
    @Nullable
    public Uri pendingPhoto;
    /** Cadrage de la photo en attente. */
    @Nullable
    public RectF pendingCrop;

    /** Suggestions retenues (photo la plus sûre). */
    @Nullable
    public ImageAnalysis analysis;

    public String title = "";
    @Nullable
    public Category category;
    /** Couleur saisie librement. */
    @Nullable
    public String color;
    public String description = "";
    @Nullable
    public CampusLocation location;
    /** Lieu prérempli par la position capturée (onglet « J'ai trouvé »). */
    public boolean locationCaptured;
    /** Recherche de position en cours. */
    public boolean locating;
    @Nullable
    public Long date;

    @Nullable
    public String aiTitle;
    @Nullable
    public Category aiCategory;
    @Nullable
    public String aiColor;

    public void reset(AnnouncementType newType) {
        editingId = null;
        editLoaded = false;
        if (publisher != null) publisher.cancel();
        publisher = null;
        publishedId = null;
        draftId = UUID.randomUUID().toString();
        photos.clear();
        removedRemotePaths.clear();
        cameraTarget = null;
        pendingPhoto = null;
        pendingCrop = null;
        analysis = null;
        title = "";
        category = null;
        color = null;
        description = "";
        location = null;
        locationCaptured = false;
        locating = false;
        date = null;
        aiTitle = null;
        aiCategory = null;
        aiColor = null;
        type = newType;
    }

    public void loadForEdit(@NonNull Announcement a, @NonNull List<PhotoAnalysisDto> analyses) {
        reset(a.type);
        editingId = a.id;
        editLoaded = true;
        for (String path : a.photoPaths) photos.add(new PhotoItem(null, path));
        for (PhotoAnalysisDto d : analyses) {
            if (d.photoIndex >= 0 && d.photoIndex < photos.size()) {
                photos.get(d.photoIndex).analysis = new ImageAnalysis(d.labels, "",
                        d.labelConfidence == null ? 0 : d.labelConfidence, d.detectedTexts,
                        storedColors(d.dominantColor),
                        a.category, a.title);
            }
        }
        title = a.title;
        category = a.category;
        color = a.color;
        description = a.description == null ? "" : a.description;
        location = a.location;
        date = a.eventDate;
    }

    private static List<DetectedColor> storedColors(@Nullable String stored) {
        List<DetectedColor> out = new ArrayList<>();
        String name = ColorNames.display(stored);
        if (name != null) {
            Integer rgb = ColorNames.rgbFor(name);
            out.add(new DetectedColor(name, rgb == null ? 0xFF9AA0A6 : rgb, 1f));
        }
        return out;
    }

    @Override
    protected void onCleared() {
        if (publisher != null) publisher.cancel();
    }

    public boolean isEditing() {
        return editingId != null;
    }

    /** Change d'onglet ; « J'ai perdu » oublie la position capturée automatiquement. */
    public void setType(AnnouncementType newType) {
        type = newType;
        if (newType == AnnouncementType.LOST && locationCaptured) {
            location = null;
            locationCaptured = false;
        }
    }

    /** « J'ai trouvé » sans lieu : il faut capturer la position. */
    public boolean needsCapturedLocation() {
        return type == AnnouncementType.FOUND && location == null && !isEditing();
    }

    public void setCapturedLocation(@NonNull CampusLocation captured) {
        location = captured;
        locationCaptured = true;
    }

    public void setLocation(@NonNull CampusLocation newLocation) {
        location = newLocation;
        locationCaptured = false;
    }

    public int remainingSlots(int max) {
        return Math.max(0, max - photos.size());
    }

    public void addLocalPhotos(List<Uri> uris, int max) {
        for (Uri u : uris) {
            if (photos.size() >= max) break;
            photos.add(new PhotoItem(u, null));
        }
    }

    public void removePhoto(int index) {
        if (index < 0 || index >= photos.size()) return;
        PhotoItem removed = photos.remove(index);
        if (removed.remotePath != null) removedRemotePaths.add(removed.remotePath);
        // Un essai précédent a pu l'enregistrer : supprimée seulement après le prochain enregistrement.
        if (removed.uploadedPath != null) removedRemotePaths.add(removed.uploadedPath);
    }

    /** Photos à analyser : la photo prise en attente, sinon les photos locales pas encore analysées. */
    public List<Uri> analysisTargets() {
        List<Uri> out = new ArrayList<>();
        if (pendingPhoto != null) {
            out.add(pendingPhoto);
            return out;
        }
        for (PhotoItem p : photos) if (p.localUri != null && p.analysis == null) out.add(p.localUri);
        return out;
    }

    @Nullable
    public Uri previewSource() {
        if (pendingPhoto != null) return pendingPhoto;
        for (PhotoItem p : photos) if (p.localUri != null) return p.localUri;
        return null;
    }

    @Nullable
    public RectF cropFor(@NonNull Uri uri) {
        if (uri.equals(pendingPhoto)) return pendingCrop == null ? null : new RectF(pendingCrop);
        for (PhotoItem p : photos) {
            if (uri.equals(p.localUri)) return p.crop == null ? null : new RectF(p.crop);
        }
        return null;
    }

    /** Enregistre le cadrage et marque la photo à réanalyser. */
    public void setCrop(@NonNull Uri uri, @Nullable RectF crop) {
        RectF copy = crop == null ? null : new RectF(crop);
        if (uri.equals(pendingPhoto)) {
            pendingCrop = copy;
            return;
        }
        for (PhotoItem p : photos) {
            if (uri.equals(p.localUri)) {
                p.crop = copy;
                p.analysis = null;
            }
        }
    }

    /** Première photo locale (cible par défaut du bouton « Cadrer l'objet »). */
    @Nullable
    public Uri firstLocalPhoto() {
        for (PhotoItem p : photos) if (p.localUri != null) return p.localUri;
        return null;
    }

    /** Range une analyse avec sa photo ; la photo prise en attente rejoint la liste. */
    public void attachAnalysis(@NonNull Uri uri, @Nullable ImageAnalysis result, int max) {
        if (uri.equals(pendingPhoto)) {
            if (photos.size() < max) {
                PhotoItem item = new PhotoItem(uri, null);
                item.analysis = result;
                item.crop = pendingCrop;
                photos.add(item);
            }
            pendingPhoto = null;
            pendingCrop = null;
            return;
        }
        for (PhotoItem p : photos) {
            if (uri.equals(p.localUri)) p.analysis = result;
        }
    }

    /**
     * Applique les suggestions de la photo la plus sûre aux champs vides ou encore égaux
     * à une ancienne suggestion (jamais à une saisie de l'utilisateur, RG03).
     */
    public void applyBestSuggestions() {
        // Une photo cadrée par l'utilisateur passe devant les autres, quelle que soit leur confiance.
        ImageAnalysis best = null;
        boolean bestCropped = false;
        for (PhotoItem p : photos) {
            if (p.analysis == null) continue;
            boolean cropped = p.crop != null;
            if (best == null || (cropped && !bestCropped)
                    || (cropped == bestCropped && p.analysis.confidence > best.confidence)) {
                best = p.analysis;
                bestCropped = cropped;
            }
        }
        if (best == null) return;
        analysis = best;
        if (title.trim().isEmpty() || title.equals(aiTitle)) {
            title = best.suggestedTitle;
            aiTitle = best.suggestedTitle;
        }
        if (category == null || category == aiCategory) {
            category = best.suggestedCategory;
            aiCategory = best.suggestedCategory;
        }
        if (best.dominantColor != null
                && (color == null || color.trim().isEmpty() || ColorNames.same(color, aiColor))) {
            color = best.dominantColor;
            aiColor = best.dominantColor;
        }
    }

    public boolean titleIsAiSuggestion() {
        return aiTitle != null && aiTitle.equals(title);
    }

    public boolean categoryIsAiSuggestion() {
        return aiCategory != null && aiCategory == category;
    }

    public boolean colorIsAiSuggestion() {
        return ColorNames.same(aiColor, color);
    }
}
