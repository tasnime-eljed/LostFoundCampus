package com.example.lostfoundcampus.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Prépare les photos avant envoi (exigence de performance du rapport) :
 * redimensionnement, orientation EXIF corrigée, JPEG compressé sous une taille maximale.
 * À appeler hors du fil principal.
 */
public final class PhotoCompressor {

    public static final int PHOTO_MAX_SIDE = 1600;
    public static final int PHOTO_MAX_BYTES = 1_800_000;   // bucket limité à 2 Mo
    public static final int AVATAR_MAX_SIDE = 512;
    public static final int AVATAR_MAX_BYTES = 900_000;    // bucket limité à 1 Mo
    public static final int ANALYSIS_MAX_SIDE = 1024;

    private PhotoCompressor() {
    }

    @NonNull
    public static Bitmap decode(Context context, Uri uri, int maxSide) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Flux introuvable");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Image illisible");

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new IOException("Image illisible");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException ignored) {
            // pas d'EXIF : on garde l'orientation telle quelle
        }

        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.postRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.postScale(-1, 1); break;
            default: break;
        }
        float scale = Math.min(1f, (float) maxSide / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (scale < 1f) m.postScale(scale, scale);
        if (!m.isIdentity()) {
            Bitmap transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (transformed != bitmap) bitmap.recycle();
            bitmap = transformed;
        }
        return bitmap;
    }

    @NonNull
    public static byte[] toJpeg(Bitmap bitmap, int maxBytes) {
        int quality = 80;
        byte[] out;
        do {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            out = bos.toByteArray();
            quality -= 10;
        } while (out.length > maxBytes && quality >= 40);
        return out;
    }

    @NonNull
    public static byte[] compress(Context context, Uri uri, int maxSide, int maxBytes) throws IOException {
        Bitmap bitmap = decode(context, uri, maxSide);
        try {
            return toJpeg(bitmap, maxBytes);
        } finally {
            bitmap.recycle();
        }
    }
}
