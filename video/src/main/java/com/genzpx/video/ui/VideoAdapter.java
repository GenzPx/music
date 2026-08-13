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
package com.genzpx.video.ui;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzpx.video.R;
import com.genzpx.video.data.Prefs;
import com.genzpx.video.model.Video;
import com.genzpx.video.util.Fmt;
import com.genzpx.video.util.ThumbLoader;

import java.util.ArrayList;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface OnClick { void onVideo(List<Video> list, int position); }

    private List<Video> data = new ArrayList<>();
    private final OnClick listener;
    private boolean grid;

    public VideoAdapter(OnClick l, boolean grid) {
        this.listener = l;
        this.grid = grid;
    }

    public void submit(List<Video> list) {
        data = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    public void setGrid(boolean g) { grid = g; notifyDataSetChanged(); }

    @Override public int getItemViewType(int position) { return grid ? 1 : 0; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.item_video_grid : R.layout.item_video_list;
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Video v = data.get(position);
        h.title.setText(v.title);

        String meta = Fmt.time(v.duration);
        String q = v.qualityLabel();
        if (!q.isEmpty()) meta += "  ·  " + q;
        if (h.meta2 != null) {
            h.meta.setText(Fmt.time(v.duration));
            h.meta2.setText(q.isEmpty() ? Fmt.size(v.size) : q + "  ·  " + Fmt.size(v.size));
        } else {
            h.meta.setText(meta);
        }

        // Tanda lanjutkan tontonan
        long saved = Prefs.get().getPosition(v.id);
        if (saved > 0 && v.duration > 0) {
            h.progress.setVisibility(View.VISIBLE);
            h.progress.setProgress((int) (saved * 100 / v.duration));
        } else {
            h.progress.setVisibility(View.GONE);
        }

        h.thumb.setImageResource(R.drawable.bg_thumb_placeholder);
        Bitmap cached = ThumbLoader.cached(v.id);
        if (cached != null) h.thumb.setImageBitmap(cached);
        else h.loadThumb(v);

        h.itemView.setOnClickListener(x -> {
            if (listener != null) listener.onVideo(data, h.getAdapterPosition());
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, meta;
        final TextView meta2;
        final ImageView thumb;
        final ProgressBar progress;
        private ThumbTask task;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            meta2 = v.findViewById(R.id.meta2);
            thumb = v.findViewById(R.id.thumb);
            progress = v.findViewById(R.id.watch_progress);
        }

        void loadThumb(Video v) {
            if (task != null) task.cancel(true);
            task = new ThumbTask(this, v);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /** Gambar mini dimuat di latar supaya gulir tetap mulus. */
    static class ThumbTask extends AsyncTask<Void, Void, Bitmap> {
        private final VH holder;
        private final Video video;
        private final long expectId;

        ThumbTask(VH h, Video v) { holder = h; video = v; expectId = v.id; }

        @Override protected Bitmap doInBackground(Void... voids) {
            return ThumbLoader.load(holder.itemView.getContext(), video);
        }

        @Override protected void onPostExecute(Bitmap b) {
            if (isCancelled() || b == null) return;
            holder.thumb.setImageBitmap(b);
        }
    }
}
