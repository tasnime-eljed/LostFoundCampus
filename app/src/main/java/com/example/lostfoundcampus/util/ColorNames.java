package com.example.lostfoundcampus.util;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Noms de couleurs en français : rapprochement d'une teinte mesurée avec le nom le plus proche
 * (distance dans l'espace CIELAB, proche de la perception humaine), et mise en forme du texte saisi.
 */
public final class ColorNames {

    /** Longueur maximale d'une couleur saisie (même limite côté Supabase). */
    public static final int MAX_LENGTH = 30;

    private static final String[] NAMES = {
            "Noir", "Gris anthracite", "Gris", "Argent", "Gris clair", "Blanc", "Blanc cassé", "Beige",
            "Camel", "Marron", "Marron foncé", "Doré", "Or rose", "Jaune", "Jaune pâle", "Moutarde",
            "Orange", "Corail", "Rouge", "Bordeaux", "Rose", "Rose pâle", "Fuchsia", "Violet", "Lilas",
            "Mauve", "Prune", "Bleu marine", "Bleu", "Bleu clair", "Turquoise", "Bleu canard",
            "Vert", "Vert foncé", "Vert clair", "Vert menthe", "Kaki"};

    private static final int[] RGB = {
            0x111111, 0x3A3D42, 0x808080, 0xB4B8BE, 0xD6D8DB, 0xFAFAFA, 0xF0EADA, 0xDECBA4,
            0xC18C50, 0x784828, 0x4A2C1A, 0xD4AF37, 0xDCA596, 0xF5D21E, 0xFAF096, 0xC8A01E,
            0xF5821E, 0xFA8072, 0xD22328, 0x6E1423, 0xF082AA, 0xF8C8D7, 0xD7288C, 0x7832A0, 0xC8A2DC,
            0xAF78A5, 0x5F2850, 0x142355, 0x285AC8, 0x8CBEEB, 0x28C8C8, 0x006E78,
            0x28963C, 0x145028, 0x8CD278, 0xAAE6C8, 0x6E6E3C};

    /** Anciennes valeurs (nuancier fixe) encore présentes dans la base. */
    private static final String[][] LEGACY = {
            {"BLACK", "Noir"}, {"WHITE", "Blanc"}, {"GREY", "Gris"}, {"RED", "Rouge"},
            {"BLUE", "Bleu"}, {"GREEN", "Vert"}, {"BROWN", "Marron"}, {"OTHER", null}};

    private static double[][] labCache;

    private ColorNames() {
    }

    /** Nom français le plus proche de la teinte. */
    @NonNull
    public static String nearest(@ColorInt int rgb) {
        double[][] labs = labs();
        double[] lab = toLab(rgb);
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < labs.length; i++) {
            double dl = lab[0] - labs[i][0], da = lab[1] - labs[i][1], db = lab[2] - labs[i][2];
            double d = dl * dl + da * da + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return NAMES[best];
    }

    /** Teinte de référence d'un nom connu (insensible à la casse), sinon {@code null}. */
    @Nullable
    @ColorInt
    public static Integer rgbFor(@Nullable String name) {
        if (name == null) return null;
        String n = name.trim();
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(n)) return 0xFF000000 | RGB[i];
        }
        return null;
    }

    /** Texte à afficher pour une couleur stockée (convertit les anciens codes BLACK, BLUE…). */
    @Nullable
    public static String display(@Nullable String stored) {
        if (stored == null) return null;
        String s = stored.trim();
        for (String[] l : LEGACY) if (l[0].equals(s)) return l[1];
        return s.isEmpty() ? null : s;
    }

    /** Mise en forme d'une couleur saisie : espaces nettoyés, majuscule initiale, 30 caractères max. */
    @Nullable
    public static String normalize(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return null;
        if (s.length() > MAX_LENGTH) s = s.substring(0, MAX_LENGTH).trim();
        return s.substring(0, 1).toUpperCase(Locale.FRANCE) + s.substring(1);
    }

    public static boolean same(@Nullable String a, @Nullable String b) {
        String na = normalize(a), nb = normalize(b);
        return na != null && na.equalsIgnoreCase(nb);
    }

    private static synchronized double[][] labs() {
        if (labCache == null) {
            labCache = new double[RGB.length][];
            for (int i = 0; i < RGB.length; i++) labCache[i] = toLab(RGB[i]);
        }
        return labCache;
    }

    private static double[] toLab(int rgb) {
        double r = lin(Color.red(rgb) / 255.0), g = lin(Color.green(rgb) / 255.0), b = lin(Color.blue(rgb) / 255.0);
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722);
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }
}
