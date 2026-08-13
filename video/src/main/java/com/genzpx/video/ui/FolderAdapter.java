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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzpx.video.R;
import com.genzpx.video.data.VideoLibrary;
import com.genzpx.video.util.ThumbLoader;

import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {

    public interface OnClick { void onFolder(VideoLibrary.Folder f); }

    private List<VideoLibrary.Folder> data = new ArrayList<>();
    private final OnClick listener;

    public FolderAdapter(OnClick l) { listener = l; }

    public void submit(List<VideoLibrary.Folder> list) {
        data = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video_list, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VideoLibrary.Folder f = data.get(position);
        h.title.setText(f.name);
        h.meta.setText(h.itemView.getContext()
                .getString(R.string.folder_count, f.items.size()));
        if (h.meta2 != null) h.meta2.setText(f.path);
        h.progress.setVisibility(View.GONE);
        h.thumb.setImageResource(R.drawable.bg_thumb_placeholder);

        if (!f.items.isEmpty()) {
            Bitmap cached = ThumbLoader.cached(f.items.get(0).id);
            if (cached != null) h.thumb.setImageBitmap(cached);
            else h.loadThumb(f);
        }

        h.itemView.setOnClickListener(x -> { if (listener != null) listener.onFolder(f); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, meta, meta2;
        final ImageView thumb;
        final android.widget.ProgressBar progress;
        private Task task;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            meta2 = v.findViewById(R.id.meta2);
            thumb = v.findViewById(R.id.thumb);
            progress = v.findViewById(R.id.watch_progress);
        }

        void loadThumb(VideoLibrary.Folder f) {
            if (task != null) task.cancel(true);
            task = new Task(this, f);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    static class Task extends AsyncTask<Void, Void, Bitmap> {
        private final VH holder;
        private final VideoLibrary.Folder folder;

        Task(VH h, VideoLibrary.Folder f) { holder = h; folder = f; }

        @Override protected Bitmap doInBackground(Void... voids) {
            return ThumbLoader.load(holder.itemView.getContext(), folder.items.get(0));
        }

        @Override protected void onPostExecute(Bitmap b) {
            if (isCancelled() || b == null) return;
            holder.thumb.setImageBitmap(b);
        }
    }
}
