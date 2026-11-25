package com.example.lostfoundcampus.util;

import android.os.Handler;
import android.os.Looper;

/**
 * Interrogation périodique du serveur (messagerie par polling).
 * La tâche signale la fin de chaque tour avec done(succès) : en cas d'échec, l'intervalle
 * double jusqu'à maxMs, puis revient à la normale au premier succès.
 * À démarrer dans onResume() et arrêter dans onPause() pour ne rien interroger écran caché.
 */
public final class Poller {

    public interface Task {
        void run(Poller poller);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long baseMs;
    private final long maxMs;
    private final Task task;
    private long currentMs;
    private boolean running;
    private boolean inFlight;
    /** now() appelé pendant un tour : on en relance un dès la fin de celui-ci. */
    private boolean rerun;
    /** Change à chaque start/stop : un tour lancé avant un stop ne reprogramme rien. */
    private int generation;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || inFlight) return;
            inFlight = true;
            task.run(Poller.this);
        }
    };

    public Poller(long baseMs, long maxMs, Task task) {
        this.baseMs = baseMs;
        this.maxMs = maxMs;
        this.task = task;
        this.currentMs = baseMs;
    }

    /** Démarre et lance un premier tour immédiatement. */
    public void start() {
        if (running) return;
        running = true;
        generation++;
        currentMs = baseMs;
        inFlight = false;
        handler.post(tick);
    }

    public void stop() {
        running = false;
        generation++;
        inFlight = false;
        rerun = false;
        handler.removeCallbacks(tick);
    }

    /** Relance un tour tout de suite (après un envoi, par exemple). */
    public void now() {
        if (!running) return;
        handler.removeCallbacks(tick);
        if (inFlight) rerun = true;
        else handler.post(tick);
    }

    /** Callback de fin de tour, à passer à la requête lancée par la tâche. */
    public Done token() {
        final int gen = generation;
        return success -> {
            if (gen != generation) return;
            inFlight = false;
            currentMs = success ? baseMs : Math.min(maxMs, currentMs * 2);
            if (running) {
                handler.removeCallbacks(tick);
                handler.postDelayed(tick, rerun ? 0 : currentMs);
            }
            rerun = false;
        };
    }

    public interface Done {
        void done(boolean success);
    }
}
