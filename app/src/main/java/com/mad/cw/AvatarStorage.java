package com.mad.cw;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Persists the user's profile photo as a JPEG in app-private storage ({@code filesDir/profile_avatar.jpg}).
 */
public final class AvatarStorage {

    private static final String FILE_NAME = "profile_avatar.jpg";
    private static final int MAX_EDGE_PX = 720;

    private AvatarStorage() {}

    @NonNull
    public static File getFile(@NonNull Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    public static boolean hasLocalAvatar(@NonNull Context context) {
        File f = getFile(context);
        return f.exists() && f.length() > 0;
    }

    public static void clear(@NonNull Context context) {
        File f = getFile(context);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /**
     * Copies and scales an image from a content {@link Uri} into {@link #getFile(Context)}.
     *
     * @return true if a file was written
     */
    public static boolean savePickedImage(@NonNull Context context, @Nullable Uri sourceUri) throws IOException {
        if (sourceUri == null) {
            return false;
        }
        ContentResolver cr = context.getApplicationContext().getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in0 = cr.openInputStream(sourceUri)) {
            if (in0 == null) {
                return false;
            }
            BitmapFactory.decodeStream(in0, null, bounds);
        }
        int w = bounds.outWidth;
        int h = bounds.outHeight;
        if (w <= 0 || h <= 0) {
            return false;
        }
        int sample = 1;
        while (w / sample > MAX_EDGE_PX || h / sample > MAX_EDGE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in1 = cr.openInputStream(sourceUri)) {
            if (in1 == null) {
                return false;
            }
            bmp = BitmapFactory.decodeStream(in1, null, opts);
        }
        if (bmp == null) {
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(getFile(context))) {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                bmp.recycle();
                return false;
            }
        } finally {
            bmp.recycle();
        }
        return true;
    }
}
