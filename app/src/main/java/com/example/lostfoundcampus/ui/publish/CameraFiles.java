package com.example.lostfoundcampus.ui.publish;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/** Fichier temporaire partagé avec l'appareil photo via FileProvider (cache privé de l'app). */
final class CameraFiles {

    private CameraFiles() {
    }

    static Uri newTarget(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Dossier photo indisponible");
        File file = new File(dir, "LF_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }
}
