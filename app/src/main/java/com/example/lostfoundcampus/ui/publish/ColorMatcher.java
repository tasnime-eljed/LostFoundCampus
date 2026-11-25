package com.example.lostfoundcampus.ui.publish;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.palette.graphics.Palette;

import com.example.lostfoundcampus.data.model.DetectedColor;
import com.example.lostfoundcampus.util.ColorNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Couleurs réellement présentes sur la photo (Palette), nommées en français (lilas, bleu marine, argent…).
 * À appeler hors du fil principal.
 */
final class ColorMatcher {

    private static final int MAX_COLORS = 3;
    /** Une couleur doit couvrir au moins cette part de la zone pour être citée. */
    private static final float MIN_SHARE = 0.07f;
    /** Bord de la photo étudié pour reconnaître le fond (part de chaque côté). */
    private static final float BORDER_FRACTION = 0.12f;
    /** Une couleur qui couvre au moins cette part du bord est considérée comme le fond. */
    private static final float BACKGROUND_SHARE = 0.25f;
    /** Poids restant d'une couleur de fond au centre (elle peut aussi être celle de l'objet). */
    private static final float BACKGROUND_WEIGHT = 0.35f;

    private ColorMatcher() {
    }

    /**
     * @param skipSkin écarter les tons chair (main qui tient l'objet) ; inutile quand l'utilisateur
     *                 a déjà cadré l'objet.
     * @param background noms des couleurs du fond (voir {@link #backgroundColors}) : elles comptent
     *                   moins, pour que l'objet passe devant la table ou le sol qui l'entoure.
     * @return de 0 à 3 couleurs, la plus présente en premier.
     */
    @NonNull
    static List<DetectedColor> detect(Bitmap bitmap, boolean skipSkin, @NonNull Set<String> background) {
        // Sans filtre : le filtre par défaut écarte le quasi-blanc et le quasi-noir, couleurs fréquentes d'objets.
        Palette palette = Palette.from(bitmap).maximumColorCount(24).clearFilters().generate();
        List<Palette.Swatch> swatches = new ArrayList<>(palette.getSwatches());
        List<DetectedColor> out = new ArrayList<>();
        if (swatches.isEmpty()) return out;
        swatches.sort((a, b) -> Integer.compare(b.getPopulation(), a.getPopulation()));

        int total = 0, nonSkin = 0;
        for (Palette.Swatch s : swatches) {
            total += s.getPopulation();
            if (!isSkin(s.getRgb())) nonSkin += s.getPopulation();
        }
        // On n'écarte la peau que s'il reste assez d'autres couleurs (sinon l'objet est peut-être couleur chair).
        boolean dropSkin = skipSkin && nonSkin >= total * 0.25f;
        int base = dropSkin ? nonSkin : total;

        // Regroupement par nom : la teinte affichée est celle du groupe le plus représenté.
        Map<String, int[]> groups = new LinkedHashMap<>(); // nom -> {population, rgb}
        for (Palette.Swatch s : swatches) {
            if (dropSkin && isSkin(s.getRgb())) continue;
            String name = ColorNames.nearest(s.getRgb());
            int[] g = groups.get(name);
            if (g == null) groups.put(name, new int[]{s.getPopulation(), s.getRgb(), s.getPopulation()});
            else {
                g[0] += s.getPopulation();
                // Teinte affichée : celle de la nuance la plus représentée du groupe.
                if (s.getPopulation() > g[2]) {
                    g[1] = s.getRgb();
                    g[2] = s.getPopulation();
                }
            }
        }
        // Le fond n'est pénalisé que s'il reste une autre couleur à proposer.
        boolean allBackground = true;
        for (String name : groups.keySet()) if (!background.contains(name)) allBackground = false;
        if (!allBackground) {
            base = 0;
            for (Map.Entry<String, int[]> e : groups.entrySet()) {
                if (background.contains(e.getKey())) e.getValue()[0] = Math.round(e.getValue()[0] * BACKGROUND_WEIGHT);
                base += e.getValue()[0];
            }
        }
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, int[]> e : sorted) {
            float share = base == 0 ? 0f : (float) e.getValue()[0] / base;
            if (!out.isEmpty() && share < MIN_SHARE) break;
            out.add(new DetectedColor(e.getKey(), e.getValue()[1], share));
            if (out.size() >= MAX_COLORS) break;
        }
        return out;
    }

    /**
     * Couleurs du fond : celles qui dominent la bordure de la photo entière (on cadre l'objet au
     * centre, le bord montre la table, le sol ou le mur).
     */
    @NonNull
    static Set<String> backgroundColors(Bitmap full) {
        Set<String> out = new HashSet<>();
        int size = 48;
        Bitmap small = Bitmap.createScaledBitmap(full, size, size, true);
        int border = Math.max(1, Math.round(size * BORDER_FRACTION));
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean edge = x < border || y < border || x >= size - border || y >= size - border;
                if (!edge) continue;
                String name = ColorNames.nearest(small.getPixel(x, y));
                Integer c = counts.get(name);
                counts.put(name, c == null ? 1 : c + 1);
                total++;
            }
        }
        if (small != full) small.recycle();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= total * BACKGROUND_SHARE) out.add(e.getKey());
        }
        return out;
    }

    /** Tons chair, toutes carnations : teinte orangée, saturation moyenne, pas trop sombre. */
    static boolean isSkin(int rgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(rgb, hsv);
        return hsv[0] <= 45 && hsv[1] >= 0.18f && hsv[1] <= 0.65f && hsv[2] >= 0.35f;
    }
}
