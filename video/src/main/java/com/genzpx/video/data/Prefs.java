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

/** Setelan lokal. Tidak pernah dikirim ke mana pun. */
public class Prefs {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int SORT_NAME = 0;
    public static final int SORT_DATE = 1;
    public static final int SORT_SIZE = 2;
    public static final int SORT_DURATION = 3;

    private static Prefs sInstance;
    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("video_prefs", Context.MODE_PRIVATE);
    }

    public static void init(Context c) { if (sInstance == null) sInstance = new Prefs(c); }
    public static Prefs get() { return sInstance; }

    public int getThemeMode() { return sp.getInt("theme", THEME_SYSTEM); }
    public void setThemeMode(int v) { sp.edit().putInt("theme", v).apply(); }

    public int getSort() { return sp.getInt("sort", SORT_DATE); }
    public void setSort(int v) { sp.edit().putInt("sort", v).apply(); }

    public boolean isGridView() { return sp.getBoolean("grid", true); }
    public void setGridView(boolean v) { sp.edit().putBoolean("grid", v).apply(); }

    /** Lanjutkan audio saja saat aplikasi ditinggalkan. Mati secara bawaan. */
    public boolean isAudioModeEnabled() { return sp.getBoolean("audio_mode", false); }
    public void setAudioModeEnabled(boolean v) { sp.edit().putBoolean("audio_mode", v).apply(); }

    /** Masuk Picture-in-Picture otomatis saat menekan Home. Mati secara bawaan. */
    public boolean isAutoPip() { return sp.getBoolean("auto_pip", false); }
    public void setAutoPip(boolean v) { sp.edit().putBoolean("auto_pip", v).apply(); }

    public float getPlaybackSpeed() { return sp.getFloat("speed", 1.0f); }
    public void setPlaybackSpeed(float v) { sp.edit().putFloat("speed", v).apply(); }

    public int getResizeMode() { return sp.getInt("resize", 0); }
    public void setResizeMode(int v) { sp.edit().putInt("resize", v).apply(); }

    // ---- Lanjutkan tontonan ----
    // Posisi disimpan per video, dibatasi jumlahnya supaya tidak menumpuk.

    public void savePosition(long videoId, long positionMs, long durationMs) {
        // Jangan simpan kalau baru mulai atau sudah hampir habis
        if (durationMs > 0 && (positionMs < 5000 || positionMs > durationMs - 10000)) {
            clearPosition(videoId);
            return;
        }
        sp.edit().putLong("pos_" + videoId, positionMs).apply();
    }

    public long getPosition(long videoId) { return sp.getLong("pos_" + videoId, 0); }

    public void clearPosition(long videoId) { sp.edit().remove("pos_" + videoId).apply(); }

    public long getLastVideoId() { return sp.getLong("last_video", -1); }
    public void setLastVideoId(long v) { sp.edit().putLong("last_video", v).apply(); }

    // ---- Deteksi penghentian paksa oleh ROM ----

    public boolean isPlaybackFlagSet() { return sp.getBoolean("playing_flag", false); }

    @SuppressWarnings("ApplySharedPref")
    public void setPlaybackFlag(boolean v) { sp.edit().putBoolean("playing_flag", v).commit(); }

    public int getKillCount() { return sp.getInt("kill_count", 0); }
    public void incrementKillCount() { sp.edit().putInt("kill_count", getKillCount() + 1).apply(); }
    public void resetKillCount() { sp.edit().putInt("kill_count", 0).apply(); }

    public boolean isGuardTipDismissed() { return sp.getBoolean("guard_dismissed", false); }
    public void setGuardTipDismissed(boolean v) { sp.edit().putBoolean("guard_dismissed", v).apply(); }
}
