package id.klinik.inventarisobat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab 1 "Beranda"  : web app Apps Script apa adanya (dashboard, input barang masuk/keluar, ekspor, dst).
 * Tab 2 "Cari"     : pencarian cepat -> stok gudang, stok depo, tanggal expired.
 * Tab 3 "Restok"   : obat habis total + obat/instrumen dengan stok total di bawah batas (100).
 * Tab 2 dan 3 membaca langsung spreadsheet lewat endpoint ?api=items di Apps Script yang sama.
 */
public class MainActivity extends Activity {

    private static final int TAB_WEB = 0, TAB_SEARCH = 1, TAB_RESTOCK = 2;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int BG = Color.parseColor("#F1F7FE");
    private static final int PRIMARY = Color.parseColor("#1E88E5");
    private static final int PRIMARY_DARK = Color.parseColor("#1565C0");
    private static final int TEXT = Color.parseColor("#0F2A43");
    private static final int MUTED = Color.parseColor("#5B7083");
    private static final int BORDER = Color.parseColor("#D6E4F5");
    private static final int RED = Color.parseColor("#D32F2F");
    private static final int ORANGE = Color.parseColor("#EF6C00");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // data
    private List<Item> allItems = new ArrayList<>();
    private String generated = "";
    private int batas = 100;
    private String statusNote = "";
    private boolean loading = false;
    private long lastLoad = 0;
    private String restockFilter = "semua";
    private int current = -1;

    // ui umum
    private View[] panels = new View[3];
    private TextView[] navLabels = new TextView[3];
    private View[] navBars = new View[3];
    private LinearLayout bottomNav;
    private TextView printBtn, refreshBtn;

    // web
    private FrameLayout webPanel;
    private WebView webView;
    private ProgressBar webProgress;
    private boolean webError = false;
    private LinearLayout popupLayer;
    private FrameLayout popupContainer;
    private WebView popupView;

    // cari
    private LinearLayout searchPanel;
    private EditText searchBox;
    private TextView searchInfo, searchEmpty;
    private ItemAdapter searchAdapter;

