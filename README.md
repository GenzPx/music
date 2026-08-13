# Music

Pemutar musik offline untuk Android. Tanpa iklan, tanpa login, tanpa akun.

Dibuat karena aplikasi Musik bawaan ColorOS di Indonesia menampilkan iklan
setiap kali dibuka, dan toggle "Hide Ads" yang tersedia di sebagian negara
lain tidak ada di region ini.

---

## Tanpa izin INTERNET

Ini bukan janji, ini dipaksa sistem operasi.

`AndroidManifest.xml` aplikasi ini **tidak mencantumkan
`android.permission.INTERNET`**. Tanpa izin itu, Android melarang aplikasi
membuka koneksi jaringan apa pun di level kernel — walaupun ada kode yang
mencoba. Konsekuensinya:

- Iklan tidak bisa dimuat, karena harus diunduh dari server
- Data penggunaan tidak bisa dikirim keluar
- Tidak ada pembaruan diam-diam yang menambahkan iklan

Silakan buktikan sendiri setelah memasang:

**Pengaturan → Aplikasi → Music → Izin**

Setiap build di CI juga diperiksa otomatis. Kalau APK sampai meminta izin
internet, build langsung digagalkan sebelum dirilis.

---

## Fitur

| | |
|---|---|
| Pindai otomatis | Membaca lagu dari MediaStore, tanpa perlu atur folder |
| Jelajah | Tab Lagu, Album, Artis, dan Folder |
| Pemutaran latar | Tetap jalan saat layar mati, kontrol di notifikasi & layar kunci |
| Kontrol media | Tombol headset dan Bluetooth berfungsi |
| Antrean | Acak, ulangi satu, ulangi semua |
| Cari | Judul, artis, album |
| Lanjutkan | Buka lagi aplikasi, lanjut di detik terakhir |
| Timer tidur | 15 / 30 / 45 / 60 / 90 menit |
| Equalizer | Memakai equalizer bawaan sistem |
| Tema | Terang, gelap, atau ikut sistem |

Otomatis jeda saat headset dicabut, dan saat ada telepon masuk atau aplikasi
lain memutar suara.

---

## Unduh

APK tersedia di halaman [Releases](../../releases).

Cara pasang:

1. Unduh berkas `.apk` versi terbaru
2. Buka lewat pengelola berkas di HP
3. Kalau muncul peringatan, izinkan "Instal aplikasi tak dikenal"
4. Buka aplikasi dan beri izin baca file audio

Aplikasi ini tidak menggantikan aplikasi bawaan — keduanya bisa terpasang
bersamaan.

**Minimal Android 7.0 (API 24).** Diuji pada target Android 14 (API 34).

---

## Build sendiri

```bash
git clone https://github.com/GenzPx/music.git
cd music
./gradlew assembleRelease
```

Butuh JDK 17 dan Android SDK (compileSdk 34).

Hasilnya ada di `app/build/outputs/apk/release/`.

### Menandatangani rilis

Supaya update APK berikutnya bisa menimpa versi lama, gunakan keystore yang
sama setiap kali. Buat sekali:

```bash
keytool -genkeypair -v -keystore release.jks \
  -alias music -keyalg RSA -keysize 2048 -validity 10000
```

Lalu simpan di GitHub sebagai repository secrets:

| Secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | Hasil `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | Kata sandi keystore |
| `KEY_ALIAS` | `music` |
| `KEY_PASSWORD` | Kata sandi kunci |

> Simpan berkas `release.jks` baik-baik dan jangan pernah di-commit.
> Kalau hilang, update berikutnya tidak bisa menimpa versi terpasang —
> pengguna harus copot pasang dulu.

### Merilis versi baru

```bash
git tag v0.1
git push origin v0.1
```

GitHub Actions akan build APK, memverifikasi tidak ada izin internet, lalu
membuat Release otomatis.

---

## Teknis

- Java, tanpa Kotlin — APK lebih kecil, build lebih cepat
- `MediaPlayer` + `MediaSessionCompat`, tanpa ExoPlayer
- Tanpa library pihak ketiga selain AndroidX dan Material Components
- Tanpa analitik, tanpa crash reporter, tanpa SDK iklan
- Ukuran APK sekitar 1,6 MB

---

## Lisensi

GPL-3.0 — lihat [LICENSE](LICENSE).

Artinya siapa pun boleh memakai, mempelajari, dan memodifikasi kode ini,
tetapi versi yang disebarluaskan wajib tetap sumber terbuka dengan lisensi
yang sama. Ini disengaja: supaya tidak ada yang mengambil kode ini lalu
merilisnya kembali dengan iklan di dalamnya.

Dibuat oleh **GenzPX**.
