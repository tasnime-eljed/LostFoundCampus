package com.example.lostfoundcampus.data.model;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/** Couleur trouvée sur une photo : son nom en français et la teinte réelle mesurée. */
public final class DetectedColor {

    @NonNull
    public final String name;
    @ColorInt
    public final int rgb;
    /** Part de la zone analysée couverte par cette couleur (0..1). */
    public final float share;

    public DetectedColor(@NonNull String name, @ColorInt int rgb, float share) {
        this.name = name;
        this.rgb = rgb;
        this.share = share;
    }
}
