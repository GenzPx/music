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
import android.content.SharedPreferences;

import com.genzpx.video.model.Video;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Menyimpan video favorit dan riwayat tontonan.
 *
 * Semuanya cuma id angka di SharedPreferences, tidak ada basis data dan
 * tidak ada apa pun yang keluar dari perangkat. Kalau berkasnya sudah
 * dihapus dari penyimpanan, id-nya otomatis diabaikan saat dibaca.
 */
public class Watchlist {

    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final int HISTORY_LIMIT = 100;

    private static Watchlist sInstance;
    private final SharedPreferences sp;

    private Watchlist(Context c) {
        sp = c.getApplicationContext()
                .getSharedPreferences("video_watchlist", Context.MODE_PRIVATE);
    }

    public static void init(Context c) {
        if (sInstance == null) sInstance = new Watchlist(c);
    }

    public static Watchlist get() { return sInstance; }

    // ---------- Favorit ----------

    public boolean isFavorite(long id) {
        return ids(KEY_FAVORITES).contains(id);
    }

    /** @return keadaan baru setelah dibalik: true kalau sekarang jadi favorit. */
    public boolean toggleFavorite(long id) {
        LinkedHashSet<Long> set = ids(KEY_FAVORITES);
        boolean added;
        if (set.contains(id)) {
            set.remove(id);
            added = false;
        } else {
            set.add(id);
            added = true;
        }
        save(KEY_FAVORITES, set);
        return added;
    }

    public int favoriteCount() { return ids(KEY_FAVORITES).size(); }

    /** Favorit yang berkasnya masih ada, urut sesuai waktu ditandai (terbaru dulu). */
    public List<Video> favorites() {
        List<Long> order = new ArrayList<>(ids(KEY_FAVORITES));
        java.util.Collections.reverse(order);
        return resolve(order);
    }

    // ---------- Riwayat ----------

    /** Dicatat saat video mulai diputar; yang lama terdorong keluar. */
    public void addHistory(long id) {
        LinkedHashSet<Long> set = ids(KEY_HISTORY);
        set.remove(id);          // biar pindah ke posisi terbaru
        set.add(id);
        while (set.size() > HISTORY_LIMIT) {
            java.util.Iterator<Long> it = set.iterator();
            it.next();
            it.remove();
        }
        save(KEY_HISTORY, set);
    }

    /** Riwayat terbaru lebih dulu. */
    public List<Video> history() {
        List<Long> order = new ArrayList<>(ids(KEY_HISTORY));
        java.util.Collections.reverse(order);
        return resolve(order);
    }

    public int historyCount() { return ids(KEY_HISTORY).size(); }

    public void clearHistory() { sp.edit().remove(KEY_HISTORY).apply(); }

    /**
     * Video yang tontonannya belum selesai, terbaru dulu. Dipakai untuk
     * baris "Lanjutkan menonton".
     */
    public List<Video> continueWatching() {
        List<Video> out = new ArrayList<>();
        for (Video v : history()) {
            if (Prefs.get().getPosition(v.id) > 0) out.add(v);
        }
        return out;
    }

    // ---------- Internal ----------

    private LinkedHashSet<Long> ids(String key) {
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        String raw = sp.getString(key, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            if (part.isEmpty()) continue;
            try {
                out.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
                // entri rusak dilewati saja
            }
        }
        return out;
    }

    private void save(String key, LinkedHashSet<Long> set) {
        StringBuilder sb = new StringBuilder();
        for (Long id : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        sp.edit().putString(key, sb.toString()).apply();
    }

    /** Ubah daftar id jadi daftar video, buang yang berkasnya sudah hilang. */
    private List<Video> resolve(List<Long> order) {
        List<Video> out = new ArrayList<>();
        for (Long id : order) {
            Video v = VideoLibrary.get().byId(id);
            if (v != null) out.add(v);
        }
        return out;
    }
}
