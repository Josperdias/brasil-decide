package br.com.centraleleicoes.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Central Eleições 2026 — versão Android NATIVA (sem WebView).
 * Interface nativa, abre offline com o último snapshot, polling pausa em background.
 */
public class MainActivity extends Activity {
    static final String VERSION = "2026.10.05-nativo";
    private static final int BG = 0xFF040913, PANEL = 0xFF0A1728, LINE = 0xFF1D3858, TEXT = 0xFFF7FBFF, MUTED = 0xFF93A9C2,
            CYAN = 0xFF50D5FF, MINT = 0xFF64F5CB, AMBER = 0xFFFFC966;

    private static final String[][] UFS = {
            {"AC", "Acre"}, {"AL", "Alagoas"}, {"AP", "Amapá"}, {"AM", "Amazonas"}, {"BA", "Bahia"}, {"CE", "Ceará"},
            {"DF", "Distrito Federal"}, {"ES", "Espírito Santo"}, {"GO", "Goiás"}, {"MA", "Maranhão"}, {"MT", "Mato Grosso"},
            {"MS", "Mato Grosso do Sul"}, {"MG", "Minas Gerais"}, {"PA", "Pará"}, {"PB", "Paraíba"}, {"PR", "Paraná"},
            {"PE", "Pernambuco"}, {"PI", "Piauí"}, {"RJ", "Rio de Janeiro"}, {"RN", "Rio Grande do Norte"},
            {"RS", "Rio Grande do Sul"}, {"RO", "Rondônia"}, {"RR", "Roraima"}, {"SC", "Santa Catarina"},
            {"SP", "São Paulo"}, {"SE", "Sergipe"}, {"TO", "Tocantins"}};
    // chave, título, cargo, usa eleição federal?
    private static final Object[][] DF_SRC = {
            {"pres", "Presidente da República", 1, true}, {"gov", "Governador do Distrito Federal", 3, false},
            {"sen", "Senador pelo Distrito Federal", 5, false}, {"depf", "Deputado Federal — DF", 6, false},
            {"depd", "Deputado Distrital — DF", 8, false}};

    static final class Snap {
        Model.Result pres;
        final Map<String, Model.Result> states = new HashMap<>(), gov = new HashMap<>(), df = new HashMap<>();
        final Map<String, Boolean> govAbsent = new HashMap<>(), dfAbsent = new HashMap<>();
        long lastChange, at;
        String presSig = "";
    }

    private final Snap[] snaps = {new Snap(), new Snap()};
    private final Map<String, Long> absentAt = new HashMap<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor(), net = Executors.newFixedThreadPool(6);
    private SharedPreferences prefs;
    private String[][] codes = {Tse.DEFAULT_CODES[0].clone(), Tse.DEFAULT_CODES[1].clone()};
    private boolean codesFromConfig;
    private int turn = 1;
    private String tab = "mapa", sel = "DF";
    private boolean govMode, busy, resumed, waiting;
    private long delayMs = 10000, lastPoll;
    private String lastError = "nenhum", statusText = "Abrindo…", statusSub = "";

