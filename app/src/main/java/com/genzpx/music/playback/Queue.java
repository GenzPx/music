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
package com.genzpx.music.playback;

import com.genzpx.music.data.Prefs;
import com.genzpx.music.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Antrean pemutaran + logika shuffle/repeat. */
public class Queue {
    private final List<Song> original = new ArrayList<>();
    private final List<Song> order = new ArrayList<>();
    private int index = -1;

    public synchronized void setQueue(List<Song> songs, int startIndex) {
        original.clear();
        original.addAll(songs);
        Song start = (startIndex < 0 || startIndex >= songs.size()) ? null : songs.get(startIndex);
        rebuildOrder(start);
    }

    private void rebuildOrder(Song keepCurrent) {
        order.clear();
        order.addAll(original);
        if (Prefs.get().isShuffle()) {
            Collections.shuffle(order);
            if (keepCurrent != null) {
                order.remove(keepCurrent);
                order.add(0, keepCurrent);
            }
        }
        if (keepCurrent == null) index = order.isEmpty() ? -1 : 0;
        else index = Math.max(0, order.indexOf(keepCurrent));
    }

    public synchronized void reshuffle() { rebuildOrder(current()); }

    public synchronized Song current() {
        if (index < 0 || index >= order.size()) return null;
        return order.get(index);
    }

    public synchronized int size() { return order.size(); }
    public synchronized List<Song> items() { return new ArrayList<>(order); }
    public synchronized int currentIndex() { return index; }
    public synchronized boolean isEmpty() { return order.isEmpty(); }

    public synchronized Song jumpTo(int i) {
        if (i < 0 || i >= order.size()) return null;
        index = i;
        return current();
    }

    public synchronized Song next(boolean manual) {
        if (order.isEmpty()) return null;
        int repeat = Prefs.get().getRepeat();
        if (!manual && repeat == Prefs.REPEAT_ONE) return current();
        if (index + 1 < order.size()) { index++; return current(); }
        if (repeat == Prefs.REPEAT_ALL || manual) { index = 0; return current(); }
        return null;
    }

    public synchronized Song previous() {
        if (order.isEmpty()) return null;
        index = (index - 1 >= 0) ? index - 1 : order.size() - 1;
        return current();
    }
}
