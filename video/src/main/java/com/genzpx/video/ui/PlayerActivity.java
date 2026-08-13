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

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Looper;
import android.provider.Settings;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.genzpx.video.R;
import com.genzpx.video.data.Prefs;
import com.genzpx.video.data.VideoLibrary;
import com.genzpx.video.data.Watchlist;
import com.genzpx.video.model.Video;
import com.genzpx.video.playback.AudioModeService;
import com.genzpx.video.util.Fmt;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_LIST_IDS = "list_ids";

    private static final int[] RESIZE_MODES = {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
    };
    private static final int[] RESIZE_LABELS = {
            R.string.resize_fit, R.string.resize_zoom, R.string.resize_fill
    };
    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private ExoPlayer player;
    private PlayerView playerView;
    private View controls, lockOverlay, gestureHud;
    private TextView hudText, titleText, posText, durText, speedBadge;
    private SeekBar seek;
    private ImageButton btnPlay, btnLock, btnRotate, btnPip;

    private AudioManager audioManager;
    private GestureDetector gestures;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Video current;
    private final List<Video> queue = new ArrayList<>();
    private int index = 0;

    private boolean locked = false;
    private boolean kidsLock = false;      // kunci anak: seluruh layar tak bisa disentuh
    private long sleepAtMs = 0;            // 0 berarti timer mati
    private long repeatA = -1, repeatB = -1;   // penanda ulang bagian A-B
    private float videoScale = 1f;         // zoom cubit
    private ScaleGestureDetector pinch;
    private boolean controlsVisible = true;
    private boolean userSeeking = false;
    private boolean inPip = false;
    private boolean leavingForAudioMode = false;

    // Keadaan sementara selama gestur berlangsung
    private float gestureStartX, gestureStartY;
    private long gestureStartPosition;
    private int gestureMode = 0; // 0 belum, 1 seek, 2 volume, 3 kecerahan
    private float startBrightness, startVolume;

    private final Runnable hideControls = () -> setControlsVisible(false);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null && !userSeeking) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (dur > 0) {
                    seek.setMax((int) dur);
                    seek.setProgress((int) pos);
                    durText.setText(Fmt.time(dur));
                }
                posText.setText(Fmt.time(pos));

                // Ulang bagian A-B
                if (repeatA >= 0 && repeatB > repeatA && pos >= repeatB) {
                    player.seekTo(repeatA);
                }
            }
            checkSleepTimer();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Prefs.get().isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        playerView = findViewById(R.id.player_view);
        controls = findViewById(R.id.controls);
        lockOverlay = findViewById(R.id.lock_overlay);
        gestureHud = findViewById(R.id.gesture_hud);
        hudText = findViewById(R.id.hud_text);
        titleText = findViewById(R.id.player_title);
        posText = findViewById(R.id.player_pos);
        durText = findViewById(R.id.player_dur);
        speedBadge = findViewById(R.id.speed_badge);
        seek = findViewById(R.id.player_seek);
        btnPlay = findViewById(R.id.btn_play);
        btnLock = findViewById(R.id.btn_lock);
        btnRotate = findViewById(R.id.btn_rotate);
        btnPip = findViewById(R.id.btn_pip);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        resolveIntent();
        setupPlayer();
        setupControls();
        setupGestures();

        hideSystemBars();
        scheduleHide();
    }

    // ---------- Menentukan apa yang diputar ----------

    private void resolveIntent() {
        Intent it = getIntent();

        // Dibuka dari aplikasi lain, misalnya pengelola berkas
        Uri data = it.getData();
        if (data != null && it.getLongExtra(EXTRA_VIDEO_ID, -1) < 0) {
            current = null;
            queue.clear();
            titleText.setText(data.getLastPathSegment() == null
                    ? getString(R.string.app_name) : data.getLastPathSegment());
            return;
        }

        long id = it.getLongExtra(EXTRA_VIDEO_ID, -1);
        long[] ids = it.getLongArrayExtra(EXTRA_LIST_IDS);
        if (ids != null) {
            for (long vid : ids) {
                Video v = VideoLibrary.get().byId(vid);
                if (v != null) queue.add(v);
            }
        }
        current = VideoLibrary.get().byId(id);
        if (current == null && !queue.isEmpty()) current = queue.get(0);
        index = Math.max(0, queue.indexOf(current));
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);   // kontrol digambar sendiri

        playerView.setResizeMode(RESIZE_MODES[
                Math.min(Prefs.get().getResizeMode(), RESIZE_MODES.length - 1)]);

        SubtitleView sv = playerView.getSubtitleView();
        if (sv != null) {
            sv.setUserDefaultStyle();
            sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                    * (Prefs.get().getSubtitleScale() / 100f));
        }

        MediaItem item;
        long startAt = 0;
        Uri data = getIntent().getData();
        if (current != null) {
            item = MediaItem.fromUri(current.uri());
            titleText.setText(current.title);
            startAt = Prefs.get().isResumeEnabled() ? Prefs.get().getPosition(current.id) : 0;
            Prefs.get().setLastVideoId(current.id);
            Watchlist.get().addHistory(current.id);
        } else if (data != null) {
            item = MediaItem.fromUri(data);
        } else {
            Toast.makeText(this, R.string.err_no_video, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        player.setMediaItem(item);
        float sp = Prefs.get().getPlaybackSpeed();
        player.setPlaybackParameters(new PlaybackParameters(sp));
        updateSpeedBadge(sp);

        if (startAt > 0) player.seekTo(startAt);
        player.prepare();
        player.setPlayWhenReady(true);
        Prefs.get().setPlaybackFlag(true);

        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    if (current != null) Prefs.get().clearPosition(current.id);
                    playNext(false);
                }
            }

            @Override public void onPlayerError(@NonNull PlaybackException error) {
                // Berkas rusak atau format tidak didukung: beri tahu, jangan diam saja
                new AlertDialog.Builder(PlayerActivity.this)
                        .setTitle(R.string.err_playback_title)
                        .setMessage(getString(R.string.err_playback_body,
                                String.valueOf(error.getErrorCodeName())))
                        .setPositiveButton(R.string.close, (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });

        handler.post(ticker);
    }

    // ---------- Kontrol di layar ----------

    private void setupControls() {
        btnPlay.setOnClickListener(v -> {
            if (player.isPlaying()) player.pause(); else player.play();
            scheduleHide();
        });
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_next).setOnClickListener(v -> playNext(true));
        findViewById(R.id.btn_prev).setOnClickListener(v -> playPrev());
        findViewById(R.id.btn_rew).setOnClickListener(v -> seekBy(-10000));
        findViewById(R.id.btn_ff).setOnClickListener(v -> seekBy(10000));

        btnLock.setOnClickListener(v -> setLocked(true));

        lockOverlay.setOnClickListener(v -> {
            if (kidsLock) {
                // Sengaja dipersulit: anak kecil tidak akan sengaja menahan tombol
                Toast.makeText(this, R.string.kids_lock_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            setLocked(false);
        });
        lockOverlay.setOnLongClickListener(v -> {
            kidsLock = false;
            setLocked(false);
            return true;
        });

        btnRotate.setOnClickListener(v -> {
            int o = getResources().getConfiguration().orientation;
            setRequestedOrientation(o == Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            scheduleHide();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnPip.setVisibility(View.VISIBLE);
            btnPip.setOnClickListener(v -> enterPip());
        } else {
            btnPip.setVisibility(View.GONE);
        }

        findViewById(R.id.btn_more).setOnClickListener(v -> showMore());

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) posText.setText(Fmt.time(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
                handler.removeCallbacks(hideControls);
            }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                player.seekTo(s.getProgress());
                scheduleHide();
            }
        });
    }

    private void seekBy(long deltaMs) {
        long target = Math.max(0, Math.min(player.getCurrentPosition() + deltaMs,
                player.getDuration()));
        player.seekTo(target);
        showHud(Fmt.signedTime(deltaMs) + "  " + Fmt.time(target));
        scheduleHide();
    }

    private void playNext(boolean manual) {
        if (queue.isEmpty() || index + 1 >= queue.size()) {
            if (manual) Toast.makeText(this, R.string.last_video, Toast.LENGTH_SHORT).show();
            else finish();
            return;
        }
        savePosition();
        index++;
        openAt(index);
    }

    private void playPrev() {
        if (player.getCurrentPosition() > 5000) { player.seekTo(0); return; }
        if (queue.isEmpty() || index - 1 < 0) return;
        savePosition();
        index--;
        openAt(index);
    }

    private void openAt(int i) {
        current = queue.get(i);
        titleText.setText(current.title);
        Prefs.get().setLastVideoId(current.id);
        player.setMediaItem(MediaItem.fromUri(current.uri()));
        player.prepare();
        long start = Prefs.get().getPosition(current.id);
        if (start > 0) player.seekTo(start);
        player.play();
    }

    // ---------- Gestur ----------

    private void setupGestures() {
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (locked) return true;
                setControlsVisible(!controlsVisible);
                return true;
            }

            @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (locked) return true;
                // Ketuk dua kali di kiri mundur, di kanan maju
                float third = playerView.getWidth() / 3f;
                if (e.getX() < third) seekBy(-10000);
                else if (e.getX() > third * 2) seekBy(10000);
                else { if (player.isPlaying()) player.pause(); else player.play(); }
                return true;
            }
        });

        pinch = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(@NonNull ScaleGestureDetector d) {
                videoScale = Math.max(1f, Math.min(3f, videoScale * d.getScaleFactor()));
                View sv = playerView.getVideoSurfaceView();
                if (sv != null) {
                    sv.setScaleX(videoScale);
                    sv.setScaleY(videoScale);
                }
                showHud(Math.round(videoScale * 100) + "%");
                return true;
            }

            @Override public void onScaleEnd(@NonNull ScaleGestureDetector d) {
                hideHudSoon();
            }
        });

        playerView.setOnTouchListener((v, ev) -> {
            if (locked) return true;

            pinch.onTouchEvent(ev);
            if (pinch.isInProgress() || ev.getPointerCount() > 1) {
                gestureMode = -1;   // jangan campur dengan gestur satu jari
                return true;
            }

            gestures.onTouchEvent(ev);

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureStartX = ev.getX();
                    gestureStartY = ev.getY();
                    gestureStartPosition = player.getCurrentPosition();
                    gestureMode = 0;
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    startBrightness = currentBrightness();
                    break;

                case MotionEvent.ACTION_MOVE: {
                    if (gestureMode == -1) break;
                    float dx = ev.getX() - gestureStartX;
                    float dy = ev.getY() - gestureStartY;
                    float threshold = 40f;

                    if (gestureMode == 0) {
                        if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                            gestureMode = 1;
                        } else if (Math.abs(dy) > threshold) {
                            gestureMode = gestureStartX < playerView.getWidth() / 2f ? 3 : 2;
                        }
                    }

                    if (gestureMode == 1) {
                        // Geser mendatar: maju atau mundur, 100 piksel kira-kira 10 detik
                        long delta = (long) (dx / 100f * 10000);
                        long target = Math.max(0, Math.min(gestureStartPosition + delta,
                                player.getDuration()));
                        showHud(Fmt.signedTime(target - gestureStartPosition)
                                + "\n" + Fmt.time(target));
                    } else if (gestureMode == 2) {
                        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        float change = -dy / (playerView.getHeight() * 0.7f) * max;
                        int nv = Math.max(0, Math.min(max, Math.round(startVolume + change)));
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0);
                        showHud(getString(R.string.hud_volume, nv * 100 / max));
                    } else if (gestureMode == 3) {
                        float change = -dy / (playerView.getHeight() * 0.7f);
                        float nb = Math.max(0.01f, Math.min(1f, startBrightness + change));
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = nb;
                        getWindow().setAttributes(lp);
                        showHud(getString(R.string.hud_brightness, Math.round(nb * 100)));
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (gestureMode == -1) { gestureMode = 0; break; }
                    if (gestureMode == 1) {
                        float dx = ev.getX() - gestureStartX;
                        long delta = (long) (dx / 100f * 10000);
                        long target = Math.max(0, Math.min(gestureStartPosition + delta,
                                player.getDuration()));
                        player.seekTo(target);
                    }
                    gestureMode = 0;
                    hideHudSoon();
                    break;
            }
            return true;
        });
    }

    private float currentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        if (b >= 0) return b;
        try {
            int sys = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            return sys / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private void showHud(String text) {
        handler.removeCallbacks(hideHud);
        hudText.setText(text);
        gestureHud.setVisibility(View.VISIBLE);
    }

    private final Runnable hideHud = () -> gestureHud.setVisibility(View.GONE);

    private void hideHudSoon() {
        handler.removeCallbacks(hideHud);
        handler.postDelayed(hideHud, 600);
    }

    // ---------- Tampilan kontrol ----------

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        controls.animate().alpha(visible ? 1f : 0f).setDuration(150)
                .withStartAction(() -> { if (visible) controls.setVisibility(View.VISIBLE); })
                .withEndAction(() -> { if (!visible) controls.setVisibility(View.GONE); })
                .start();
        if (visible) { showSystemBars(); scheduleHide(); }
        else hideSystemBars();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 3500);
    }

    private void setLocked(boolean lock) {
        locked = lock;
        if (lock) {
            setControlsVisible(false);
            lockOverlay.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.locked_hint, Toast.LENGTH_SHORT).show();
        } else {
            lockOverlay.setVisibility(View.GONE);
            setControlsVisible(true);
        }
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void showSystemBars() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
    }

    // ---------- Menu lainnya ----------

    private void showMore() {
        handler.removeCallbacks(hideControls);
        boolean fav = current != null && Watchlist.get().isFavorite(current.id);

        List<String> items = new ArrayList<>();
        items.add(getString(R.string.menu_speed));
        items.add(getString(R.string.menu_resize));
        items.add(getString(R.string.menu_subtitle));
        items.add(getString(R.string.menu_subtitle_size));
        items.add(getString(R.string.menu_audio_track));
        items.add(getString(R.string.menu_ab_repeat));
        items.add(getString(sleepAtMs > 0 ? R.string.menu_sleep_on : R.string.menu_sleep));
        items.add(getString(R.string.menu_kids_lock));
        items.add(getString(fav ? R.string.remove_favorite : R.string.add_favorite));
        items.add(getString(R.string.menu_share));
        items.add(getString(R.string.menu_info));

        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    switch (which) {
                        case 0: showSpeedDialog(); break;
                        case 1: cycleResize(); break;
                        case 2: showTrackDialog(C.TRACK_TYPE_TEXT); break;
                        case 3: showSubtitleSizeDialog(); break;
                        case 4: showTrackDialog(C.TRACK_TYPE_AUDIO); break;
                        case 5: showAbRepeatDialog(); break;
                        case 6: showSleepDialog(); break;
                        case 7: enableKidsLock(); break;
                        case 8: toggleFavorite(); break;
                        case 9: shareVideo(); break;
                        default: showInfo(); break;
                    }
                })
                .setOnDismissListener(d -> scheduleHide())
                .show();
    }

    // ---------- Timer tidur ----------

    private void showSleepDialog() {
        final int[] mins = {0, 15, 30, 45, 60, 90};
        String[] labels = new String[mins.length];
        labels[0] = getString(R.string.sleep_off);
        for (int i = 1; i < mins.length; i++) {
            labels[i] = getString(R.string.sleep_minutes, mins[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_sleep)
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        sleepAtMs = 0;
                        Toast.makeText(this, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
                    } else {
                        sleepAtMs = SystemClock.elapsedRealtime() + mins[which] * 60_000L;
                        Toast.makeText(this, getString(R.string.sleep_set, mins[which]),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** Dipanggil tiap denyut ticker; menjeda pemutaran begitu waktunya habis. */
    private void checkSleepTimer() {
        if (sleepAtMs <= 0) return;
        if (SystemClock.elapsedRealtime() >= sleepAtMs) {
            sleepAtMs = 0;
            if (player != null) player.pause();
            Toast.makeText(this, R.string.sleep_done, Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Ulang bagian A-B ----------

    private void showAbRepeatDialog() {
        String[] labels;
        if (repeatA < 0) {
            labels = new String[]{getString(R.string.ab_set_a)};
        } else if (repeatB < 0) {
            labels = new String[]{getString(R.string.ab_set_b), getString(R.string.ab_clear)};
        } else {
            labels = new String[]{getString(R.string.ab_clear)};
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_ab_repeat)
                .setItems(labels, (d, which) -> {
                    long pos = player.getCurrentPosition();
                    if (repeatA < 0) {
                        repeatA = pos;
                        showHud(getString(R.string.ab_a_at, Fmt.time(pos)));
                        hideHudSoon();
                    } else if (repeatB < 0) {
                        if (which == 0) {
                            if (pos <= repeatA) {
                                Toast.makeText(this, R.string.ab_b_too_early,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            repeatB = pos;
                            showHud(getString(R.string.ab_active,
                                    Fmt.time(repeatA), Fmt.time(repeatB)));
                            hideHudSoon();
                        } else {
                            clearAbRepeat();
                        }
                    } else {
                        clearAbRepeat();
                    }
                })
                .show();
    }

    private void clearAbRepeat() {
        repeatA = -1;
        repeatB = -1;
        Toast.makeText(this, R.string.ab_cleared, Toast.LENGTH_SHORT).show();
    }

    // ---------- Kunci anak ----------

    /**
     * Kunci anak mengabaikan semua sentuhan sampai tombol buka ditahan lama,
     * supaya video tidak berhenti gara-gara layar dicolek.
     */
    private void enableKidsLock() {
        kidsLock = true;
        setLocked(true);
        Toast.makeText(this, R.string.kids_lock_on, Toast.LENGTH_LONG).show();
    }

    // ---------- Ukuran subtitle ----------

    private void showSubtitleSizeDialog() {
        final int[] scales = {75, 100, 125, 150, 200};
        String[] labels = new String[scales.length];
        int checked = 1;
        int cur = Prefs.get().getSubtitleScale();
        for (int i = 0; i < scales.length; i++) {
            labels[i] = scales[i] + "%";
            if (scales[i] == cur) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_subtitle_size)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.get().setSubtitleScale(scales[which]);
                    SubtitleView sv = playerView.getSubtitleView();
                    if (sv != null) {
                        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                                * (scales[which] / 100f));
                    }
                    d.dismiss();
                })
                .show();
    }

    // ---------- Favorit dan bagikan ----------

    private void toggleFavorite() {
        if (current == null) return;
        boolean added = Watchlist.get().toggleFavorite(current.id);
        Toast.makeText(this, added ? R.string.added_favorite : R.string.removed_favorite,
                Toast.LENGTH_SHORT).show();
    }

    private void shareVideo() {
        if (current == null) return;
        try {
            Intent it = new Intent(Intent.ACTION_SEND)
                    .setType("video/*")
                    .putExtra(Intent.EXTRA_STREAM, current.uri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, getString(R.string.menu_share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSpeedDialog() {
        String[] labels = new String[SPEEDS.length];
        int checked = 2;
        float cur = Prefs.get().getPlaybackSpeed();
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = SPEEDS[i] + "x";
            if (Math.abs(SPEEDS[i] - cur) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_speed)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    float sp = SPEEDS[which];
                    player.setPlaybackParameters(new PlaybackParameters(sp));
                    Prefs.get().setPlaybackSpeed(sp);
                    updateSpeedBadge(sp);
                    d.dismiss();
                })
                .show();
    }

    private void updateSpeedBadge(float sp) {
        if (Math.abs(sp - 1f) < 0.01f) speedBadge.setVisibility(View.GONE);
        else {
            speedBadge.setVisibility(View.VISIBLE);
            speedBadge.setText(sp + "x");
        }
    }

    private void cycleResize() {
        int next = (Prefs.get().getResizeMode() + 1) % RESIZE_MODES.length;
        Prefs.get().setResizeMode(next);
        playerView.setResizeMode(RESIZE_MODES[next]);
        Toast.makeText(this, RESIZE_LABELS[next], Toast.LENGTH_SHORT).show();
    }

    /** Pemilih jalur subtitle atau audio dari trek yang ada di dalam berkas. */
    private void showTrackDialog(int trackType) {
        Tracks tracks = player.getCurrentTracks();
        List<String> labels = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();

        labels.add(getString(trackType == C.TRACK_TYPE_TEXT
                ? R.string.subtitle_off : R.string.track_default));
        overrides.add(null);

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != trackType) continue;
            TrackGroup tg = group.getMediaTrackGroup();
            for (int i = 0; i < tg.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                androidx.media3.common.Format f = tg.getFormat(i);
                String name = f.label != null ? f.label
                        : (f.language != null ? f.language : "Trek " + (labels.size()));
                labels.add(name);
                overrides.add(new TrackSelectionOverride(tg, i));
            }
        }

        if (labels.size() == 1) {
            Toast.makeText(this, trackType == C.TRACK_TYPE_TEXT
                    ? R.string.no_subtitle : R.string.no_audio_track, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(trackType == C.TRACK_TYPE_TEXT
                        ? R.string.menu_subtitle : R.string.menu_audio_track)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    TrackSelectionOverride ov = overrides.get(which);
                    if (ov == null) {
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters().buildUpon()
                                        .clearOverridesOfType(trackType)
                                        .setTrackTypeDisabled(trackType,
                                                trackType == C.TRACK_TYPE_TEXT)
                                        .build());
                    } else {
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters().buildUpon()
                                        .setTrackTypeDisabled(trackType, false)
                                        .addOverride(ov)
                                        .build());
                    }
                })
                .show();
    }

    private void showInfo() {
        if (current == null) return;
        String body = getString(R.string.info_body_fmt,
                current.title,
                current.resolution().isEmpty() ? "-" : current.resolution(),
                Fmt.time(current.duration),
                Fmt.size(current.size),
                current.path);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_info)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    // ---------- Picture in Picture ----------

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            int w = player.getVideoSize().width;
            int h = player.getVideoSize().height;
            if (w <= 0 || h <= 0) { w = 16; h = 9; }
            // Android menolak rasio yang terlalu ekstrem
            float ratio = (float) w / h;
            if (ratio < 0.42f) { w = 42; h = 100; }
            if (ratio > 2.39f) { w = 239; h = 100; }

            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(w, h))
                    .build());
        } catch (Exception e) {
            Toast.makeText(this, R.string.pip_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, @NonNull Configuration cfg) {
        super.onPictureInPictureModeChanged(isInPip, cfg);
        inPip = isInPip;
        controls.setVisibility(isInPip ? View.GONE : View.VISIBLE);
        controlsVisible = !isInPip;
        if (!isInPip) scheduleHide();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Menekan Home: masuk PiP kalau diaktifkan, kalau tidak cek mode audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && Prefs.get().isAutoPip() && player != null && player.isPlaying() && !inPip) {
            enterPip();
        }
    }

    // ---------- Daur hidup ----------

    private void savePosition() {
        if (current != null && player != null) {
            Prefs.get().savePosition(current.id,
                    player.getCurrentPosition(), player.getDuration());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();

        if (inPip) return;   // PiP tetap berjalan

        if (Prefs.get().isAudioModeEnabled() && player != null && player.isPlaying()) {
            // Lanjutkan suaranya saja lewat layanan latar depan
            leavingForAudioMode = true;
            long pos = player.getCurrentPosition();
            player.pause();
            Intent i = new Intent(this, AudioModeService.class)
                    .setAction(AudioModeService.ACTION_START)
                    .putExtra(AudioModeService.EXTRA_URI,
                            current != null ? current.uri() : getIntent().getData())
                    .putExtra(AudioModeService.EXTRA_TITLE,
                            current != null ? current.title : getString(R.string.app_name))
                    .putExtra(AudioModeService.EXTRA_POSITION, pos);
            androidx.core.content.ContextCompat.startForegroundService(this, i);
            finish();
        } else if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        savePosition();
        if (!leavingForAudioMode) Prefs.get().setPlaybackFlag(false);
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (kidsLock) {
            Toast.makeText(this, R.string.kids_lock_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        if (locked) { Toast.makeText(this, R.string.locked_hint, Toast.LENGTH_SHORT).show(); return; }
        super.onBackPressed();
    }
}
