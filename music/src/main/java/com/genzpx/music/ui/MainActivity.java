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
import com.genzpx.music.data.Library;
import com.genzpx.music.data.MediaLibrary;
import com.genzpx.music.data.Prefs;
import com.genzpx.music.model.Song;
import com.genzpx.music.playback.PlayerService;
import com.genzpx.music.util.ArtLoader;
import com.genzpx.music.util.DeviceGuard;
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
    private View emptyView, permView, miniPlayer, tipCard;
    private TextView emptyText, miniTitle, miniArtist;
    private ImageView miniArt;
    private ImageButton miniPlayPause;
    private EditText searchBox;
    private BottomNavigationView bottomNav;
    private TextView toolbarTitle;
    private ImageButton backBtn;

    private int currentTab = 0;
    private LibraryAdapter libAdapter;
    private MediaLibrary.Group openedGroup = null;
    private String openedPlaylist = null;
    private int libraryMode = 0; // 0 ringkasan, 1 favorit, 2 baru diputar
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
        tipCard = findViewById(R.id.tip_card);
        miniTitle = findViewById(R.id.mini_title);
        miniArtist = findViewById(R.id.mini_artist);
        miniArt = findViewById(R.id.mini_art);
        miniPlayPause = findViewById(R.id.mini_play_pause);
        searchBox = findViewById(R.id.search_box);
        bottomNav = findViewById(R.id.bottom_nav);
        toolbarTitle = findViewById(R.id.toolbar_title);
        backBtn = findViewById(R.id.btn_back);

        list.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(this::playFrom, this::onSongLongPress);
        groupAdapter = new GroupAdapter(this::openGroup, R.drawable.ic_album_placeholder);
        list.setAdapter(songAdapter);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_songs) currentTab = 0;
            else if (id == R.id.nav_albums) currentTab = 1;
            else if (id == R.id.nav_artists) currentTab = 2;
            else if (id == R.id.nav_folders) currentTab = 3;
            else currentTab = 4;
            openedGroup = null;
            openedPlaylist = null;
            Prefs.get().setLastTab(currentTab);
            showTab();
            return true;
        });

        findViewById(R.id.btn_search).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.btn_menu).setOnClickListener(v -> showMenu());
        backBtn.setOnClickListener(v -> {
            if (openedPlaylist != null) openedPlaylist = null;
            else if (currentTab == 4 && libraryMode != 0) libraryMode = 0;
            else { openedGroup = null; searching = false; }
            showTab();
        });

        miniPlayer.setOnClickListener(v -> openNowPlaying());
        miniPlayPause.setOnClickListener(v -> { if (bound) service.togglePlayPause(); });
        findViewById(R.id.mini_next).setOnClickListener(v -> { if (bound) service.next(true); });

        findViewById(R.id.btn_grant).setOnClickListener(v -> requestPerm());

        findViewById(R.id.tip_action).setOnClickListener(v -> new GuardSheet(this).show());
        findViewById(R.id.tip_close).setOnClickListener(v -> {
            // Ditutup permanen: jangan pernah ganggu pengguna lagi
            Prefs.get().setGuardTipDismissed(true);
            Prefs.get().resetKillCount();
            tipCard.setVisibility(View.GONE);
        });

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

        updateTipCard();

        currentTab = Prefs.get().getLastTab();
        bottomNav.setSelectedItemId(tabToMenuId(currentTab));

        if (hasPerm()) loadLibrary();
        else showPermScreen();
    }

    /**
     * Kartu saran hanya tampil kalau pemutaran benar-benar pernah dihentikan
     * paksa oleh sistem (minimal dua kali), dan pengguna belum menutupnya.
     * Aplikasi tidak pernah menampilkan ini tanpa sebab.
     */
    private void updateTipCard() {
        boolean show = !Prefs.get().isGuardTipDismissed()
                && Prefs.get().getKillCount() >= 2
                && !DeviceGuard.isBatteryUnrestricted(this);
        tipCard.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private int tabToMenuId(int tab) {
        switch (tab) {
            case 1: return R.id.nav_albums;
            case 2: return R.id.nav_artists;
            case 3: return R.id.nav_folders;
            case 4: return R.id.nav_library;
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

        if (openedPlaylist != null) {
            toolbarTitle.setText(openedPlaylist);
            backBtn.setVisibility(View.VISIBLE);
            List<Song> items = Library.get().playlistSongs(openedPlaylist);
            songAdapter.submit(items);
            list.setAdapter(songAdapter);
            updateEmpty(items.isEmpty(), getString(R.string.playlist_empty));
            refreshMini();
            return;
        }

        if (currentTab == 4) {
            showLibraryTab();
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
                List<Song> s = MediaLibrary.get().sorted();
                songAdapter.submit(s);
                list.setAdapter(songAdapter);
                updateEmpty(s.isEmpty(), getString(R.string.empty_songs));
                break;
            }
        }
        refreshMini();
    }

    /** Tab Pustaka: favorit, baru diputar, dan daftar putar buatan sendiri. */
    private void showLibraryTab() {
        if (libraryMode == 1) {
            toolbarTitle.setText(R.string.favorites);
            backBtn.setVisibility(View.VISIBLE);
            List<Song> f = Library.get().favorites();
            songAdapter.submit(f);
            list.setAdapter(songAdapter);
            updateEmpty(f.isEmpty(), getString(R.string.no_favorites));
            return;
        }
        if (libraryMode == 2) {
            toolbarTitle.setText(R.string.recently_played);
            backBtn.setVisibility(View.VISIBLE);
            List<Song> r = Library.get().recentlyPlayed();
            songAdapter.submit(r);
            list.setAdapter(songAdapter);
            updateEmpty(r.isEmpty(), getString(R.string.no_recent));
            return;
        }

        toolbarTitle.setText(R.string.tab_library);
        backBtn.setVisibility(View.GONE);
        list.setAdapter(libraryAdapter());
        updateEmpty(false, "");
    }

    private LibraryAdapter libraryAdapter() {
        if (libAdapter == null) {
            libAdapter = new LibraryAdapter(new LibraryAdapter.Listener() {
                @Override public void onFavorites() { libraryMode = 1; showTab(); }
                @Override public void onRecent() { libraryMode = 2; showTab(); }
                @Override public void onPlaylist(String name) { openedPlaylist = name; showTab(); }
                @Override public void onNewPlaylist() {
                    SongActions.promptNewPlaylist(MainActivity.this, name -> showTab());
                }
                @Override public void onPlaylistLongPress(String name) {
                    showPlaylistMenu(name);
                }
            });
        }
        libAdapter.refresh();
        return libAdapter;
    }

    private void showPlaylistMenu(String name) {
        String[] items = {getString(R.string.rename), getString(R.string.delete)};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(items, (d, which) -> {
                    if (which == 0) promptRename(name);
                    else confirmDelete(name);
                })
                .show();
    }

    private void promptRename(String oldName) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(oldName);
        input.setSingleLine(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.rename)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    if (!Library.get().renamePlaylist(oldName,
                            input.getText().toString())) {
                        Toast.makeText(this, R.string.name_taken, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showTab();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.delete_playlist_confirm, name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Library.get().deletePlaylist(name);
                    showTab();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateEmpty(boolean empty, String msg) {
        emptyText.setText(msg);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onSongLongPress(Song s) {
        SongActions.showMenu(this, s, this::showTab);
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

    /** Pemutar mini tetap ada; menekannya membuka layar penuh. */
    private void openNowPlaying() {
        if (!bound || service == null || service.currentSong() == null) return;
        startActivity(new Intent(this, NowPlayingActivity.class));
        overridePendingTransition(R.anim.slide_up, 0);
    }

    // ---------- Menu ----------

    private void showMenu() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_menu, null);

        v.findViewById(R.id.menu_sort).setOnClickListener(x -> { d.dismiss(); showSortDialog(); });
        v.findViewById(R.id.menu_sleep).setOnClickListener(x -> { d.dismiss(); showSleepTimer(); });
        v.findViewById(R.id.menu_eq).setOnClickListener(x -> { d.dismiss(); openEqualizer(); });
        v.findViewById(R.id.menu_theme).setOnClickListener(x -> { d.dismiss(); showThemeDialog(); });
        v.findViewById(R.id.menu_rescan).setOnClickListener(x -> {
            d.dismiss();
            ArtLoader.clear();
            loadLibrary();
        });
        v.findViewById(R.id.menu_guard).setOnClickListener(x -> {
            d.dismiss();
            new GuardSheet(this).show();
        });
        v.findViewById(R.id.menu_about).setOnClickListener(x -> { d.dismiss(); showAbout(); });

        d.setContentView(v);
        d.show();
    }

    private void showSortDialog() {
        String[] items = {
                getString(R.string.sort_title_az), getString(R.string.sort_artist),
                getString(R.string.sort_album), getString(R.string.sort_date),
                getString(R.string.sort_duration)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_by)
                .setSingleChoiceItems(items, Prefs.get().getSort(), (d, which) -> {
                    Prefs.get().setSort(which);
                    d.dismiss();
                    showSortDirection();
                })
                .show();
    }

    private void showSortDirection() {
        String[] dir = {getString(R.string.ascending), getString(R.string.descending)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_order)
                .setSingleChoiceItems(dir, Prefs.get().isSortDescending() ? 1 : 0, (d, which) -> {
                    Prefs.get().setSortDescending(which == 1);
                    d.dismiss();
                    showTab();
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
    protected void onResume() {
        super.onResume();
        updateTipCard();
    }

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
        if (openedPlaylist != null) { openedPlaylist = null; showTab(); return; }
        if (currentTab == 4 && libraryMode != 0) { libraryMode = 0; showTab(); return; }
        if (openedGroup != null || searching) {
            openedGroup = null;
            searching = false;
            showTab();
            return;
        }
        super.onBackPressed();
    }
}
