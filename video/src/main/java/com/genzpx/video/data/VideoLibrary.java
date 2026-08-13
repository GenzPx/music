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
package com.genzpx.video.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.genzpx.video.model.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Membaca daftar video dari MediaStore. Nol koneksi jaringan. */
public class VideoLibrary {

    private static final VideoLibrary INSTANCE = new VideoLibrary();
    private List<Video> videos = new ArrayList<>();
    private volatile boolean loaded = false;

    private VideoLibrary() {}
    public static VideoLibrary get() { return INSTANCE; }
    public boolean isLoaded() { return loaded; }

    public synchronized List<Video> getAll() { return new ArrayList<>(videos); }

    /** Jalankan di thread latar. */
    public void load(Context context) {
        List<Video> result = new ArrayList<>();

        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATE_ADDED
        };

        // Buang klip sangat pendek supaya thumbnail sistem dan sampah lain tidak ikut
        String selection = MediaStore.Video.Media.DURATION + " > 3000";

        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC");
            if (c != null) {
                int iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int iData = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
                int iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
                int iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);

                while (c.moveToNext()) {
                    result.add(new Video(
                            c.getLong(iId),
                            c.getString(iName),
                            c.getLong(iDur),
                            c.getLong(iSize),
                            c.getString(iData),
                            c.getInt(iW),
                            c.getInt(iH),
                            c.getLong(iDate)));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        synchronized (this) { videos = result; }
        loaded = true;
    }

    /** Daftar video terurut sesuai pilihan pengguna. */
    public List<Video> sorted() {
        List<Video> l = getAll();
        final int mode = Prefs.get().getSort();
        Collections.sort(l, new Comparator<Video>() {
            @Override public int compare(Video a, Video b) {
                switch (mode) {
                    case Prefs.SORT_NAME: return a.title.compareToIgnoreCase(b.title);
                    case Prefs.SORT_SIZE: return Long.compare(b.size, a.size);
                    case Prefs.SORT_DURATION: return Long.compare(b.duration, a.duration);
                    default: return Long.compare(b.dateAdded, a.dateAdded);
                }
            }
        });
        return l;
    }

    public static class Folder {
        public final String path;
        public final String name;
        public final List<Video> items;
        Folder(String path, String name, List<Video> items) {
            this.path = path; this.name = name; this.items = items;
        }
    }

    public List<Folder> folders() {
        Map<String, List<Video>> map = new LinkedHashMap<>();
        for (Video v : sorted()) {
            String key = v.folderPath();
            List<Video> l = map.get(key);
            if (l == null) { l = new ArrayList<>(); map.put(key, l); }
            l.add(v);
        }
        List<Folder> out = new ArrayList<>();
        for (Map.Entry<String, List<Video>> e : map.entrySet()) {
            out.add(new Folder(e.getKey(), e.getValue().get(0).folderName(), e.getValue()));
        }
        Collections.sort(out, new Comparator<Folder>() {
            @Override public int compare(Folder a, Folder b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    public List<Video> search(String query) {
        List<Video> out = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return out;
        String q = query.toLowerCase(Locale.getDefault());
        for (Video v : sorted()) {
            if (v.title.toLowerCase(Locale.getDefault()).contains(q)) out.add(v);
        }
        return out;
    }

    public Video byId(long id) {
        for (Video v : getAll()) if (v.id == id) return v;
        return null;
    }
}
