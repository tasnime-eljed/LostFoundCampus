package com.example.lostfoundcampus.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fil d'arrière-plan pour les traitements d'image, et retour sur le fil principal. */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static void io(Runnable r) {
        IO.execute(r);
    }

    public static void main(Runnable r) {
        MAIN.post(r);
    }
}
