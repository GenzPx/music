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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzpx.music.R;
import com.genzpx.music.data.Library;

import java.util.ArrayList;
import java.util.List;

/**
 * Isi tab Pustaka: pintasan Favorit dan Baru diputar, lalu daftar putar
 * buatan pengguna, dan terakhir tombol membuat daftar putar baru.
 */
public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.VH> {

    public interface Listener {
        void onFavorites();
        void onRecent();
        void onPlaylist(String name);
        void onNewPlaylist();
        void onPlaylistLongPress(String name);
    }

    private static final int TYPE_SHORTCUT = 0;
    private static final int TYPE_PLAYLIST = 1;
    private static final int TYPE_NEW = 2;

    private final Listener listener;
    private List<String> playlists = new ArrayList<>();

    public LibraryAdapter(Listener l) { listener = l; }

    public void refresh() {
        playlists = Library.get().playlistNames();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 2) return TYPE_SHORTCUT;
        if (position < 2 + playlists.size()) return TYPE_PLAYLIST;
        return TYPE_NEW;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_library, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        int type = getItemViewType(position);
        h.itemView.setOnLongClickListener(null);

        if (type == TYPE_SHORTCUT) {
            if (position == 0) {
                h.icon.setImageResource(R.drawable.ic_heart_filled);
                h.title.setText(R.string.favorites);
                h.subtitle.setText(h.itemView.getContext()
                        .getString(R.string.song_count, Library.get().favoriteCount()));
                h.itemView.setOnClickListener(v -> listener.onFavorites());
            } else {
                h.icon.setImageResource(R.drawable.ic_history);
                h.title.setText(R.string.recently_played);
                h.subtitle.setText(h.itemView.getContext()
                        .getString(R.string.song_count,
                                Library.get().recentlyPlayed().size()));
                h.itemView.setOnClickListener(v -> listener.onRecent());
            }
        } else if (type == TYPE_PLAYLIST) {
            String name = playlists.get(position - 2);
            h.icon.setImageResource(R.drawable.ic_playlist);
            h.title.setText(name);
            h.subtitle.setText(h.itemView.getContext()
                    .getString(R.string.song_count, Library.get().playlistCount(name)));
            h.itemView.setOnClickListener(v -> listener.onPlaylist(name));
            h.itemView.setOnLongClickListener(v -> {
                listener.onPlaylistLongPress(name);
                return true;
            });
        } else {
            h.icon.setImageResource(R.drawable.ic_add);
            h.title.setText(R.string.new_playlist);
            h.subtitle.setText(R.string.new_playlist_hint);
            h.itemView.setOnClickListener(v -> listener.onNewPlaylist());
        }
    }

    @Override public int getItemCount() { return 2 + playlists.size() + 1; }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle;
        final ImageView icon;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            icon = v.findViewById(R.id.icon);
        }
    }
}
