# Nex Pic

Galeri Android bergaya **Material 3** (`com.nex.pic`), Android 11 – 16 (API 30 – 36).

## Fitur
- Komponen Material 3, pengaturan tertata ulang, ikon bergaya Material Symbols
- Warna dinamis **Monet** (Android 12+), 5 palet kustom (Android 11 memakai palet), bilah translusen, tema **AMOLED** hitam pekat
- Ikon adaptif + ikon bertema (themed icon), splash screen animasi
- Layar tinggi, display cutout, layar lipat (jumlah kolom menyesuaikan lebar jendela)
- Album, Momen (per bulan), urutan, pencarian, ukuran thumbnail
- Penampil: **ketuk dua kali = zoom, ketuk dua kali lagi = kembali 100%**, **cubit dua jari = zoom in/out**, geser antar gambar
- Slideshow, hapus / ganti nama / bagikan / detail, pemutar video, kunci aplikasi (kunci layar perangkat)
- Rotasi (kiri / kanan / 180°), **Sunting** (potong, balik, menyusut), **Pindahkan ke**, **Salin ke**, **Perbaiki tanggal**, **Tampilkan di peta**
- Urutan disimpan **per folder**; folder bisa disembunyikan, dikecualikan, atau diganti namanya
- Release dikompres: R8 + shrink resources, tanpa library native

## Build lewat GitHub Actions
1. Upload seluruh isi folder ini ke repo GitHub (branch `main`).
2. Buka tab **Actions** → workflow **Build Nex Pic** (otomatis jalan saat push, atau klik *Run workflow*).
3. Setelah selesai, unduh APK di **Artifacts → NexPic-release**.
4. Untuk membuat Release: buat tag, mis. `v1.0.0` → APK terlampir otomatis.

### Tanda tangan (opsional, disarankan)
Tanpa secrets, workflow membuat keystore baru tiap build, jadi APK tidak bisa menimpa instalasi lama
dengan tanda tangan berbeda. Agar stabil, tambahkan di *Settings → Secrets → Actions*:
`KEYSTORE_BASE64` (hasil `base64 -w0 nexpic.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`.

## Build lokal
JDK 17 + Android SDK 36, lalu `gradle assembleRelease` (Gradle 8.13).
Untuk membuat wrapper: `gradle wrapper --gradle-version 8.13`.
