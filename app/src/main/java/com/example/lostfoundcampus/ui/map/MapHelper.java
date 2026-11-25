package com.example.lostfoundcampus.ui.map;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.lostfoundcampus.BuildConfig;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.CampusLocation;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;

/**
 * Création des cartes OpenStreetMap (osmdroid). Toute erreur est absorbée :
 * l'appelant affiche alors l'état de repli « Carte indisponible ».
 */
public final class MapHelper {

    private static final String PREFS = "osmdroid";
    /** Dossier de cache pour les tuiles OpenStreetMap. */
    private static final String TILE_CACHE = "tiles_v2";

    /**
     * Tuiles OpenStreetMap France (données OpenStreetMap, serveur gratuit et sans clé d'API).
     */
    private static final OnlineTileSourceBase OSM_FR = new XYTileSource("OSMFr",
            0, 19, 256, ".png", new String[]{
            "https://a.tile.openstreetmap.fr/osmfr/",
            "https://b.tile.openstreetmap.fr/osmfr/",
            "https://c.tile.openstreetmap.fr/osmfr/"},
            "© OpenStreetMap contributors, ODbL - OSM France");

    /** Centre du campus, utilisé tant qu'aucun lieu n'est choisi. À adapter à votre campus. */
    private static final double CAMPUS_LAT = 36.8368;
    private static final double CAMPUS_LON = 10.1475;

    @NonNull
    public static CampusLocation defaultCenter() {
        return new CampusLocation(CAMPUS_LAT, CAMPUS_LON, null);
    }

    private MapHelper() {
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return true;
    }

    /** Crée la carte dans le conteneur, ou renvoie null si elle ne peut pas s'afficher. */
    @Nullable
    public static MapView createMap(@NonNull FrameLayout container, boolean interactive) {
        Context context = container.getContext();
        if (!isNetworkAvailable(context)) return null;
        try {
            IConfigurationProvider config = Configuration.getInstance();
            config.load(context, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
            // User-agent conforme pour les serveurs de tuiles OpenStreetMap.
            config.setUserAgentValue(context.getPackageName() + "/" + BuildConfig.VERSION_NAME);

            // Cache des tuiles dans le dossier privé de l'app : aucune permission de stockage.
            File base = new File(context.getCacheDir(), PREFS);
            config.setOsmdroidBasePath(base);
            config.setOsmdroidTileCache(new File(base, TILE_CACHE));

            MapView map = new MapView(context);
            map.setTileSource(OSM_FR);
            map.setMultiTouchControls(interactive);
            map.setTilesScaledToDpi(true);
            map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
            map.setMinZoomLevel(5.0);
            map.setMaxZoomLevel(19.0);
            map.setImportantForAccessibility(MapView.IMPORTANT_FOR_ACCESSIBILITY_NO);
            applyPaletteFilter(map);
            container.addView(map, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return map;
        } catch (Throwable t) {
            container.removeAllViews();
            return null;
        }
    }

    /**
     * Équivalent du « style JSON » de Google Maps : les tuiles OSM sont désaturées
     * puis légèrement réchauffées pour s'accorder au fond crème de l'app.
     */
    private static void applyPaletteFilter(MapView map) {
        ColorMatrix muted = new ColorMatrix();
        muted.setSaturation(0.50f);
        ColorMatrix warm = new ColorMatrix(new float[]{
                1.00f, 0, 0, 0, 8,
                0, 0.97f, 0, 0, 4,
                0, 0, 0.90f, 0, 0,
                0, 0, 0, 1, 0
        });
        muted.postConcat(warm);
        map.getOverlayManager().getTilesOverlay().setColorFilter(new ColorMatrixColorFilter(muted));
    }

    @NonNull
    public static Marker addMarker(@NonNull MapView map, @NonNull GeoPoint point, boolean draggable) {
        Context context = map.getContext();
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_map_marker));
        marker.setTitle(context.getString(R.string.cd_map_marker));
        marker.setDraggable(draggable);
        marker.setOnMarkerClickListener((m, mapView) -> true);
        map.getOverlays().add(marker);
        return marker;
    }

    public static void center(@NonNull MapView map, @NonNull CampusLocation location, int zoom) {
        map.getController().setZoom((double) zoom);
        map.getController().setCenter(toGeoPoint(location));
    }

    @NonNull
    public static GeoPoint toGeoPoint(@NonNull CampusLocation location) {
        return new GeoPoint(location.latitude, location.longitude);
    }

    /** Texte du lieu : la précision saisie, sinon « Point choisi sur la carte ». */
    @NonNull
    public static String label(@NonNull Context context, @NonNull CampusLocation location) {
        return location.hasPrecision() ? location.precision.trim() : context.getString(R.string.map_point_chosen);
    }
}
