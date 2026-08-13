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
package com.genzpx.music.model;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.Serializable;

public class Song implements Serializable {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final long duration;
    public final String path;
    public final int track;
    public final long dateAdded;

    public Song(long id, String title, String artist, String album,
                long albumId, long duration, String path, int track) {
        this(id, title, artist, album, albumId, duration, path, track, 0L);
    }

    public Song(long id, String title, String artist, String album,
                long albumId, long duration, String path, int track, long dateAdded) {
        this.id = id;
        this.title = title == null || title.isEmpty() ? "Tanpa judul" : title;
        this.artist = artist == null || artist.isEmpty() || "<unknown>".equals(artist)
                ? "Artis tidak diketahui" : artist;
        this.album = album == null || album.isEmpty() ? "Album tidak diketahui" : album;
        this.albumId = albumId;
        this.duration = duration;
        this.path = path == null ? "" : path;
        this.track = track;
        this.dateAdded = dateAdded;
    }

    public Uri uri() {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    public Uri albumArtUri() {
        return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId);
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

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        return id == ((Song) o).id;
    }

    @Override public int hashCode() { return (int) (id ^ (id >>> 32)); }
}
