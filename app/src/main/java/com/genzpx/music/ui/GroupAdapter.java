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
import com.genzpx.music.data.MediaLibrary;
import com.genzpx.music.util.ArtLoader;

import java.util.ArrayList;
import java.util.List;

/** Daftar Album / Artis / Folder. */
public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {

    public interface OnGroupClick { void onGroup(MediaLibrary.Group group); }

    private List<MediaLibrary.Group> data = new ArrayList<>();
    private final OnGroupClick listener;
    private final int iconRes;

    public GroupAdapter(OnGroupClick l, int iconRes) {
        this.listener = l;
        this.iconRes = iconRes;
    }

    public void submit(List<MediaLibrary.Group> list) {
        data = list == null ? new ArrayList<>() : list;
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
        MediaLibrary.Group g = data.get(position);
        h.title.setText(g.title);
        h.subtitle.setText(g.subtitle);
        h.playing.setVisibility(View.GONE);
        h.art.setImageResource(iconRes);
        h.bindArt(g, iconRes);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onGroup(g); });
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

        void bindArt(MediaLibrary.Group g, int fallback) {
            if (task != null) task.cancel(true);
            if (g.items.isEmpty()) { art.setImageResource(fallback); return; }
            task = new ArtTask(this, g);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    static class ArtTask extends AsyncTask<Void, Void, Bitmap> {
        private final VH holder;
        private final MediaLibrary.Group group;

        ArtTask(VH h, MediaLibrary.Group g) { holder = h; group = g; }

        @Override protected Bitmap doInBackground(Void... voids) {
            return ArtLoader.load(holder.itemView.getContext(), group.items.get(0), 128);
        }

        @Override protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled() || bitmap == null) return;
            holder.art.setImageBitmap(bitmap);
        }
    }
}
