# Inventaris Obat - Aplikasi Android (Klinik Lapas Blangpidie)

Tiga tab:
- **Beranda** - web app Apps Script Anda apa adanya (dashboard, input barang masuk/keluar, ekspor, dst).
- **Cari** - pencarian cepat: stok Gudang, stok Depo Obat, tanggal expired.
- **Restok** - obat habis total + obat/instrumen dengan stok total (gudang + depo) di bawah 100.

Semua membaca/menulis lewat script dan spreadsheet yang sama, jadi web, aplikasi, dan spreadsheet selalu sinkron.

## Langkah 1 - Aktifkan API di Apps Script (wajib)
1. Buka proyek Apps Script > klik **+** di samping "File" > **Script** > beri nama `Api` > tempel seluruh isi `apps-script/Api.gs`.
2. Di `Code.gs`, tambahkan SATU baris di paling atas fungsi `doGet(e)` yang sudah ada:

       if (e && e.parameter && e.parameter.api) return apiHandle_(e);

3. **Deploy > Kelola deployment** > ikon pensil > Versi: **Versi baru** > **Terapkan**.
   (Wajib versi baru, tetapi alamat /exec tidak berubah.)
4. Tes di browser: `https://script.google.com/macros/s/.../exec?api=items`
   Harus muncul teks JSON yang diawali `{"ok":true,...`

## Langkah 2 - Buat APK (pilih salah satu)
**A. GitHub (tanpa memasang apa pun di komputer)**
1. Buat akun/repository GitHub baru (boleh private).
2. Upload ISI folder ini ke repository (termasuk folder `.github`). Jika folder `.github` tidak ikut
   terunggah, pilih **Add file > Create new file**, ketik nama `.github/workflows/build-apk.yml`,
   lalu tempel isi file tersebut.
3. Tab **Actions** > **Build APK** > **Run workflow**. Tunggu sekitar 3-5 menit.
4. Buka run yang selesai > bagian **Artifacts** > unduh `inventaris-obat-apk` (zip berisi `app-debug.apk`).

**B. Android Studio**
File > Open > pilih folder ini > tunggu sinkronisasi > **Build > Build APK(s)**.
File ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Langkah 3 - Pasang di HP
Kirim `app-debug.apk` ke HP (WhatsApp/Drive/kabel), buka, izinkan "pasang dari sumber ini".
Butuh Android 8.0 atau lebih baru.

## Pengaturan
- Alamat web app: `app/src/main/java/id/klinik/inventarisobat/Config.java` (EXEC_URL).
- Batas restok (100) dan batas "hampir exp" (90 hari): bagian atas `Api.gs`
  (`API_BATAS_RESTOK`, `API_HAMPIR_EXP_HARI`). Ubah lalu deploy versi baru; aplikasi ikut otomatis.
- Ikon: ganti `res/drawable/ic_launcher_foreground.xml` / warna di `res/values/colors.xml`.

## Cara kerja data
- Baris Gudang dan Depo Obat dengan nama sama digabung menjadi satu item.
- Stok yang dipakai: kolom **Stok Terkini**. Sel kosong dianggap 0.
- Expired yang ditampilkan: tanggal paling cepat di antara baris yang masih ada stoknya.
- Tanggal berbentuk teks dibaca hari/bulan/tahun (21/06/2028); "09/2027" dianggap akhir bulan.
- Habis = total 0 atau kurang. Menipis = total di bawah 100.
- Tab Cari/Restok dimuat ulang saat dibuka dan saat aplikasi dibuka kembali; tombol **Segarkan** memuat paksa.
  Data terakhir disimpan di HP sebagai cadangan jika sinyal hilang.
