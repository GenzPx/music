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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzpx.video.BuildConfig;
import com.genzpx.video.R;
import com.genzpx.video.VideoApp;
import com.genzpx.video.data.Prefs;
import com.genzpx.video.data.VideoLibrary;
import com.genzpx.video.data.Watchlist;
import com.genzpx.video.model.Video;
import com.genzpx.video.util.DeviceGuard;
import com.genzpx.video.util.ThumbLoader;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERM = 100;

    private RecyclerView list;
    private VideoAdapter videoAdapter;
    private FolderAdapter folderAdapter;
    private View emptyView, permView, tipCard;
    private TextView emptyText, toolbarTitle;
    private EditText searchBox;
    private ImageButton backBtn, viewBtn;

    private boolean showingFolders = false;
    private int viewMode = 0;   // 0 semua video, 2 favorit, 3 riwayat
    private VideoLibrary.Folder openedFolder = null;
    private boolean searching = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty_view);
        emptyText = findViewById(R.id.empty_text);
        permView = findViewById(R.id.perm_view);
        tipCard = findViewById(R.id.tip_card);
        toolbarTitle = findViewById(R.id.toolbar_title);
        searchBox = findViewById(R.id.search_box);
        backBtn = findViewById(R.id.btn_back);
        viewBtn = findViewById(R.id.btn_view);

        videoAdapter = new VideoAdapter(this::playFrom, Prefs.get().isGridView(), this::showVideoMenu);
        folderAdapter = new FolderAdapter(f -> { openedFolder = f; showContent(); });
        applyLayoutManager();
        list.setAdapter(videoAdapter);

        findViewById(R.id.btn_search).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.btn_menu).setOnClickListener(v -> showMenu());
        backBtn.setOnClickListener(v -> {
            if (viewMode != 0) viewMode = 0;
            openedFolder = null;
            searching = false;
            showContent();
        });

        viewBtn.setOnClickListener(v -> {
            Prefs.get().setGridView(!Prefs.get().isGridView());
            videoAdapter.setGrid(Prefs.get().isGridView());
            applyLayoutManager();
            updateViewIcon();
        });
        updateViewIcon();

        findViewById(R.id.btn_grant).setOnClickListener(v -> requestPerm());

        findViewById(R.id.tip_action).setOnClickListener(v -> new GuardSheet(this).show());
        findViewById(R.id.tip_close).setOnClickListener(v -> {
            Prefs.get().setGuardTipDismissed(true);
            Prefs.get().resetKillCount();
            tipCard.setVisibility(View.GONE);
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (!searching) return;
                List<Video> r = VideoLibrary.get().search(s.toString());
                videoAdapter.submit(r);
                list.setAdapter(videoAdapter);
                updateEmpty(r.isEmpty(), getString(R.string.no_result));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (hasPerm()) loadLibrary();
        else showPermScreen();
    }

    private void applyLayoutManager() {
        if (showingFolders || openedFolder == null && showingFolders) {
            list.setLayoutManager(new LinearLayoutManager(this));
            return;
        }
        if (Prefs.get().isGridView()) {
            list.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            list.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void updateViewIcon() {
        viewBtn.setImageResource(Prefs.get().isGridView()
                ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
    }

    // ---------- Izin ----------

    private String permName() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasPerm() {
        return ContextCompat.checkSelfPermission(this, permName())
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerm() {
        List<String> p = new ArrayList<>();
        p.add(permName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this, p.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_PERM && hasPerm()) {
            permView.setVisibility(View.GONE);
            loadLibrary();
        }
    }

    private void showPermScreen() {
        permView.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    // ---------- Memuat ----------

    private void loadLibrary() {
        permView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.scanning);
        list.setVisibility(View.GONE);

        new AsyncTask<Void, Void, Void>() {
            @Override protected Void doInBackground(Void... v) {
                VideoLibrary.get().load(MainActivity.this);
                return null;
            }
            @Override protected void onPostExecute(Void v) {
                if (isFinishing()) return;
                showContent();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showContent() {
        searching = false;
        searchBox.setVisibility(View.GONE);
        searchBox.setText("");

        if (openedFolder != null) {
            toolbarTitle.setText(openedFolder.name);
            backBtn.setVisibility(View.VISIBLE);
            viewBtn.setVisibility(View.VISIBLE);
            applyLayoutManager();
            videoAdapter.submit(openedFolder.items);
            list.setAdapter(videoAdapter);
            updateEmpty(openedFolder.items.isEmpty(), getString(R.string.empty));
            return;
        }

        if (viewMode == 2 || viewMode == 3) {
            boolean fav = viewMode == 2;
            toolbarTitle.setText(fav ? R.string.title_favorites : R.string.title_history);
            backBtn.setVisibility(View.VISIBLE);
            viewBtn.setVisibility(View.VISIBLE);
            applyLayoutManager();
            List<Video> items = fav
                    ? Watchlist.get().favorites()
                    : Watchlist.get().history();
            videoAdapter.submit(items);
            list.setAdapter(videoAdapter);
            updateEmpty(items.isEmpty(),
                    getString(fav ? R.string.no_favorites : R.string.no_history));
            return;
        }

        backBtn.setVisibility(View.GONE);

        if (showingFolders) {
            toolbarTitle.setText(R.string.title_folders);
            viewBtn.setVisibility(View.GONE);
            list.setLayoutManager(new LinearLayoutManager(this));
            List<VideoLibrary.Folder> f = VideoLibrary.get().folders();
            folderAdapter.submit(f);
            list.setAdapter(folderAdapter);
            updateEmpty(f.isEmpty(), getString(R.string.empty));
        } else {
            toolbarTitle.setText(R.string.title_videos);
            viewBtn.setVisibility(View.VISIBLE);
            applyLayoutManager();
            List<Video> v = VideoLibrary.get().sorted();
            videoAdapter.submit(v);
            list.setAdapter(videoAdapter);
            updateEmpty(v.isEmpty(), getString(R.string.empty));
        }
    }

    /** Daftar video yang tontonannya belum selesai, langsung bisa dilanjutkan. */
    private void showContinueWatching() {
        List<Video> items = Watchlist.get().continueWatching();
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.no_continue, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Video v = items.get(i);
            long pos = Prefs.get().getPosition(v.id);
            labels[i] = v.title + "\n" + com.genzpx.video.util.Fmt.time(pos)
                    + " / " + com.genzpx.video.util.Fmt.time(v.duration);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_continue)
                .setItems(labels, (d, which) -> playFrom(items, which))
                .show();
    }

    /** Menu tekan-lama pada satu video. */
    private void showVideoMenu(Video video) {
        boolean fav = Watchlist.get().isFavorite(video.id);
        long saved = Prefs.get().getPosition(video.id);

        List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();

        labels.add(getString(fav ? R.string.remove_favorite : R.string.add_favorite));
        actions.add(0);

        if (saved > 0) {
            labels.add(getString(R.string.resume_from,
                    com.genzpx.video.util.Fmt.time(saved)));
            actions.add(1);
            labels.add(getString(R.string.play_from_start));
            actions.add(2);
        }

        labels.add(getString(R.string.menu_share));
        actions.add(3);
        labels.add(getString(R.string.menu_info));
        actions.add(4);

        new AlertDialog.Builder(this)
                .setTitle(video.title)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    switch (actions.get(which)) {
                        case 0:
                            boolean added = Watchlist.get().toggleFavorite(video.id);
                            Toast.makeText(this,
                                    added ? R.string.added_favorite : R.string.removed_favorite,
                                    Toast.LENGTH_SHORT).show();
                            if (viewMode == 2) showContent();
                            break;
                        case 1:
                            playSingle(video);
                            break;
                        case 2:
                            Prefs.get().clearPosition(video.id);
                            playSingle(video);
                            break;
                        case 3:
                            shareVideo(video);
                            break;
                        default:
                            showVideoInfo(video);
                            break;
                    }
                })
                .show();
    }

    private void playSingle(Video v) {
        List<Video> one = new ArrayList<>();
        one.add(v);
        playFrom(one, 0);
    }

    private void shareVideo(Video v) {
        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                    .setType("video/*")
                    .putExtra(Intent.EXTRA_STREAM, v.uri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    getString(R.string.menu_share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showVideoInfo(Video v) {
        String res = v.resolution().isEmpty() ? "-" : v.resolution();
        String body = getString(R.string.info_body_fmt,
                v.title,
                com.genzpx.video.util.Fmt.time(v.duration),
                res,
                com.genzpx.video.util.Fmt.size(v.size),
                v.path);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_info)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void updateEmpty(boolean empty, String msg) {
        emptyText.setText(msg);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void toggleSearch() {
        searching = !searching;
        if (searching) {
            searchBox.setVisibility(View.VISIBLE);
            searchBox.requestFocus();
            backBtn.setVisibility(View.VISIBLE);
            videoAdapter.submit(new ArrayList<>());
            list.setAdapter(videoAdapter);
            updateEmpty(true, getString(R.string.type_to_search));
        } else {
            showContent();
        }
    }

    // ---------- Memutar ----------

    private void playFrom(List<Video> items, int position) {
        if (position < 0 || position >= items.size()) return;
        long[] ids = new long[items.size()];
        for (int i = 0; i < items.size(); i++) ids[i] = items.get(i).id;

        startActivity(new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, items.get(position).id)
                .putExtra(PlayerActivity.EXTRA_LIST_IDS, ids));
    }

    // ---------- Menu ----------

    private void showMenu() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_menu, null);

        TextView foldersRow = v.findViewById(R.id.menu_folders);
        foldersRow.setText(showingFolders ? R.string.show_all_videos : R.string.title_folders);
        foldersRow.setOnClickListener(x -> {
            d.dismiss();
            showingFolders = !showingFolders;
            viewMode = 0;
            openedFolder = null;
            showContent();
        });

        v.findViewById(R.id.menu_favorites).setOnClickListener(x -> {
            d.dismiss();
            viewMode = 2;
            showingFolders = false;
            openedFolder = null;
            showContent();
        });
        v.findViewById(R.id.menu_history).setOnClickListener(x -> {
            d.dismiss();
            viewMode = 3;
            showingFolders = false;
            openedFolder = null;
            showContent();
        });
        v.findViewById(R.id.menu_continue).setOnClickListener(x -> {
            d.dismiss();
            showContinueWatching();
        });

        v.findViewById(R.id.menu_sort).setOnClickListener(x -> { d.dismiss(); showSort(); });
        v.findViewById(R.id.menu_playback).setOnClickListener(x -> { d.dismiss(); showPlaybackOptions(); });
        v.findViewById(R.id.menu_theme).setOnClickListener(x -> { d.dismiss(); showTheme(); });
        v.findViewById(R.id.menu_guard).setOnClickListener(x -> { d.dismiss(); new GuardSheet(this).show(); });
        v.findViewById(R.id.menu_rescan).setOnClickListener(x -> {
            d.dismiss();
            ThumbLoader.clear();
            loadLibrary();
        });
        v.findViewById(R.id.menu_about).setOnClickListener(x -> { d.dismiss(); showAbout(); });

        d.setContentView(v);
        d.show();
    }

    private void showSort() {
        String[] items = {
                getString(R.string.sort_date), getString(R.string.sort_name),
                getString(R.string.sort_size), getString(R.string.sort_duration)
        };
        // Urutan tampilan berbeda dari nilai konstanta, jadi dipetakan
        final int[] map = {Prefs.SORT_DATE, Prefs.SORT_NAME, Prefs.SORT_SIZE, Prefs.SORT_DURATION};
        int checked = 0;
        for (int i = 0; i < map.length; i++) if (map[i] == Prefs.get().getSort()) checked = i;

        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    Prefs.get().setSort(map[which]);
                    d.dismiss();
                    showContent();
                })
                .show();
    }

    private void showPlaybackOptions() {
        String[] items = {
                getString(R.string.opt_audio_mode),
                getString(R.string.opt_auto_pip),
                getString(R.string.opt_resume),
                getString(R.string.opt_keep_screen)
        };
        boolean[] checked = {
                Prefs.get().isAudioModeEnabled(),
                Prefs.get().isAutoPip(),
                Prefs.get().isResumeEnabled(),
                Prefs.get().isKeepScreenOn()
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_playback)
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> {
                    switch (which) {
                        case 0: Prefs.get().setAudioModeEnabled(isChecked); break;
                        case 1: Prefs.get().setAutoPip(isChecked); break;
                        case 2: Prefs.get().setResumeEnabled(isChecked); break;
                        default: Prefs.get().setKeepScreenOn(isChecked); break;
                    }
                })
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showTheme() {
        String[] items = {getString(R.string.theme_system),
                getString(R.string.theme_light), getString(R.string.theme_dark)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme)
                .setSingleChoiceItems(items, Prefs.get().getThemeMode(), (d, which) -> {
                    Prefs.get().setThemeMode(which);
                    VideoApp.applyTheme(which);
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
        new AlertDialog.Builder(this).setView(v)
                .setPositiveButton(R.string.close, null).show();
    }

    // ---------- Kartu saran ----------

    private void updateTipCard() {
        boolean show = !Prefs.get().isGuardTipDismissed()
                && Prefs.get().getKillCount() >= 2
                && !DeviceGuard.isBatteryUnrestricted(this);
        tipCard.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTipCard();
        // Kemajuan menonton bisa berubah setelah kembali dari pemutar
        if (VideoLibrary.get().isLoaded() && list.getAdapter() != null) {
            list.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (viewMode != 0 && !searching && openedFolder == null) {
            viewMode = 0;
            showContent();
            return;
        }
        if (openedFolder != null || searching) {
            openedFolder = null;
            searching = false;
            showContent();
            return;
        }
        super.onBackPressed();
    }
}
