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
package com.genzpx.music.ui;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.genzpx.music.R;
import com.genzpx.music.data.Library;
import com.genzpx.music.model.Song;
import com.genzpx.music.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/** Kumpulan aksi untuk satu lagu, dipakai bersama oleh beberapa layar. */
public class SongActions {

    /** Menu yang muncul saat sebuah lagu ditekan lama. */
    public static void showMenu(Activity a, Song s, Runnable onChanged) {
        boolean fav = Library.get().isFavorite(s.id);
        List<String> items = new ArrayList<>();
        items.add(a.getString(fav ? R.string.remove_from_favorites : R.string.add_to_favorites));
        items.add(a.getString(R.string.add_to_playlist));
        items.add(a.getString(R.string.set_as_ringtone));
        items.add(a.getString(R.string.share));
        items.add(a.getString(R.string.song_info));

        new AlertDialog.Builder(a)
                .setTitle(s.title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    switch (which) {
                        case 0:
                            Library.get().toggleFavorite(s.id);
                            Toast.makeText(a, Library.get().isFavorite(s.id)
                                            ? R.string.added_to_favorites
                                            : R.string.removed_from_favorites,
                                    Toast.LENGTH_SHORT).show();
                            if (onChanged != null) onChanged.run();
                            break;
                        case 1: addToPlaylist(a, s); break;
                        case 2: setAsRingtone(a, s); break;
                        case 3: share(a, s); break;
                        default: showInfo(a, s); break;
                    }
                })
                .show();
    }

    // ---------- Daftar putar ----------

    public static void addToPlaylist(Activity a, Song s) {
        List<String> names = Library.get().playlistNames();
        List<String> options = new ArrayList<>();
        options.add(a.getString(R.string.new_playlist));
        options.addAll(names);

        new AlertDialog.Builder(a)
                .setTitle(R.string.add_to_playlist)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        promptNewPlaylist(a, name -> {
                            Library.get().addToPlaylist(name, s.id);
                            Toast.makeText(a, a.getString(R.string.added_to_fmt, name),
                                    Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        String name = options.get(which);
                        boolean added = Library.get().addToPlaylist(name, s.id);
                        Toast.makeText(a, added
                                        ? a.getString(R.string.added_to_fmt, name)
                                        : a.getString(R.string.already_in_playlist),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    public interface NameCallback { void onName(String name); }

    public static void promptNewPlaylist(Activity a, NameCallback cb) {
        EditText input = new EditText(a);
        input.setHint(R.string.playlist_name_hint);
        input.setSingleLine(true);

        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(a)
                .setTitle(R.string.new_playlist)
                .setView(box)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(a, R.string.name_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!Library.get().createPlaylist(name)) {
                        Toast.makeText(a, R.string.name_taken, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (cb != null) cb.onName(name);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- Nada dering ----------

    /**
     * Menjadikan lagu sebagai nada dering.
     *
     * Android 6 ke atas mewajibkan izin khusus menulis setelan sistem, dan
     * izin itu hanya bisa diberikan lewat halaman Setelan, bukan lewat dialog
     * biasa. Kalau belum diberikan, pengguna diarahkan ke sana.
     */
    public static void setAsRingtone(Activity a, Song s) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.System.canWrite(a)) {
            new AlertDialog.Builder(a)
                    .setTitle(R.string.set_as_ringtone)
                    .setMessage(R.string.ringtone_perm_body)
                    .setPositiveButton(R.string.open_settings, (d, w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            i.setData(Uri.parse("package:" + a.getPackageName()));
                            a.startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(a, R.string.cannot_open_settings,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        try {
            Uri uri = s.uri();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            try {
                a.getContentResolver().update(uri, cv, null, null);
            } catch (Exception ignored) {
                // Sebagian ROM menolak pembaruan ini; nada dering biasanya tetap bisa disetel
            }
            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                    a, android.media.RingtoneManager.TYPE_RINGTONE, uri);
            Toast.makeText(a, a.getString(R.string.ringtone_set_fmt, s.title),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(a, R.string.ringtone_failed, Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Bagikan ----------

    public static void share(Activity a, Song s) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_STREAM, s.uri());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, a.getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(a, R.string.share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Info ----------

    public static void showInfo(Context c, Song s) {
        String body = c.getString(R.string.info_body_fmt,
                s.title, s.artist, s.album,
                TimeUtil.format(s.duration), s.path);
        new AlertDialog.Builder(c)
                .setTitle(R.string.song_info)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
