package id.klinik.inventarisobat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Logika murni (tanpa Android) untuk pencarian, daftar restok, dan format angka/tanggal. */
final class Logic {
    private static final String[] BULAN = {"Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"};

    private Logic() {}

    static List<Item> search(List<Item> all, String query) {
        List<Item> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            out.addAll(all);
            return out;
        }
        String[] tokens = q.split("\\s+");
        for (Item it : all) {
            String hay = (it.nama + " " + it.kodeGudang + " " + it.kodeDepo).toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String t : tokens) {
                if (!hay.contains(t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(it);
        }
        return out;
    }

    /** filter: "semua", "habis", atau "menipis". Habis di urutan paling atas, lalu stok paling sedikit. */
    static List<Item> restock(List<Item> all, String filter) {
        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            if (it.restok.isEmpty()) continue;
            if (filter.equals("semua") || filter.equals(it.restok)) out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int ra = a.restok.equals("habis") ? 0 : 1;
                int rb = b.restok.equals("habis") ? 0 : 1;
                if (ra != rb) return ra - rb;
                int c = Double.compare(a.total, b.total);
                if (c != 0) return c;
                return a.nama.compareToIgnoreCase(b.nama);
            }
        });
        return out;
    }

    /** type: "habis", "menipis", atau "semua" (habis + menipis). */
    static int count(List<Item> all, String type) {
        int n = 0;
        for (Item it : all) {
            if (it.restok.isEmpty()) continue;
            if (type.equals("semua") || type.equals(it.restok)) n++;
        }
        return n;
    }

    static String num(double v) {
        if (v == 0) v = 0; // hindari "-0"
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }

    /** "2026-11-18 14:05:09" -> "18 Nov 2026 14:05" */
    static String formatGenerated(String g) {
        try {
            if (g == null || g.length() < 16) return "-";
            int month = Integer.parseInt(g.substring(5, 7));
            int day = Integer.parseInt(g.substring(8, 10));
            return day + " " + BULAN[month - 1] + " " + g.substring(0, 4) + " " + g.substring(11, 16);
        } catch (Exception e) {
            return g == null ? "-" : g;
        }
    }
}
