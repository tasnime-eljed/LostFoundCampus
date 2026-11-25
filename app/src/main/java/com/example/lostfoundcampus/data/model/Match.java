package com.example.lostfoundcampus.data.model;

/** Résultat de la fonction SQL match_candidates : une annonce candidate et son score (sur 100). */
public class Match {

    public final String announcementId;
    public final int score;

    public Match(String announcementId, int score) {
        this.announcementId = announcementId;
        this.score = score;
    }
}
