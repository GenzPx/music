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
package com.genzpx.music.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.genzpx.music.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Baca lagu dari MediaStore (index bawaan Android). Nol koneksi jaringan. */
public class MediaLibrary {

    private static final MediaLibrary INSTANCE = new MediaLibrary();
    private List<Song> songs = new ArrayList<>();
    private volatile boolean loaded = false;

    private MediaLibrary() {}
    public static MediaLibrary get() { return INSTANCE; }
    public boolean isLoaded() { return loaded; }

    public synchronized List<Song> getSongs() { return new ArrayList<>(songs); }

    /** Jalankan di background thread. */
    public void load(Context context) {
        List<Song> result = new ArrayList<>();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATE_ADDED
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " > 15000";
        String sort = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        // Urutan akhir ditentukan di sorted(), kueri hanya perlu hasil yang stabil

        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sort);
            if (c != null) {
                int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
                int iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
                while (c.moveToNext()) {
                    result.add(new Song(c.getLong(iId), c.getString(iTitle), c.getString(iArtist),
                            c.getString(iAlbum), c.getLong(iAlbumId), c.getLong(iDur),
                            c.getString(iData), c.getInt(iTrack), c.getLong(iDate)));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        synchronized (this) { songs = result; }
        loaded = true;
    }

    public static class Group {
        public final String key;
        public final String title;
        public final String subtitle;
        public final List<Song> items;
        Group(String key, String title, String subtitle, List<Song> items) {
            this.key = key; this.title = title; this.subtitle = subtitle; this.items = items;
        }
        public long albumIdForArt() { return items.isEmpty() ? -1 : items.get(0).albumId; }
    }

    /** Daftar lagu terurut sesuai pilihan pengguna. */
    public List<Song> sorted() {
        List<Song> l = getSongs();
        final int mode = Prefs.get().getSort();
        Collections.sort(l, new Comparator<Song>() {
            @Override public int compare(Song a, Song b) {
                switch (mode) {
                    case Prefs.SORT_ARTIST: {
                        int c = a.artist.compareToIgnoreCase(b.artist);
                        return c != 0 ? c : a.title.compareToIgnoreCase(b.title);
                    }
                    case Prefs.SORT_ALBUM: {
                        int c = a.album.compareToIgnoreCase(b.album);
                        if (c != 0) return c;
                        if (a.track != b.track) return Integer.compare(a.track, b.track);
                        return a.title.compareToIgnoreCase(b.title);
                    }
                    case Prefs.SORT_DATE:
                        return Long.compare(b.dateAdded, a.dateAdded);
                    case Prefs.SORT_DURATION:
                        return Long.compare(b.duration, a.duration);
                    default:
                        return a.title.compareToIgnoreCase(b.title);
                }
            }
        });
        if (Prefs.get().isSortDescending()) Collections.reverse(l);
        return l;
    }

    public List<Group> albums() {
        Map<String, List<Song>> map = new LinkedHashMap<>();
        for (Song s : getSongs()) {
            String key = s.album + "\u0000" + s.albumId;
            List<Song> l = map.get(key);
            if (l == null) { l = new ArrayList<>(); map.put(key, l); }
            l.add(s);
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<Song>> e : map.entrySet()) {
            List<Song> l = e.getValue();
            Collections.sort(l, new Comparator<Song>() {
                @Override public int compare(Song a, Song b) {
                    if (a.track != b.track) return Integer.compare(a.track, b.track);
                    return a.title.compareToIgnoreCase(b.title);
                }
            });
            out.add(new Group(e.getKey(), l.get(0).album, l.get(0).artist, l));
        }
        sortGroups(out);
        return out;
    }

    public List<Group> artists() {
        Map<String, List<Song>> map = new LinkedHashMap<>();
        for (Song s : getSongs()) {
            String key = s.artist.toLowerCase(Locale.getDefault());
            List<Song> l = map.get(key);
            if (l == null) { l = new ArrayList<>(); map.put(key, l); }
            l.add(s);
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<Song>> e : map.entrySet()) {
            List<Song> l = e.getValue();
            out.add(new Group(e.getKey(), l.get(0).artist, l.size() + " lagu", l));
        }
        sortGroups(out);
        return out;
    }

    public List<Group> folders() {
        Map<String, List<Song>> map = new LinkedHashMap<>();
        for (Song s : getSongs()) {
            String key = s.folderPath();
            List<Song> l = map.get(key);
            if (l == null) { l = new ArrayList<>(); map.put(key, l); }
            l.add(s);
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<Song>> e : map.entrySet()) {
            List<Song> l = e.getValue();
            out.add(new Group(e.getKey(), l.get(0).folderName(), l.size() + " lagu", l));
        }
        sortGroups(out);
        return out;
    }

    private void sortGroups(List<Group> list) {
        Collections.sort(list, new Comparator<Group>() {
            @Override public int compare(Group a, Group b) { return a.title.compareToIgnoreCase(b.title); }
        });
    }

    public List<Song> search(String query) {
        List<Song> out = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return out;
        String q = query.toLowerCase(Locale.getDefault());
        for (Song s : getSongs()) {
            if (s.title.toLowerCase(Locale.getDefault()).contains(q)
                    || s.artist.toLowerCase(Locale.getDefault()).contains(q)
                    || s.album.toLowerCase(Locale.getDefault()).contains(q)) out.add(s);
        }
        return out;
    }

    public Song byId(long id) {
        for (Song s : getSongs()) if (s.id == id) return s;
        return null;
    }
}
