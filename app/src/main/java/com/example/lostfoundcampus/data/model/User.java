package com.example.lostfoundcampus.data.model;

import androidx.annotation.Nullable;

public class User {

    public final String uid;
    public String fullName;
    public final String universityEmail;
    /** Chemin de la photo dans le bucket « avatars », ou null (initiales). */
    @Nullable
    public String photoPath;

    public User(String uid, String fullName, String universityEmail, @Nullable String photoPath) {
        this.uid = uid;
        this.fullName = fullName;
        this.universityEmail = universityEmail;
        this.photoPath = photoPath;
    }

    public String firstName() {
        String name = fullName == null ? "" : fullName.trim();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    public boolean hasPhoto() {
        return photoPath != null && !photoPath.isEmpty();
    }
}
