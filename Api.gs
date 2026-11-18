/**
 * Api.gs - tambahan untuk web app "Inventaris Obat - Klinik Lapas Blangpidie"
 * Dipakai aplikasi Android (tab Cari & Restok). Tidak mengubah fungsi lain di Code.gs.
 *
 * Cara pakai: tempel seluruh isi file ini sebagai file baru (Api.gs) di proyek Apps Script,
 * lalu tambahkan SATU baris di awal fungsi doGet(e) yang sudah ada:
 *
 *   if (e && e.parameter && e.parameter.api) return apiHandle_(e);
 */
var API_SPREADSHEET_ID = '17J8xeWjWMn1NyttaBDQBOrGb2lJ0-mARncHyp7WeICY';
var API_SHEET_NAME = 'Master Obat';
var API_BATAS_RESTOK = 100;      // stok total (gudang + depo) di bawah angka ini = menipis
var API_HAMPIR_EXP_HARI = 90;    // sisa hari sebelum expired yang dihitung "hampir exp" (samakan dengan Code.gs)
var API_BULAN = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];

function apiHandle_(e) {
  var out;
  try {
    var action = String((e && e.parameter && e.parameter.api) || 'items');
    if (action === 'items') {
      out = apiItems_();
    } else {
      out = { ok: false, error: 'Aksi tidak dikenal: ' + action };
    }
  } catch (err) {
    out = { ok: false, error: String(err && err.message ? err.message : err) };
  }
  return ContentService.createTextOutput(JSON.stringify(out))
    .setMimeType(ContentService.MimeType.JSON);
}

function apiItems_() {
  var ss = SpreadsheetApp.openById(API_SPREADSHEET_ID);
  var sh = ss.getSheetByName(API_SHEET_NAME);
  if (!sh) throw new Error('Sheet "' + API_SHEET_NAME + '" tidak ditemukan');
  return apiBuild_(sh.getDataRange().getValues(), ss.getSpreadsheetTimeZone(), new Date());
}

function apiFindCol_(headers, prefix) {
  for (var i = 0; i < headers.length; i++) {
    if (String(headers[i]).toLowerCase().replace(/\s+/g, ' ').trim().indexOf(prefix) === 0) return i;
  }
  throw new Error('Kolom "' + prefix + '" tidak ditemukan di baris judul sheet ' + API_SHEET_NAME);
}

function apiNum_(v) {
  if (typeof v === 'number') return isFinite(v) ? v : 0;
  if (v === null || v === undefined) return 0;
  var s = String(v).replace(/\s/g, '');
  if (s === '') return 0;
  var n = Number(s.replace(/\./g, '').replace(',', '.'));
  return isFinite(n) ? n : 0;
}

function apiPad_(n) { return (n < 10 ? '0' : '') + n; }

/** Mengembalikan {y,m,d,bulanSaja} atau null. Tanggal teks dibaca format Indonesia (hari/bulan/tahun). */
function apiParseDate_(v, tz) {
  if (v === null || v === undefined || v === '') return null;
  if (Object.prototype.toString.call(v) === '[object Date]') {
    if (isNaN(v.getTime())) return null;
    var p = Utilities.formatDate(v, tz, 'yyyy-MM-dd').split('-');
    return { y: +p[0], m: +p[1], d: +p[2], bulanSaja: false };
  }
  if (typeof v === 'number') {
    if (v > 30000 && v < 80000) {
      var dt = new Date(Math.round((v - 25569) * 86400000));
      return { y: dt.getUTCFullYear(), m: dt.getUTCMonth() + 1, d: dt.getUTCDate(), bulanSaja: false };
    }
    return null;
  }
  var s = String(v).trim();
  var m;
  if ((m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/))) {
    return apiCheck_(+m[1], +m[2], +m[3], false);
  }
  if ((m = s.match(/^(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4})$/))) {
    var d = +m[1], mo = +m[2];
    if (mo > 12 && d <= 12) { var t = d; d = mo; mo = t; }
    return apiCheck_(+m[3], mo, d, false);
  }
  if ((m = s.match(/^(\d{1,2})[\/\-.](\d{4})$/))) {
    return apiCheck_(+m[2], +m[1], 1, true);
  }
  if ((m = s.match(/^(\d{4})[\/\-.](\d{1,2})$/))) {
    return apiCheck_(+m[1], +m[2], 1, true);
  }
  return null;
}

function apiCheck_(y, m, d, bulanSaja) {
  if (m < 1 || m > 12 || d < 1 || d > 31 || y < 1990 || y > 2200) return null;
  if (bulanSaja) d = new Date(Date.UTC(y, m, 0)).getUTCDate(); // akhir bulan
  return { y: y, m: m, d: d, bulanSaja: bulanSaja };
}

