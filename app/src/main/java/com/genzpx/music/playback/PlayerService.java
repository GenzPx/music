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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import com.genzpx.music.R;
import com.genzpx.music.data.Prefs;
import com.genzpx.music.model.Song;
import com.genzpx.music.ui.MainActivity;
import com.genzpx.music.util.ArtLoader;

import java.util.List;

public class PlayerService extends Service
        implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY_PAUSE = "com.genzpx.music.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.genzpx.music.NEXT";
    public static final String ACTION_PREV = "com.genzpx.music.PREV";
    public static final String ACTION_STOP = "com.genzpx.music.STOP";

    public static final String BROADCAST_STATE = "com.genzpx.music.STATE_CHANGED";

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIF_ID = 1;

    private final IBinder binder = new LocalBinder();
    private final Queue queue = new Queue();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private MediaSessionCompat session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private PowerManager.WakeLock wakeLock;

    private boolean prepared = false;
    private boolean playAfterPrepare = false;
    private boolean wasPlayingBeforeLoss = false;
    private int audioSessionId = 0;

    private Runnable sleepRunnable;
    private long sleepAtMillis = 0;

    public class LocalBinder extends Binder {
        public PlayerService getService() { return PlayerService.this; }
    }

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Headset dicabut -> pause, jangan tiba-tiba nyaring di speaker
            if (isPlaying()) pause();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Music:playback");
        wakeLock.setReferenceCounted(false);

        createChannel();
        setupSession();

        registerReceiver(noisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void setupSession() {
        session = new MediaSessionCompat(this, "Music");
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(true); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
            @Override public void onStop() { stopPlayback(); }
        });
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE: togglePlayPause(); break;
                case ACTION_NEXT: next(true); break;
                case ACTION_PREV: previous(); break;
                case ACTION_STOP: stopPlayback(); break;
                default: MediaButtonReceiver.handleIntent(session, intent); break;
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return binder; }

    // ---------------- Kontrol ----------------

    public Queue getQueue() { return queue; }

    public void setQueueAndPlay(List<Song> songs, int index) {
        queue.setQueue(songs, index);
        openCurrent(true, 0);
    }

    public void playAt(int index) {
        if (queue.jumpTo(index) != null) openCurrent(true, 0);
    }

    /** Siapkan lagu terakhir tanpa langsung memutar (fitur resume). */
    public void restoreLast(List<Song> songs, int index, int positionMs) {
        queue.setQueue(songs, index);
        openCurrent(false, positionMs);
    }

    private void openCurrent(boolean autoPlay, int seekMs) {
        Song s = queue.current();
        if (s == null) return;

        releasePlayer();
        prepared = false;
        playAfterPrepare = autoPlay;

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build());
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        player.setOnPreparedListener(mp -> {
            prepared = true;
            audioSessionId = mp.getAudioSessionId();
            if (seekMs > 0 && seekMs < mp.getDuration()) mp.seekTo(seekMs);
            if (playAfterPrepare) play();
            else {
                updateMetadata();
                updateState();
                startForeground(NOTIF_ID, buildNotification());
                broadcast();
            }
        });

        try {
            player.setDataSource(this, s.uri());
            player.prepareAsync();
        } catch (Exception e) {
            // File hilang / rusak -> lompat ke lagu berikutnya
            handler.postDelayed(() -> next(false), 300);
        }
    }

    public void play() {
        if (player == null || !prepared) { playAfterPrepare = true; return; }
        if (!requestFocus()) return;
        player.start();
        Prefs.get().setPlaybackFlag(true);
        if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
        updateMetadata();
        updateState();
        startForeground(NOTIF_ID, buildNotification());
        broadcast();
        startProgressSaver();
    }

    public void pause() {
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
            savePosition();
        }
        Prefs.get().setPlaybackFlag(false);
        if (wakeLock.isHeld()) wakeLock.release();
        updateState();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
        stopForeground(false);
        broadcast();
    }

    public void togglePlayPause() { if (isPlaying()) pause(); else play(); }

    public void next(boolean manual) {
        Song s = queue.next(manual);
        if (s == null) { stopPlayback(); return; }
        openCurrent(true, 0);
    }

    public void previous() {
        // Kalau lagu udah jalan > 3 detik, ulang dari awal (kebiasaan pemutar musik)
        if (player != null && prepared && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            broadcast();
            return;
        }
        if (queue.previous() != null) openCurrent(true, 0);
    }

    public void seekTo(int ms) {
        if (player != null && prepared) {
            player.seekTo(ms);
            updateState();
            broadcast();
        }
    }

    public void stopPlayback() {
        savePosition();
        Prefs.get().setPlaybackFlag(false);
        releasePlayer();
        abandonFocus();
        if (wakeLock.isHeld()) wakeLock.release();
        session.setActive(false);
        stopForeground(true);
        broadcast();
        stopSelf();
    }

    public boolean isPlaying() {
        return player != null && prepared && player.isPlaying();
    }

    public Song currentSong() { return queue.current(); }

    public int position() {
        try { return (player != null && prepared) ? player.getCurrentPosition() : 0; }
        catch (Exception e) { return 0; }
    }

    public int duration() {
        try { return (player != null && prepared) ? player.getDuration() : 0; }
        catch (Exception e) { return 0; }
    }

    public int getAudioSessionId() { return audioSessionId; }

    public void toggleShuffle() {
        Prefs.get().setShuffle(!Prefs.get().isShuffle());
        queue.reshuffle();
        broadcast();
    }

    public void cycleRepeat() {
        int r = Prefs.get().getRepeat();
        Prefs.get().setRepeat((r + 1) % 3);
        broadcast();
    }

    // ---------------- Sleep timer ----------------

    public void setSleepTimer(int minutes) {
        cancelSleepTimer();
        if (minutes <= 0) return;
        sleepAtMillis = System.currentTimeMillis() + minutes * 60_000L;
        sleepRunnable = () -> {
            pause();
            sleepAtMillis = 0;
            sleepRunnable = null;
            broadcast();
        };
        handler.postDelayed(sleepRunnable, minutes * 60_000L);
        broadcast();
    }

    public void cancelSleepTimer() {
        if (sleepRunnable != null) handler.removeCallbacks(sleepRunnable);
        sleepRunnable = null;
        sleepAtMillis = 0;
    }

    /** Sisa menit timer, 0 kalau tidak aktif. */
    public long sleepRemainingMs() {
        return sleepAtMillis == 0 ? 0 : Math.max(0, sleepAtMillis - System.currentTimeMillis());
    }

    // ---------------- Equalizer sistem ----------------

    public Intent equalizerIntent() {
        Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        return i;
    }

    // ---------------- Audio focus ----------------

    private boolean requestFocus() {
        int res;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener(this)
                    .build();
            res = audioManager.requestAudioFocus(focusRequest);
        } else {
            res = audioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                wasPlayingBeforeLoss = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                wasPlayingBeforeLoss = isPlaying();
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null && prepared) player.setVolume(0.2f, 0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (player != null && prepared) player.setVolume(1f, 1f);
                if (wasPlayingBeforeLoss) { play(); wasPlayingBeforeLoss = false; }
                break;
        }
    }

    // ---------------- Notifikasi ----------------

    private Notification buildNotification() {
        Song s = queue.current();
        boolean playing = isPlaying();

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(s == null ? getString(R.string.app_name) : s.title)
                .setContentText(s == null ? "" : s.artist)
                .setSubText(s == null ? null : s.album)
                .setContentIntent(openPi)
                .setDeleteIntent(servicePi(ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing);

        Bitmap art = s == null ? null : ArtLoader.load(this, s, 512);
        if (art != null) b.setLargeIcon(art);

        b.addAction(R.drawable.ic_prev, getString(R.string.prev), servicePi(ACTION_PREV));
        b.addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                getString(playing ? R.string.pause : R.string.play), servicePi(ACTION_PLAY_PAUSE));
        b.addAction(R.drawable.ic_next, getString(R.string.next), servicePi(ACTION_NEXT));

        b.setStyle(new MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(servicePi(ACTION_STOP)));

        return b.build();
    }

    private int flagImmutable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private PendingIntent servicePi(String action) {
        Intent i = new Intent(this, PlayerService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());
    }

    private void updateMetadata() {
        Song s = queue.current();
        if (s == null) return;
        MediaMetadataCompat.Builder m = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, s.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration());
        Bitmap art = ArtLoader.load(this, s, 512);
        if (art != null) m.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(m.build());
    }

    private void updateState() {
        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_STOP;
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING
                                : PlaybackStateCompat.STATE_PAUSED,
                        position(), 1f)
                .build());
    }

    private void broadcast() {
        sendBroadcast(new Intent(BROADCAST_STATE).setPackage(getPackageName()));
    }

    // ---------------- Resume ----------------

    private final Runnable saver = new Runnable() {
        @Override public void run() {
            if (isPlaying()) {
                savePosition();
                handler.postDelayed(this, 5000);
            }
        }
    };

    private void startProgressSaver() {
        handler.removeCallbacks(saver);
        handler.postDelayed(saver, 5000);
    }

    private void savePosition() {
        Song s = queue.current();
        if (s != null) Prefs.get().saveLastPlayed(s.id, position());
    }

    // ---------------- Lifecycle ----------------

    @Override public void onCompletion(MediaPlayer mp) { next(false); }

    @Override public boolean onError(MediaPlayer mp, int what, int extra) {
        prepared = false;
        handler.postDelayed(() -> next(false), 300);
        return true;
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); player.release(); } catch (Exception ignored) {}
            player = null;
        }
        prepared = false;
    }

    @Override
    public void onDestroy() {
        savePosition();
        Prefs.get().setPlaybackFlag(false);
        cancelSleepTimer();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        abandonFocus();
        try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (session != null) session.release();
        super.onDestroy();
    }
}