    // restok
    private LinearLayout restockPanel;
    private TextView restockTitle, restockSub, restockEmpty;
    private TextView chipAll, chipHabis, chipMenipis;
    private ItemAdapter restockAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PRIMARY_DARK);
        prefs = getSharedPreferences("cache", MODE_PRIVATE);
        buildUi();
        loadCache();
        webView.loadUrl(Config.EXEC_URL);
        selectTab(TAB_WEB);
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        FrameLayout rootFrame = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(BG);
        rootFrame.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        // bar atas
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(PRIMARY);
        top.setPadding(dp(16), dp(10), dp(10), dp(10));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Inventaris Obat", 18, Color.WHITE, true));
        titles.addView(text("Klinik Lapas Blangpidie", 12, Color.parseColor("#CFE6FF"), false));
        top.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));
        printBtn = actionButton("Cetak");
        printBtn.setOnClickListener(v -> printView(webView, "Inventaris Obat"));
        refreshBtn = actionButton("Segarkan");
        refreshBtn.setOnClickListener(v -> refreshCurrent());
        top.addView(printBtn, actionParams());
        top.addView(refreshBtn, actionParams());
        col.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        // isi
        FrameLayout content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        buildWebPanel();
        buildSearchPanel();
        buildRestockPanel();
        panels[TAB_WEB] = webPanel;
        panels[TAB_SEARCH] = searchPanel;
        panels[TAB_RESTOCK] = restockPanel;
        for (View p : panels) content.addView(p, new FrameLayout.LayoutParams(MATCH, MATCH));

        // navigasi bawah
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        col.addView(divider, new LinearLayout.LayoutParams(MATCH, dp(1)));
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Color.WHITE);
        String[] labels = {"Beranda", "Cari", "Restok"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            View bar = new View(this);
            TextView label = text(labels[i], 14, MUTED, true);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(12), 0, dp(12));
            item.addView(bar, new LinearLayout.LayoutParams(dp(40), dp(3)));
            item.addView(label, new LinearLayout.LayoutParams(MATCH, WRAP));
            item.setOnClickListener(v -> selectTab(idx));
            navLabels[i] = label;
            navBars[i] = bar;
            bottomNav.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        col.addView(bottomNav, new LinearLayout.LayoutParams(MATCH, WRAP));

        // lapisan pop-up (jendela baru dari web app, mis. halaman cetak)
        popupLayer = new LinearLayout(this);
        popupLayer.setOrientation(LinearLayout.VERTICAL);
        popupLayer.setBackgroundColor(BG);
        popupLayer.setClickable(true);
        popupLayer.setVisibility(View.GONE);
        LinearLayout pbar = new LinearLayout(this);
        pbar.setOrientation(LinearLayout.HORIZONTAL);
        pbar.setGravity(Gravity.CENTER_VERTICAL);
        pbar.setBackgroundColor(PRIMARY);
        pbar.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView closeBtn = actionButton("Tutup");
        closeBtn.setOnClickListener(v -> closePopup());
        TextView popupPrint = actionButton("Cetak / simpan PDF");
        popupPrint.setOnClickListener(v -> printView(popupView, "Rekap Inventaris Obat"));
        View spacer = new View(this);
        pbar.addView(closeBtn);
        pbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        pbar.addView(popupPrint);
        popupLayer.addView(pbar, new LinearLayout.LayoutParams(MATCH, WRAP));
        popupContainer = new FrameLayout(this);
        popupLayer.addView(popupContainer, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        rootFrame.addView(popupLayer, new FrameLayout.LayoutParams(MATCH, MATCH));

        setContentView(rootFrame);

        // sembunyikan navigasi bawah saat keyboard terbuka
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            decor.getWindowVisibleDisplayFrame(r);
            int screenH = decor.getRootView().getHeight();
            boolean keyboard = screenH - r.bottom > screenH * 0.15;
            bottomNav.setVisibility(keyboard ? View.GONE : View.VISIBLE);
        });
    }

    private void buildWebPanel() {
        webPanel = new FrameLayout(this);
        webView = newWebView(false);
        webPanel.addView(webView, new FrameLayout.LayoutParams(MATCH, MATCH));
        webProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        webProgress.setMax(100);
        webPanel.addView(webProgress, new FrameLayout.LayoutParams(MATCH, dp(3)));
    }

    private void buildSearchPanel() {
        searchPanel = new LinearLayout(this);
        searchPanel.setOrientation(LinearLayout.VERTICAL);
        searchPanel.setPadding(dp(12), dp(12), dp(12), 0);

        searchBox = new EditText(this);
        searchBox.setHint("Cari nama obat atau kode\u2026");
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchBox.setTextColor(TEXT);
        searchBox.setHintTextColor(MUTED);
        searchBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        searchBox.setBackground(rounded(Color.WHITE, 12, BORDER));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSearch(); }
        });
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            return true;
        });
        searchPanel.addView(searchBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        searchInfo = text("", 12, MUTED, false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(MATCH, WRAP);
        ip.topMargin = dp(8);
        ip.bottomMargin = dp(8);
        searchPanel.addView(searchInfo, ip);

        FrameLayout box = new FrameLayout(this);
        searchAdapter = new ItemAdapter(false);
        ListView list = newList(searchAdapter);
        box.addView(list, new FrameLayout.LayoutParams(MATCH, MATCH));
        searchEmpty = text("", 14, MUTED, false);
        searchEmpty.setGravity(Gravity.CENTER);
        searchEmpty.setPadding(dp(24), dp(48), dp(24), 0);
        searchEmpty.setVisibility(View.GONE);
        box.addView(searchEmpty, new FrameLayout.LayoutParams(MATCH, WRAP));
        searchPanel.addView(box, new LinearLayout.LayoutParams(MATCH, 0, 1f));
    }

    private void buildRestockPanel() {
        restockPanel = new LinearLayout(this);
        restockPanel.setOrientation(LinearLayout.VERTICAL);
        restockPanel.setPadding(dp(12), dp(12), dp(12), 0);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        summary.setBackground(rounded(Color.WHITE, 12, BORDER));
        restockTitle = text("", 20, TEXT, true);
        restockSub = text("", 13, MUTED, false);
        summary.addView(restockTitle);
        summary.addView(restockSub);
        restockPanel.addView(summary, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipAll = chip("semua");
        chipHabis = chip("habis");
        chipMenipis = chip("menipis");
        chips.addView(chipAll, chipParams());
        chips.addView(chipHabis, chipParams());
        chips.addView(chipMenipis, chipParams());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cp.topMargin = dp(10);
        cp.bottomMargin = dp(10);
        restockPanel.addView(chips, cp);

        FrameLayout box = new FrameLayout(this);
        restockAdapter = new ItemAdapter(true);
        ListView list = newList(restockAdapter);
        box.addView(list, new FrameLayout.LayoutParams(MATCH, MATCH));
        restockEmpty = text("", 14, MUTED, false);
        restockEmpty.setGravity(Gravity.CENTER);
        restockEmpty.setPadding(dp(24), dp(48), dp(24), 0);
        restockEmpty.setVisibility(View.GONE);
        box.addView(restockEmpty, new FrameLayout.LayoutParams(MATCH, WRAP));
        restockPanel.addView(box, new LinearLayout.LayoutParams(MATCH, 0, 1f));
    }

    private ListView newList(ItemAdapter adapter) {
        ListView l = new ListView(this);
        l.setDivider(new ColorDrawable(Color.TRANSPARENT));
        l.setDividerHeight(dp(8));
        l.setSelector(new ColorDrawable(Color.TRANSPARENT));
        l.setClipToPadding(false);
        l.setPadding(0, 0, 0, dp(12));
        l.setVerticalScrollBarEnabled(false);
        l.setAdapter(adapter);
        return l;
    }

    private TextView chip(final String filter) {
        TextView t = text("", 13, MUTED, true);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setOnClickListener(v -> {
            restockFilter = filter;
            updateRestock();
        });
        return t;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
        p.rightMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
        p.leftMargin = dp(8);
        return p;
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private WebView newWebView(final boolean popup) {
        final WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                Uri u = r.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")) return false;
                if ((scheme.equals("http") || scheme.equals("https")) && isGoogleHost(u.getHost())) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                if (!popup) webProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (!popup) webProgress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (!popup && r.isForMainFrame()) showWebError(v);
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (popup) return;
                webProgress.setProgress(p);
                webProgress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView pv = newWebView(true);
                openPopup(pv);
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(pv);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView w) {
                if (popup) closePopup();
            }
        });

        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "Gunakan tombol Cetak untuk menyimpan sebagai PDF.", Toast.LENGTH_LONG).show();
            } else {
                openExternal(Uri.parse(url));
            }
        });
        return wv;
    }

    private void showWebError(WebView v) {
        webError = true;
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;padding:32px;color:#0F2A43;background:#F1F7FE'>"
                + "<h3>Tidak dapat terhubung</h3>"
                + "<p>Periksa koneksi internet, lalu ketuk <b>Segarkan</b>.</p></body></html>";
        v.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private static boolean isGoogleHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return hostIs(h, "google.com") || hostIs(h, "googleusercontent.com")
                || hostIs(h, "gstatic.com") || hostIs(h, "googleapis.com");
    }

    private static boolean hostIs(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPopup(WebView pv) {
        closePopup();
        popupView = pv;
        popupContainer.removeAllViews();
        popupContainer.addView(pv, new FrameLayout.LayoutParams(MATCH, MATCH));
        popupLayer.setVisibility(View.VISIBLE);
    }

    private void closePopup() {
        final WebView old = popupView;
        popupView = null;
        if (old != null) {
            popupContainer.removeView(old);
            ui.post(old::destroy);
        }
        popupLayer.setVisibility(View.GONE);
    }

    private void printView(WebView wv, String name) {
        if (wv == null) return;
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null) return;
        PrintDocumentAdapter adapter = wv.createPrintDocumentAdapter(name);
        pm.print(name, adapter, new PrintAttributes.Builder().build());
    }

    // ------------------------------------------------------------------ tab & refresh

    private void selectTab(int tab) {
        current = tab;
        for (int i = 0; i < 3; i++) {
            panels[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            navLabels[i].setTextColor(i == tab ? PRIMARY : MUTED);
            navBars[i].setBackgroundColor(i == tab ? PRIMARY : Color.TRANSPARENT);
        }
        printBtn.setVisibility(tab == TAB_WEB ? View.VISIBLE : View.GONE);
        if (tab != TAB_SEARCH) hideKeyboard();
        if (tab != TAB_WEB && isStale()) loadData();
    }

    private void refreshCurrent() {
        if (current == TAB_WEB) {
            if (webError) {
                webError = false;
                webView.loadUrl(Config.EXEC_URL);
            } else {
                webView.reload();
            }
        }
        loadData();
    }

    private boolean isStale() {
        return System.currentTimeMillis() - lastLoad > Config.STALE_MS;
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (current != -1 && isStale()) loadData();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        closePopup();
        webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (popupView != null) {
            closePopup();
        } else if (current == TAB_WEB && webView.canGoBack()) {
            webView.goBack();
        } else if (current != TAB_WEB) {
            selectTab(TAB_WEB);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ data

    private void loadCache() {
        String json = prefs.getString("json", null);
        if (json == null) return;
        try {
            ApiClient.Result r = ApiClient.parse(json);
            allItems = r.items;
            generated = r.generated;
            batas = r.batas;
            renderAll();
        } catch (Exception ignored) {
            // cache rusak: abaikan
        }
    }

    private void loadData() {
        if (loading) return;
        loading = true;
        refreshBtn.setText("Memuat\u2026");
        renderAll();
        io.execute(() -> {
            ApiClient.Result res = null;
            String err = null;
            try {
                res = ApiClient.fetch(Config.EXEC_URL);
            } catch (ApiClient.ApiException e) {
                err = e.getMessage();
            } catch (Exception e) {
                err = "Tidak dapat terhubung ke server. Periksa koneksi internet.";
            }
            final ApiClient.Result fRes = res;
            final String fErr = err;
            ui.post(() -> onData(fRes, fErr));
        });
    }

    private void onData(ApiClient.Result res, String err) {
        loading = false;
        refreshBtn.setText("Segarkan");
        if (res != null) {
            allItems = res.items;
            generated = res.generated;
            batas = res.batas;
            statusNote = "";
            lastLoad = System.currentTimeMillis();
            prefs.edit().putString("json", res.raw).apply();
        } else {
            statusNote = err + (allItems.isEmpty() ? "" : " Menampilkan data terakhir.");
        }
        renderAll();
    }

    private void renderAll() {
        updateSearch();
        updateRestock();
        int n = Logic.count(allItems, "semua");
        navLabels[TAB_RESTOCK].setText(n > 0 ? "Restok (" + n + ")" : "Restok");
    }

    private void updateSearch() {
        String q = searchBox.getText().toString();
        List<Item> res = Logic.search(allItems, q);
        searchAdapter.set(res);
        StringBuilder sb = new StringBuilder();
        sb.append(Logic.num(res.size())).append(q.trim().isEmpty() ? " item" : " hasil");
        if (!generated.isEmpty()) sb.append(" \u2022 diperbarui ").append(Logic.formatGenerated(generated));
        if (!statusNote.isEmpty() && !allItems.isEmpty()) sb.append("\n").append(statusNote);
        searchInfo.setText(sb.toString());
        String empty = null;
        if (res.isEmpty()) {
            if (allItems.isEmpty()) {
                empty = loading ? "Memuat data\u2026" : (statusNote.isEmpty() ? "Belum ada data." : statusNote);
            } else {
                empty = "Tidak ada obat yang cocok dengan \u201C" + q.trim() + "\u201D.";
            }
        }
        searchEmpty.setText(empty == null ? "" : empty);
        searchEmpty.setVisibility(empty == null ? View.GONE : View.VISIBLE);
    }

    private void updateRestock() {
        int habis = Logic.count(allItems, "habis");
        int menipis = Logic.count(allItems, "menipis");
        int semua = habis + menipis;
        restockTitle.setText(Logic.num(semua) + " item perlu restok");
        String sub = Logic.num(habis) + " habis total \u2022 " + Logic.num(menipis) + " stok di bawah " + Logic.num(batas);
        if (!statusNote.isEmpty() && !allItems.isEmpty()) sub += "\n" + statusNote;
        restockSub.setText(sub);
        styleChip(chipAll, "Semua (" + semua + ")", restockFilter.equals("semua"));
        styleChip(chipHabis, "Habis (" + habis + ")", restockFilter.equals("habis"));
        styleChip(chipMenipis, "Menipis (" + menipis + ")", restockFilter.equals("menipis"));
        List<Item> list = Logic.restock(allItems, restockFilter);
        restockAdapter.set(list);
        String empty = null;
        if (list.isEmpty()) {
            if (allItems.isEmpty()) {
                empty = loading ? "Memuat data\u2026" : (statusNote.isEmpty() ? "Belum ada data." : statusNote);
            } else {
                empty = "Tidak ada item pada kategori ini.";
            }
        }
        restockEmpty.setText(empty == null ? "" : empty);
        restockEmpty.setVisibility(empty == null ? View.GONE : View.VISIBLE);
    }

    private void styleChip(TextView t, String label, boolean active) {
        t.setText(label);
        t.setTextColor(active ? Color.WHITE : MUTED);
        t.setBackground(active ? rounded(PRIMARY, 20, 0) : rounded(Color.WHITE, 20, BORDER));
    }

    // ------------------------------------------------------------------ daftar

    private final class Holder {
        TextView name, kode, badge, v1, v2, v3, s3, note;
    }

    private final class ItemAdapter extends BaseAdapter {
        private final boolean restock;
        private List<Item> data = new ArrayList<>();

        ItemAdapter(boolean restock) {
            this.restock = restock;
        }

        void set(List<Item> d) {
            data = d;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            View v = convertView;
            if (v == null) {
                h = new Holder();
                v = buildRow(restock, h);
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            bindRow(h, data.get(position), restock);
            return v;
        }
    }

    private View buildRow(boolean restock, Holder h) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Color.WHITE, 12, BORDER));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        h.name = text("", 16, TEXT, true);
        head.addView(h.name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        h.badge = text("", 11, Color.WHITE, true);
        h.badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        head.addView(h.badge, new LinearLayout.LayoutParams(WRAP, WRAP));
        card.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        h.kode = text("", 11, MUTED, false);
        card.addView(h.kode, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        h.v1 = text("", 15, TEXT, true);
        h.v2 = text("", 15, TEXT, true);
        h.v3 = text("", 15, TEXT, true);
        h.s3 = text("", 11, MUTED, false);
        cols.addView(column("Gudang", h.v1, null), new LinearLayout.LayoutParams(0, WRAP, 1f));
        cols.addView(column("Depo Obat", h.v2, null), new LinearLayout.LayoutParams(0, WRAP, 1f));
        cols.addView(column(restock ? "Total" : "Expired", h.v3, h.s3), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cp.topMargin = dp(10);
        card.addView(cols, cp);

        h.note = text("", 12, MUTED, false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(MATCH, WRAP);
        np.topMargin = dp(8);
        card.addView(h.note, np);
        return card;
    }

    private LinearLayout column(String label, TextView value, TextView sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(text(label, 11, MUTED, false));
        c.addView(value);
        if (sub != null) c.addView(sub);
        return c;
    }

    private void bindRow(Holder h, Item it, boolean restock) {
        h.name.setText(it.nama);
        h.kode.setText(kodeLine(it));
        h.v1.setText(stock(it.gudang, it.satuanGudang));
        h.v1.setTextColor(it.gudang <= 0 ? RED : TEXT);
        h.v2.setText(stock(it.depo, it.satuanDepo));
        h.v2.setTextColor(it.depo <= 0 ? RED : TEXT);
        if (restock) {
            boolean habis = it.restok.equals("habis");
            h.badge.setText(habis ? "HABIS" : "MENIPIS");
            h.badge.setBackground(rounded(habis ? RED : ORANGE, 6, 0));
            h.badge.setVisibility(View.VISIBLE);
            h.v3.setText(Logic.num(it.total));
            h.v3.setTextColor(habis ? RED : ORANGE);
            h.s3.setVisibility(View.GONE);
            if (it.expStatus.equals("none")) {
                h.note.setVisibility(View.GONE);
            } else {
                h.note.setText("Expired: " + it.expText + expSuffix(it));
                h.note.setVisibility(View.VISIBLE);
            }
        } else {
            h.badge.setVisibility(View.GONE);
            h.note.setVisibility(View.GONE);
            h.v3.setText(it.expText);
            h.v3.setTextColor(expColor(it.expStatus));
            String suffix = expSuffix(it);
            if (suffix.isEmpty()) {
                h.s3.setVisibility(View.GONE);
            } else {
                h.s3.setText(suffix.trim());
                h.s3.setTextColor(expColor(it.expStatus));
                h.s3.setVisibility(View.VISIBLE);
            }
        }
    }

    private String expSuffix(Item it) {
        if (it.expStatus.equals("expired")) return " \u2022 sudah expired";
        if (it.expStatus.equals("hampir") && it.expHari != null) return " \u2022 sisa " + it.expHari + " hari";
        return "";
    }

    private int expColor(String status) {
        if (status.equals("expired")) return RED;
        if (status.equals("hampir")) return ORANGE;
        return TEXT;
    }

    private String kodeLine(Item it) {
        StringBuilder sb = new StringBuilder();
        if (!it.kodeGudang.isEmpty()) sb.append(it.kodeGudang);
        if (!it.kodeDepo.isEmpty()) {
            if (sb.length() > 0) sb.append(" \u00B7 ");
            sb.append(it.kodeDepo);
        }
        return sb.toString();
    }

    private String stock(double v, String unit) {
        return Logic.num(v) + (unit == null || unit.isEmpty() ? "" : " " + unit);
    }

    // ------------------------------------------------------------------ helper tampilan

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView actionButton(String label) {
        TextView t = text(label, 14, Color.WHITE, true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(rounded(PRIMARY_DARK, 8, 0));
        return t;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (imm != null && f != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }
}
