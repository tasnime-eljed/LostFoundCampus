package com.example.lostfoundcampus.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Analyse ML Kit d'une photo : étiquettes, texte visible, couleurs présentes, et suggestions déduites. */
public class ImageAnalysis {

    /** Étiquettes ML Kit normalisées (minuscules, en anglais), utilisées pour les correspondances. */
    public final List<String> labels = new ArrayList<>();
    /** Étiquette principale, traduite pour l'affichage. */
    public final String displayLabel;
    public final int confidence;
    public final List<String> detectedTexts = new ArrayList<>();
    /** Couleurs réellement présentes (1 à 3), la plus présente en premier. */
    public final List<DetectedColor> colors = new ArrayList<>();
    /** Nom de la couleur principale (ex. « Lilas »), ou {@code null}. */
    @Nullable
    public final String dominantColor;
    public final Category suggestedCategory;
    public final String suggestedTitle;

    public ImageAnalysis(List<String> labels, String displayLabel, int confidence, List<String> detectedTexts,
                         @Nullable List<DetectedColor> colors, Category suggestedCategory, String suggestedTitle) {
        if (labels != null) this.labels.addAll(labels);
        this.displayLabel = displayLabel;
        this.confidence = confidence;
        if (detectedTexts != null) this.detectedTexts.addAll(detectedTexts);
        if (colors != null) this.colors.addAll(colors);
        this.dominantColor = this.colors.isEmpty() ? null : this.colors.get(0).name;
        this.suggestedCategory = suggestedCategory;
        this.suggestedTitle = suggestedTitle;
    }

    @Nullable
    public String mainText() {
        return detectedTexts.isEmpty() ? null : detectedTexts.get(0);
    }
}
