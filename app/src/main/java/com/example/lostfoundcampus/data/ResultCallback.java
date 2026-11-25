package com.example.lostfoundcampus.data;

/** Résultat asynchrone d'un dépôt, toujours rappelé sur le fil principal. */
public interface ResultCallback<T> {
    void onSuccess(T value);

    /** @param message message en français, prêt à afficher (explique le problème et la solution). */
    void onError(String message);
}