function apiDayNumber_(t) { return Math.floor(Date.UTC(t.y, t.m - 1, t.d) / 86400000); }

function apiBuild_(values, tz, now) {
  if (!values || values.length < 2) return { ok: true, generated: '', batasRestok: API_BATAS_RESTOK, hampirExpHari: API_HAMPIR_EXP_HARI, count: 0, items: [] };
  var h = values[0];
  var cKode = apiFindCol_(h, 'kode');
  var cNama = apiFindCol_(h, 'nama');
  var cLok = apiFindCol_(h, 'lokasi');
  var cSat = apiFindCol_(h, 'jenis satuan');
  var cStok = apiFindCol_(h, 'stok terkini');
  var cExp = apiFindCol_(h, 'tanggal expired');

  var map = {}, order = [], skipped = 0;
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    var nama = String(row[cNama] === undefined ? '' : row[cNama]).replace(/\s+/g, ' ').trim();
    if (!nama) continue;
    var lok = String(row[cLok] || '').toLowerCase();
    var loc = lok.indexOf('gudang') >= 0 ? 'gudang' : (lok.indexOf('depo') >= 0 ? 'depo' : '');
    if (!loc) { skipped++; continue; }
    var key = nama.toLowerCase();
    var it = map[key];
    if (!it) {
      it = map[key] = { nama: nama, kodeGudang: '', kodeDepo: '', satuanGudang: '', satuanDepo: '', gudang: 0, depo: 0, batch: [] };
      order.push(key);
    }
    var stok = apiNum_(row[cStok]);
    var kode = String(row[cKode] || '').trim();
    var sat = String(row[cSat] || '').trim();
    if (loc === 'gudang') {
      it.gudang += stok;
      if (!it.kodeGudang) it.kodeGudang = kode;
      if (!it.satuanGudang) it.satuanGudang = sat;
    } else {
      it.depo += stok;
      if (!it.kodeDepo) it.kodeDepo = kode;
      if (!it.satuanDepo) it.satuanDepo = sat;
    }
    var dt = apiParseDate_(row[cExp], tz);
    if (dt) it.batch.push({ t: dt, stok: stok });
  }

  var today = Utilities.formatDate(now, tz, 'yyyy-MM-dd').split('-');
  var todayNo = apiDayNumber_({ y: +today[0], m: +today[1], d: +today[2] });
  var items = [];
  for (var i = 0; i < order.length; i++) {
    var x = map[order[i]];
    var total = x.gudang + x.depo;
    // expired yang dipakai: yang paling cepat di antara baris yang masih ada stoknya; kalau tidak ada stok, yang paling cepat
    var pool = x.batch.filter(function (b) { return b.stok > 0; });
    if (!pool.length) pool = x.batch;
    var best = null;
    for (var j = 0; j < pool.length; j++) {
      if (!best || apiDayNumber_(pool[j].t) < apiDayNumber_(best)) best = pool[j].t;
    }
    var exp = '', expText = '-', expHari = null, expStatus = 'none';
    if (best) {
      exp = best.y + '-' + apiPad_(best.m) + '-' + apiPad_(best.d);
      expText = best.bulanSaja ? (API_BULAN[best.m - 1] + ' ' + best.y) : (best.d + ' ' + API_BULAN[best.m - 1] + ' ' + best.y);
      expHari = apiDayNumber_(best) - todayNo;
      expStatus = expHari < 0 ? 'expired' : (expHari <= API_HAMPIR_EXP_HARI ? 'hampir' : 'aman');
    }
    items.push({
      nama: x.nama,
      kodeGudang: x.kodeGudang, kodeDepo: x.kodeDepo,
      satuanGudang: x.satuanGudang, satuanDepo: x.satuanDepo,
      gudang: x.gudang, depo: x.depo, total: total,
      exp: exp, expText: expText, expHari: expHari, expStatus: expStatus,
      restok: total <= 0 ? 'habis' : (total < API_BATAS_RESTOK ? 'menipis' : '')
    });
  }
  items.sort(function (a, b) { return a.nama.toLowerCase() < b.nama.toLowerCase() ? -1 : (a.nama.toLowerCase() > b.nama.toLowerCase() ? 1 : 0); });
  return {
    ok: true,
    generated: Utilities.formatDate(now, tz, 'yyyy-MM-dd HH:mm:ss'),
    batasRestok: API_BATAS_RESTOK,
    hampirExpHari: API_HAMPIR_EXP_HARI,
    skipped: skipped,
    count: items.length,
    items: items
  };
}
