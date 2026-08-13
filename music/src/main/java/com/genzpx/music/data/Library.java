/*
 * Music - pemutar musik offline tanpa iklan
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
package com.genzpx.music.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.genzpx.music.model.Song;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Favorit, daftar putar, dan riwayat pemutaran.
 *
 * Disimpan sebagai teks sederhana di SharedPreferences, bukan basis data,
 * supaya tidak menambah pustaka apa pun. Isinya hanya berupa id lagu.
 * Tidak pernah meninggalkan perangkat.
 */
public class Library {

    private static final String SEP = ",";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_RECENT = "recent";
    private static final String KEY_PLAYLIST_NAMES = "playlist_names";
    private static final String PREFIX_PLAYLIST = "playlist_";
    private static final int RECENT_LIMIT = 100;

    private static Library sInstance;
    private final SharedPreferences sp;

    private Library(Context c) {
        sp = c.getApplicationContext()
                .getSharedPreferences("music_library", Context.MODE_PRIVATE);
    }

    public static void init(Context c) { if (sInstance == null) sInstance = new Library(c); }
    public static Library get() { return sInstance; }

    // ---------- Bantuan penyimpanan ----------

    private List<Long> readIds(String key) {
        List<Long> out = new ArrayList<>();
        String raw = sp.getString(key, "");
        if (TextUtils.isEmpty(raw)) return out;
        for (String part : raw.split(SEP)) {
            try { out.add(Long.parseLong(part)); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private void writeIds(String key, List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(SEP);
            sb.append(ids.get(i));
        }
        sp.edit().putString(key, sb.toString()).apply();
    }

    /** Mengubah daftar id menjadi lagu, melewati yang berkasnya sudah hilang. */
    private List<Song> resolve(List<Long> ids) {
        List<Song> out = new ArrayList<>();
        for (Long id : ids) {
            Song s = MediaLibrary.get().byId(id);
            if (s != null) out.add(s);
        }
        return out;
    }

    // ---------- Favorit ----------

    public boolean isFavorite(long songId) { return readIds(KEY_FAV).contains(songId); }

    public void toggleFavorite(long songId) {
        List<Long> ids = readIds(KEY_FAV);
        if (!ids.remove(songId)) ids.add(0, songId);
        writeIds(KEY_FAV, ids);
    }

    public List<Song> favorites() { return resolve(readIds(KEY_FAV)); }

    public int favoriteCount() { return readIds(KEY_FAV).size(); }

    // ---------- Baru diputar ----------

    public void addRecent(long songId) {
        List<Long> ids = readIds(KEY_RECENT);
        ids.remove(songId);
        ids.add(0, songId);
        while (ids.size() > RECENT_LIMIT) ids.remove(ids.size() - 1);
        writeIds(KEY_RECENT, ids);
    }

    public List<Song> recentlyPlayed() { return resolve(readIds(KEY_RECENT)); }

    public void clearRecent() { sp.edit().remove(KEY_RECENT).apply(); }

    // ---------- Daftar putar ----------

    public List<String> playlistNames() {
        List<String> out = new ArrayList<>();
        String raw = sp.getString(KEY_PLAYLIST_NAMES, "");
        if (TextUtils.isEmpty(raw)) return out;
        // Nama dipisah baris baru supaya koma boleh dipakai di dalam nama
        for (String n : raw.split("\n")) if (!n.isEmpty()) out.add(n);
        return out;
    }

    private void savePlaylistNames(List<String> names) {
        sp.edit().putString(KEY_PLAYLIST_NAMES, TextUtils.join("\n", names)).apply();
    }

    /** @return false kalau nama kosong atau sudah dipakai */
    public boolean createPlaylist(String name) {
        if (name == null) return false;
        name = name.trim().replace("\n", " ");
        if (name.isEmpty()) return false;
        List<String> names = playlistNames();
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) return false;
        }
        names.add(name);
        savePlaylistNames(names);
        return true;
    }

    public void deletePlaylist(String name) {
        List<String> names = playlistNames();
        Iterator<String> it = names.iterator();
        while (it.hasNext()) if (it.next().equals(name)) it.remove();
        savePlaylistNames(names);
        sp.edit().remove(PREFIX_PLAYLIST + name).apply();
    }

    public boolean renamePlaylist(String oldName, String newName) {
        if (newName == null) return false;
        newName = newName.trim().replace("\n", " ");
        if (newName.isEmpty()) return false;
        List<String> names = playlistNames();
        if (!names.contains(oldName)) return false;
        for (String n : names) if (n.equalsIgnoreCase(newName) && !n.equals(oldName)) return false;

        List<Long> ids = readIds(PREFIX_PLAYLIST + oldName);
        names.set(names.indexOf(oldName), newName);
        savePlaylistNames(names);
        sp.edit().remove(PREFIX_PLAYLIST + oldName).apply();
        writeIds(PREFIX_PLAYLIST + newName, ids);
        return true;
    }

    public List<Song> playlistSongs(String name) {
        return resolve(readIds(PREFIX_PLAYLIST + name));
    }

    public int playlistCount(String name) {
        return readIds(PREFIX_PLAYLIST + name).size();
    }

    /** @return true kalau ditambahkan, false kalau lagu sudah ada di dalamnya */
    public boolean addToPlaylist(String name, long songId) {
        List<Long> ids = readIds(PREFIX_PLAYLIST + name);
        if (ids.contains(songId)) return false;
        ids.add(songId);
        writeIds(PREFIX_PLAYLIST + name, ids);
        return true;
    }

    public void addAllToPlaylist(String name, List<Song> songs) {
        Set<Long> ids = new LinkedHashSet<>(readIds(PREFIX_PLAYLIST + name));
        for (Song s : songs) ids.add(s.id);
        writeIds(PREFIX_PLAYLIST + name, new ArrayList<>(ids));
    }

    public void removeFromPlaylist(String name, long songId) {
        List<Long> ids = readIds(PREFIX_PLAYLIST + name);
        ids.remove(songId);
        writeIds(PREFIX_PLAYLIST + name, ids);
    }

    /** Nama daftar putar yang cocok, untuk pencarian. */
    public List<String> searchPlaylists(String q) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(q)) return out;
        String lower = q.toLowerCase(Locale.getDefault());
        for (String n : playlistNames()) {
            if (n.toLowerCase(Locale.getDefault()).contains(lower)) out.add(n);
        }
        return out;
    }
}
