package id.klinik.inventarisobat;

/** Satu obat/instrumen (baris Gudang + baris Depo Obat sudah digabung oleh Api.gs). */
final class Item {
    String nama = "";
    String kodeGudang = "";
    String kodeDepo = "";
    String satuanGudang = "";
    String satuanDepo = "";
    double gudang;
    double depo;
    double total;
    String exp = "";
    String expText = "-";
    Integer expHari;            // null bila tidak ada tanggal expired
    String expStatus = "none";  // none | aman | hampir | expired
    String restok = "";         // "" | menipis | habis
}
