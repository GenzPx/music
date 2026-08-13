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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.genzpx.music.R;
import com.genzpx.music.data.Prefs;
import com.genzpx.music.model.Song;
import com.genzpx.music.playback.PlayerService;
import com.genzpx.music.util.ArtLoader;
import com.genzpx.music.util.TimeUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Layar "sedang diputar" full-width, mirip pemutar bawaan. */
public class NowPlayingSheet extends BottomSheetDialog {

    private final PlayerService service;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView art;
    private TextView title, artist, posText, durText, sleepText;
    private SeekBar seek;
    private ImageButton play, next, prev, shuffle, repeat;
    private boolean userSeeking = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!userSeeking) updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    public NowPlayingSheet(Context context, PlayerService service) {
        super(context, R.style.SheetTheme);
        this.service = service;
        setContentView(R.layout.sheet_now_playing);

        art = findViewById(R.id.np_art);
        title = findViewById(R.id.np_title);
        artist = findViewById(R.id.np_artist);
        posText = findViewById(R.id.np_pos);
        durText = findViewById(R.id.np_dur);
        sleepText = findViewById(R.id.np_sleep);
        seek = findViewById(R.id.np_seek);
        play = findViewById(R.id.np_play);
        next = findViewById(R.id.np_next);
        prev = findViewById(R.id.np_prev);
        shuffle = findViewById(R.id.np_shuffle);
        repeat = findViewById(R.id.np_repeat);

        play.setOnClickListener(v -> { service.togglePlayPause(); refresh(); });
        next.setOnClickListener(v -> { service.next(true); refresh(); });
        prev.setOnClickListener(v -> { service.previous(); refresh(); });
        shuffle.setOnClickListener(v -> { service.toggleShuffle(); refresh(); });
        repeat.setOnClickListener(v -> { service.cycleRepeat(); refresh(); });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) posText.setText(TimeUtil.format(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                service.seekTo(s.getProgress());
            }
        });

        setOnShowListener(d -> {
            IntentFilter f = new IntentFilter(PlayerService.BROADCAST_STATE);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, f);
            }
            handler.post(ticker);
            refresh();
        });

        setOnDismissListener(d -> {
            handler.removeCallbacks(ticker);
            try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
        });
    }

    private void refresh() {
        Song s = service.currentSong();
        if (s == null) { dismiss(); return; }

        title.setText(s.title);
        artist.setText(s.artist + " · " + s.album);
        play.setImageResource(service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        shuffle.setAlpha(Prefs.get().isShuffle() ? 1f : 0.35f);
        int r = Prefs.get().getRepeat();
        repeat.setAlpha(r == Prefs.REPEAT_OFF ? 0.35f : 1f);
        repeat.setImageResource(r == Prefs.REPEAT_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);

        long sleep = service.sleepRemainingMs();
        if (sleep > 0) {
            sleepText.setVisibility(View.VISIBLE);
            sleepText.setText(getContext().getString(R.string.sleep_remaining,
                    (int) Math.ceil(sleep / 60000.0)));
        } else {
            sleepText.setVisibility(View.GONE);
        }

        updateProgress();

        new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... v) {
                return ArtLoader.load(getContext(), s, 720);
            }
            @Override protected void onPostExecute(Bitmap b) {
                if (b != null) art.setImageBitmap(b);
                else art.setImageResource(R.drawable.ic_album_placeholder);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void updateProgress() {
        int dur = service.duration();
        int pos = service.position();
        if (dur > 0) {
            seek.setMax(dur);
            seek.setProgress(Math.min(pos, dur));
            durText.setText(TimeUtil.format(dur));
        }
        posText.setText(TimeUtil.format(pos));
    }
}
