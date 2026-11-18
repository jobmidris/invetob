package id.klinik.inventarisobat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Mengambil data dari endpoint ?api=items milik Api.gs (Apps Script). */
final class ApiClient {
    private ApiClient() {}

    static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }

    static final class Result {
        List<Item> items = new ArrayList<>();
        String generated = "";
        int batas = 100;
        String raw = "";
    }

    static Result fetch(String execUrl) throws Exception {
        String sep = execUrl.contains("?") ? "&" : "?";
        URL url = new URL(execUrl + sep + "api=items&_=" + System.currentTimeMillis());
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : read(in);
            if (code >= 400 && !body.trim().startsWith("{")) {
                throw new ApiException("Server menjawab kode " + code + ".");
            }
            return parse(body);
        } finally {
            c.disconnect();
        }
    }

    static Result parse(String body) throws ApiException {
        String t = body == null ? "" : body.trim();
        if (!t.startsWith("{")) {
            throw new ApiException("Endpoint API belum aktif. Tempel Api.gs ke Apps Script, tambahkan satu baris di doGet, lalu deploy versi baru.");
        }
        try {
            JSONObject o = new JSONObject(t);
            if (!o.optBoolean("ok", false)) {
                throw new ApiException(o.optString("error", "Respons tidak valid."));
            }
            Result r = new Result();
            r.raw = t;
            r.generated = o.optString("generated", "");
            r.batas = o.optInt("batasRestok", 100);
            JSONArray arr = o.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject j = arr.getJSONObject(i);
                    Item it = new Item();
                    it.nama = j.optString("nama", "");
                    it.kodeGudang = j.optString("kodeGudang", "");
                    it.kodeDepo = j.optString("kodeDepo", "");
                    it.satuanGudang = j.optString("satuanGudang", "");
                    it.satuanDepo = j.optString("satuanDepo", "");
                    it.gudang = j.optDouble("gudang", 0);
                    it.depo = j.optDouble("depo", 0);
                    it.total = j.optDouble("total", it.gudang + it.depo);
                    it.exp = j.optString("exp", "");
                    it.expText = j.optString("expText", "-");
                    it.expHari = j.isNull("expHari") ? null : Integer.valueOf(j.optInt("expHari"));
                    it.expStatus = j.optString("expStatus", "none");
                    it.restok = j.optString("restok", "");
                    r.items.add(it);
                }
            }
            return r;
        } catch (JSONException e) {
            throw new ApiException("Format data tidak dikenali.");
        }
    }

    private static String read(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        br.close();
        return sb.toString();
    }
}
