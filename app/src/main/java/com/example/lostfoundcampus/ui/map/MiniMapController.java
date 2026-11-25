package com.example.lostfoundcampus.ui.map;

import android.annotation.SuppressLint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.databinding.ViewMiniMapBinding;
import com.example.lostfoundcampus.data.model.CampusLocation;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

/** Pilote la mini-carte incluse (view_mini_map) : aperçu sans gestes, en lecture seule ou modifiable. */
public class MiniMapController {

    private final ViewMiniMapBinding b;
    @Nullable
    private final MapView map;
    @Nullable
    private Marker marker;

    /**
     * @param onOpen action au toucher (ouvrir l'écran de sélection) ; null = lecture seule.
     */
    @SuppressLint("ClickableViewAccessibility")
    public MiniMapController(@NonNull ViewMiniMapBinding binding, @Nullable Runnable onOpen) {
        this.b = binding;
        // Coins arrondis appliqués aussi à la carte elle-même.
        b.getRoot().setClipToOutline(true);
        this.map = MapHelper.createMap(b.mapContainer, false);
        b.fallback.setVisibility(map == null ? View.VISIBLE : View.GONE);

        if (onOpen != null) {
            b.touchOverlay.setOnClickListener(v -> onOpen.run());
            b.touchOverlay.setContentDescription(b.getRoot().getContext().getString(R.string.cd_map_preview));
            b.txtAction.setVisibility(View.VISIBLE);
        } else {
            // Lecture seule : la couche absorbe les gestes sans action.
            b.touchOverlay.setClickable(false);
            b.touchOverlay.setFocusable(false);
            b.touchOverlay.setBackground(null);
            b.touchOverlay.setOnTouchListener((v, event) -> true);
            b.touchOverlay.setContentDescription(b.getRoot().getContext().getString(R.string.cd_map_readonly));
            b.txtAction.setVisibility(View.GONE);
        }
    }

    /** Affiche le lieu ; sans lieu, centre sur le campus et invite à toucher la carte. */
    public void show(@Nullable CampusLocation location, @NonNull CampusLocation fallbackCenter,
                     @Nullable String labelOverride) {
        int zoom = b.getRoot().getResources().getInteger(R.integer.lf_map_zoom_preview);
        if (location == null) {
            if (labelOverride != null) b.txtLocation.setText(labelOverride);
            else b.txtLocation.setText(R.string.map_tap_to_place);
            if (map != null) {
                MapHelper.center(map, fallbackCenter, zoom);
                if (marker != null) {
                    map.getOverlays().remove(marker);
                    marker = null;
                }
                map.invalidate();
            }
            return;
        }
        b.txtLocation.setText(labelOverride != null ? labelOverride
                : MapHelper.label(b.getRoot().getContext(), location));
        if (map != null) {
            MapHelper.center(map, location, zoom);
            if (marker == null) {
                marker = MapHelper.addMarker(map, MapHelper.toGeoPoint(location), false);
            } else {
                marker.setPosition(MapHelper.toGeoPoint(location));
            }
            map.invalidate();
        }
    }

    public void onResume() {
        if (map != null) map.onResume();
    }

    public void onPause() {
        if (map != null) map.onPause();
    }

    public void onDestroy() {
        if (map != null) map.onDetach();
    }
}
