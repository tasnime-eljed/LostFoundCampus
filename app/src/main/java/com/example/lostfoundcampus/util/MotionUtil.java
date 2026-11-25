package com.example.lostfoundcampus.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** Respect de la préférence système « Supprimer les animations ». */
public final class MotionUtil {

    private MotionUtil() {
    }

    public static boolean isReducedMotion(Context context) {
        float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) return true;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled();
    }
}
