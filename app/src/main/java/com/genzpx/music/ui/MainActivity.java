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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzpx.music.BuildConfig;
import com.genzpx.music.MusicApp;
import com.genzpx.music.R;
import com.genzpx.music.data.MediaLibrary;
import com.genzpx.music.data.Prefs;
import com.genzpx.music.model.Song;
import com.genzpx.music.playback.PlayerService;
import com.genzpx.music.util.ArtLoader;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERM = 100;

    private PlayerService service;
    private boolean bound = false;

    private RecyclerView list;
    private SongAdapter songAdapter;
    private GroupAdapter groupAdapter;
    private View emptyView, permView, miniPlayer;
    private TextView emptyText, miniTitle, miniArtist;
    private ImageView miniArt;
    private ImageButton miniPlayPause;
    private EditText searchBox;
    private BottomNavigationView bottomNav;
    private TextView toolbarTitle;
    private ImageButton backBtn;

    private int currentTab = 0;
    private MediaLibrary.Group openedGroup = null;
    private boolean searching = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlayerService.LocalBinder) binder).getService();
            bound = true;
            refreshMini();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false; service = null;
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refreshMini(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty_view);
        emptyText = findViewById(R.id.empty_text);
        permView = findViewById(R.id.perm_view);
        miniPlayer = findViewById(R.id.mini_player);
        miniTitle = findViewById(R.id.mini_title);
        miniArtist = findViewById(R.id.mini_artist);
        miniArt = findViewById(R.id.mini_art);
        miniPlayPause = findViewById(R.id.mini_play_pause);
        searchBox = findViewById(R.id.search_box);
        bottomNav = findViewById(R.id.bottom_nav);
        toolbarTitle = findViewById(R.id.toolbar_title);
        backBtn = findViewById(R.id.btn_back);

        list.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(this::playFrom);
        groupAdapter = new GroupAdapter(this::openGroup, R.drawable.ic_album_placeholder);
        list.setAdapter(songAdapter);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_songs) currentTab = 0;
            else if (id == R.id.nav_albums) currentTab = 1;
            else if (id == R.id.nav_artists) currentTab = 2;
            else currentTab = 3;
            openedGroup = null;
            Prefs.get().setLastTab(currentTab);
            showTab();
            return true;
        });

        findViewById(R.id.btn_search).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.btn_menu).setOnClickListener(v -> showMenu());
        backBtn.setOnClickListener(v -> { openedGroup = null; searching = false; showTab(); });

        miniPlayer.setOnClickListener(v -> openNowPlaying());
        miniPlayPause.setOnClickListener(v -> { if (bound) service.togglePlayPause(); });
        findViewById(R.id.mini_next).setOnClickListener(v -> { if (bound) service.next(true); });

        findViewById(R.id.btn_grant).setOnClickListener(v -> requestPerm());

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (searching) {
                    songAdapter.submit(MediaLibrary.get().search(s.toString()));
                    list.setAdapter(songAdapter);
                    updateEmpty(MediaLibrary.get().search(s.toString()).isEmpty(),
                            getString(R.string.no_result));
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        currentTab = Prefs.get().getLastTab();
        bottomNav.setSelectedItemId(tabToMenuId(currentTab));

        if (hasPerm()) loadLibrary();
        else showPermScreen();
    }

    private int tabToMenuId(int tab) {
        switch (tab) {
            case 1: return R.id.nav_albums;
            case 2: return R.id.nav_artists;
            case 3: return R.id.nav_folders;
            default: return R.id.nav_songs;
        }
    }

    // ---------- Izin ----------

    private String permName() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasPerm() {
        return ContextCompat.checkSelfPermission(this, permName())
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerm() {
        List<String> perms = new ArrayList<>();
        perms.add(permName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM && hasPerm()) {
            permView.setVisibility(View.GONE);
            loadLibrary();
        }
    }

    private void showPermScreen() {
        permView.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    // ---------- Muat pustaka ----------

    private void loadLibrary() {
        permView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.scanning);
        list.setVisibility(View.GONE);

        new AsyncTask<Void, Void, Void>() {
            @Override protected Void doInBackground(Void... voids) {
                MediaLibrary.get().load(MainActivity.this);
                return null;
            }
            @Override protected void onPostExecute(Void v) {
                if (isFinishing()) return;
                showTab();
                bindService();
                restoreLastSession();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Siapkan lagu terakhir supaya bisa langsung lanjut. */
    private void restoreLastSession() {
        long lastId = Prefs.get().getLastSongId();
        if (lastId < 0) return;
        Song s = MediaLibrary.get().byId(lastId);
        if (s == null) return;
        handler.postDelayed(() -> {
            if (bound && service.currentSong() == null) {
                List<Song> all = MediaLibrary.get().getSongs();
                int idx = all.indexOf(s);
                if (idx >= 0) {
                    service.restoreLast(all, idx, Prefs.get().getLastPosition());
                }
            }
        }, 500);
    }

    // ---------- Tampilan tab ----------

    private void showTab() {
        searching = false;
        searchBox.setVisibility(View.GONE);
        searchBox.setText("");

        if (openedGroup != null) {
            toolbarTitle.setText(openedGroup.title);
            backBtn.setVisibility(View.VISIBLE);
            songAdapter.submit(openedGroup.items);
            list.setAdapter(songAdapter);
            updateEmpty(openedGroup.items.isEmpty(), getString(R.string.empty_songs));
            refreshMini();
            return;
        }

        backBtn.setVisibility(View.GONE);
        switch (currentTab) {
            case 1: {
                toolbarTitle.setText(R.string.tab_albums);
                List<MediaLibrary.Group> g = MediaLibrary.get().albums();
                groupAdapter.submit(g);
                list.setAdapter(groupAdapter);
                updateEmpty(g.isEmpty(), getString(R.string.empty_songs));
                break;
            }
            case 2: {
                toolbarTitle.setText(R.string.tab_artists);
                List<MediaLibrary.Group> g = MediaLibrary.get().artists();
                groupAdapter.submit(g);
                list.setAdapter(groupAdapter);
                updateEmpty(g.isEmpty(), getString(R.string.empty_songs));
                break;
            }
            case 3: {
                toolbarTitle.setText(R.string.tab_folders);
                List<MediaLibrary.Group> g = MediaLibrary.get().folders();
                groupAdapter.submit(g);
                list.setAdapter(groupAdapter);
                updateEmpty(g.isEmpty(), getString(R.string.empty_songs));
                break;
            }
            default: {
                toolbarTitle.setText(R.string.tab_songs);
                List<Song> s = MediaLibrary.get().getSongs();
                songAdapter.submit(s);
                list.setAdapter(songAdapter);
                updateEmpty(s.isEmpty(), getString(R.string.empty_songs));
                break;
            }
        }
        refreshMini();
    }

    private void updateEmpty(boolean empty, String msg) {
        emptyText.setText(msg);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openGroup(MediaLibrary.Group g) {
        openedGroup = g;
        showTab();
    }

    private void toggleSearch() {
        searching = !searching;
        if (searching) {
            searchBox.setVisibility(View.VISIBLE);
            searchBox.requestFocus();
            songAdapter.submit(new ArrayList<>());
            list.setAdapter(songAdapter);
            updateEmpty(true, getString(R.string.type_to_search));
            backBtn.setVisibility(View.VISIBLE);
        } else {
            showTab();
        }
    }

    // ---------- Pemutaran ----------

    private void playFrom(List<Song> songs, int position) {
        if (position < 0) return;
        if (!bound) { bindService(); Toast.makeText(this, R.string.starting, Toast.LENGTH_SHORT).show(); }
        Intent i = new Intent(this, PlayerService.class);
        ContextCompat.startForegroundService(this, i);
        handler.postDelayed(() -> {
            if (bound && service != null) {
                service.setQueueAndPlay(songs, position);
                refreshMini();
            }
        }, bound ? 0 : 400);
    }

    private void bindService() {
        bindService(new Intent(this, PlayerService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private void refreshMini() {
        if (!bound || service == null || service.currentSong() == null) {
            miniPlayer.setVisibility(View.GONE);
            songAdapter.setHighlight(-1);
            return;
        }
        Song s = service.currentSong();
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(s.title);
        miniArtist.setText(s.artist);
        miniPlayPause.setImageResource(
                service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        songAdapter.setHighlight(s.id);

        new AsyncTask<Void, Void, Bitmap>() {
            @Override protected Bitmap doInBackground(Void... v) {
                return ArtLoader.load(MainActivity.this, s, 128);
            }
            @Override protected void onPostExecute(Bitmap b) {
                if (b != null) miniArt.setImageBitmap(b);
                else miniArt.setImageResource(R.drawable.ic_album_placeholder);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void openNowPlaying() {
        if (!bound || service.currentSong() == null) return;
        new NowPlayingSheet(this, service).show();
    }

    // ---------- Menu ----------

    private void showMenu() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_menu, null);

        v.findViewById(R.id.menu_sleep).setOnClickListener(x -> { d.dismiss(); showSleepTimer(); });
        v.findViewById(R.id.menu_eq).setOnClickListener(x -> { d.dismiss(); openEqualizer(); });
        v.findViewById(R.id.menu_theme).setOnClickListener(x -> { d.dismiss(); showThemeDialog(); });
        v.findViewById(R.id.menu_rescan).setOnClickListener(x -> {
            d.dismiss();
            ArtLoader.clear();
            loadLibrary();
        });
        v.findViewById(R.id.menu_about).setOnClickListener(x -> { d.dismiss(); showAbout(); });

        d.setContentView(v);
        d.show();
    }

    private void showSleepTimer() {
        final int[] mins = {15, 30, 45, 60, 90};
        String[] labels = new String[mins.length + 1];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] + " menit";
        labels[mins.length] = getString(R.string.sleep_off);

        new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_timer)
                .setItems(labels, (dlg, which) -> {
                    if (!bound) return;
                    if (which == mins.length) {
                        service.cancelSleepTimer();
                        Toast.makeText(this, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
                    } else {
                        service.setSleepTimer(mins[which]);
                        Toast.makeText(this,
                                getString(R.string.sleep_set, mins[which]),
                                Toast.LENGTH_SHORT).show();
                    }
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

    private void showThemeDialog() {
        String[] items = {getString(R.string.theme_system),
                getString(R.string.theme_light), getString(R.string.theme_dark)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme)
                .setSingleChoiceItems(items, Prefs.get().getThemeMode(), (d, which) -> {
                    Prefs.get().setThemeMode(which);
                    MusicApp.applyTheme(which);
                    d.dismiss();
                    recreate();
                })
                .show();
    }

    private void showAbout() {
        View v = getLayoutInflater().inflate(R.layout.dialog_about, null);
        TextView ver = v.findViewById(R.id.about_version);
        ver.setText(getString(R.string.about_version_fmt,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        new AlertDialog.Builder(this).setView(v).setPositiveButton(R.string.close, null).show();
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onStart() {
        super.onStart();
        if (hasPerm() && !bound) bindService();
        IntentFilter f = new IntentFilter(PlayerService.BROADCAST_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, f);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        if (bound) { try { unbindService(conn); } catch (Exception ignored) {} bound = false; }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (openedGroup != null || searching) {
            openedGroup = null;
            searching = false;
            showTab();
            return;
        }
        super.onBackPressed();
    }
}
