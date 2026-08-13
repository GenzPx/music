/*
 * Video - pemutar video offline tanpa iklan
 * Copyright (C) 2026 GenzPx
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.genzpx.video.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;

import com.genzpx.video.model.Video;

import java.io.File;

/** Pemuat gambar mini video. Cache di memori, tanpa pustaka eksternal. */
public class ThumbLoader {

    private static final LruCache<Long, Bitmap> CACHE =
            new LruCache<Long, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 6144)) {
                @Override protected int sizeOf(Long key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private static final LruCache<Long, Boolean> MISSES = new LruCache<>(400);

    public static Bitmap cached(long id) { return CACHE.get(id); }

    public static Bitmap load(Context ctx, Video v) {
        if (v == null) return null;
        Bitmap b = CACHE.get(v.id);
        if (b != null) return b;
        if (MISSES.get(v.id) != null) return null;

        Bitmap out = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                out = ctx.getContentResolver().loadThumbnail(
                        v.uri(), new Size(512, 512), new CancellationSignal());
            } else {
                File f = new File(v.path);
                if (f.exists()) {
                    out = ThumbnailUtils.createVideoThumbnail(
                            v.path, MediaStore.Images.Thumbnails.MINI_KIND);
                }
            }
        } catch (Throwable ignored) {
        }

        if (out != null) CACHE.put(v.id, out);
        else MISSES.put(v.id, Boolean.TRUE);
        return out;
    }

    public static void clear() { CACHE.evictAll(); MISSES.evictAll(); }
}
