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
package com.genzpx.music.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Membantu pengguna melonggarkan pembatasan latar belakang bawaan ROM
 * (autostart / penghemat baterai) yang sering menghentikan pemutaran.
 *
 * Semuanya bersifat saran. Aplikasi tidak pernah mengubah setelan sistem
 * sendiri, hanya membukakan halaman yang tepat supaya pengguna tidak perlu
 * mencari manual.
 */
public class DeviceGuard {

    public static final int BRAND_GENERIC = 0;
    public static final int BRAND_XIAOMI = 1;
    public static final int BRAND_OPPO = 2;
    public static final int BRAND_REALME = 3;
    public static final int BRAND_VIVO = 4;
    public static final int BRAND_SAMSUNG = 5;
    public static final int BRAND_HUAWEI = 6;
    public static final int BRAND_TRANSSION = 7;
    public static final int BRAND_ONEPLUS = 8;
    public static final int BRAND_ASUS = 9;

    public static int brand() {
        String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER)
                .toLowerCase(Locale.US);
        String b = (Build.BRAND == null ? "" : Build.BRAND).toLowerCase(Locale.US);
        String all = m + " " + b;

        if (all.contains("xiaomi") || all.contains("redmi") || all.contains("poco")) return BRAND_XIAOMI;
        if (all.contains("realme")) return BRAND_REALME;
        if (all.contains("oppo")) return BRAND_OPPO;
        if (all.contains("vivo") || all.contains("iqoo")) return BRAND_VIVO;
        if (all.contains("samsung")) return BRAND_SAMSUNG;
        if (all.contains("huawei") || all.contains("honor")) return BRAND_HUAWEI;
        if (all.contains("infinix") || all.contains("tecno") || all.contains("itel")
                || all.contains("transsion")) return BRAND_TRANSSION;
        if (all.contains("oneplus")) return BRAND_ONEPLUS;
        if (all.contains("asus")) return BRAND_ASUS;
        return BRAND_GENERIC;
    }

    /** Nama merek untuk ditampilkan. */
    public static String brandLabel() {
        switch (brand()) {
            case BRAND_XIAOMI: return "Xiaomi / Redmi / POCO";
            case BRAND_OPPO: return "OPPO";
            case BRAND_REALME: return "realme";
            case BRAND_VIVO: return "vivo / iQOO";
            case BRAND_SAMSUNG: return "Samsung";
            case BRAND_HUAWEI: return "Huawei / Honor";
            case BRAND_TRANSSION: return "Infinix / Tecno / itel";
            case BRAND_ONEPLUS: return "OnePlus";
            case BRAND_ASUS: return "ASUS";
            default:
                String m = Build.MANUFACTURER;
                if (m == null || m.isEmpty()) return "perangkat ini";
                return m.substring(0, 1).toUpperCase(Locale.US) + m.substring(1);
        }
    }

    /** Apakah aplikasi sudah dikecualikan dari penghemat baterai. */
    public static boolean isBatteryUnrestricted(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return true;
        try {
            return pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Buka daftar penghemat baterai.
     *
     * Sengaja memakai ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS (membuka
     * daftar) dan bukan ACTION_REQUEST_... (dialog langsung), karena yang
     * kedua mewajibkan izin REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. Aplikasi
     * ini menjaga daftar izinnya tetap sependek mungkin.
     */
    public static boolean openBatterySettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (start(ctx, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                return true;
            }
        }
        return openAppInfo(ctx);
    }

    /** Buka halaman info aplikasi. Selalu tersedia di Android mana pun. */
    public static boolean openAppInfo(Context ctx) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", ctx.getPackageName(), null));
        return start(ctx, i);
    }

    /**
     * Coba buka layar autostart / pengelola startup khas merek.
     * Layar ini tidak resmi dan sering berubah antar versi ROM, jadi setiap
     * kandidat dicoba berurutan dan kegagalan ditangani diam-diam.
     *
     * @return true kalau salah satu berhasil dibuka
     */
    public static boolean openAutostart(Context ctx) {
        for (ComponentName cn : autostartCandidates()) {
            Intent i = new Intent();
            i.setComponent(cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (start(ctx, i)) return true;
        }
        return false;
    }

    /** Apakah perangkat ini punya layar autostart yang bisa dibuka. */
    public static boolean hasAutostart(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (ComponentName cn : autostartCandidates()) {
            Intent i = new Intent().setComponent(cn);
            if (i.resolveActivity(pm) != null) return true;
        }
        return false;
    }

    private static List<ComponentName> autostartCandidates() {
        List<ComponentName> l = new ArrayList<>();
        switch (brand()) {
            case BRAND_XIAOMI:
                l.add(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                break;
            case BRAND_OPPO:
            case BRAND_REALME:
                l.add(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                l.add(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"));
                l.add(new ComponentName("com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"));
                l.add(new ComponentName("com.coloros.phonemanager", ""));
                break;
            case BRAND_VIVO:
                l.add(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                l.add(new ComponentName("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"));
                l.add(new ComponentName("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
                break;
            case BRAND_HUAWEI:
                l.add(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                l.add(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                break;
            case BRAND_SAMSUNG:
                l.add(new ComponentName("com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"));
                l.add(new ComponentName("com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"));
                break;
            case BRAND_TRANSSION:
                l.add(new ComponentName("com.transsion.phonemaster",
                        "com.cyin.himgr.autostart.AutoStartActivity"));
                l.add(new ComponentName("com.transsion.phonemanager", ""));
                break;
            case BRAND_ASUS:
                l.add(new ComponentName("com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"));
                l.add(new ComponentName("com.asus.mobilemanager",
                        "com.asus.mobilemanager.entry.FunctionActivity"));
                break;
            case BRAND_ONEPLUS:
                l.add(new ComponentName("com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                break;
            default:
                break;
        }
        // Buang entri berkomponen kosong yang dipakai sebagai penanda paket saja
        List<ComponentName> out = new ArrayList<>();
        for (ComponentName cn : l) {
            if (cn.getClassName() != null && !cn.getClassName().isEmpty()) out.add(cn);
        }
        return out;
    }

    private static boolean start(Context ctx, Intent i) {
        try {
            if (i.resolveActivity(ctx.getPackageManager()) == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Langkah manual khas merek, ditampilkan sebagai daftar bernomor. */
    public static String[] steps() {
        switch (brand()) {
            case BRAND_XIAOMI:
                return new String[]{
                        "Setelan → Aplikasi → Kelola aplikasi → Music",
                        "Buka Hemat baterai, pilih Tanpa batasan",
                        "Kembali, lalu aktifkan Mulai otomatis",
                        "Di layar Aplikasi Terkini, tahan kartu Music lalu tekan ikon gembok"
                };
            case BRAND_OPPO:
            case BRAND_REALME:
                return new String[]{
                        "Setelan → Baterai → Konsumsi daya latar belakang, izinkan Music",
                        "Setelan → Aplikasi → Music → Izin mulai otomatis, aktifkan",
                        "Di layar Aplikasi Terkini, tahan kartu Music lalu tekan ikon gembok"
                };
            case BRAND_VIVO:
                return new String[]{
                        "Setelan → Baterai → Konsumsi daya tinggi latar belakang, izinkan Music",
                        "Setelan → Aplikasi → Akses khusus → Mulai otomatis, aktifkan untuk Music",
                        "Di layar Aplikasi Terkini, tahan kartu Music lalu tekan ikon gembok"
                };
            case BRAND_SAMSUNG:
                return new String[]{
                        "Setelan → Baterai → Batas penggunaan latar belakang",
                        "Buka Aplikasi tidak pernah tidur, tambahkan Music",
                        "Pastikan Music tidak ada di daftar Aplikasi tidur"
                };
            case BRAND_HUAWEI:
                return new String[]{
                        "Setelan → Baterai → Peluncuran aplikasi",
                        "Cari Music, ubah ke Kelola manual",
                        "Aktifkan ketiganya: Luncurkan otomatis, Luncurkan sekunder, Jalan di latar belakang"
                };
            case BRAND_TRANSSION:
                return new String[]{
                        "Buka Phone Master → Penghemat daya",
                        "Masukkan Music ke daftar yang dilindungi",
                        "Setelan → Aplikasi → Music → Baterai, pilih Tanpa batasan"
                };
            case BRAND_ONEPLUS:
                return new String[]{
                        "Setelan → Baterai → Optimasi baterai → Music → Jangan optimalkan",
                        "Setelan → Baterai → Pengoptimalan baterai lanjutan, matikan",
                        "Di layar Aplikasi Terkini, tahan kartu Music lalu tekan ikon gembok"
                };
            case BRAND_ASUS:
                return new String[]{
                        "Buka Mobile Manager → Pengelola daya",
                        "Masukkan Music ke daftar Boot otomatis",
                        "Setelan → Aplikasi → Music → Baterai, pilih Tanpa batasan"
                };
            default:
                return new String[]{
                        "Setelan → Aplikasi → Music → Baterai, pilih Tanpa batasan",
                        "Di layar Aplikasi Terkini, tahan kartu Music lalu tekan ikon gembok"
                };
        }
    }
}
