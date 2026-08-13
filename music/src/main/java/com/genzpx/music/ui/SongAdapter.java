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
package com.genzpx.music.ui;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzpx.music.R;
import com.genzpx.music.model.Song;
import com.genzpx.music.util.ArtLoader;
import com.genzpx.music.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    public interface OnSongClick { void onSong(List<Song> list, int position); }

    private List<Song> data = new ArrayList<>();
    private final OnSongClick listener;
    private long highlightId = -1;

    public SongAdapter(OnSongClick l) { this.listener = l; }

    public void submit(List<Song> list) {
        data = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    public void setHighlight(long songId) {
        if (highlightId == songId) return;
        highlightId = songId;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Song s = data.get(position);
        h.title.setText(s.title);
        h.subtitle.setText(s.artist + " · " + TimeUtil.format(s.duration));
        h.title.setSelected(s.id == highlightId);
        h.playing.setVisibility(s.id == highlightId ? View.VISIBLE : View.GONE);
        h.art.setImageResource(R.drawable.ic_album_placeholder);
        h.bindArt(s);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSong(data, h.getAdapterPosition());
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle;
        final ImageView art, playing;
        private ArtTask task;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            art = v.findViewById(R.id.art);
            playing = v.findViewById(R.id.playing_indicator);
        }

        void bindArt(Song s) {
            if (task != null) task.cancel(true);
            task = new ArtTask(this, s);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /** Muat sampul di background supaya scroll tetap mulus. */
    static class ArtTask extends AsyncTask<Void, Void, Bitmap> {
        private final VH holder;
        private final Song song;

        ArtTask(VH h, Song s) { holder = h; song = s; }

        @Override protected Bitmap doInBackground(Void... voids) {
            return ArtLoader.load(holder.itemView.getContext(), song, 128);
        }

        @Override protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled() || bitmap == null) return;
            holder.art.setImageBitmap(bitmap);
        }
    }
}
