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
package com.genzpx.video;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.genzpx.video.data.Prefs;
import com.genzpx.video.data.Watchlist;

public class VideoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Watchlist.init(this);
        applyTheme(Prefs.get().getThemeMode());
    }

    public static void applyTheme(int mode) {
        switch (mode) {
            case Prefs.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Prefs.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