    private FrameLayout root;
    private TextView tvStatus, tvSub, tvCaption;
    private LinearLayout tabsRow, content, turnRow;
    private ScrollView scroll;
    private BrazilMapView mapView;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            ui.postDelayed(this, delayMs);
        }
    };

    // ---------------------------------------------------------------- ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("central", MODE_PRIVATE);
        turn = prefs.getInt("turn", 1) == 2 ? 2 : 1;
        loadCache();
        buildUi();
        setupEdgeToEdge();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        ui.removeCallbacks(poller);
        ui.post(poller);
    }

    @Override protected void onPause() {
        resumed = false;
        ui.removeCallbacks(poller);
        super.onPause();
    }

    @Override protected void onDestroy() {
        bg.shutdownNow();
        net.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI base
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private LinearLayout card() {
        LinearLayout c = col();
        c.setBackground(rounded(PANEL, 16, LINE));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView pill(String label, boolean active, View.OnClickListener l) {
        TextView t = tv(label, 13, active ? 0xFF04111D : MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(rounded(active ? MINT : 0xFF09182A, 14, active ? 0 : LINE));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(0, 0, dp(6), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private View bar(double pct, int color, int heightDp) {
        LinearLayout l = row();
        l.setBackground(rounded(0xFF020914, 8, 0));
        float p = (float) Math.max(0, Math.min(100, pct));
        View fill = new View(this);
        fill.setBackground(rounded(color, 8, 0));
        l.addView(fill, new LinearLayout.LayoutParams(0, dp(heightDp), Math.max(0.0001f, p)));
        View rest = new View(this);
        l.addView(rest, new LinearLayout.LayoutParams(0, dp(heightDp), Math.max(0.0001f, 100f - p)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(4));
        l.setLayoutParams(lp);
        return l;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout page = col();
        page.setPadding(dp(14), dp(8), dp(14), 0);

        page.addView(tv("ELEIÇÕES 2026 • CENTRAL NATIVA", 11, MINT, true));
        page.addView(tv("Brasil decide", 26, TEXT, true));
        tvStatus = tv("", 14, TEXT, true);
        tvSub = tv("", 11, MUTED, false);
        page.addView(tvStatus);
        page.addView(tvSub);

        turnRow = row();
        turnRow.setPadding(0, dp(8), 0, dp(4));
        page.addView(turnRow);
        tvCaption = tv("", 11, MUTED, false);
        page.addView(tvCaption);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabsRow = row();
        tabsRow.setPadding(0, dp(8), 0, dp(8));
        hs.addView(tabsRow);
        page.addView(hs);

        scroll = new ScrollView(this);
        content = col();
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        mapView = new BrazilMapView(this);
        mapView.setOnUf(uf -> { sel = uf; render(); });
    }

    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
    }

    // ---------------------------------------------------------------- render
    private Snap snap() { return snaps[turn - 1]; }
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));
    private static String n(long v) { return INT.format(v); }
    private static String pc(double v) { return String.format(new Locale("pt", "BR"), "%.2f%%", v); }
    private static String ufName(String uf) { for (String[] u : UFS) if (u[0].equals(uf)) return u[1]; return uf; }

    private void render() {
        try {
            int keep = scroll.getScrollY();
            tvStatus.setText(statusText);
            tvSub.setText(statusSub);
            tvCaption.setText(turn + "º turno • TSE " + codes[turn - 1][0] + " (federal) / " + codes[turn - 1][1] + " (estadual)");
            turnRow.removeAllViews();
            turnRow.addView(pill("1º turno", turn == 1, v -> setTurn(1)));
            turnRow.addView(pill("2º turno", turn == 2, v -> setTurn(2)));
            turnRow.addView(pill("↻", false, v -> { delayMs = 10000; refresh(); }));
            tabsRow.removeAllViews();
            String[][] tabs = {{"mapa", "🗺️ Mapa"}, {"brasil", "🇧🇷 Brasil"}, {"ufs", "27 UFs"}, {"df", "🏛️ DF"}, {"diag", "🛠️ Diagnóstico"}};
            for (String[] t : tabs) tabsRow.addView(pill(t[1], tab.equals(t[0]), v -> { tab = t[0]; render(); }));
            if (mapView.getParent() != null) ((ViewGroup) mapView.getParent()).removeView(mapView);
            content.removeAllViews();
            switch (tab) {
                case "mapa": renderMap(); break;
                case "brasil": renderBrasil(); break;
                case "ufs": renderUfs(); break;
                case "df": renderDf(); break;
                default: renderDiag();
            }
            final int k = keep;
            scroll.post(() -> scroll.scrollTo(0, k));
        } catch (Throwable t) {
            lastError = "render: " + t;
        }
    }

    private void addCand(LinearLayout parent, Model.Cand c, int idx) {
        int col = Model.color(c.nome);
        LinearLayout r = row();
        View dot = new View(this);
        dot.setBackground(rounded(col, 6, 0));
        r.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        LinearLayout mid = col();
        mid.setPadding(dp(8), 0, dp(8), 0);
        mid.addView(tv((idx + 1) + "º  " + c.nome, 14, TEXT, true));
        mid.addView(tv(c.partido + (c.numero.isEmpty() ? "" : " · nº " + c.numero) + (c.sit.isEmpty() ? "" : " · " + c.sit), 11, MUTED, false));
        r.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout right = col();
        right.setGravity(Gravity.END);
        TextView p = tv(pc(c.pct), 15, TEXT, true);
        p.setGravity(Gravity.END);
        TextView v = tv(n(c.votos) + " votos", 11, MUTED, false);
        v.setGravity(Gravity.END);
        right.addView(p);
        right.addView(v);
        r.addView(right);
        r.setPadding(0, dp(6), 0, 0);
        parent.addView(r);
        parent.addView(bar(c.pct, col, 6));
    }

    private LinearLayout resultCard(String title, String sub, Model.Result r, int top) {
        LinearLayout c = card();
        c.addView(tv(title, 16, TEXT, true));
        c.addView(tv(sub, 11, MUTED, false));
        if (r == null) { c.addView(tv("Carregando dados oficiais…", 12, MUTED, false)); return c; }
        TextView pr = tv("Apuração " + pc(r.progress) + "  •  " + n(r.sections) + "/" + n(r.sectionsTotal) + " seções" + (r.fin ? " • final" : ""), 12, TEXT, true);
        pr.setPadding(0, dp(8), 0, 0);
        c.addView(pr);
        c.addView(bar(r.progress, CYAN, 10));
        for (int i = 0; i < Math.min(top, r.cands.size()); i++) addCand(c, r.cands.get(i), i);
        if (r.cands.size() >= 2) {
            long gap = r.cands.get(0).votos - r.cands.get(1).votos;
            double pp = r.cands.get(0).pct - r.cands.get(1).pct;
            c.addView(tv("Diferença 1º × 2º: " + n(gap) + " votos (" + String.format(new Locale("pt", "BR"), "%.2f", pp) + " p.p.)", 12, MINT, true));
        }
        c.addView(tv("Arquivo TSE: " + (r.date + " " + r.time).trim() + " • válidos " + n(r.valid), 10, MUTED, false));
        return c;
    }

    private void renderMap() {
        LinearLayout modes = row();
        modes.addView(pill("Presidente", !govMode, v -> { govMode = false; render(); }));
        modes.addView(pill("Governador", govMode, v -> { govMode = true; refresh(); render(); }));
        content.addView(modes);
        Map<String, Model.Result> data = govMode ? snap().gov : snap().states;
        Map<String, Integer> fills = new HashMap<>();
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        int loaded = 0;
        for (String[] u : UFS) {
            Model.Result r = data.get(u[0]);
            Model.Cand l = r == null ? null : r.lead();
            if (l != null) {
                loaded++;
                fills.put(u[0], Model.color(l.nome));
                counts.merge(l.nome, 1, Integer::sum);
            }
        }
        mapView.setFills(fills);
        mapView.setSelected(sel);
        LinearLayout c = card();
        c.addView(tv((govMode ? "Governador" : "Presidente") + " — liderança por UF", 15, TEXT, true));
        c.addView(tv(turn + "º turno • " + loaded + "/27 UFs coloridas", 11, MUTED, false));
        c.addView(mapView, new LinearLayout.LayoutParams(-1, -2));
        List<Map.Entry<String, Integer>> es = new ArrayList<>(counts.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : es) {
            LinearLayout r = row();
            View dot = new View(this);
            dot.setBackground(rounded(Model.color(e.getKey()), 6, 0));
            r.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
            TextView t = tv("  " + e.getKey() + " — " + e.getValue() + " UF" + (e.getValue() > 1 ? "s" : ""), 12, TEXT, false);
            r.addView(t);
            r.setPadding(0, dp(3), 0, dp(3));
            c.addView(r);
        }
        if (loaded == 0) c.addView(tv(govMode && snap().govAbsent.size() > 0 ? "Sem disputa neste cargo/UF." : "Aguardando dados do TSE…", 12, MUTED, false));
        content.addView(c);
        Model.Result d = data.get(sel);
        String sub = (govMode ? "Governador" : "Presidente") + " • " + turn + "º turno";
        if (d == null && govMode && Boolean.TRUE.equals(snap().govAbsent.get(sel))) {
            LinearLayout e = card();
            e.addView(tv(ufName(sel) + " (" + sel + ")", 16, TEXT, true));
            e.addView(tv("Sem disputa neste cargo/UF" + (turn == 2 ? " no 2º turno." : "."), 12, MUTED, false));
            content.addView(e);
        } else content.addView(resultCard(ufName(sel) + " (" + sel + ")", sub, d, 3));
    }

    private void renderBrasil() {
        content.addView(resultCard("Presidente da República", "Brasil • " + turn + "º turno", snap().pres, turn == 2 ? 2 : 3));
        if (snap().pres == null && waiting) {
            LinearLayout w = card();
            w.addView(tv("O TSE ainda não publicou o resultado presidencial do " + turn + "º turno. Nova consulta automática em ~60 s.", 12, AMBER, false));
            content.addView(w);
        }
    }

    private void renderUfs() {
        for (String[] u : UFS) {
            Model.Result r = snap().states.get(u[0]);
            Model.Cand l = r == null ? null : r.lead();
            LinearLayout c = card();
            LinearLayout h = row();
            View dot = new View(this);
            dot.setBackground(rounded(l == null ? 0xFF17304F : Model.color(l.nome), 6, 0));
            h.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
            TextView t = tv("  " + u[0] + " · " + u[1], 14, TEXT, true);
            h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            h.addView(tv(r == null ? "—" : pc(r.progress), 12, MINT, true));
            c.addView(h);
            c.addView(tv(l == null ? "Carregando…" : l.nome + " • " + pc(l.pct), 12, MUTED, false));
            c.addView(bar(r == null ? 0 : r.progress, CYAN, 6));
            c.setOnClickListener(v -> { sel = u[0]; tab = "mapa"; govMode = false; render(); });
            content.addView(c);
        }
    }

    private void renderDf() {
        for (Object[] s : DF_SRC) {
            String key = (String) s[0];
            if (turn == 2 && !key.equals("pres") && !key.equals("gov")) {
                LinearLayout e = card();
                e.addView(tv((String) s[1], 16, TEXT, true));
                e.addView(tv("Sem disputa neste cargo no 2º turno.", 12, MUTED, false));
                content.addView(e);
                continue;
            }
            if (Boolean.TRUE.equals(snap().dfAbsent.get(key)) && snap().df.get(key) == null) {
                LinearLayout e = card();
                e.addView(tv((String) s[1], 16, TEXT, true));
                e.addView(tv("Sem disputa neste cargo/UF.", 12, MUTED, false));
                content.addView(e);
                continue;
            }
            int top = key.equals("pres") ? (turn == 2 ? 2 : 3) : key.equals("gov") ? (turn == 2 ? 2 : 6) : key.equals("sen") ? 8 : key.equals("depf") ? 10 : 14;
            content.addView(resultCard((String) s[1], "Distrito Federal • " + turn + "º turno", snap().df.get(key), top));
        }
    }

    private void renderDiag() {
        LinearLayout c = card();
        c.addView(tv("Diagnóstico", 16, TEXT, true));
        String[][] rows = {
                {"Versão", VERSION},
                {"Rede", networkStatus()},
                {"Endpoint", Tse.BASE + "/" + Tse.CICLO + "/…"},
                {"Turno / códigos", turn + "º • " + codes[turn - 1][0] + " / " + codes[turn - 1][1] + (codesFromConfig ? " (ele-c.json)" : " (padrão)")},
                {"Última consulta", lastPoll == 0 ? "—" : new java.text.SimpleDateFormat("dd/MM HH:mm:ss", new Locale("pt", "BR")).format(new java.util.Date(lastPoll))},
                {"Último dado novo", snap().lastChange == 0 ? "—" : new java.text.SimpleDateFormat("dd/MM HH:mm:ss", new Locale("pt", "BR")).format(new java.util.Date(snap().lastChange))},
                {"Intervalo", (delayMs / 1000) + " s (pausa em segundo plano)"},
                {"Último erro", lastError},
                {"Cache", snap().at == 0 ? "vazio" : "snapshot de " + new java.text.SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(new java.util.Date(snap().at))}};
        for (String[] r : rows) {
            c.addView(tv(r[0], 11, MUTED, false));
            TextView v = tv(r[1], 13, TEXT, false);
            v.setPadding(0, 0, 0, dp(6));
            c.addView(v);
        }
        content.addView(c);
    }

    private String networkStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network nw = cm.getActiveNetwork();
            NetworkCapabilities nc = nw == null ? null : cm.getNetworkCapabilities(nw);
            if (nc == null) return "sem conexão";
            String t = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "celular" : "outra";
            return "online (" + t + ")" + (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "" : " • sem validação");
        } catch (Throwable t) { return "desconhecido"; }
    }

    // ---------------------------------------------------------------- dados
    private void setTurn(int t) {
        if (t == turn) return;
        turn = t;
        prefs.edit().putInt("turn", t).apply();
        delayMs = 10000;
        render();
        refresh();
    }

    private Callable<Object[]> task(final String key, final String url, final long ttl) {
        return () -> {
            Long at;
            synchronized (absentAt) { at = absentAt.get(url); }
            if (at != null && System.currentTimeMillis() - at < ttl) { Tse.Fetch f = new Tse.Fetch(); f.absent = true; return new Object[]{key, f}; }
            Tse.Fetch f = Tse.fetch(url);
            if (f.absent) synchronized (absentAt) { absentAt.put(url, System.currentTimeMillis()); }
            return new Object[]{key, f};
        };
    }

    private List<Object[]> runAll(List<Callable<Object[]>> tasks) {
        List<Object[]> out = new ArrayList<>();
        try {
            for (Future<Object[]> f : net.invokeAll(tasks)) {
                try { out.add(f.get()); } catch (Throwable t) { /* tarefa falhou: ignorada */ }
            }
        } catch (Throwable t) { lastError = "rede: " + t; }
        return out;
    }

    private void refresh() {
        if (busy) return;
        busy = true;
        final int t = turn;
        final boolean wantGov = govMode;
        ui.post(() -> { statusText = "Consultando o TSE…"; render(); });
        bg.execute(() -> {
            final Map<String, Model.Result> sOk = new HashMap<>(), gOk = new HashMap<>(), dOk = new HashMap<>();
            final Map<String, Boolean> gAbs = new HashMap<>(), dAbs = new HashMap<>();
            final Model.Result[] pres = {null};
            final boolean[] wait = {false};
            int failures = 0;
            boolean cfg = false;
            try {
                if (!codesFromConfig) {
                    String[][] found = Tse.discoverCodes();
                    if (found != null) { codes = found; cfg = true; }
                }
                String fed = codes[t - 1][0], est = codes[t - 1][1];
                List<Object[]> br = runAll(java.util.Collections.singletonList(task("br", Tse.url(fed, "br", 1), 60000)));
                Tse.Fetch bf = br.isEmpty() ? null : (Tse.Fetch) br.get(0)[1];
                if (bf == null || bf.error != null) { failures++; if (bf != null) lastError = Tse.url(fed, "br", 1) + " → " + bf.error; }
                else if (bf.absent) wait[0] = true;
                else pres[0] = bf.result;
                if (!wait[0]) {
                    List<Callable<Object[]>> ts = new ArrayList<>();
                    for (String[] u : UFS) ts.add(task("s:" + u[0], Tse.url(fed, u[0].toLowerCase(Locale.ROOT), 1), 300000));
                    if (wantGov) for (String[] u : UFS) ts.add(task("g:" + u[0], Tse.url(est, u[0].toLowerCase(Locale.ROOT), 3), 300000));
                    for (Object[] s : DF_SRC) {
                        String k = (String) s[0];
                        if (t == 2 && !k.equals("pres") && !k.equals("gov")) continue;
                        ts.add(task("d:" + k, Tse.url((Boolean) s[3] ? fed : est, "df", (Integer) s[2]), 300000));
                    }
                    for (Object[] o : runAll(ts)) {
                        String k = (String) o[0];
                        Tse.Fetch f = (Tse.Fetch) o[1];
                        String id = k.substring(2);
                        if (f.error != null) { failures++; lastError = k + " → " + f.error; continue; }
                        if (k.startsWith("s:") && f.result != null) sOk.put(id, f.result);
                        else if (k.startsWith("g:")) { if (f.result != null) gOk.put(id, f.result); else if (f.absent) gAbs.put(id, true); }
                        else if (k.startsWith("d:")) { if (f.result != null) dOk.put(id, f.result); else if (f.absent) dAbs.put(id, true); }
                    }
                }
            } catch (Throwable th) { failures++; lastError = String.valueOf(th); }
            final int fails = failures;
            final boolean cfgDone = cfg;
            ui.post(() -> {
                try {
                    if (cfgDone) codesFromConfig = true;
                    commit(t, pres[0], sOk, gOk, dOk, gAbs, dAbs, wait[0], fails);
                } catch (Throwable th) { lastError = "commit: " + th; }
                busy = false;
                render();
            });
        });
    }

    private void commit(int t, Model.Result pres, Map<String, Model.Result> s, Map<String, Model.Result> g, Map<String, Model.Result> d,
                        Map<String, Boolean> gAbs, Map<String, Boolean> dAbs, boolean wait, int failures) {
        Snap sn = snaps[t - 1];
        String oldSig = sn.presSig;
        if (pres != null) { sn.pres = pres; sn.presSig = pres.sig; }
        sn.states.putAll(s);
        sn.gov.putAll(g);
        sn.df.putAll(d);
        sn.govAbsent.putAll(gAbs);
        sn.dfAbsent.putAll(dAbs);
        lastPoll = System.currentTimeMillis();
        boolean changed = pres != null && !pres.sig.equals(oldSig);
        if (changed || sn.lastChange == 0 && pres != null) sn.lastChange = lastPoll;
        waiting = wait;
        if (t != turn) return;
        if (wait) {
            delayMs = 60000;
            statusText = turn + "º turno • aguardando arquivos do TSE";
            statusSub = "o cargo ainda não tem arquivo publicado";
        } else {
            delayMs = 10000;
            boolean offline = pres == null && s.isEmpty() && failures > 0;
            statusText = offline ? "Sem conexão • último resultado salvo" : failures > 0 ? "Online • dados parciais" : changed ? "Ao vivo • dados novos" : "Ao vivo • TSE sem mudança";
            statusSub = failures > 0 ? failures + " arquivos serão tentados de novo" : "dados oficiais • sem projeção";
        }
        if (failures == 0 || pres != null || !s.isEmpty()) saveCache(t);
    }

    // ---------------------------------------------------------------- cache do último snapshot válido
    private void saveCache(int t) {
        try {
            Snap sn = snaps[t - 1];
            JSONObject o = new JSONObject();
            if (sn.pres != null) o.put("pres", sn.pres.toJson());
            o.put("states", mapJson(sn.states)).put("gov", mapJson(sn.gov)).put("df", mapJson(sn.df))
                    .put("lc", sn.lastChange).put("sig", sn.presSig).put("at", System.currentTimeMillis());
            sn.at = System.currentTimeMillis();
            prefs.edit().putString("snap" + t, o.toString()).apply();
        } catch (Throwable th) { lastError = "cache: " + th; }
    }

    private static JSONObject mapJson(Map<String, Model.Result> m) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Model.Result> e : m.entrySet()) o.put(e.getKey(), e.getValue().toJson());
        return o;
    }

    private static void readMap(JSONObject src, Map<String, Model.Result> dst) {
        if (src == null) return;
        java.util.Iterator<String> it = src.keys();
        while (it.hasNext()) { String k = it.next(); JSONObject r = src.optJSONObject(k); if (r != null) dst.put(k, Model.Result.fromJson(r)); }
    }

    private void loadCache() {
        for (int t = 1; t <= 2; t++) {
            try {
                String s = prefs.getString("snap" + t, null);
                if (s == null) continue;
                JSONObject o = new JSONObject(s);
                Snap sn = snaps[t - 1];
                JSONObject p = o.optJSONObject("pres");
                if (p != null) sn.pres = Model.Result.fromJson(p);
                readMap(o.optJSONObject("states"), sn.states);
                readMap(o.optJSONObject("gov"), sn.gov);
                readMap(o.optJSONObject("df"), sn.df);
                sn.lastChange = o.optLong("lc");
                sn.presSig = o.optString("sig");
                sn.at = o.optLong("at");
            } catch (Throwable th) { lastError = "cache ilegível: " + th; }
        }
        if (snaps[turn - 1].at != 0) { statusText = "Último resultado salvo"; statusSub = "atualizando…"; }
    }
}
