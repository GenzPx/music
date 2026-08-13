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
import android.content.SharedPreferences;

/** Setelan lokal. Tidak pernah dikirim ke mana pun. */
public class Prefs {
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static Prefs sInstance;
    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("music_prefs", Context.MODE_PRIVATE);
    }

    public static void init(Context c) { if (sInstance == null) sInstance = new Prefs(c); }
    public static Prefs get() { return sInstance; }

    public int getThemeMode() { return sp.getInt("theme", THEME_SYSTEM); }
    public void setThemeMode(int v) { sp.edit().putInt("theme", v).apply(); }

    public boolean isShuffle() { return sp.getBoolean("shuffle", false); }
    public void setShuffle(boolean v) { sp.edit().putBoolean("shuffle", v).apply(); }

    public int getRepeat() { return sp.getInt("repeat", REPEAT_OFF); }
    public void setRepeat(int v) { sp.edit().putInt("repeat", v).apply(); }

    public void saveLastPlayed(long songId, int positionMs) {
        sp.edit().putLong("last_song", songId).putInt("last_pos", positionMs).apply();
    }
    public long getLastSongId() { return sp.getLong("last_song", -1); }
    public int getLastPosition() { return sp.getInt("last_pos", 0); }

    public int getLastTab() { return sp.getInt("last_tab", 0); }
    public void setLastTab(int v) { sp.edit().putInt("last_tab", v).apply(); }
}
