package com.example.lostfoundcampus.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * Point réel sur la carte (latitude / longitude) avec une précision facultative
 * saisie par l'utilisateur. Deviendra un GeoPoint Firestore à l'étape 2.
 */
public class CampusLocation implements Serializable {

    public final double latitude;
    public final double longitude;
    @Nullable
    public final String precision;

    public CampusLocation(double latitude, double longitude, @Nullable String precision) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.precision = precision;
    }

    public boolean hasPrecision() {
        return precision != null && !precision.trim().isEmpty();
    }

    @NonNull
    public CampusLocation withPrecision(@Nullable String newPrecision) {
        return new CampusLocation(latitude, longitude, newPrecision);
    }
}
