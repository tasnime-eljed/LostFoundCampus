package com.example.lostfoundcampus.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Gestion de l'affichage bord à bord (imposé à partir d'Android 15). */
public final class InsetsUtil {

    private InsetsUtil() {
    }

    /**
     * Ajoute les marges système au padding initial de la vue.
     *
     * @param top    ajoute la hauteur de la barre d'état
     * @param bottom ajoute la hauteur de la barre de navigation
     * @param ime    remonte le contenu au-dessus du clavier
     */
    public static void applyPadding(View view, boolean top, boolean bottom, boolean ime) {
        final int left = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            int extraBottom = 0;
            if (bottom) extraBottom = bars.bottom;
            if (ime) extraBottom = Math.max(extraBottom, keyboard.bottom);
            v.setPadding(left + bars.left, initialTop + (top ? bars.top : 0),
                    right + bars.right, initialBottom + extraBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Décale une vue flottante (bouton retour sur photo) sous la barre d'état. */
    public static void applyTopMargin(View view) {
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        final int initial = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = initial + bars.top;
            v.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void applyTop(View view) {
        applyPadding(view, true, false, false);
    }

    public static void applyBottom(View view, boolean ime) {
        applyPadding(view, false, true, ime);
    }
}
