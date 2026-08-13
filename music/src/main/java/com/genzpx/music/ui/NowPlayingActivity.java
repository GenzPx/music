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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;

import com.genzpx.music.R;
import com.genzpx.music.data.Library;
import com.genzpx.music.data.Prefs;
import com.genzpx.music.model.Song;
import com.genzpx.music.playback.PlayerService;
import com.genzpx.music.util.ArtLoader;
import com.genzpx.music.util.TimeUtil;

import java.util.List;

/**
 * Layar "sedang diputar" satu halaman penuh.
 *
 * Dibuka dengan menekan pemutar mini di layar utama. Pemutar mini sendiri
 * tetap ada di tempatnya; layar ini adalah tampilan tambahan, bukan
 * pengganti.
 */
public class NowPlayingActivity extends AppCompatActivity {

    private PlayerService service;
    private boolean bound = false;

    private ImageView art;
    private View root;
    private TextView title, artist, posText, durText, sleepText, queueInfo;
    private SeekBar seek;
    private ImageButton play, next, prev, shuffle, repeat, favorite;
    private boolean userSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            service = ((PlayerService.LocalBinder) b).getService();
            bound = true;
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            bound = false; service = null;
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (bound && !userSeeking) updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        root = findViewById(R.id.np_root);
        art = findViewById(R.id.np_art);
        title = findViewById(R.id.np_title);
        artist = findViewById(R.id.np_artist);
        posText = findViewById(R.id.np_pos);
        durText = findViewById(R.id.np_dur);
        sleepText = findViewById(R.id.np_sleep);
        queueInfo = findViewById(R.id.np_queue_info);
        seek = findViewById(R.id.np_seek);
        play = findViewById(R.id.np_play);
        next = findViewById(R.id.np_next);
        prev = findViewById(R.id.np_prev);
        shuffle = findViewById(R.id.np_shuffle);
        repeat = findViewById(R.id.np_repeat);
        favorite = findViewById(R.id.np_favorite);

        findViewById(R.id.np_close).setOnClickListener(v -> finish());
        findViewById(R.id.np_more).setOnClickListener(v -> showMore());
        findViewById(R.id.np_queue_btn).setOnClickListener(v -> showQueue());

        play.setOnClickListener(v -> { if (bound) { service.togglePlayPause(); refresh(); } });
        next.setOnClickListener(v -> { if (bound) { service.next(true); refresh(); } });
        prev.setOnClickListener(v -> { if (bound) { service.previous(); refresh(); } });
        shuffle.setOnClickListener(v -> { if (bound) { service.toggleShuffle(); refresh(); } });
        repeat.setOnClickListener(v -> { if (bound) { service.cycleRepeat(); refresh(); } });

        favorite.setOnClickListener(v -> {
            if (!bound || service.currentSong() == null) return;
            Song s = service.currentSong();
            Library.get().toggleFavorite(s.id);
            updateFavoriteIcon(s);
            Toast.makeText(this, Library.get().isFavorite(s.id)
                    ? R.string.added_to_favorites : R.string.removed_from_favorites,
                    Toast.LENGTH_SHORT).show();
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) posText.setText(TimeUtil.format(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (bound) service.seekTo(s.getProgress());
            }
        });
    }

    // ---------- Pembaruan tampilan ----------

    private void refresh() {
        if (!bound || service == null) return;
        Song s = service.currentSong();
        if (s == null) { finish(); return; }

        title.setText(s.title);
        artist.setText(s.artist + "  ·  " + s.album);
        play.setImageResource(service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        shuffle.setAlpha(Prefs.get().isShuffle() ? 1f : 0.35f);
        int r = Prefs.get().getRepeat();
        repeat.setAlpha(r == Prefs.REPEAT_OFF ? 0.35f : 1f);
        repeat.setImageResource(r == Prefs.REPEAT_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);

        updateFavoriteIcon(s);

        int idx = service.getQueue().currentIndex();
        int total = service.getQueue().size();
        queueInfo.setText(getString(R.string.queue_position, idx + 1, total));

        long sleep = service.sleepRemainingMs();
        if (sleep > 0) {
            sleepText.setVisibility(View.VISIBLE);
            sleepText.setText(getString(R.string.sleep_remaining,
                    (int) Math.ceil(sleep / 60000.0)));
        } else {
            sleepText.setVisibility(View.GONE);
        }

        updateProgress();
        loadArt(s);
    }

    private void updateFavoriteIcon(Song s) {
        boolean fav = Library.get().isFavorite(s.id);
        favorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        favorite.setAlpha(fav ? 1f : 0.6f);
    }

    private void loadArt(Song s) {
        new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... v) {
                return ArtLoader.load(NowPlayingActivity.this, s, 1024);
            }
            @Override protected void onPostExecute(Bitmap b) {
                if (isFinishing()) return;
                if (b != null) {
                    art.setImageBitmap(b);
                    applyBackdrop(b);
                } else {
                    art.setImageResource(R.drawable.ic_album_placeholder);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Latar bergradasi mengikuti warna sampul, seperti pemutar bawaan. */
    private void applyBackdrop(Bitmap bmp) {
        Palette.from(bmp).clearFilters().generate(palette -> {
            if (palette == null || isFinishing()) return;
            int base = palette.getDarkMutedColor(
                    palette.getMutedColor(
                            getResources().getColor(R.color.bg)));
            int bottom = getResources().getColor(R.color.bg);
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{base, bottom});
            root.setBackground(g);
        });
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

    // ---------- Antrean ----------

    private void showQueue() {
        if (!bound) return;
        List<Song> items = service.getQueue().items();
        if (items.isEmpty()) return;
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Song q = items.get(i);
            labels[i] = (i + 1) + ". " + q.title + "  ·  " + q.artist;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.queue_title, items.size()))
                .setSingleChoiceItems(labels, service.getQueue().currentIndex(), (d, which) -> {
                    service.playAt(which);
                    d.dismiss();
                    refresh();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    // ---------- Menu ----------

    private void showMore() {
        if (!bound || service.currentSong() == null) return;
        String[] items = {
                getString(R.string.add_to_playlist),
                getString(R.string.sleep_timer),
                getString(R.string.equalizer),
                getString(R.string.set_as_ringtone),
                getString(R.string.song_info)
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: SongActions.addToPlaylist(this, service.currentSong()); break;
                        case 1: showSleepTimer(); break;
                        case 2: openEqualizer(); break;
                        case 3: SongActions.setAsRingtone(this, service.currentSong()); break;
                        default: SongActions.showInfo(this, service.currentSong()); break;
                    }
                })
                .show();
    }

    private void showSleepTimer() {
        final int[] mins = {15, 30, 45, 60, 90};
        String[] labels = new String[mins.length + 1];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] + " menit";
        labels[mins.length] = getString(R.string.sleep_off);

        new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_timer)
                .setItems(labels, (d, which) -> {
                    if (!bound) return;
                    if (which == mins.length) {
                        service.cancelSleepTimer();
                        Toast.makeText(this, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
                    } else {
                        service.setSleepTimer(mins[which]);
                        Toast.makeText(this, getString(R.string.sleep_set, mins[which]),
                                Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                })
                .show();
    }

    private void openEqualizer() {
        if (!bound) return;
        try {
            startActivityForResult(service.equalizerIntent(), 999);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_equalizer, Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Daur hidup ----------

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, PlayerService.class), conn, Context.BIND_AUTO_CREATE);
        IntentFilter f = new IntentFilter(PlayerService.BROADCAST_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, f);
        }
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
        if (bound) { try { unbindService(conn); } catch (Exception ignored) {} bound = false; }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_down);
    }
}
