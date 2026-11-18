package id.klinik.inventarisobat;

final class Config {
    private Config() {}

    /** Alamat web app Apps Script (deployment yang sama dengan yang dipakai di browser). */
    static final String EXEC_URL =
            "https://script.google.com/macros/s/AKfycbzkU8iyHvZVtJWLveTsrUL7iyaT2rh-u5Xn2ttaKKQup-hay52T1hH5u1JsHJeFxHM/exec";

    /** Data Cari/Restok dimuat ulang saat tab dibuka bila data sudah lebih tua dari ini (milidetik). */
    static final long STALE_MS = 5000;
}
