package com.example.lostfoundcampus.data.model;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.example.lostfoundcampus.R;

/** Couleurs d'objets proposées (et détectées par l'analyse à l'étape 2). */
public enum ColorSwatch {
    BLACK(R.string.color_black, R.color.swatch_black),
    WHITE(R.string.color_white, R.color.swatch_white),
    GREY(R.string.color_grey, R.color.swatch_grey),
    RED(R.string.color_red, R.color.swatch_red),
    BLUE(R.string.color_blue, R.color.swatch_blue),
    GREEN(R.string.color_green, R.color.swatch_green),
    BROWN(R.string.color_brown, R.color.swatch_brown),
    OTHER(R.string.color_other, R.color.swatch_other);

    @StringRes
    public final int labelRes;
    @ColorRes
    public final int colorRes;

    ColorSwatch(@StringRes int labelRes, @ColorRes int colorRes) {
        this.labelRes = labelRes;
        this.colorRes = colorRes;
    }
}
