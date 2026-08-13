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
package com.genzpx.video.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.genzpx.video.R;
import com.genzpx.video.data.Prefs;
import com.genzpx.video.ui.MainActivity;

/**
 * Melanjutkan suara saja setelah pengguna meninggalkan layar pemutar.
 * Hanya berjalan kalau opsi "Lanjutkan audio saja" dinyalakan sendiri
 * oleh pengguna; secara bawaan opsi ini mati.
 */
@OptIn(markerClass = UnstableApi.class)
public class AudioModeService extends Service {

    public static final String ACTION_START = "com.genzpx.video.AUDIO_START";
    public static final String ACTION_TOGGLE = "com.genzpx.video.AUDIO_TOGGLE";
    public static final String ACTION_STOP = "com.genzpx.video.AUDIO_STOP";

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_POSITION = "position";

    private static final String CHANNEL_ID = "audio_mode";
    private static final int NOTIF_ID = 2;

    private ExoPlayer player;
    private String title = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.channel_audio), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) { stopSelf(); return START_NOT_STICKY; }

        switch (intent.getAction()) {
            case ACTION_START: {
                Uri uri = intent.getParcelableExtra(EXTRA_URI);
                title = intent.getStringExtra(EXTRA_TITLE);
                long pos = intent.getLongExtra(EXTRA_POSITION, 0);
                if (uri == null) { stopSelf(); break; }
                start(uri, pos);
                break;
            }
            case ACTION_TOGGLE:
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                    updateNotification();
                }
                break;
            case ACTION_STOP:
            default:
                stopEverything();
                break;
        }
        return START_NOT_STICKY;
    }

    private void start(Uri uri, long position) {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(new Player.Listener() {
                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    updateNotification();
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) stopEverything();
                }
            });
        }
        player.setMediaItem(MediaItem.fromUri(uri));
        player.setPlaybackParameters(
                new androidx.media3.common.PlaybackParameters(Prefs.get().getPlaybackSpeed()));
        player.prepare();
        if (position > 0) player.seekTo(position);
        player.play();
        Prefs.get().setPlaybackFlag(true);
        startForeground(NOTIF_ID, buildNotification());
    }

    private void stopEverything() {
        Prefs.get().setPlaybackFlag(false);
        if (player != null) { player.release(); player = null; }
        stopForeground(true);
        stopSelf();
    }

    private int flagImmutable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private PendingIntent servicePi(String action) {
        Intent i = new Intent(this, AudioModeService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());
    }

    private Notification buildNotification() {
        boolean playing = player != null && player.isPlaying();

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play)
                .setContentTitle(title == null || title.isEmpty()
                        ? getString(R.string.app_name) : title)
                .setContentText(getString(R.string.audio_only_running))
                .setContentIntent(open)
                .setDeleteIntent(servicePi(ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        getString(playing ? R.string.pause : R.string.play),
                        servicePi(ACTION_TOGGLE))
                .addAction(R.drawable.ic_close, getString(R.string.stop),
                        servicePi(ACTION_STOP))
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        Prefs.get().setPlaybackFlag(false);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
