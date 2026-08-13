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
package com.genzpx.video.model;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.Serializable;

public class Video implements Serializable {
    public final long id;
    public final String title;
    public final long duration;   // ms
    public final long size;       // byte
    public final String path;
    public final int width;
    public final int height;
    public final long dateAdded;

    public Video(long id, String title, long duration, long size,
                 String path, int width, int height, long dateAdded) {
        this.id = id;
        this.title = title == null || title.isEmpty() ? "Tanpa judul" : title;
        this.duration = duration;
        this.size = size;
        this.path = path == null ? "" : path;
        this.width = width;
        this.height = height;
        this.dateAdded = dateAdded;
    }

    public Uri uri() {
        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
    }

    public String folderPath() {
        int i = path.lastIndexOf('/');
        return i > 0 ? path.substring(0, i) : "/";
    }

    public String folderName() {
        String f = folderPath();
        int i = f.lastIndexOf('/');
        return i >= 0 && i < f.length() - 1 ? f.substring(i + 1) : f;
    }

    /** Contoh: "1920x1080" atau string kosong kalau tidak diketahui. */
    public String resolution() {
        if (width <= 0 || height <= 0) return "";
        return width + "x" + height;
    }

    /** Label ringkas seperti "1080p" untuk ditampilkan di daftar. */
    public String qualityLabel() {
        int shorter = Math.min(width, height);
        if (shorter <= 0) return "";
        if (shorter >= 2160) return "4K";
        if (shorter >= 1440) return "1440p";
        if (shorter >= 1080) return "1080p";
        if (shorter >= 720) return "720p";
        if (shorter >= 480) return "480p";
        return shorter + "p";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Video)) return false;
        return id == ((Video) o).id;
    }

    @Override public int hashCode() { return (int) (id ^ (id >>> 32)); }
}
