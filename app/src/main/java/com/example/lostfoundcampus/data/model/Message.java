package com.example.lostfoundcampus.data.model;

public class Message {

    public enum State { SENDING, SENT, FAILED }

    /** Identifiant serveur (croissant) ; négatif tant que le message n'est pas enregistré. */
    public long id;
    public final String text;
    public long sentAt;
    public final String senderId;
    public State state;
    public boolean isRead;

    public Message(long id, String text, long sentAt, String senderId, State state, boolean isRead) {
        this.id = id;
        this.text = text;
        this.sentAt = sentAt;
        this.senderId = senderId;
        this.state = state;
        this.isRead = isRead;
    }
}
