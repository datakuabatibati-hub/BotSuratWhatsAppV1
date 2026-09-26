# Bot Surat WhatsApp V1 — Baileys di dalam APK

V1 ini dirancang agar satu HP Android menjalankan:

**WhatsApp ⇄ Baileys ⇄ Node.js embedded ⇄ API lokal ⇄ UI Android ⇄ Preview ⇄ Print**

Tidak perlu VPS, Cloudflare Worker, atau polling internet untuk fungsi dasarnya.

## Fitur V1

- Node.js 24 embedded (`libnode.so`, arm64-v8a).
- Baileys berjalan di foreground service Android.
- Pairing menggunakan nomor telepon / pairing code.
- Perintah WhatsApp `SURAT` / `BUAT SURAT`.
- Form percakapan: jenis → nama → NIK → alamat → keperluan → konfirmasi.
- Antrean surat tersimpan lokal di HP.
- Preview surat HTML A4.
- Print melalui Android Print Framework.
- Tombol DEMO agar alur print bisa dites tanpa WhatsApp.
- Status `PENDING` → `PRINTED`.

## Catatan penting

Baileys adalah library tidak resmi WhatsApp. Gunakan untuk alur layanan yang sah dan hindari spam/broadcast massal. Perubahan protokol WhatsApp dapat membuat Baileys perlu diperbarui.

## Cara build paling mudah: GitHub Actions

1. Buat repository GitHub kosong.
2. Upload seluruh isi folder proyek ini ke repository.
3. Pastikan branch bernama `main`.
4. Buka tab **Actions**.
5. Pilih **Build Bot Surat APK** → **Run workflow**.
6. Setelah build selesai, buka hasil workflow dan download artifact **BotSuratWhatsApp-debug**.
7. Di dalam artifact terdapat `app-debug.apk`.

Workflow otomatis:

- memasang Node.js host,
- `npm install` Baileys,
- membungkus `nodejs-project` menjadi asset APK,
- mengunduh Node.js Mobile 24 untuk Android,
- mengambil `libnode.so` ARM64 + headers,
- build APK dengan Gradle/CMake.

## Cara memakai APK

1. Install APK pada HP Android ARM64.
2. Buka APK, tekan **NYALAKAN BOT**.
3. Masukkan nomor WhatsApp dalam format kode negara, contoh `628123456789`.
4. Tekan **BUAT KODE PAIRING**.
5. Di WhatsApp buka **Perangkat tertaut → Tautkan perangkat → Tautkan dengan nomor telepon** lalu masukkan kode.
6. Setelah status `🟢 TERHUBUNG`, dari nomor lain kirim `SURAT` ke nomor bot.
7. Ikuti pertanyaan bot.
8. Setelah konfirmasi `YA`, buka/refresh antrean pada APK.
9. Pilih surat → cek preview → **PRINT SURAT**. Setelah benar-benar keluar dari printer, tekan **TANDAI SUDAH SELESAI / TERCETAK**.

## Tes tanpa WhatsApp

Tekan **BUAT DEMO**. Sebuah surat contoh akan masuk ke antrean. Pilih surat itu dan tekan **PRINT SURAT**. Ini berguna untuk memastikan APK + printer sudah bekerja sebelum menguji Baileys.

## Mengubah template surat

Edit:

`nodejs-project/templates/surat-keterangan.html`

Placeholder yang tersedia:

- `{{nomor}}`
- `{{nama}}`
- `{{nik}}`
- `{{alamat}}`
- `{{keperluan}}`
- `{{tanggal}}`

Setelah mengubah template, commit/push lagi agar GitHub Actions membuat APK baru.

## Menambah jenis surat

V1 sengaja hanya satu jenis surat agar fondasi Baileys-in-APK diuji dulu. Setelah pairing, background service, penerimaan pesan dan print stabil, tambahkan template baru dan state percakapan pada `nodejs-project/index.mjs`.

## Struktur

```text
BotSuratWhatsAppV1/
├─ .github/workflows/build-apk.yml
├─ nodejs-project/
│  ├─ index.mjs
│  ├─ package.json
│  └─ templates/surat-keterangan.html
├─ scripts/
│  ├─ prepare_node_assets.sh
│  └─ prepare_libnode.sh
└─ app/
   ├─ CMakeLists.txt
   ├─ libnode/                 # diisi otomatis saat build
   └─ src/main/
      ├─ cpp/native-lib.cpp
      ├─ java/...              # UI + foreground service
      └─ res/layout/activity_main.xml
```

## Batasan V1

- APK hanya dibuild untuk **arm64-v8a**.
- Foreground service harus dibiarkan aktif; beberapa merek HP mungkin perlu menonaktifkan battery optimization untuk APK ini.
- V1 mencetak dari WebView melalui Android Print Framework. Printer harus memiliki Android Print Service / dukungan sistem.
- Template sekarang adalah contoh `Surat Keterangan`, bukan format final resmi.
- Baileys dapat berubah sewaktu-waktu; bila WhatsApp mengubah protokol, dependency perlu diperbarui dan APK dibuild ulang.
