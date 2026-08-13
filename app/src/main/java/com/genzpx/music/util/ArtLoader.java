/*
 * Music - pemutar musik offline tanpa iklan
 * Copyright (C) 2026 GenzPX
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
package com.genzpx.music.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.LruCache;

import com.genzpx.music.model.Song;

import java.io.InputStream;

/** Loader sampul album dari file lokal. Cache di memori, tanpa library eksternal. */
public class ArtLoader {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8192)) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private static final Object NULL_MARK = new Object();
    private static final LruCache<String, Object> MISSES = new LruCache<>(500);

    public static Bitmap load(Context ctx, Song song, int size) {
        if (song == null || song.albumId < 0) return null;
        String key = song.albumId + "@" + size;

        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        if (MISSES.get(key) != null) return null;

        Bitmap bmp = null;
        InputStream is = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: cara resmi, hindari akses langsung ke tabel albumart
                bmp = ctx.getContentResolver().loadThumbnail(
                        song.uri(), new android.util.Size(size, size), null);
            } else {
                is = ctx.getContentResolver().openInputStream(song.albumArtUri());
                if (is != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    bmp = BitmapFactory.decodeStream(is, null, o);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
        }

        if (bmp != null) CACHE.put(key, bmp);
        else MISSES.put(key, NULL_MARK);
        return bmp;
    }

    public static void clear() { CACHE.evictAll(); MISSES.evictAll(); }
}
