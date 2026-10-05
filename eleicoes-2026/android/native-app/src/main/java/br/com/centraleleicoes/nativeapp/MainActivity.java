package br.com.centraleleicoes.nativeapp;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
 * Brasil Decide — versão Android NATIVA (sem WebView).
 * Abre offline com o último snapshot, polling pausa em background, falhas de rede nunca derrubam a Activity.
 */
public class MainActivity extends Activity {
    static final String VERSION = "2026.10.05-nativo";
    /** Link direto do APK (release "app-latest", atualizada pelo CI) e página de divulgação. */
    static final String DL_URL = "https://github.com/Josperdias/concursos-df/releases/download/app-latest/CentralEleicoes2026.apk";
    static final String PAGE_URL = "https://github.com/Josperdias/concursos-df/releases/latest";
    private static final String RELEASE_API = "https://api.github.com/repos/Josperdias/concursos-df/releases/tags/app-latest";
    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);

    private static final String[][] UFS = {
            {"AC", "Acre", "Norte"}, {"AL", "Alagoas", "Nordeste"}, {"AP", "Amapá", "Norte"}, {"AM", "Amazonas", "Norte"},
            {"BA", "Bahia", "Nordeste"}, {"CE", "Ceará", "Nordeste"}, {"DF", "Distrito Federal", "Centro-Oeste"},
            {"ES", "Espírito Santo", "Sudeste"}, {"GO", "Goiás", "Centro-Oeste"}, {"MA", "Maranhão", "Nordeste"},
            {"MT", "Mato Grosso", "Centro-Oeste"}, {"MS", "Mato Grosso do Sul", "Centro-Oeste"}, {"MG", "Minas Gerais", "Sudeste"},
            {"PA", "Pará", "Norte"}, {"PB", "Paraíba", "Nordeste"}, {"PR", "Paraná", "Sul"}, {"PE", "Pernambuco", "Nordeste"},
            {"PI", "Piauí", "Nordeste"}, {"RJ", "Rio de Janeiro", "Sudeste"}, {"RN", "Rio Grande do Norte", "Nordeste"},
            {"RS", "Rio Grande do Sul", "Sul"}, {"RO", "Rondônia", "Norte"}, {"RR", "Roraima", "Norte"}, {"SC", "Santa Catarina", "Sul"},
            {"SP", "São Paulo", "Sudeste"}, {"SE", "Sergipe", "Nordeste"}, {"TO", "Tocantins", "Norte"}};
    private static final String[] REGIONS = {"Norte", "Nordeste", "Centro-Oeste", "Sudeste", "Sul"};
    private static final String[] REGION_ICON = {"🌳", "☀️", "🌾", "🏙️", "🧉"};
    // chave, título, ícone, cargo, usa eleição federal?
    private static final Object[][] DF_SRC = {
            {"pres", "Presidente da República", "🇧🇷", 1, true}, {"gov", "Governador do Distrito Federal", "🏢", 3, false},
            {"sen", "Senador pelo Distrito Federal", "🏛️", 5, false}, {"depf", "Deputado Federal — DF", "🗳️", 6, false},
            {"depd", "Deputado Distrital — DF", "📜", 8, false}};
    private static final String[][] TABS = {{"mapa", "", "Mapa"}, {"brasil", "", "Brasil"}, {"df", "", "DF"}, {"midia", "", "Mídia"}, {"mais", "", "Mais"}};
    private static final int[] TAB_ICON = {NavIconView.MAPA, NavIconView.BRASIL, NavIconView.DF, NavIconView.MIDIA, NavIconView.MAIS};
    // chave, rótulo, consulta, filtro do YouTube
    private static final String[][] LIVE_FILTERS = {{"live", "🔴 Ao vivo", "eleições 2026 ao vivo", Youtube.LIVE}, {"apuracao", "📊 Apuração", "apuração eleições 2026", Youtube.BY_DATE},
            {"debates", "🎙️ Debates", "debate eleições 2026", Youtube.BY_DATE}, {"analises", "💬 Análises", "análise eleições 2026", Youtube.BY_DATE}};
    private static final String[] CHANNELS = {"GloboNews", "CNN Brasil", "BandNews TV", "Jovem Pan News", "Record News", "SBT News", "TV Senado", "TV Câmara", "TV Brasil", "TSE"};
    private static final String[][] NEWS_FILTERS = {{"brasil", "🇧🇷 Brasil"}, {"mundo", "🌍 Mundo"}, {"mercados", "📈 Mercados"}, {"analises", "💬 Análises"}};
    private static final int[] INTERVALS = {5, 10, 15, 30, 60};

    static final class Snap {
        Model.Result pres;
        final Map<String, Model.Result> states = new HashMap<>(), gov = new HashMap<>(), df = new HashMap<>();
        final Map<String, Boolean> govAbsent = new HashMap<>(), dfAbsent = new HashMap<>();
        final List<LinkedHashMap<String, Float>> hist = new ArrayList<>();
        long lastChange, at;
        String presSig = "";
    }

    static final class Event { String icon, title, detail; long at; }

    private final Snap[] snaps = {new Snap(), new Snap()};
    private final Map<String, Long> absentAt = new HashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor(), net = Executors.newFixedThreadPool(6),
            newsPool = Executors.newFixedThreadPool(4);
    private final Map<String, News.Batch> newsCache = new HashMap<>();
    private SharedPreferences prefs;
    private String[][] codes = {Tse.DEFAULT_CODES[0].clone(), Tse.DEFAULT_CODES[1].clone()};
    private boolean codesFromConfig;
    private int turn = 1;
    private String tab = "mapa", mapaSub = "mapa", midiaSub = "news", sel = "DF", newsFilter = "brasil", cmpA = "DF", cmpB = "SP";
    private boolean govMode, busy, resumed, waiting, newsBusy, tv, livesBusy, animateNext = true, genStatusPending;
    private String livesFilter = "live", livesErr = "";
    private final Map<String, List<Youtube.Video>> livesCache = new HashMap<>();
    private final Map<String, Long> livesAt = new HashMap<>();
    private int studioType = StatusCard.PLACAR, studioFormat = StatusCard.STORY;
    private String studioUf = "DF";
    private String localUf = "DF"; // aba Estado: sempre abre no DF
    // Voto no exterior (aba Mapa > Mundo)
    private ExteriorUi exUi;
    private final Exterior.Snapshot[] exSnaps = new Exterior.Snapshot[2];
    private boolean exBusy;
    private long exAt;
    private final ExecutorService exPool = Executors.newSingleThreadExecutor();
    private String ctxUe = "BR", ctxRole = "candidato à presidência";
    private boolean roundShowElected;
    private long roundGovAt;
    private boolean roundGovBusy;
    private int localPickX;
    private final java.util.Set<String> localBusy = new java.util.HashSet<>();
    private final Map<String, Long> localAt = new HashMap<>();
    private int studioCargo = 2;
    private Bitmap studioBmp;
    private boolean studioBusy;
    private ImageView studioPreview;
    private LinearLayout studioHolder;
    private Dialog sheetDialog;
    private boolean updateAvailable, updating, awaitingInstallPermission;
    private long lastUpdateCheck;
    private int updatePct;
    private int remoteVersion;
    private long delayMs = 10000, intervalMs = 10000, lastPoll;
    private int titleTaps;
    private long firstTap;
    private String lastError = "nenhum", statusText = "Abrindo…", statusSub = "";
    private int insetL, insetT, insetR, insetB;

    private FrameLayout root;
    private LinearLayout page, nav;
    private ScrollView scroll;
    private LinearLayout content;
    private BrazilMapView mapView;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            ui.postDelayed(this, delayMs);
        }
    };

    // ============================================================ ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = getSharedPreferences("central", MODE_PRIVATE);
        turn = prefs.getInt("turn", 1) == 2 ? 2 : 1;
        intervalMs = delayMs = prefs.getInt("interval", 10) * 1000L;
        cmpA = prefs.getString("cmpA", "DF");
        cmpB = prefs.getString("cmpB", "SP");
        loadCache();
        for (int ti = 1; ti <= 2; ti++) exSnaps[ti - 1] = Exterior.restore(getFilesDir(), ti);
        Intent in = getIntent();
        if (in != null) { // atalhos de teste/automação: --es tab news --es sel SP --ez tv true
            if (in.getStringExtra("tab") != null) goTab(in.getStringExtra("tab"));
            if (in.getStringExtra("sel") != null) sel = in.getStringExtra("sel");
            if (in.getStringExtra("msub") != null) mapaSub = in.getStringExtra("msub");
            if (in.getStringExtra("luf") != null) { localUf = in.getStringExtra("luf").toUpperCase(Locale.ROOT); studioUf = localUf; }
            if (in.getStringExtra("filter") != null) newsFilter = in.getStringExtra("filter");
            if (in.getIntExtra("turn", 0) == 2) turn = 2;
            tv = in.getBooleanExtra("tv", false);
            if (in.getStringExtra("lfilter") != null) livesFilter = in.getStringExtra("lfilter");
            genStatusPending = in.getBooleanExtra("genstatus", false);
            if (in.getBooleanExtra("studio", false)) ui.postDelayed(this::showStatusStudio, 15000);
            if (in.getStringExtra("ficha") != null) { final int fi = Integer.parseInt(in.getStringExtra("ficha")); ui.postDelayed(() -> { Model.Result pr = snap().pres; if (pr != null && fi < pr.cands.size()) showFicha(pr.cands.get(fi), pr, "BR", "candidato à presidência"); }, 14000); }
            if (in.getStringExtra("sheet") != null) { final String su = in.getStringExtra("sheet"); ui.postDelayed(() -> showSheet(detailView(su)), 12000); }
        }
        buildUi();
        setupEdgeToEdge();
        if (tv) setTv(true);
        render();
        checkUpdate();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        ui.removeCallbacks(poller);
        ui.post(poller);
        checkUpdate();
        if (awaitingInstallPermission && Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) { awaitingInstallPermission = false; startUpdate(); }
    }

    @Override protected void onPause() {
        resumed = false;
        ui.removeCallbacks(poller);
        super.onPause();
    }

    @Override protected void onDestroy() {
        bg.shutdownNow();
        net.shutdownNow();
        newsPool.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tv) { setTv(false); render(); return; }
        super.onBackPressed();
    }

    // ============================================================ UI base
    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(Ui.pageBg());
        page = Ui.col(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = Ui.col(this);
        scroll.addView(content);
        page.addView(scroll, Ui.lp(-1, 0, 1f));
        nav = Ui.row(this);
        nav.setBackgroundColor(0xF2030911);
        page.addView(nav, Ui.lp(-1, -2));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        mapView = new BrazilMapView(this);
        mapView.setOnUf(uf -> {
            sel = uf;
            if (tv) return;
            render();
            showSheet(detailView(uf));
        });
    }

    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetL = bars.left; insetT = bars.top; insetR = bars.right; insetB = bars.bottom;
                applyInsets();
                return WindowInsets.CONSUMED;
            });
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
        }
    }

    private void applyInsets() {
        page.setPadding(insetL, 0, insetR, 0);
        content.setPadding(Ui.dp(14), insetT + Ui.dp(10), Ui.dp(14), Ui.dp(16));
        nav.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), insetB + Ui.dp(6));
    }

    private void setTv(boolean on) {
        tv = on;
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (on) { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
                else c.show(WindowInsets.Type.systemBars());
            }
        }
    }

    private int localVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Throwable t) { return 0; }
    }

    /** Pergunta ao GitHub se há versão mais nova do app (release app-latest; "versionCode=N" nas notas). Falha em silêncio. */
    private void checkUpdate() {
        if (System.currentTimeMillis() - lastUpdateCheck < 20 * 60 * 1000L) return;
        lastUpdateCheck = System.currentTimeMillis();
        bg.execute(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(RELEASE_API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "CentralEleicoes2026-Nativo");
                if (c.getResponseCode() != 200) return;
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 600000) out.write(buf, 0, n);
                in.close();
                String body = new JSONObject(out.toString("UTF-8")).optString("body", "");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("versionCode=(\\d+)").matcher(body);
                if (!m.find()) return;
                final int remote = Integer.parseInt(m.group(1));
                if (remote > localVersion() && localVersion() > 0) ui.post(() -> { updateAvailable = true; remoteVersion = remote; render(); });
            } catch (Throwable ignored) { }
        });
    }

    private Snap snap() { return snaps[turn - 1]; }

    private static String n(long v) { return INT.format(v); }

    private static String pc(double v) { return String.format(BR, "%.2f%%", v); }

    private static String hhmmss(long t) { return new SimpleDateFormat("HH:mm:ss", BR).format(new Date(t)); }

    private static String hhmm(long t) { return new SimpleDateFormat("HH:mm", BR).format(new Date(t)); }

    private static String ufName(String uf) { for (String[] u : UFS) if (u[0].equals(uf)) return u[1]; return uf; }

    private String cargoSub(String what) { return what + " • " + turn + "º turno"; }

    // ============================================================ render
    private void render() {
        try {
            int keep = scroll.getScrollY();
            content.removeAllViews();
            if (mapView.getParent() != null) ((ViewGroup) mapView.getParent()).removeView(mapView);
            applyInsets();
            if (tv) { page.setPadding(0, 0, 0, 0); nav.setVisibility(View.GONE); renderTv(); return; }
            nav.setVisibility(View.VISIBLE);
            renderHeader();
            if (updateAvailable) content.addView(updateBanner());
            if (tab.equals("brasil") || tab.equals("mapa")) content.addView(roundStrip());
            if (tab.equals("brasil") || (tab.equals("mapa") && mapaSub.equals("ufs"))) renderHero();
            switch (tab) {
                case "mapa":
                    content.addView(subTabs(new String[]{"mapa", "ufs", "mundo"}, new String[]{"Mapa", "Estados", "🌍 Mundo"}, mapaSub, x -> mapaSub = x));
                    if (mapaSub.equals("ufs")) renderUfs(); else if (mapaSub.equals("mundo")) renderMundo(); else renderMap();
                    break;
                case "brasil": renderBrasil(); break;
                case "df": renderDf(); break;
                case "midia":
                    content.addView(subTabs(new String[]{"news", "lives"}, new String[]{"Notícias", "Lives e vídeos"}, midiaSub, x -> midiaSub = x));
                    if (midiaSub.equals("lives")) renderLives(); else renderNews();
                    break;
                default: renderMais();
            }
            renderNav();
            if (tab.equals("midia") && midiaSub.equals("news") && needNews() && !newsBusy) loadNews(false);
            if (tab.equals("midia") && midiaSub.equals("lives") && needLives() && !livesBusy) loadLives(false);
            if (animateNext) { animateNext = false; animateIn(); }
            scroll.post(() -> scroll.scrollTo(0, keep));
        } catch (Throwable t) {
            lastError = "render: " + t;
        }
    }

    /** Aceita ids antigos (ufs/news/lives) e abre a aba/sub-aba certa. */
    private void goTab(String t) {
        switch (t) {
            case "ufs": tab = "mapa"; mapaSub = "ufs"; break;
            case "news": tab = "midia"; midiaSub = "news"; break;
            case "lives": tab = "midia"; midiaSub = "lives"; break;
            default: tab = t;
        }
    }

    private View subTabs(String[] ids, String[] labels, String current, final java.util.function.Consumer<String> cb) {
        LinearLayout seg = Ui.row(this);
        seg.setBackground(Ui.fill(0xE00A1728, 13, Ui.LINE));
        seg.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            boolean on = id.equals(current);
            TextView b = Ui.text(this, labels[i], 13, on ? Ui.INK : Ui.MUTED, true);
            b.setGravity(Gravity.CENTER);
            if (on) b.setBackground(Ui.accent(10));
            b.setOnClickListener(v -> { cb.accept(id); animateNext = true; render(); scroll.scrollTo(0, 0); });
            seg.addView(b, Ui.lp(0, Ui.dp(36), 1f));
        }
        seg.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
        return seg;
    }

    private void animateIn() {
        int n = Math.min(content.getChildCount(), 12);
        for (int i = 0; i < n; i++) {
            View v = content.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(14));
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 35L).setDuration(320).start();
        }
    }

    private void renderNav() {
        nav.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final String[] t = TABS[i];
            boolean on = tab.equals(t[0]);
            LinearLayout b = Ui.col(this);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
            if (on) b.setBackground(Ui.accent(16));
            b.addView(new NavIconView(this, TAB_ICON[i]).state(on, on ? Ui.INK : Ui.MUTED));
            TextView l = Ui.text(this, t[0].equals("df") ? localUf : t[2], 11, on ? Ui.INK : Ui.MUTED, true);
            l.setPadding(0, Ui.dp(3), 0, 0);
            l.setSingleLine();
            b.addView(l);
            b.setOnClickListener(v -> { tab = t[0]; animateNext = true; render(); scroll.scrollTo(0, 0); });
            LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1f);
            lp.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
            nav.addView(b, lp);
        }
    }

    private void renderHeader() {
        LinearLayout bar = Ui.row(this);
        TextView logo = Ui.text(this, "✓", 19, Ui.INK, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.accent(99));
        bar.addView(logo, Ui.margins(Ui.lp(Ui.dp(40), Ui.dp(40)), 0, 0, 12, 0));
        LinearLayout t = Ui.col(this);
        TextView title = Ui.big(this, "Brasil decide", 22);
        title.setOnClickListener(v -> easterEgg());
        t.addView(title);
        LinearLayout st = Ui.row(this);
        View dot = new View(this);
        boolean bad = statusText.contains("Sem conexão") || statusText.contains("parciais") || statusText.contains("aguardando");
        dot.setBackground(Ui.fill(bad ? Ui.AMBER : Ui.GREEN, 99, 0));
        st.addView(dot, Ui.margins(Ui.lp(Ui.dp(7), Ui.dp(7)), 0, 0, 7, 0));
        ObjectAnimator a = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f);
        a.setDuration(900);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.start();
        TextView stt = Ui.text(this, statusText + " • " + (delayMs / 1000) + " s", 11, Ui.MUTED, false);
        stt.setSingleLine();
        stt.setEllipsize(TextUtils.TruncateAt.END);
        st.addView(stt, Ui.lp(0, -2, 1f));
        t.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 5, 0, 0));
        bar.addView(t, Ui.lp(0, -2, 1f));
        TextView rb = Ui.text(this, busy ? "…" : "↻", 20, Ui.TEXT, true);
        rb.setGravity(Gravity.CENTER);
        rb.setBackground(Ui.fill(0xFF0E2138, 13, Ui.LINE));
        rb.setOnClickListener(v -> { delayMs = intervalMs; refresh(); if (tab.equals("mapa") && mapaSub.equals("mundo")) loadExterior(true); });
        bar.addView(rb, Ui.margins(Ui.lp(Ui.dp(40), Ui.dp(40)), 8, 0, 0, 0));
        content.addView(bar, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));

        LinearLayout seg = Ui.row(this);
        seg.setBackground(Ui.fill(0xE00A1728, 13, Ui.LINE));
        seg.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        seg.addView(turnBtn(1), Ui.lp(0, Ui.dp(34), 1f));
        seg.addView(turnBtn(2), Ui.lp(0, Ui.dp(34), 1f));
        content.addView(seg, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
    }

    private TextView turnBtn(int t) {
        boolean on = turn == t;
        TextView b = Ui.text(this, t + "º turno", 14, on ? Ui.INK : Ui.MUTED, true);
        b.setGravity(Gravity.CENTER);
        if (on) b.setBackground(Ui.accent(11));
        b.setOnClickListener(v -> setTurn(t));
        return b;
    }

    private void renderHero() {
        Model.Result r = snap().pres;
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        LinearLayout row = Ui.row(this);
        row.setBaselineAligned(false);
        LinearLayout a = Ui.col(this);
        a.addView(Ui.label(this, "Apuração • " + turn + "º turno"));
        TextView big = Ui.big(this, r == null ? "—" : pc(r.progress), 32);
        big.setPadding(0, Ui.dp(5), 0, 0);
        a.addView(big);
        a.addView(new GradientBar(this, 8).value(r == null ? 0 : r.progress));
        a.addView(Ui.text(this, r == null ? "carregando seções" : n(r.sections) + "/" + n(r.sectionsTotal) + " seções", 10, Ui.MUTED, false));
        row.addView(a, Ui.lp(0, -2, 1.5f));
        View div = new View(this);
        div.setBackgroundColor(0xFF1D3858);
        row.addView(div, Ui.margins(Ui.lp(1, Ui.dp(64)), 12, 0, 12, 0));
        LinearLayout b = Ui.col(this);
        b.addView(Ui.label(this, "Último dado"));
        String hm = snap().lastChange == 0 ? "—" : hhmm(snap().lastChange);
        TextView t = Ui.big(this, hm, 26);
        t.setPadding(0, Ui.dp(5), 0, 0);
        b.addView(t);
        b.addView(Ui.text(this, "consulta " + (lastPoll == 0 ? "—" : hhmmss(lastPoll)), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        b.addView(Ui.text(this, snap().states.size() + "/27 UFs", 10, Ui.MINT, true), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        row.addView(b, Ui.lp(0, -2, 1f));
        card.addView(row);
        content.addView(card);
    }

    // ============================================================ blocos de resultado
    private LinearLayout candRow(Model.Result r, Model.Cand c, int idx, boolean photo) {
        int col = Model.color(c.nome);
        LinearLayout w = Ui.col(this);
        w.setPadding(Ui.dp(2), Ui.dp(9), Ui.dp(2), Ui.dp(9));
        if (idx == 0) w.setBackground(Ui.gradient(0x14_64F5CB, 0x00000000, 10, 0, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT));
        LinearLayout row = Ui.row(this);
        if (photo) {
            AvatarView av = new AvatarView(this, 42).set(c.nome, col);
            if (r != null && !r.photoBase.isEmpty() && !c.sq.isEmpty()) Photos.load(r.photoBase + c.sq + ".jpeg", av);
            row.addView(av, Ui.margins(Ui.lp(Ui.dp(42), Ui.dp(42)), 0, 0, 10, 0));
        } else {
            View dot = new View(this);
            dot.setBackground(Ui.fill(col, 99, 0));
            row.addView(dot, Ui.margins(Ui.lp(Ui.dp(11), Ui.dp(11)), 4, 0, 9, 0));
        }
        LinearLayout mid = Ui.col(this);
        LinearLayout nm = Ui.row(this);
        TextView rank = Ui.text(this, (idx + 1) + "º", 10, Ui.SOFT, true);
        rank.setGravity(Gravity.CENTER);
        rank.setBackground(Ui.fill(0xFF143251, 6, 0));
        nm.addView(rank, Ui.margins(Ui.lp(Ui.dp(24), Ui.dp(18)), 0, 0, 6, 0));
        TextView name = Ui.text(this, c.nome, 13.5f, Ui.TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nm.addView(name, Ui.lp(0, -2, 1f));
        mid.addView(nm);
        LinearLayout meta = Ui.row(this);
        meta.setPadding(0, Ui.dp(5), 0, 0);
        TextView party = Ui.chip(this, c.partido + (c.numero.isEmpty() ? "" : " · nº " + c.numero), Ui.MUTED);
        meta.addView(party);
        if (!c.sit.isEmpty()) {
            boolean good = c.sit.toLowerCase(Locale.ROOT).matches(".*(eleit|2º|segundo).*");
            meta.addView(Ui.chip(this, c.sit, good ? Ui.GREEN : Ui.AMBER), Ui.margins(Ui.lp(-2, -2), 5, 0, 0, 0));
        }
        mid.addView(meta);
        row.addView(mid, Ui.lp(0, -2, 1f));
        LinearLayout right = Ui.col(this);
        right.setGravity(Gravity.END);
        TextView p = Ui.big(this, pc(c.pct), 16);
        p.setGravity(Gravity.END);
        TextView v = Ui.text(this, n(c.votos) + " votos", 10, Ui.MUTED, false);
        v.setGravity(Gravity.END);
        right.addView(p);
        right.addView(v, Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        row.addView(right, Ui.margins(Ui.lp(-2, -2), 8, 0, 0, 0));
        w.addView(row);
        w.addView(new GradientBar(this, 5).colors(col, Model.lighten(col)).value(c.pct));
        final String fue = ctxUe, frole = ctxRole;
        w.setOnClickListener(vw -> showFicha(c, r, fue, frole));
        return w;
    }

    private LinearLayout statBox(String value, String label) {
        LinearLayout b = Ui.col(this);
        b.setBackground(Ui.fill(0xFF09192B, 9, 0));
        b.setPadding(Ui.dp(8), Ui.dp(7), Ui.dp(8), Ui.dp(7));
        b.addView(Ui.text(this, value, 12, Ui.TEXT, true));
        b.addView(Ui.text(this, label, 9, Ui.MUTED, false));
        return b;
    }

    private LinearLayout resultCard(String title, String sub, String icon, Model.Result r, int top) {
        return resultCard(title, sub, icon, r, top, "BR", "candidato à presidência");
    }

    private LinearLayout resultCard(String title, String sub, String icon, Model.Result r, int top, String ue, String role) {
        ctxUe = ue;
        ctxRole = role;
        LinearLayout c = Ui.card(this);
        LinearLayout head = Ui.row(this);
        TextView ic = Ui.text(this, icon, 18, Ui.TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.fill(0xFF113052, 13, 0));
        head.addView(ic, Ui.margins(Ui.lp(Ui.dp(40), Ui.dp(40)), 0, 0, 12, 0));
        LinearLayout t = Ui.col(this);
        t.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        t.addView(Ui.text(this, sub + (r != null && r.fin ? " • totalização final" : ""), 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        head.addView(t, Ui.lp(0, -2, 1f));
        c.addView(head);
        if (r != null) {
            LinearLayout pr = Ui.row(this);
            pr.setPadding(0, Ui.dp(12), 0, 0);
            pr.addView(Ui.big(this, pc(r.progress), 20));
            pr.addView(Ui.text(this, "  das seções", 11, Ui.MUTED, false), Ui.lp(0, -2, 1f));
            pr.addView(Ui.text(this, n(r.sections) + "/" + n(r.sectionsTotal), 11, Ui.SOFT, true));
            c.addView(pr);
            c.addView(new GradientBar(this, 7).value(r.progress));
        }
        if (r == null) {
            c.addView(Ui.space(this, 12));
            for (int i = 0; i < 3; i++) c.addView(new ShimmerView(this, i == 0 ? 54 : 44, 12));
            return c;
        }
        View sep = new View(this);
        sep.setBackgroundColor(0xCC1D3858);
        c.addView(sep, Ui.margins(Ui.lp(-1, 1), 0, 11, 0, 3));
        for (int i = 0; i < Math.min(top, r.cands.size()); i++) c.addView(candRow(r, r.cands.get(i), i, true));
        if (r.cands.isEmpty()) c.addView(Ui.text(this, "Sem votos disponíveis ainda.", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 6));
        LinearLayout foot = Ui.row(this);
        foot.addView(statBox(n(r.valid), "válidos"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
        foot.addView(statBox(n(r.sections) + "/" + n(r.sectionsTotal), "seções"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 3, 0));
        foot.addView(statBox(n(r.blank), "brancos"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 3, 0));
        foot.addView(statBox(pc(r.abstPct), "abstenção"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 0, 0));
        c.addView(foot, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        c.addView(Ui.text(this, "Arquivo TSE: " + ((r.date + " • " + r.time).replaceAll("^ • | • $", "").trim()), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        return c;
    }

    private LinearLayout scoreline(Model.Result r) {
        LinearLayout c = Ui.card(this);
        LinearLayout row = Ui.row(this);
        Model.Cand a = r == null || r.cands.size() < 1 ? null : r.cands.get(0), b = r == null || r.cands.size() < 2 ? null : r.cands.get(1);
        row.addView(side(a == null ? "Aguardando dados" : "1º • " + a.nome, a == null ? "—" : pc(a.pct), a == null ? Ui.MUTED : Model.color(a.nome), Gravity.START), Ui.lp(0, -2, 1f));
        LinearLayout mid = Ui.col(this);
        mid.setGravity(Gravity.CENTER);
        mid.addView(Ui.text(this, a != null && b != null ? n(a.votos - b.votos) : "—", 15, Ui.TEXT, true));
        mid.addView(Ui.text(this, a != null && b != null ? String.format(BR, "%.2f p.p. de diferença", a.pct - b.pct) : "diferença", 9, Ui.MUTED, false));
        row.addView(mid, Ui.margins(Ui.lp(-2, -2), 8, 0, 8, 0));
        row.addView(side(b == null ? "" : "2º • " + b.nome, b == null ? "" : pc(b.pct), b == null ? Ui.MUTED : Model.color(b.nome), Gravity.END), Ui.lp(0, -2, 1f));
        c.addView(row);
        return c;
    }

    private LinearLayout side(String who, String pct, int color, int gravity) {
        LinearLayout s = Ui.col(this);
        s.setGravity(gravity);
        TextView w = Ui.text(this, who, 11, Ui.MUTED, false);
        w.setMaxLines(2);
        w.setEllipsize(TextUtils.TruncateAt.END);
        w.setGravity(gravity);
        s.addView(w);
        TextView p = Ui.big(this, pct, 19);
        p.setTextColor(color);
        p.setPadding(0, Ui.dp(3), 0, 0);
        s.addView(p);
        return s;
    }

    private LinearLayout sectionHead(String title, String sub) {
        LinearLayout h = Ui.col(this);
        h.setPadding(Ui.dp(2), Ui.dp(6), 0, Ui.dp(10));
        h.addView(Ui.text(this, title, 18, Ui.TEXT, true));
        if (sub != null) h.addView(Ui.text(this, sub, 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        return h;
    }

    // ============================================================ abas
    private void renderMap() {
        LinearLayout modes = Ui.row(this);
        modes.setBackground(Ui.fill(0xFF09182A, 12, Ui.LINE));
        modes.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        modes.addView(modeBtn("Presidente", !govMode, false), Ui.lp(0, Ui.dp(34), 1f));
        modes.addView(modeBtn("Governador", govMode, true), Ui.lp(0, Ui.dp(34), 1f));

        Map<String, Model.Result> data = govMode ? snap().gov : snap().states;
        Map<String, Integer> fills = new HashMap<>();
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        int loaded = 0, absent = 0;
        for (String[] u : UFS) {
            Model.Result r = data.get(u[0]);
            Model.Cand l = r == null ? null : r.lead();
            if (l != null) { loaded++; fills.put(u[0], Model.color(l.nome)); counts.merge(l.nome, 1, Integer::sum); }
            else if (govMode && Boolean.TRUE.equals(snap().govAbsent.get(u[0]))) absent++;
        }
        mapView.setFills(fills);
        mapView.setSelected(sel);
        LinearLayout c = Ui.card(this);
        LinearLayout top = Ui.row(this);
        LinearLayout tt = Ui.col(this);
        tt.addView(Ui.text(this, "Mapa eleitoral • " + (govMode ? "Governador" : "Presidente"), 15, Ui.TEXT, true));
        Model.Result pr0 = snap().pres;
        tt.addView(Ui.text(this, loaded == 0 ? "Aguardando dados do TSE • " + turn + "º turno" : turn + "º turno" + (!govMode && pr0 != null ? " • apuração " + pc(pr0.progress) : "") + (absent > 0 ? " • " + absent + " sem disputa" : ""), 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        top.addView(tt, Ui.lp(0, -2, 1f));
        top.addView(Ui.chip(this, loaded + "/27 UFs", Ui.MUTED));
        c.addView(top);
        c.addView(modes, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 4));
        c.addView(mapView, Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 6));
        FlowLayout legend = new FlowLayout(this, 6);
        List<Map.Entry<String, Integer>> es = new ArrayList<>(counts.entrySet());
        es.sort((x, y) -> y.getValue() - x.getValue());
        for (Map.Entry<String, Integer> e : es) legend.addView(legendPill(Model.color(e.getKey()), e.getKey(), e.getValue() + " UF" + (e.getValue() > 1 ? "s" : "")));
        if (loaded > 0 && loaded < 27) legend.addView(legendPill(0xFF17304F, absent > 0 ? "Sem disputa / sem dados" : "Sem dados ainda", (27 - loaded) + " UFs"));
        if (loaded == 0) legend.addView(legendPill(0xFF17304F, "Aguardando líderes por UF…", ""));
        c.addView(legend);
        c.addView(Ui.text(this, "As cores identificam candidatos no mapa; não representam avaliação política nem projeção. Geometria: @svg-maps/brazil (CC BY 4.0).", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 0));
        content.addView(c);
        if (govMode && data.get(sel) == null && Boolean.TRUE.equals(snap().govAbsent.get(sel)))
            content.addView(emptyCard(ufName(sel) + " (" + sel + ")", "Sem disputa neste cargo/UF" + (turn == 2 ? " no 2º turno." : ".")));
        else content.addView(resultCard(ufName(sel) + " (" + sel + ")", cargoSub(govMode ? "Governador" : "Presidente"), "📍", data.get(sel), 3, govMode ? sel : "BR", govMode ? "candidato a governador" : "candidato à presidência"));
        content.addView(roundCard());
    }

    private TextView modeBtn(String label, boolean on, boolean gov) {
        TextView b = Ui.text(this, label, 13, on ? Ui.TEXT : Ui.MUTED, true);
        b.setGravity(Gravity.CENTER);
        if (on) b.setBackground(Ui.fill(0xFF143251, 9, 0));
        b.setOnClickListener(v -> { govMode = gov; if (gov && snap().gov.isEmpty()) refresh(); render(); });
        return b;
    }

    private View legendPill(int color, String name, String count) {
        LinearLayout p = Ui.row(this);
        p.setBackground(Ui.fill(0xFF09182A, 99, Ui.LINE));
        p.setPadding(Ui.dp(9), Ui.dp(5), Ui.dp(11), Ui.dp(5));
        View d = new View(this);
        d.setBackground(Ui.fill(color, 99, 0));
        p.addView(d, Ui.margins(Ui.lp(Ui.dp(10), Ui.dp(10)), 0, 0, 7, 0));
        p.addView(Ui.text(this, name, 11, Ui.TEXT, true));
        if (!count.isEmpty()) p.addView(Ui.text(this, "  " + count, 11, Ui.MUTED, false));
        return p;
    }

    private LinearLayout emptyCard(String title, String msg) {
        LinearLayout e = Ui.card(this);
        e.addView(Ui.text(this, title, 15, Ui.TEXT, true));
        e.addView(Ui.text(this, msg, 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return e;
    }

    private void renderBrasil() {
        content.addView(sectionHead("Presidente — Brasil", "Votos e situação publicados oficialmente pelo TSE."));
        Model.Result r = snap().pres;
        content.addView(scoreline(r));
        content.addView(promoStatus());
        content.addView(roundCard());
        content.addView(resultCard("Presidente da República", cargoSub("Brasil"), "🇧🇷", r, turn == 2 ? 2 : 3));
        if (r == null && waiting) content.addView(emptyCard("Aguardando o TSE", "O resultado presidencial do " + turn + "º turno ainda não foi publicado. Nova consulta automática em ~60 s."));

        // Pulso: gráfico do top 3
        content.addView(sectionHead("Pulso da apuração", "Percentual dos válidos a cada arquivo novo do TSE (histórico guardado neste aparelho, separado por turno)."));
        LinearLayout pc = Ui.card(this);
        LineChartView chart = new LineChartView(this);
        List<float[]> series = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        FlowLayout leg = new FlowLayout(this, 8);
        if (r != null) {
            for (int i = 0; i < Math.min(3, r.cands.size()); i++) {
                String nm = r.cands.get(i).nome;
                List<Float> vals = new ArrayList<>();
                for (LinkedHashMap<String, Float> h : snap().hist) if (h.containsKey(nm)) vals.add(h.get(nm));
                float[] arr = new float[vals.size()];
                for (int k = 0; k < arr.length; k++) arr[k] = vals.get(k);
                series.add(arr);
                cols.add(Model.color(nm));
                leg.addView(legendPill(Model.color(nm), nm, ""));
            }
        }
        chart.set(series, cols);
        pc.addView(chart);
        pc.addView(leg, Ui.margins(Ui.lp(-1, -2), 0, 8, 0, 0));
        content.addView(pc);

        // Marcos
        content.addView(sectionHead("Marcos da totalização", null));
        FlowLayout ms = new FlowLayout(this, 6);
        double prog = r == null ? 0 : r.progress;
        for (int m : new int[]{25, 50, 75, 90, 95, 99, 100}) {
            boolean hit = prog >= m;
            TextView t = Ui.text(this, (hit ? "✓ " : "") + m + "%", 12, hit ? 0xFFA7F0C1 : Ui.MUTED, true);
            t.setPadding(Ui.dp(11), Ui.dp(7), Ui.dp(11), Ui.dp(7));
            t.setBackground(Ui.fill(hit ? 0x1C55DF8B : 0x00000000, 99, hit ? 0xFF33764F : Ui.LINE));
            ms.addView(t);
        }
        content.addView(ms, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));

        // Movimentos
        content.addView(sectionHead("Últimos movimentos", "Alterações detectadas pelo app — sem previsão."));
        LinearLayout ev = Ui.card(this);
        if (events.isEmpty()) ev.addView(Ui.text(this, "Aguardando mudanças nos arquivos do TSE…", 12, Ui.MUTED, false));
        for (int i = 0; i < Math.min(8, events.size()); i++) {
            Event e = events.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
            TextView ic = Ui.text(this, e.icon, 13, Ui.TEXT, false);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(Ui.fill(0xFF102B49, 9, 0));
            row.addView(ic, Ui.margins(Ui.lp(Ui.dp(28), Ui.dp(28)), 0, 0, 10, 0));
            LinearLayout tx = Ui.col(this);
            tx.addView(Ui.text(this, e.title, 12, Ui.TEXT, true));
            tx.addView(Ui.text(this, e.detail, 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
            row.addView(tx, Ui.lp(0, -2, 1f));
            row.addView(Ui.text(this, hhmm(e.at), 10, Ui.MUTED, false));
            ev.addView(row);
        }
        content.addView(ev);

        // Regiões
        content.addView(sectionHead("Regiões", "Soma real dos votos dos estados; apuração ponderada pelas seções."));
        for (int i = 0; i < REGIONS.length; i++) {
            Model.Result agg = aggregate(REGIONS[i]);
            LinearLayout c = Ui.card(this);
            LinearLayout h = Ui.row(this);
            h.addView(Ui.text(this, REGION_ICON[i] + "  " + REGIONS[i], 15, Ui.TEXT, true), Ui.lp(0, -2, 1f));
            h.addView(Ui.text(this, agg == null ? "…" : pc(agg.progress), 12, Ui.MINT, true));
            c.addView(h);
            if (agg == null) c.addView(Ui.text(this, "carregando…", 11, Ui.MUTED, false));
            else {
                c.addView(new GradientBar(this, 6).value(agg.progress));
                for (int k = 0; k < Math.min(3, agg.cands.size()); k++) c.addView(miniRow(agg.cands.get(k), k));
            }
            content.addView(c);
        }
    }

    private View updateBanner() {
        LinearLayout c = Ui.row(this);
        c.setBackground(Ui.gradient(0x44FFC966, 0x22FFC966, 16, 0x99FFC966, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT));
        c.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        c.addView(Ui.text(this, "🔔", 20, Ui.TEXT, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 12, 0));
        LinearLayout t = Ui.col(this);
        t.addView(Ui.text(this, updating ? "Baixando a atualização… " + updatePct + "%" : "Nova versão disponível (build " + remoteVersion + ")", 14, Ui.TEXT, true));
        t.addView(Ui.text(this, updating ? "Quando terminar, toque em Instalar na tela do Android." : "Toque para atualizar aqui mesmo — sem abrir o navegador.", 10, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        if (updating) t.addView(new GradientBar(this, 6).colors(Ui.AMBER, Ui.MINT).value(updatePct), Ui.margins(Ui.lp(-1, Ui.dp(6)), 0, 8, 0, 0));
        c.addView(t, Ui.lp(0, -2, 1f));
        if (!updating) c.addView(Ui.text(this, "Atualizar ›", 13, Ui.AMBER, true));
        c.setOnClickListener(v -> startUpdate());
        c.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
        return c;
    }

    /** Baixa o APK novo para o cache do app e abre o instalador do Android (uma confirmação do usuário). */
    private void startUpdate() {
        if (updating) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            awaitingInstallPermission = true;
            Toast.makeText(this, "Ative “Permitir desta fonte” e volte — a atualização começa sozinha", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
            catch (Throwable t) { openUrl(DL_URL); }
            return;
        }
        updating = true;
        updatePct = 0;
        render();
        bg.execute(() -> {
            java.io.File out = new java.io.File(new java.io.File(getCacheDir(), "share"), "update.apk");
            try {
                out.getParentFile().mkdirs();
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(DL_URL).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "CentralEleicoes2026-Nativo");
                if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
                long total = c.getContentLengthLong(), got = 0;
                java.io.InputStream in = c.getInputStream();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                byte[] buf = new byte[32768];
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    got += n;
                    final int pct = total > 0 ? (int) (got * 100 / total) : 0;
                    if (pct != last) { last = pct; ui.post(() -> { updatePct = pct; if (!tv) render(); }); }
                }
                fo.close();
                in.close();
                if (out.length() < 100000) throw new IllegalStateException("arquivo incompleto");
                ui.post(() -> { updating = false; render(); installApk(); });
            } catch (Throwable t) {
                lastError = "atualização: " + t;
                out.delete();
                ui.post(() -> { updating = false; render(); Toast.makeText(this, "Não foi possível baixar a atualização. Tente de novo ou use o link de download.", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private void installApk() {
        try {
            Uri uri = new Uri.Builder().scheme("content").authority(StatusProvider.AUTHORITY).appendPath("update.apk").build();
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) { lastError = "instalar: " + t; openUrl(DL_URL); }
    }

    private View promoStatus() {
        LinearLayout c = Ui.row(this);
        c.setBackground(Ui.gradient(0x3350D5FF, 0x3364F5CB, 18, 0x6650D5FF, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT));
        c.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        TextView ic = Ui.text(this, "📸", 24, Ui.TEXT, false);
        c.addView(ic, Ui.margins(Ui.lp(-2, -2), 0, 0, 12, 0));
        LinearLayout t = Ui.col(this);
        t.addView(Ui.text(this, "Imagens para status", 14, Ui.TEXT, true));
        t.addView(Ui.text(this, "Gere um card pronto (story ou post) com o resultado oficial.", 10, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        c.addView(t, Ui.lp(0, -2, 1f));
        c.addView(Ui.text(this, "Criar ›", 13, Ui.MINT, true));
        c.setOnClickListener(v -> { studioType = StatusCard.PLACAR; showStatusStudio(); });
        c.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
        return c;
    }

    private View miniRow(Model.Cand c, int idx) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(5), 0, Ui.dp(5));
        View d = new View(this);
        d.setBackground(Ui.fill(Model.color(c.nome), 99, 0));
        r.addView(d, Ui.margins(Ui.lp(Ui.dp(10), Ui.dp(10)), 0, 0, 8, 0));
        TextView nm = Ui.text(this, (idx + 1) + "º  " + c.nome, 13, Ui.TEXT, idx == 0);
        nm.setSingleLine();
        nm.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(nm, Ui.lp(0, -2, 1f));
        r.addView(Ui.text(this, pc(c.pct), 13, Ui.TEXT, true));
        return r;
    }

    private Model.Result aggregate(String region) {
        Map<String, Model.Cand> map = new LinkedHashMap<>();
        long sections = 0, total = 0, valid = 0;
        int have = 0;
        for (String[] u : UFS) {
            if (!u[2].equals(region)) continue;
            Model.Result d = snap().states.get(u[0]);
            if (d == null) continue;
            have++;
            sections += d.sections; total += d.sectionsTotal; valid += d.valid;
            for (Model.Cand c : d.cands) {
                String k = c.numero.isEmpty() ? c.nome : c.numero;
                Model.Cand z = map.get(k);
                if (z == null) { z = new Model.Cand(); z.nome = c.nome; z.partido = c.partido; z.numero = c.numero; map.put(k, z); }
                z.votos += c.votos;
            }
        }
        if (have == 0) return null;
        Model.Result r = new Model.Result();
        r.cands.addAll(map.values());
        r.cands.sort((a, b) -> Long.compare(b.votos, a.votos));
        for (Model.Cand c : r.cands) c.pct = valid == 0 ? 0 : c.votos * 100.0 / valid;
        r.sections = sections; r.sectionsTotal = total; r.valid = valid;
        r.progress = total == 0 ? 0 : sections * 100.0 / total;
        return r;
    }

    private void renderUfs() {
        content.addView(sectionHead("Todos os estados", "Líder e apuração. Toque numa UF para abrir top 3, diferença e votos."));
        for (int ri = 0; ri < REGIONS.length; ri++) {
            LinearLayout h = Ui.row(this);
            h.setPadding(Ui.dp(2), Ui.dp(8), 0, Ui.dp(8));
            h.addView(Ui.text(this, REGION_ICON[ri] + "  " + REGIONS[ri], 15, Ui.TEXT, true), Ui.lp(0, -2, 1f));
            content.addView(h);
            List<String[]> ufs = new ArrayList<>();
            for (String[] u : UFS) if (u[2].equals(REGIONS[ri])) ufs.add(u);
            for (int i = 0; i < ufs.size(); i += 2) {
                LinearLayout row = Ui.row(this);
                row.setBaselineAligned(false);
                for (int k = 0; k < 2; k++) {
                    if (i + k < ufs.size()) row.addView(ufTile(ufs.get(i + k)[0]), Ui.margins(Ui.lp(0, -2, 1f), k == 0 ? 0 : 5, 0, k == 0 ? 5 : 0, 10));
                    else row.addView(new View(this), Ui.lp(0, 1, 1f));
                }
                content.addView(row, Ui.lp(-1, -2));
            }
        }
    }

    private View ufTile(String uf) {
        Model.Result r = snap().states.get(uf);
        Model.Cand l = r == null ? null : r.lead();
        LinearLayout t = Ui.row(this);
        t.setBackground(Ui.cardBg(14));
        View stripe = new View(this);
        stripe.setBackground(Ui.fill(l == null ? 0xFF17304F : Model.color(l.nome), 4, 0));
        t.addView(stripe, Ui.margins(Ui.lp(Ui.dp(5), -1), 0, 0, 0, 0));
        LinearLayout c = Ui.col(this);
        c.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(11), Ui.dp(10));
        LinearLayout top = Ui.row(this);
        top.addView(Ui.big(this, uf, 15), Ui.lp(0, -2, 1f));
        top.addView(Ui.text(this, r == null ? "—" : pc(r.progress), 11, Ui.MINT, true));
        c.addView(top);
        TextView nm = Ui.text(this, l == null ? "Carregando…" : l.nome, 12, Ui.TEXT, true);
        nm.setSingleLine();
        nm.setEllipsize(TextUtils.TruncateAt.END);
        nm.setPadding(0, Ui.dp(6), 0, 0);
        c.addView(nm);
        c.addView(Ui.text(this, l == null ? ufName(uf) : pc(l.pct), 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        c.addView(new GradientBar(this, 4).value(r == null ? 0 : r.progress));
        t.addView(c, Ui.lp(0, -2, 1f));
        t.setOnClickListener(v -> { sel = uf; showSheet(detailView(uf)); });
        return t;
    }

    private static int localCargo(String key, String uf) {
        switch (key) {
            case "pres": return 1;
            case "gov": return 3;
            case "sen": return 5;
            case "depf": return 6;
            default: return uf.equals("DF") ? 8 : 7; // Deputado Distrital no DF, Estadual nos demais
        }
    }

    private String localTitle(String key) {
        boolean df = localUf.equals("DF");
        switch (key) {
            case "pres": return "Presidente da República";
            case "gov": return df ? "Governador do Distrito Federal" : "Governador — " + ufName(localUf);
            case "sen": return df ? "Senador pelo Distrito Federal" : "Senador — " + ufName(localUf);
            case "depf": return "Deputado Federal — " + localUf;
            default: return (df ? "Deputado Distrital — " : "Deputado Estadual — ") + localUf;
        }
    }

    private String localSub(String key) {
        String nm = ufName(localUf);
        switch (key) {
            case "sen": return cargoSub(nm + " • 2 vagas");
            case "depf": return cargoSub(nm + " • " + StatusCard.bancada(localUf) + " vagas");
            case "depd": return cargoSub(nm + " • " + StatusCard.vagasEst(localUf) + " vagas");
            default: return cargoSub(nm);
        }
    }

    private List<Callable<Object[]>> localTasks(int t, String uf) {
        List<Callable<Object[]>> ts = new ArrayList<>();
        String fed = codes[t - 1][0], est = codes[t - 1][1], abr = uf.toLowerCase(Locale.ROOT);
        for (Object[] s : DF_SRC) {
            String k = (String) s[0];
            if (t == 2 && !k.equals("pres") && !k.equals("gov")) continue;
            boolean isFed = (Boolean) s[4];
            ts.add(task("d:" + uf + "|" + k, Tse.url(isFed ? fed : est, abr, localCargo(k, uf)), 300000, Tse.photoBase(isFed ? fed : est, isFed ? "br" : abr)));
        }
        return ts;
    }

    /** Busca os cargos de uma UF (aba Estado e estúdio de imagens) sem refazer o ciclo nacional. */
    private void loadLocal(final String uf, boolean force, final Runnable after) {
        final int t = turn;
        final String key = t + uf;
        if (localBusy.contains(key)) { if (after != null) ui.postDelayed(() -> loadLocal(uf, false, after), 1500); return; }
        Long at = localAt.get(key);
        if (!force && at != null && System.currentTimeMillis() - at < 30000) { if (after != null) after.run(); return; }
        localBusy.add(key);
        bg.execute(() -> {
            final Map<String, Model.Result> ok = new HashMap<>();
            final Map<String, Boolean> ab = new HashMap<>();
            try {
                for (Object[] o : runAll(localTasks(t, uf))) {
                    String k = (String) o[0];
                    Tse.Fetch f = (Tse.Fetch) o[1];
                    String id = k.substring(2);
                    if (f.error != null) { lastError = k + " → " + f.error; continue; }
                    if (f.result != null) ok.put(id, f.result); else if (f.absent) ab.put(id, true);
                }
            } catch (Throwable th) { lastError = "estado: " + th; }
            ui.post(() -> {
                localBusy.remove(key);
                localAt.put(key, System.currentTimeMillis());
                snaps[t - 1].df.putAll(ok);
                snaps[t - 1].dfAbsent.putAll(ab);
                if (!ok.isEmpty()) saveCache(t);
                if (after != null) after.run();
                if (!tv || resumed) render();
            });
        });
    }

    private View localPicker() {
        final HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        List<String> order = new ArrayList<>();
        order.add("DF");
        for (String[] u : UFS) if (!u[0].equals("DF")) order.add(u[0]);
        for (final String uf : order) {
            boolean on = localUf.equals(uf);
            TextView t = Ui.text(this, uf, 13, on ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(14), Ui.dp(9), Ui.dp(14), Ui.dp(9));
            t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { localUf = uf; loadLocal(uf, false, null); render(); });
            row.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 0, 7, 0));
        }
        hs.addView(row);
        hs.setOnScrollChangeListener((v, x, y, ox, oy) -> localPickX = x);
        hs.post(() -> hs.scrollTo(localPickX, 0));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(10);
        hs.setLayoutParams(lp);
        return hs;
    }

    private void renderDf() {
        final String nm = ufName(localUf);
        boolean loading = localBusy.contains(turn + localUf);
        content.addView(sectionHead(nm, loading ? "Carregando os resultados de " + nm + "…" : "Presidente, Governo, Senado, Câmara e Assembleia. Escolha outro estado abaixo."));
        content.addView(localPicker());
        for (Object[] s : DF_SRC) {
            String key = (String) s[0], icon = (String) s[2];
            String title = localTitle(key), dk = localUf + "|" + key;
            if (turn == 2 && !key.equals("pres") && !key.equals("gov")) { content.addView(emptyCard(title, "Sem disputa neste cargo no 2º turno.")); continue; }
            Model.Result r = snap().df.get(dk);
            if (Boolean.TRUE.equals(snap().dfAbsent.get(dk)) && r == null) { content.addView(emptyCard(title, "Sem disputa neste cargo/UF.")); continue; }
            int top = key.equals("pres") ? (turn == 2 ? 2 : 3) : key.equals("gov") ? (turn == 2 ? 2 : 6) : key.equals("sen") ? 8 : key.equals("depf") ? 10 : 14;
            String role = key.equals("pres") ? "candidato à presidência" : key.equals("gov") ? "candidato a governador" : key.equals("sen") ? "candidato ao Senado" : key.equals("depf") ? "candidato a deputado federal" : "candidato a deputado";
            content.addView(resultCard(title, localSub(key), icon, r, top, key.equals("pres") ? "BR" : localUf, role));
        }
        LinearLayout c = Ui.row(this);
        c.setBackground(Ui.gradient(0x3350D5FF, 0x3364F5CB, 18, 0x6650D5FF, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT));
        c.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        c.addView(Ui.text(this, "📸", 24, Ui.TEXT, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 12, 0));
        LinearLayout t = Ui.col(this);
        t.addView(Ui.text(this, "Imagem de " + nm, 14, Ui.TEXT, true));
        t.addView(Ui.text(this, "Card pronto (story ou post) com os cargos deste estado.", 10, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        c.addView(t, Ui.lp(0, -2, 1f));
        c.addView(Ui.text(this, "Criar ›", 13, Ui.MINT, true));
        c.setOnClickListener(v -> { studioType = StatusCard.DFC; studioUf = localUf; showStatusStudio(); });
        c.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 12));
        content.addView(c);
        if (!loading) loadLocal(localUf, false, null);
    }

    // ---------------------------------------------------------------- notícias
    private boolean needNews() {
        News.Batch b = newsCache.get(newsFilter);
        return b == null || System.currentTimeMillis() - b.at > 5 * 60 * 1000;
    }

    private void loadNews(boolean force) {
        if (newsBusy || (!force && !needNews())) return;
        newsBusy = true;
        final String f = newsFilter;
        render();
        bg.execute(() -> {
            News.Batch b = null;
            try { b = News.load(f, newsPool); } catch (Throwable t) { lastError = "notícias: " + t; }
            final News.Batch fb = b;
            ui.post(() -> {
                if (fb != null && (!fb.articles.isEmpty() || !newsCache.containsKey(f))) newsCache.put(f, fb);
                newsBusy = false;
                render();
            });
        });
    }

    private void renderNews() {
        LinearLayout head = Ui.row(this);
        LinearLayout hh = sectionHead("Radar mundial de notícias", "Várias fontes: Google News (PT/EN/ES), veículos por RSS. Toque numa matéria para ler o trecho no app.");
        head.addView(hh, Ui.lp(0, -2, 1f));
        TextView rf = Ui.text(this, newsBusy ? "…" : "↻", 20, Ui.TEXT, true);
        rf.setGravity(Gravity.CENTER);
        rf.setBackground(Ui.fill(0xFF0E2138, 12, Ui.LINE));
        rf.setOnClickListener(v -> loadNews(true));
        head.addView(rf, Ui.lp(Ui.dp(44), Ui.dp(40)));
        content.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(this);
        for (String[] f : NEWS_FILTERS) {
            boolean on = newsFilter.equals(f[0]);
            TextView t = Ui.text(this, f[1], 12, on ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(13), Ui.dp(9), Ui.dp(13), Ui.dp(9));
            t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { newsFilter = f[0]; if (needNews()) loadNews(false); render(); });
            chips.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 0, 7, 0));
        }
        hs.addView(chips);
        content.addView(hs, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        News.Batch b = newsCache.get(newsFilter);
        LinearLayout st = Ui.card(this);
        if (b == null) st.addView(Ui.text(this, newsBusy ? "Buscando em várias fontes…" : "Toque em ↻ para buscar as notícias.", 12, Ui.MUTED, false));
        else {
            int ok = 0;
            List<String> bad = new ArrayList<>();
            for (News.Health h : b.health) { if (h.ok) ok++; else bad.add(h.label); }
            java.util.Set<String> srcs = new java.util.HashSet<>(), ccs = new java.util.HashSet<>();
            for (News.Article a : b.articles) { srcs.add(a.source); ccs.add(a.cc); }
            LinearLayout m = Ui.row(this);
            m.addView(statBox(String.valueOf(b.articles.size()), "matérias"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
            m.addView(statBox(String.valueOf(srcs.size()), "fontes"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 3, 0));
            m.addView(statBox(String.valueOf(ccs.size()), "países"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 0, 0));
            st.addView(m);
            st.addView(Ui.text(this, "Atualizado " + hhmm(b.at) + " • " + ok + "/" + b.health.size() + " fontes responderam" + (newsBusy ? " • atualizando…" : ""), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 9, 0, 0));
            if (!bad.isEmpty()) st.addView(Ui.text(this, "Indisponíveis agora: " + TextUtils.join(", ", bad) + ". As demais continuam funcionando.", 10, Ui.AMBER, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        }
        content.addView(st);
        if (b != null) {
            if (b.articles.isEmpty()) content.addView(emptyCard("Nenhuma matéria agora", "Nenhuma matéria encontrada neste filtro. Tentaremos de novo."));
            for (News.Article a : b.articles) content.addView(articleCard(a));
            content.addView(Ui.text(this, "As opiniões aparecem atribuídas às respectivas fontes. O app não transforma manchetes ou colunas em fatos e não faz avaliação política própria. Classificação (notícia/análise/opinião) automática por palavras-chave.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 4, 2, 0));
        }
    }

    private View articleCard(final News.Article a) {
        LinearLayout c = Ui.card(this);
        c.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 9));
        LinearLayout meta = Ui.row(this);
        int kc = a.kind.equals("Opinião") ? 0xFFFFD9A0 : a.kind.equals("Análise") ? 0xFFD9C7FF : 0xFFBDE9FF;
        meta.addView(Ui.chip(this, a.kind, kc));
        TextView s = Ui.text(this, "  " + a.source + (a.cc.isEmpty() ? "" : " • " + a.cc) + (a.lang.isEmpty() ? "" : " • " + a.lang.toUpperCase(Locale.ROOT)) + (a.ts == 0 ? "" : " • " + new SimpleDateFormat("dd/MM HH:mm", BR).format(new Date(a.ts))), 10, Ui.MUTED, false);
        s.setSingleLine();
        s.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(s, Ui.lp(0, -2, 1f));
        c.addView(meta);
        c.addView(Ui.text(this, a.title, 14, Ui.TEXT, true), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        if (!a.desc.isEmpty()) {
            TextView d = Ui.text(this, a.desc, 12, Ui.SOFT, false);
            d.setMaxLines(4);
            d.setEllipsize(TextUtils.TruncateAt.END);
            d.setLineSpacing(0, 1.15f);
            c.addView(d, Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        }
        c.setOnClickListener(v -> showSheet(articleView(a)));
        return c;
    }

    private View articleView(final News.Article a) {
        LinearLayout c = Ui.col(this);
        LinearLayout meta = Ui.row(this);
        int kc = a.kind.equals("Opinião") ? 0xFFFFD9A0 : a.kind.equals("Análise") ? 0xFFD9C7FF : 0xFFBDE9FF;
        meta.addView(Ui.chip(this, a.kind, kc));
        meta.addView(Ui.text(this, "  " + a.source + (a.ts == 0 ? "" : " • " + new SimpleDateFormat("dd/MM HH:mm", BR).format(new Date(a.ts))), 11, Ui.MUTED, false));
        c.addView(meta);
        c.addView(Ui.text(this, a.title, 19, Ui.TEXT, true), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 10));
        if (!a.desc.isEmpty()) {
            TextView d = Ui.text(this, a.desc, 14, Ui.SOFT, false);
            d.setLineSpacing(0, 1.25f);
            c.addView(d);
            c.addView(Ui.text(this, "Trecho fornecido pela própria fonte via feed" + (a.kind.equals("Opinião") ? "; o texto expressa a visão do autor/veículo, não do app." : "."), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        } else {
            TextView n = Ui.text(this, "Esta fonte não envia trecho da matéria no feed. O app não inventa resumo: abra o original para ler o texto completo.", 12, Ui.MUTED, false);
            n.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(12));
            n.setBackground(Ui.fill(0x00000000, 12, 0xFF35577E));
            c.addView(n);
        }
        TextView open = Ui.text(this, "Abrir matéria original ↗", 14, Ui.INK, true);
        open.setGravity(Gravity.CENTER);
        open.setBackground(Ui.accent(13));
        open.setOnClickListener(v -> {
            try {
                Uri u = Uri.parse(a.url);
                if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Throwable t) { Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show(); }
        });
        c.addView(open, Ui.margins(Ui.lp(-1, Ui.dp(48)), 0, 16, 0, 0));
        TextView share = Ui.text(this, "Compartilhar manchete", 13, Ui.SOFT, true);
        share.setGravity(Gravity.CENTER);
        share.setOnClickListener(v -> share(a.title + "\n" + a.source + "\n" + a.url));
        c.addView(share, Ui.lp(-1, Ui.dp(44)));
        return c;
    }

    // ---------------------------------------------------------------- mais (labs)
    private void renderMais() {
        content.addView(sectionHead("Laboratório da apuração", "Ferramentas que transformam o app numa central de acompanhamento."));

        // comparador
        LinearLayout cmp = Ui.card(this);
        cmp.addView(Ui.text(this, "🔬 Comparador de UFs", 15, Ui.TEXT, true));
        cmp.addView(Ui.text(this, "Coloque dois estados lado a lado: top 3 e andamento das seções.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        LinearLayout sp = Ui.row(this);
        sp.addView(ufSpinner(cmpA, true), Ui.margins(Ui.lp(0, Ui.dp(44), 1f), 0, 0, 4, 0));
        sp.addView(ufSpinner(cmpB, false), Ui.margins(Ui.lp(0, Ui.dp(44), 1f), 4, 0, 0, 0));
        cmp.addView(sp);
        cmp.addView(cmpSide(cmpA), Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        cmp.addView(cmpSide(cmpB), Ui.margins(Ui.lp(-1, -2), 0, 8, 0, 0));
        content.addView(cmp);

        // ações
        LinearLayout act = Ui.card(this);
        act.addView(Ui.text(this, "🧰 Ações rápidas", 15, Ui.TEXT, true));
        LinearLayout r1 = Ui.row(this);
        r1.addView(actionBtn("📋 Compartilhar boletim", "Resumo com os dados atuais", v -> share(snapshotText())), Ui.margins(Ui.lp(0, -2, 1f), 0, 10, 4, 0));
        r1.addView(actionBtn("📺 Modo TV", "Tela cheia, mapa grande", v -> { setTv(true); render(); }), Ui.margins(Ui.lp(0, -2, 1f), 4, 10, 0, 0));
        act.addView(r1);
        LinearLayout r2 = Ui.row(this);
        r2.addView(actionBtn("📸 Imagem p/ status", "Story ou post com o resultado", v -> { studioType = StatusCard.PLACAR; showStatusStudio(); }), Ui.margins(Ui.lp(0, -2, 1f), 0, 8, 4, 0));
        r2.addView(actionBtn("🧹 Zerar histórico", "Recomeça o gráfico deste turno", v -> { snap().hist.clear(); saveCache(turn); Toast.makeText(this, "Histórico zerado", Toast.LENGTH_SHORT).show(); render(); }), Ui.margins(Ui.lp(0, -2, 1f), 4, 8, 0, 0));
        act.addView(r2);
        LinearLayout r3 = Ui.row(this);
        r3.addView(actionBtn("🌍 Radar global", "Ir para as notícias", v -> { tab = "midia"; midiaSub = "news"; animateNext = true; render(); scroll.scrollTo(0, 0); }), Ui.margins(Ui.lp(0, -2, 1f), 0, 8, 4, 0));
        r3.addView(actionBtn("📺 Lives de TV", "Canais e vídeos ao vivo", v -> { tab = "midia"; midiaSub = "lives"; animateNext = true; render(); scroll.scrollTo(0, 0); }), Ui.margins(Ui.lp(0, -2, 1f), 4, 8, 0, 0));
        act.addView(r3);
        content.addView(act);

        // intervalo
        LinearLayout iv = Ui.card(this);
        iv.addView(Ui.text(this, "⏱️ Atualização automática", 15, Ui.TEXT, true));
        iv.addView(Ui.text(this, "Intervalo entre consultas ao TSE (o limite do TSE é 100 requisições/s por IP; o app usa concorrência baixa). Pausa quando o app vai para segundo plano.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        FlowLayout fl = new FlowLayout(this, 7);
        for (int s : INTERVALS) {
            boolean on = intervalMs == s * 1000L;
            TextView t = Ui.text(this, s + " s", 13, on ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(16), Ui.dp(9), Ui.dp(16), Ui.dp(9));
            t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { intervalMs = delayMs = s * 1000L; prefs.edit().putInt("interval", s).apply(); ui.removeCallbacks(poller); ui.postDelayed(poller, delayMs); render(); });
            fl.addView(t);
        }
        iv.addView(fl);
        content.addView(iv);

        // divulgação
        LinearLayout inv = Ui.card(this);
        inv.addView(Ui.text(this, "📲 Divulgue o app", 15, Ui.TEXT, true));
        inv.addView(Ui.text(this, "Convide amigos e familiares: o app é gratuito, funciona em Android 8 ou mais novo e mostra a apuração oficial do TSE.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        LinearLayout ir = Ui.row(this);
        ir.addView(actionBtn("📤 Convidar amigos", "Compartilha o link de download", v -> share(inviteText())), Ui.margins(Ui.lp(0, -2, 1f), 0, 4, 4, 0));
        ir.addView(actionBtn("🔗 Copiar link", "Link direto do APK", v -> copyLink()), Ui.margins(Ui.lp(0, -2, 1f), 4, 4, 0, 0));
        inv.addView(ir);
        content.addView(inv);

        // diagnóstico
        content.addView(diagCard());
        content.addView(Ui.text(this, "Resultados: arquivos JSON oficiais do Tribunal Superior Eleitoral, eleições 2026. Agregados regionais são calculados localmente pela soma dos votos oficiais. O aplicativo não projeta vencedores nem recomenda escolhas políticas.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 4, 2, 0));
    }

    private Spinner ufSpinner(String current, final boolean a) {
        Spinner s = new Spinner(this);
        List<String> items = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < UFS.length; i++) { items.add(UFS[i][0] + " · " + UFS[i][1]); if (UFS[i][0].equals(current)) pos = i; }
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override public View getView(int p, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(p, cv, parent);
                t.setText(UFS[p][0] + " ▾");
                t.setTextSize(15);
                t.setTypeface(Ui.MEDIUM, android.graphics.Typeface.BOLD);
                return t;
            }
        };
        s.setAdapter(ad);
        s.setSelection(pos);
        s.setBackground(Ui.fill(0xFF08182A, 11, Ui.LINE));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean init = true;
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (init) { init = false; return; }
                if (a) cmpA = UFS[i][0]; else cmpB = UFS[i][0];
                prefs.edit().putString("cmpA", cmpA).putString("cmpB", cmpB).apply();
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        return s;
    }

    private View cmpSide(String uf) {
        Model.Result d = snap().states.get(uf);
        LinearLayout s = Ui.col(this);
        s.setBackground(Ui.fill(0xFF08182A, 12, 0x99_1D3858));
        s.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        s.addView(Ui.text(this, uf + " · " + ufName(uf), 13, Ui.TEXT, true));
        s.addView(Ui.text(this, d == null ? "—" : pc(d.progress) + " apurado", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 4));
        if (d == null) s.addView(Ui.text(this, "Carregando…", 11, Ui.MUTED, false));
        else for (int i = 0; i < Math.min(3, d.cands.size()); i++) s.addView(miniRow(d.cands.get(i), i));
        return s;
    }

    private View actionBtn(String title, String sub, View.OnClickListener l) {
        LinearLayout b = Ui.col(this);
        b.setBackground(Ui.fill(0xFF0B1D32, 13, Ui.LINE));
        b.setPadding(Ui.dp(12), Ui.dp(11), Ui.dp(12), Ui.dp(11));
        b.addView(Ui.text(this, title, 13, Ui.TEXT, true));
        b.addView(Ui.text(this, sub, 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        b.setOnClickListener(l);
        return b;
    }

    private View diagCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "🛠️ Diagnóstico", 15, Ui.TEXT, true));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss", BR);
        String[][] rows = {
                {"Versão", VERSION + " (build " + localVersion() + ")" + (updateAvailable ? " • nova versão " + remoteVersion : "")},
                {"Rede", networkStatus()},
                {"Endpoint", Tse.BASE + "/" + Tse.CICLO + "/…"},
                {"Turno / códigos", turn + "º • " + codes[turn - 1][0] + " / " + codes[turn - 1][1] + (codesFromConfig ? " (ele-c.json)" : " (padrão)")},
                {"Última consulta", lastPoll == 0 ? "—" : f.format(new Date(lastPoll))},
                {"Último dado novo", snap().lastChange == 0 ? "—" : f.format(new Date(snap().lastChange))},
                {"Intervalo", (intervalMs / 1000) + " s (pausa em segundo plano)"},
                {"Último erro", lastError},
                {"Cache", snap().at == 0 ? "vazio" : "snapshot de " + f.format(new Date(snap().at))}};
        for (String[] r : rows) {
            c.addView(Ui.text(this, r[0], 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 9, 0, 0));
            TextView v = Ui.text(this, r[1], 12, Ui.TEXT, false);
            v.setPadding(0, Ui.dp(2), 0, 0);
            c.addView(v);
        }
        return c;
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

    // ---------------------------------------------------------------- modo TV
    private void renderTv() {
        content.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(18), Ui.dp(14));
        Model.Result r = snap().pres;
        LinearLayout head = Ui.row(this);
        LinearLayout t = Ui.col(this);
        TextView k = Ui.text(this, "ELEIÇÕES 2026 • MODO TV • " + turn + "º TURNO", 10, Ui.MINT, true);
        k.setLetterSpacing(0.15f);
        t.addView(k);
        t.addView(Ui.big(this, "Brasil decide", 26), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        head.addView(t, Ui.lp(0, -2, 1f));
        LinearLayout pr = Ui.col(this);
        pr.setGravity(Gravity.END);
        TextView pp = Ui.big(this, r == null ? "—" : pc(r.progress), 30);
        pp.setGravity(Gravity.END);
        pr.addView(pp);
        TextView ss = Ui.text(this, r == null ? "aguardando" : n(r.sections) + "/" + n(r.sectionsTotal) + " seções", 11, Ui.MUTED, false);
        ss.setGravity(Gravity.END);
        pr.addView(ss);
        head.addView(pr);
        content.addView(head);
        content.addView(new GradientBar(this, 10).value(r == null ? 0 : r.progress), Ui.margins(Ui.lp(-1, Ui.dp(10)), 0, 12, 0, 8));
        Map<String, Integer> fills = new HashMap<>();
        for (String[] u : UFS) { Model.Result d = snap().states.get(u[0]); Model.Cand l = d == null ? null : d.lead(); if (l != null) fills.put(u[0], Model.color(l.nome)); }
        mapView.setFills(fills);
        mapView.setSelected("");
        content.addView(mapView, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 4));
        if (r != null) for (int i = 0; i < Math.min(3, r.cands.size()); i++) {
            Model.Cand c = r.cands.get(i);
            LinearLayout row = Ui.row(this);
            View d = new View(this);
            d.setBackground(Ui.fill(Model.color(c.nome), 99, 0));
            row.addView(d, Ui.margins(Ui.lp(Ui.dp(14), Ui.dp(14)), 0, 0, 10, 0));
            row.addView(Ui.text(this, (i + 1) + "º  " + c.nome, 17, Ui.TEXT, true), Ui.lp(0, -2, 1f));
            row.addView(Ui.big(this, pc(c.pct), 22));
            row.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
            content.addView(row);
        }
        TextView hint = Ui.text(this, "Toque para sair do modo TV • " + (lastPoll == 0 ? "" : "última consulta " + hhmmss(lastPoll)), 10, Ui.MUTED, false);
        hint.setPadding(0, Ui.dp(10), 0, 0);
        content.addView(hint);
        content.setOnClickListener(v -> { setTv(false); render(); });
        scroll.setOnClickListener(v -> { setTv(false); render(); });
    }

    // ---------------------------------------------------------------- painel deslizante e detalhes
    private Dialog showSheet(View body) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = Ui.col(this);
        wrap.setBackground(Ui.gradient(0xFF0A1B30, 0xFF071321, 0, Ui.LINE, android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM));
        android.graphics.drawable.GradientDrawable g = Ui.gradient(0xFF0A1B30, 0xFF071321, 0, Ui.LINE, android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        g.setCornerRadii(new float[]{Ui.dp(24), Ui.dp(24), Ui.dp(24), Ui.dp(24), 0, 0, 0, 0});
        wrap.setBackground(g);
        wrap.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), insetB + Ui.dp(16));
        View handle = new View(this);
        handle.setBackground(Ui.fill(0xFF35506D, 99, 0));
        wrap.addView(handle, Ui.margins(Ui.lp(Ui.dp(46), Ui.dp(4)), 0, 0, 0, 12));
        ((LinearLayout.LayoutParams) handle.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        MaxHeightScrollView sv = new MaxHeightScrollView(this, 0.8f);
        sv.addView(body);
        wrap.addView(sv, Ui.lp(-1, -2));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.55f);
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
        sheetDialog = d;
        return d;
    }

    // ============================================================ voto no exterior
    private ExteriorUi ex() {
        if (exUi == null) exUi = new ExteriorUi(new ExteriorUi.Host() {
            @Override public Context ctx() { return MainActivity.this; }
            @Override public void showSheet(View v) { MainActivity.this.showSheet(v); }
            @Override public void rerender() { render(); }
            @Override public int turn() { return turn; }
            @Override public Model.Result nationalPres() { return snap().pres; }
            @Override public void event(String icon, String title, String detail) { addEvent(icon, title, detail); }
        });
        return exUi;
    }

    private void renderMundo() {
        ex().setSnapshotIfChanged(exSnaps[turn - 1]);
        content.addView(ex().build());
        loadExterior(false);
    }

    private void loadExterior(boolean force) {
        final int t = turn;
        if (exBusy || (!force && exSnaps[t - 1] != null && !exSnaps[t - 1].fromCache && System.currentTimeMillis() - exAt < 5 * 60 * 1000L)) return;
        exBusy = true;
        final String fed = codes[t - 1][0];
        final Exterior.Snapshot prev = exSnaps[t - 1];
        exPool.execute(() -> {
            Exterior.Snapshot s = null;
            try { s = Exterior.load(fed, t, prev); } catch (Throwable th) { lastError = "exterior: " + th; }
            final Exterior.Snapshot fs = s;
            if (fs != null && fs.zz != null && !fs.fromCache) Exterior.save(getFilesDir(), fs);
            ui.post(() -> {
                exBusy = false;
                exAt = System.currentTimeMillis();
                if (fs != null && (fs.zz != null || exSnaps[t - 1] == null)) exSnaps[t - 1] = fs;
                if (fs != null && !fs.error.isEmpty()) lastError = "exterior: " + fs.error;
                if (tab.equals("mapa") && mapaSub.equals("mundo") && turn == t && (!tv || resumed)) render();
            });
        });
    }

    // ============================================================ 2º turno: data e quem disputa
    private static final java.time.LocalDate ROUND2 = java.time.LocalDate.of(2026, 10, 25);

    private View roundStrip() {
        long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")), ROUND2);
        String when = days > 1 ? "faltam " + days + " dias" : days == 1 ? "é amanhã" : days == 0 ? "é hoje!" : "já aconteceu";
        LinearLayout c = Ui.row(this);
        c.setBackground(Ui.fill(0xFF0B1D32, 14, Ui.LINE));
        c.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        c.addView(Ui.text(this, "🗓️", 16, Ui.TEXT, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 9, 0));
        LinearLayout t = Ui.col(this);
        t.addView(Ui.text(this, "2º turno: domingo, 25/10/2026", 13, Ui.TEXT, true));
        t.addView(Ui.text(this, "Presidente e governadores onde ninguém passou de 50%", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        c.addView(t, Ui.lp(0, -2, 1f));
        c.addView(Ui.chip(this, when, days <= 0 ? Ui.GREEN : Ui.AMBER));
        c.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        return c;
    }

    private static boolean goesToRound2(Model.Result r) {
        if (r != null) for (Model.Cand k : r.cands) if (k.sit.toLowerCase(Locale.ROOT).contains("2º")) return true;
        return false;
    }

    private static boolean electedFirst(Model.Result r) {
        if (r != null) for (Model.Cand k : r.cands) if (k.sit.toLowerCase(Locale.ROOT).startsWith("eleito")) return true;
        return false;
    }

    /** Governadores do 1º turno de todas as UFs (para saber onde haverá 2º turno), sem depender do modo Governador do mapa. */
    private void loadRoundGov() {
        if (roundGovBusy || System.currentTimeMillis() - roundGovAt < 5 * 60 * 1000L) return;
        roundGovBusy = true;
        final Model.Result[] pres1 = {null};
        bg.execute(() -> {
            final Map<String, Model.Result> ok = new HashMap<>();
            final Map<String, Boolean> ab = new HashMap<>();
            try {
                String est = codes[0][1];
                List<Callable<Object[]>> ts = new ArrayList<>();
                for (String[] u : UFS) ts.add(task("g:" + u[0], Tse.url(est, u[0].toLowerCase(Locale.ROOT), 3), 300000, Tse.photoBase(est, u[0].toLowerCase(Locale.ROOT))));
                for (Object[] o : runAll(ts)) {
                    Tse.Fetch f = (Tse.Fetch) o[1];
                    String id = ((String) o[0]).substring(2);
                    if (f.error != null) continue;
                    if (f.result != null) ok.put(id, f.result); else if (f.absent) ab.put(id, true);
                }
                if (snaps[0].pres == null) {
                    List<Object[]> br = runAll(java.util.Collections.singletonList(task("br", Tse.url(codes[0][0], "br", 1), 60000, Tse.photoBase(codes[0][0], "br"))));
                    if (!br.isEmpty() && ((Tse.Fetch) br.get(0)[1]).result != null) pres1[0] = ((Tse.Fetch) br.get(0)[1]).result;
                }
            } catch (Throwable th) { lastError = "2º turno: " + th; }
            ui.post(() -> {
                roundGovBusy = false;
                roundGovAt = System.currentTimeMillis();
                if (pres1[0] != null && snaps[0].pres == null) snaps[0].pres = pres1[0];
                snaps[0].gov.putAll(ok);
                snaps[0].govAbsent.putAll(ab);
                if (!ok.isEmpty()) saveCache(1);
                if (!tv || resumed) render();
            });
        });
    }

    private View roundCard() {
        loadRoundGov();
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Quem disputa o 2º turno", 15, Ui.TEXT, true));
        c.addView(Ui.text(this, "Domingo, 25/10/2026 • dados oficiais do 1º turno (TSE)", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 10));
        // presidente
        Model.Result p1 = snaps[0].pres;
        List<Model.Cand> pair = new ArrayList<>();
        if (p1 != null) for (Model.Cand k : p1.cands) if (k.sit.toLowerCase(Locale.ROOT).contains("2º")) pair.add(k);
        c.addView(Ui.text(this, "PRESIDENTE", 10, Ui.MUTED, true));
        if (pair.size() >= 2) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
            row.addView(side(pair.get(0).nome + " (" + pair.get(0).partido + ")", pc(pair.get(0).pct), Model.color(pair.get(0).nome), Gravity.START), Ui.lp(0, -2, 1f));
            row.addView(Ui.text(this, "×", 18, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 8, 0, 8, 0));
            row.addView(side(pair.get(1).nome + " (" + pair.get(1).partido + ")", pc(pair.get(1).pct), Model.color(pair.get(1).nome), Gravity.END), Ui.lp(0, -2, 1f));
            c.addView(row);
        } else if (p1 != null && electedFirst(p1)) c.addView(Ui.text(this, "Definido no 1º turno.", 12, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 8));
        else c.addView(Ui.text(this, p1 == null ? "Carregando…" : "Aguardando a totalização do 1º turno.", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 8));
        // governadores
        List<String> r2 = new ArrayList<>(), won = new ArrayList<>();
        int pending = 0, loaded = 0;
        for (String[] u : UFS) {
            Model.Result g = snaps[0].gov.get(u[0]);
            if (g == null) continue;
            loaded++;
            if (goesToRound2(g)) r2.add(u[0]); else if (electedFirst(g)) won.add(u[0]); else pending++;
        }
        View sep = new View(this);
        sep.setBackgroundColor(0xCC1D3858);
        c.addView(sep, Ui.margins(Ui.lp(-1, 1), 0, 4, 0, 8));
        c.addView(Ui.text(this, "GOVERNADORES", 10, Ui.MUTED, true));
        if (loaded == 0) c.addView(Ui.text(this, roundGovBusy ? "Carregando os 27 estados…" : "Sem dados de governador ainda.", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 4));
        else {
            c.addView(Ui.text(this, r2.size() + (r2.size() == 1 ? " estado vai" : " estados vão") + " a 2º turno • " + won.size() + " eleitos no 1º turno" + (pending > 0 ? " • " + pending + " em apuração" : ""), 13, Ui.TEXT, true), Ui.margins(Ui.lp(-2, -2), 0, 5, 0, 8));
            for (final String uf : r2) {
                Model.Result g = snaps[0].gov.get(uf);
                List<Model.Cand> two = new ArrayList<>();
                for (Model.Cand k : g.cands) if (k.sit.toLowerCase(Locale.ROOT).contains("2º")) two.add(k);
                LinearLayout row = Ui.col(this);
                row.setBackground(Ui.fill(0xFF08182A, 12, Ui.LINE));
                row.setPadding(Ui.dp(11), Ui.dp(9), Ui.dp(11), Ui.dp(9));
                row.addView(Ui.text(this, ufName(uf) + " (" + uf + ")", 13, Ui.TEXT, true));
                String line = "";
                for (int i = 0; i < Math.min(2, two.size()); i++) line += (i > 0 ? "  ×  " : "") + two.get(i).nome + " (" + two.get(i).partido + ") " + pc(two.get(i).pct);
                TextView tl = Ui.text(this, line, 11, Ui.SOFT, false);
                tl.setPadding(0, Ui.dp(3), 0, 0);
                row.addView(tl);
                row.setOnClickListener(v -> showSheet(roundUfView(uf)));
                c.addView(row, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 7));
            }
            if (!won.isEmpty()) {
                TextView tg = Ui.text(this, roundShowElected ? "▾ Governadores eleitos no 1º turno" : "▸ Ver governadores eleitos no 1º turno", 12, Ui.MINT, true);
                tg.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
                tg.setOnClickListener(v -> { roundShowElected = !roundShowElected; render(); });
                c.addView(tg);
                if (roundShowElected) {
                    FlowLayout fl = new FlowLayout(this, 6);
                    for (String uf : won) {
                        Model.Cand w = null;
                        for (Model.Cand k : snaps[0].gov.get(uf).cands) if (k.sit.toLowerCase(Locale.ROOT).startsWith("eleito")) { w = k; break; }
                        fl.addView(legendPill(w == null ? Ui.MUTED : Model.color(w.nome), uf + " • " + (w == null ? "—" : w.nome), ""));
                    }
                    c.addView(fl);
                }
            }
        }
        c.addView(Ui.text(this, "Senado, Câmara e Assembleias Legislativas não têm 2º turno: a eleição é decidida no turno único.", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 0));
        return c;
    }

    private View roundUfView(String uf) {
        LinearLayout c = Ui.col(this);
        Model.Result g = snaps[0].gov.get(uf);
        c.addView(Ui.text(this, "Governador • " + ufName(uf), 18, Ui.TEXT, true));
        c.addView(Ui.text(this, "1º turno • candidatos que vão ao 2º turno (25/10/2026)", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        if (g == null) return c;
        ctxUe = uf;
        ctxRole = "candidato a governador";
        int i = 0;
        for (Model.Cand k : g.cands) if (k.sit.toLowerCase(Locale.ROOT).contains("2º")) c.addView(candRow(g, k, i++, true));
        return c;
    }

    // ============================================================ ficha do candidato (dados oficiais + manchetes de terceiros)
    private static String ageOf(String born) {
        try {
            java.time.LocalDate b = java.time.LocalDate.parse(born, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return java.time.temporal.ChronoUnit.YEARS.between(b, java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))) + " anos";
        } catch (Throwable t) { return ""; }
    }

    private void kv(LinearLayout box, String k, String v) {
        if (v == null || v.isEmpty()) return;
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        TextView kk = Ui.text(this, k, 11, Ui.MUTED, false);
        row.addView(kk, Ui.lp(Ui.dp(92), -2));
        row.addView(Ui.text(this, v, 12, Ui.TEXT, false), Ui.lp(0, -2, 1f));
        box.addView(row);
    }

    private TextView linkRow(String text, String sub, final String url) {
        TextView t = Ui.text(this, text + (sub.isEmpty() ? "" : "\n" + sub), 12, Ui.TEXT, false);
        t.setPadding(Ui.dp(2), Ui.dp(7), Ui.dp(2), Ui.dp(7));
        t.setOnClickListener(v -> openUrl(url));
        return t;
    }

    private void fillArticles(LinearLayout box, List<News.Article> list, String emptyMsg) {
        box.removeAllViews();
        if (list == null) { box.addView(Ui.text(this, "Não foi possível buscar agora. Tente de novo mais tarde.", 11, Ui.MUTED, false)); return; }
        if (list.isEmpty()) { box.addView(Ui.text(this, emptyMsg, 11, Ui.MUTED, false)); return; }
        SimpleDateFormat df = new SimpleDateFormat("dd/MM", BR);
        for (News.Article a : list) box.addView(linkRow("• " + a.title, a.source + (a.ts > 0 ? " • " + df.format(new Date(a.ts)) : ""), a.url));
    }

    private void showFicha(final Model.Cand c, final Model.Result r, final String ue, final String role) {
        LinearLayout v = Ui.col(this);
        int col = Model.color(c.nome);
        LinearLayout head = Ui.row(this);
        AvatarView av = new AvatarView(this, 58).set(c.nome, col);
        if (r != null && !r.photoBase.isEmpty() && !c.sq.isEmpty()) Photos.load(r.photoBase + c.sq + ".jpeg", av);
        head.addView(av, Ui.margins(Ui.lp(Ui.dp(58), Ui.dp(58)), 0, 0, 12, 0));
        LinearLayout hn = Ui.col(this);
        hn.addView(Ui.text(this, c.nome, 19, Ui.TEXT, true));
        hn.addView(Ui.text(this, c.partido + (c.numero.isEmpty() ? "" : " · nº " + c.numero) + " • " + role.substring(0, 1).toUpperCase(Locale.ROOT) + role.substring(1), 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        head.addView(hn, Ui.lp(0, -2, 1f));
        v.addView(head);

        LinearLayout reg = Ui.card(this);
        reg.addView(Ui.text(this, "REGISTRO NO TSE", 10, Ui.MUTED, true));
        kv(reg, "Nome completo", c.full);
        kv(reg, "Idade", ageOf(c.born));
        kv(reg, "Situação", c.sit);
        kv(reg, "Vice", c.vice);
        kv(reg, "Coligação", c.coal);
        kv(reg, "Votos", n(c.votos) + " (" + pc(c.pct) + " dos válidos, " + turn + "º turno)");
        v.addView(reg, Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));

        LinearLayout off = Ui.card(this);
        off.addView(Ui.text(this, "PATRIMÔNIO, PLANO DE GOVERNO E GASTOS DE CAMPANHA", 10, Ui.MUTED, true));
        off.addView(Ui.text(this, "Ficha oficial do TSE (DivulgaCandContas): bens declarados, propostas e prestação de contas de campanha.", 11, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 6));
        off.addView(actionBtn("Abrir ficha oficial no TSE ›", "divulgacandcontas.tse.jus.br", x -> openUrl(Ficha.tseLink(c, ue))));
        v.addView(off, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));

        final LinearLayout mand = Ui.card(this);
        mand.addView(Ui.text(this, "MANDATO ATUAL (DADOS ABERTOS)", 10, Ui.MUTED, true));
        mand.addView(Ui.text(this, "Buscando na Câmara e no Senado…", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        v.addView(mand, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));

        final LinearLayout newsBox = Ui.card(this), chkBox = Ui.card(this);
        newsBox.addView(Ui.text(this, "MANCHETES RECENTES", 10, Ui.MUTED, true));
        final LinearLayout nl = Ui.col(this), cl = Ui.col(this);
        nl.addView(Ui.text(this, "Buscando…", 11, Ui.MUTED, false));
        newsBox.addView(nl, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        chkBox.addView(Ui.text(this, "CHECAGENS DE FATOS (LUPA, AOS FATOS, ESTADÃO VERIFICA, G1, BOATOS)", 10, Ui.MUTED, true));
        cl.addView(Ui.text(this, "Buscando…", 11, Ui.MUTED, false));
        chkBox.addView(cl, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        v.addView(newsBox, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        v.addView(chkBox, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        v.addView(Ui.text(this, "Manchetes e checagens são resultados automáticos de busca em veículos de terceiros, com fonte e link; podem incluir homônimos. O app não opina, não verifica e não escreve conteúdo sobre candidatos. Dados do registro: TSE; mandato: Câmara dos Deputados e Senado Federal.", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 12, 2, 4));
        final Dialog d = showSheet(v);

        newsPool.execute(() -> {
            final Ficha.Mandate m = Ficha.mandate(c);
            ui.post(() -> {
                if (!d.isShowing()) return;
                mand.removeAllViews();
                mand.addView(Ui.text(this, "MANDATO ATUAL (DADOS ABERTOS)", 10, Ui.MUTED, true));
                if (m == null) {
                    mand.addView(Ui.text(this, "Não consta como deputado federal nem senador em exercício (ou o nome civil não bateu com o do TSE).", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
                    return;
                }
                kv(mand, "Casa", m.house + (m.uf.isEmpty() ? "" : " • " + m.party + "/" + m.uf));
                if (m.proposals >= 0) kv(mand, "Proposições", n(m.proposals) + " apresentadas desde fev/2023 (projetos, requerimentos etc.)");
                if (m.spent >= 0) kv(mand, "Cota 2026", "R$ " + String.format(BR, "%,.2f", m.spent) + " em notas apresentadas (cota parlamentar)");
                if (!m.url.isEmpty()) mand.addView(linkRow("Ver página oficial ›", "", m.url));
            });
        });
        newsPool.execute(() -> {
            final List<News.Article> a = Ficha.headlines(c, role, false);
            ui.post(() -> { if (d.isShowing()) fillArticles(nl, a, "Nenhuma manchete recente encontrada."); });
        });
        newsPool.execute(() -> {
            final List<News.Article> a = Ficha.headlines(c, role, true);
            ui.post(() -> { if (d.isShowing()) fillArticles(cl, a, "Nenhuma checagem encontrada para este nome."); });
        });
    }

    private View detailView(String uf) {
        boolean gov = govMode && tab.equals("mapa");
        Model.Result r = (gov ? snap().gov : snap().states).get(uf);
        LinearLayout c = Ui.col(this);
        c.addView(Ui.text(this, ufName(uf) + " (" + uf + ")", 20, Ui.TEXT, true));
        c.addView(Ui.text(this, cargoSub(gov ? "Governador" : "Presidente"), 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 10));
        if (r == null) {
            c.addView(Ui.text(this, gov && Boolean.TRUE.equals(snap().govAbsent.get(uf)) ? "Sem disputa neste cargo/UF" + (turn == 2 ? " no 2º turno." : ".") : "Dados ainda não carregados.", 13, Ui.MUTED, false));
            return c;
        }
        LinearLayout pr = Ui.row(this);
        pr.addView(Ui.text(this, "Apuração", 12, Ui.MUTED, false), Ui.lp(0, -2, 1f));
        pr.addView(Ui.big(this, pc(r.progress), 18));
        c.addView(pr);
        c.addView(new GradientBar(this, 10).value(r.progress), Ui.margins(Ui.lp(-1, Ui.dp(10)), 0, 7, 0, 5));
        c.addView(Ui.text(this, n(r.sections) + "/" + n(r.sectionsTotal) + " seções totalizadas" + (r.fin ? " • totalização final" : ""), 10, Ui.MUTED, false));
        Model.Cand a = r.cands.size() > 0 ? r.cands.get(0) : null, b = r.cands.size() > 1 ? r.cands.get(1) : null;
        LinearLayout stats = Ui.row(this);
        stats.setBaselineAligned(false);
        LinearLayout s1 = Ui.col(this);
        s1.setBackground(Ui.fill(0xFF08182A, 12, 0x99_1D3858));
        s1.setPadding(Ui.dp(10), Ui.dp(9), Ui.dp(10), Ui.dp(9));
        s1.addView(Ui.label(this, "Liderando"));
        TextView ln = Ui.text(this, a == null || a.votos == 0 ? "—" : a.nome, 14, a == null ? Ui.TEXT : Model.color(a.nome), true);
        ln.setPadding(0, Ui.dp(4), 0, 0);
        s1.addView(ln);
        LinearLayout s2 = Ui.col(this);
        s2.setBackground(Ui.fill(0xFF08182A, 12, 0x99_1D3858));
        s2.setPadding(Ui.dp(10), Ui.dp(9), Ui.dp(10), Ui.dp(9));
        s2.addView(Ui.label(this, "Diferença 1º × 2º"));
        TextView df = Ui.text(this, a != null && b != null ? n(a.votos - b.votos) + " votos" : "—", 14, Ui.TEXT, true);
        df.setPadding(0, Ui.dp(4), 0, 0);
        s2.addView(df);
        if (a != null && b != null) s2.addView(Ui.text(this, String.format(BR, "%.2f p.p.", a.pct - b.pct), 10, Ui.MUTED, false));
        stats.addView(s1, Ui.margins(Ui.lp(0, -1, 1f), 0, 12, 4, 6));
        stats.addView(s2, Ui.margins(Ui.lp(0, -1, 1f), 4, 12, 0, 6));
        c.addView(stats);
        for (int i = 0; i < Math.min(3, r.cands.size()); i++) c.addView(candRow(r, r.cands.get(i), i, true));
        c.addView(Ui.text(this, "Arquivo TSE: " + ((r.date + " • " + r.time).replaceAll("^ • | • $", "").trim()), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 0));
        if (!gov) {
            final String fuf = uf;
            TextView img = Ui.text(this, "📸 Criar imagem para status deste estado", 13, Ui.INK, true);
            img.setGravity(Gravity.CENTER);
            img.setBackground(Ui.accent(13));
            img.setOnClickListener(v -> { studioType = StatusCard.UF; studioUf = fuf; showStatusStudio(); });
            c.addView(img, Ui.margins(Ui.lp(-1, Ui.dp(46)), 0, 14, 0, 0));
        }
        return c;
    }

    // ---------------------------------------------------------------- lives e vídeos (YouTube)
    private boolean needLives() {
        Long at = livesAt.get(livesFilter);
        return at == null || System.currentTimeMillis() - at > 3 * 60 * 1000;
    }

    private void openUrl(String url) {
        try {
            Uri u = Uri.parse(url);
            if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Throwable t) { Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show(); }
    }

    private void loadLives(boolean force) {
        if (livesBusy || (!force && !needLives())) return;
        livesBusy = true;
        final String f = livesFilter;
        String[] def = LIVE_FILTERS[0];
        for (String[] x : LIVE_FILTERS) if (x[0].equals(f)) def = x;
        final String q = def[2], sp = def[3];
        render();
        bg.execute(() -> {
            List<Youtube.Video> list = null;
            String err = "";
            try { list = Youtube.search(q, sp); } catch (Throwable t) { err = String.valueOf(t.getMessage()); lastError = "youtube: " + t; }
            final List<Youtube.Video> fl = list;
            final String fe = err;
            ui.post(() -> {
                livesBusy = false;
                livesErr = fe;
                if (fl != null) { livesCache.put(f, fl); livesAt.put(f, System.currentTimeMillis()); }
                else livesAt.put(f, System.currentTimeMillis() - 2 * 60 * 1000); // tenta de novo em ~1 min
                render();
            });
        });
    }

    private void renderLives() {
        LinearLayout head = Ui.row(this);
        head.addView(sectionHead("Lives e vídeos da eleição", "Canais de TV ao vivo e vídeos sobre a apuração. Toque para assistir no YouTube."), Ui.lp(0, -2, 1f));
        TextView rf = Ui.text(this, livesBusy ? "…" : "↻", 20, Ui.TEXT, true);
        rf.setGravity(Gravity.CENTER);
        rf.setBackground(Ui.fill(0xFF0E2138, 12, Ui.LINE));
        rf.setOnClickListener(v -> loadLives(true));
        head.addView(rf, Ui.lp(Ui.dp(44), Ui.dp(40)));
        content.addView(head);

        // canais de TV (atalhos que sempre funcionam: abrem a transmissão ao vivo do canal)
        content.addView(Ui.text(this, "📺  CANAIS DE TV AO VIVO", 11, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 2, 0, 0, 8));
        HorizontalScrollView chs = new HorizontalScrollView(this);
        chs.setHorizontalScrollBarEnabled(false);
        LinearLayout chRow = Ui.row(this);
        for (String ch : CHANNELS) chRow.addView(channelButton(ch), Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        chs.addView(chRow);
        content.addView(chs, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));

        content.addView(Ui.text(this, "🎬  VÍDEOS E TRANSMISSÕES", 11, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 2, 8, 0, 8));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(this);
        for (String[] f : LIVE_FILTERS) {
            boolean on = livesFilter.equals(f[0]);
            TextView t = Ui.text(this, f[1], 12, on ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(13), Ui.dp(9), Ui.dp(13), Ui.dp(9));
            t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { livesFilter = f[0]; render(); });
            chips.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 0, 7, 0));
        }
        hs.addView(chips);
        content.addView(hs, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));

        List<Youtube.Video> list = livesCache.get(livesFilter);
        if (list == null && livesBusy) {
            for (int i = 0; i < 3; i++) {
                LinearLayout c = Ui.card(this);
                c.addView(new ShimmerView(this, 170, 16));
                c.addView(new ShimmerView(this, 16, 6));
                c.addView(new ShimmerView(this, 12, 6));
                content.addView(c);
            }
        } else if (list == null || list.isEmpty()) {
            String[] def = LIVE_FILTERS[0];
            for (String[] x : LIVE_FILTERS) if (x[0].equals(livesFilter)) def = x;
            final String q = def[2], sp = def[3];
            LinearLayout e = Ui.card(this);
            e.addView(Ui.text(this, list == null ? "Não foi possível listar os vídeos agora" : "Nenhum vídeo encontrado agora", 14, Ui.TEXT, true));
            e.addView(Ui.text(this, (list == null ? "A lista depende da página pública do YouTube e pode falhar sem internet ou se o formato mudar" + (livesErr.isEmpty() ? "" : " (" + livesErr + ")") + ". " : "") + "Você ainda pode abrir a busca direto no YouTube.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 10));
            TextView open = Ui.text(this, "Abrir busca no YouTube ↗", 13, Ui.INK, true);
            open.setGravity(Gravity.CENTER);
            open.setBackground(Ui.accent(12));
            open.setOnClickListener(v -> openUrl(Youtube.searchUrl(q, sp)));
            e.addView(open, Ui.lp(-1, Ui.dp(44)));
            content.addView(e);
        } else {
            for (Youtube.Video v : list) content.addView(videoCard(v));
            content.addView(Ui.text(this, "Vídeos e transmissões pertencem aos respectivos canais. O app apenas lista resultados públicos de busca do YouTube e abre o vídeo no app/navegador; não opina nem garante o conteúdo.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 4, 2, 0));
        }
    }

    private View channelButton(final String name) {
        LinearLayout b = Ui.col(this);
        b.setBackground(Ui.cardBg(16));
        b.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        b.addView(Ui.text(this, "📺", 20, Ui.TEXT, false));
        b.setMinimumWidth(Ui.dp(140));
        TextView nm = Ui.text(this, name, 14, Ui.TEXT, true);
        nm.setMaxLines(2);
        nm.setPadding(0, Ui.dp(8), 0, 0);
        b.addView(nm);
        TextView live = Ui.text(this, "● ao vivo ↗", 10, 0xFFFF6B6B, true);
        b.addView(live, Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        b.setOnClickListener(v -> openUrl(Youtube.searchUrl(name + " ao vivo", Youtube.LIVE)));
        return b;
    }

    private View videoCard(final Youtube.Video v) {
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(12));
        c.setLayoutParams(Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        FrameLayout fl = new FrameLayout(this);
        ThumbView th = new ThumbView(this, 14);
        fl.addView(th, new FrameLayout.LayoutParams(-1, -2));
        Photos.load(v.thumb(), th, 1);
        if (v.live) {
            TextView b = Ui.text(this, "● AO VIVO", 10, Color.WHITE, true);
            b.setPadding(Ui.dp(8), Ui.dp(3), Ui.dp(8), Ui.dp(3));
            b.setBackground(Ui.fill(0xFFE5243B, 7, 0));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.setMargins(Ui.dp(8), Ui.dp(8), 0, 0);
            fl.addView(b, lp);
        } else if (!v.length.isEmpty()) {
            TextView b = Ui.text(this, v.length, 10, Color.WHITE, true);
            b.setPadding(Ui.dp(6), Ui.dp(2), Ui.dp(6), Ui.dp(3));
            b.setBackground(Ui.fill(0xCC020914, 6, 0));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
            lp.setMargins(0, 0, Ui.dp(8), Ui.dp(8));
            fl.addView(b, lp);
        }
        c.addView(fl, Ui.lp(-1, -2));
        TextView title = Ui.text(this, v.title, 14, Ui.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(title, Ui.margins(Ui.lp(-2, -2), 2, 10, 2, 0));
        String meta = v.channel + (v.views.isEmpty() ? "" : " • " + v.views) + (v.published.isEmpty() ? "" : " • " + v.published);
        TextView m = Ui.text(this, meta, 11, Ui.MUTED, false);
        m.setMaxLines(2);
        m.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(m, Ui.margins(Ui.lp(-2, -2), 2, 4, 2, 0));
        c.setOnClickListener(x -> openUrl(v.url()));
        return c;
    }

    // ---------------------------------------------------------------- estúdio de imagens para status
    private StatusCard.Data statusData() {
        StatusCard.Data d = new StatusCard.Data();
        d.type = studioType;
        d.format = studioFormat;
        d.turn = turn;
        d.pres = snap().pres;
        d.states = new HashMap<>(snap().states);
        d.ufCode = studioUf;
        d.ufName = ufName(studioUf);
        d.ufRes = snap().states.get(studioUf);
        d.df = new HashMap<>();
        for (Map.Entry<String, Model.Result> e : snap().df.entrySet()) if (e.getKey().startsWith(studioUf + "|")) d.df.put(e.getKey().substring(studioUf.length() + 1), e.getValue());
        d.dfCargo = studioCargo;
        d.at = lastPoll == 0 ? System.currentTimeMillis() : lastPoll;
        return d;
    }

    /** Gera a imagem em segundo plano (baixa antes as fotos do top 3 para saírem no card). */
    private void generateStatus(final java.util.function.Consumer<Bitmap> done, final StatusCard.Data d) {
        bg.execute(() -> {
            Bitmap b = null;
            try {
                Model.Result r = StatusCard.mainResult(d);
                if (r != null) for (Model.Cand cd : StatusCard.shown(d))
                    if (!r.photoBase.isEmpty() && !cd.sq.isEmpty()) Photos.fetch(r.photoBase + cd.sq + ".jpeg", 2);
                b = StatusCard.render(d);
            } catch (Throwable t) { lastError = "imagem: " + t; }
            final Bitmap fb = b;
            ui.post(() -> done.accept(fb));
        });
    }

    private void showStatusStudio() {
        if (sheetDialog != null) sheetDialog.dismiss();
        studioHolder = Ui.col(this);
        studioPreview = null;
        studioBmp = null;
        buildStudio();
        sheetDialog = showSheet(studioHolder);
        regenStudio();
    }

    private void regenStudio() {
        studioBusy = true;
        buildStudio();
        if (studioType == StatusCard.DFC) loadLocal(studioUf, false, this::generateStudioNow); else generateStudioNow();
    }

    private void generateStudioNow() {
        generateStatus(b -> { studioBmp = b; studioBusy = false; buildStudio(); }, statusData());
    }

    private void buildStudio() {
        if (studioHolder == null) return;
        studioHolder.removeAllViews();
        studioHolder.addView(Ui.text(this, "📸 Imagem para status", 20, Ui.TEXT, true));
        studioHolder.addView(Ui.text(this, "Card pronto com resultado oficial, fonte e horário — para WhatsApp, Instagram e redes.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 12));
        FlowLayout types = new FlowLayout(this, 7);
        String[] tn = {"Placar", "Mapa", "Estado", "Cargos"};
        for (int i = 0; i < 4; i++) {
            final int ti = i;
            types.addView(optChip(tn[i], studioType == i, v -> { studioType = ti; regenStudio(); }));
        }
        studioHolder.addView(types, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        FlowLayout fmts = new FlowLayout(this, 7);
        fmts.addView(optChip("Story 9:16", studioFormat == StatusCard.STORY, v -> { studioFormat = StatusCard.STORY; regenStudio(); }));
        fmts.addView(optChip("Post 1:1", studioFormat == StatusCard.POST, v -> { studioFormat = StatusCard.POST; regenStudio(); }));
        studioHolder.addView(fmts, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        if (studioType == StatusCard.DFC) {
            studioHolder.addView(Ui.text(this, "ESTADO", 10, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 2, 2, 0, 6));
            studioHolder.addView(ufSpinnerFor(studioUf, uf -> { studioUf = uf; regenStudio(); }), Ui.margins(Ui.lp(Ui.dp(130), Ui.dp(42)), 0, 0, 0, 8));
            studioHolder.addView(Ui.text(this, "CARGO", 10, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 2, 2, 0, 6));
            FlowLayout cg = new FlowLayout(this, 7);
            for (int i = 0; i < StatusCard.DF_LABELS.length; i++) {
                final int ci = i;
                cg.addView(optChip(StatusCard.DF_LABELS[i], studioCargo == i, v -> { studioCargo = ci; regenStudio(); }));
            }
            studioHolder.addView(cg, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        }
        if (studioType == StatusCard.UF) {
            Spinner sp = ufSpinnerFor(studioUf, uf -> { studioUf = uf; regenStudio(); });
            studioHolder.addView(sp, Ui.margins(Ui.lp(Ui.dp(130), Ui.dp(42)), 0, 0, 0, 8));
        }
        FrameLayout pf = new FrameLayout(this);
        pf.setBackground(Ui.fill(0xFF050C17, 16, Ui.LINE));
        pf.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        int ph = (int) (getResources().getDisplayMetrics().heightPixels * 0.33f);
        if (studioBmp != null && !studioBusy) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(studioBmp);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMaxHeight(ph);
            pf.addView(iv, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            studioPreview = iv;
        } else {
            pf.addView(new ShimmerView(this, studioFormat == StatusCard.STORY ? 300 : 220, 14), new FrameLayout.LayoutParams(-1, -2));
            TextView w = Ui.text(this, "Gerando imagem…", 12, Ui.MUTED, true);
            pf.addView(w, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        }
        studioHolder.addView(pf, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 12));
        LinearLayout btns = Ui.row(this);
        TextView share = Ui.text(this, "Compartilhar", 14, Ui.INK, true);
        share.setGravity(Gravity.CENTER);
        share.setBackground(Ui.accent(13));
        share.setOnClickListener(v -> shareStatusImage());
        TextView save = Ui.text(this, "Salvar na galeria", 14, Ui.TEXT, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(Ui.fill(0xFF0E2138, 13, Ui.LINE));
        save.setOnClickListener(v -> saveStatusImage());
        btns.addView(share, Ui.margins(Ui.lp(0, Ui.dp(48), 1f), 0, 0, 4, 0));
        btns.addView(save, Ui.margins(Ui.lp(0, Ui.dp(48), 1f), 4, 0, 0, 0));
        studioHolder.addView(btns);
        studioHolder.addView(Ui.text(this, "As imagens usam apenas dados oficiais do TSE e não contêm projeção nem opinião.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 10, 2, 0));
    }

    private View optChip(String label, boolean on, View.OnClickListener l) {
        TextView t = Ui.text(this, label, 13, on ? Ui.INK : Ui.MUTED, true);
        t.setPadding(Ui.dp(15), Ui.dp(9), Ui.dp(15), Ui.dp(9));
        t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
        t.setOnClickListener(l);
        return t;
    }

    private Spinner ufSpinnerFor(String current, final java.util.function.Consumer<String> cb) {
        Spinner s = new Spinner(this);
        List<String> items = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < UFS.length; i++) { items.add(UFS[i][0] + " · " + UFS[i][1]); if (UFS[i][0].equals(current)) pos = i; }
        s.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override public View getView(int p, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(p, cv, parent);
                t.setText(UFS[p][0] + " ▾");
                t.setTextSize(15);
                t.setTypeface(Ui.MEDIUM, android.graphics.Typeface.BOLD);
                return t;
            }
        });
        s.setSelection(pos);
        s.setBackground(Ui.fill(0xFF08182A, 11, Ui.LINE));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean init = true;
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) { if (init) { init = false; return; } cb.accept(UFS[i][0]); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        return s;
    }

    private String statusCaption() {
        Model.Result r = studioType == StatusCard.UF ? snap().states.get(studioUf) : studioType == StatusCard.DFC ? snap().df.get(studioUf + "|" + StatusCard.DF_KEYS[studioCargo]) : snap().pres;
        String where = studioType == StatusCard.UF ? ufName(studioUf) : studioType == StatusCard.DFC ? studioUf + " • " + StatusCard.DF_LABELS[studioCargo] : "Brasil";
        StringBuilder b = new StringBuilder("Brasil Decide • Eleições 2026 • " + turn + "º turno • " + where + "\n");
        if (r != null) {
            for (int i = 0; i < Math.min(studioType == StatusCard.DFC && studioCargo >= 2 ? (studioCargo == 2 ? 2 : 4) : 3, r.cands.size()); i++) b.append(i + 1).append("º ").append(r.cands.get(i).nome).append(" ").append(pc(r.cands.get(i).pct)).append("\n");
            b.append("Apuração: ").append(pc(r.progress)).append(" das seções\n");
        }
        return b.append("Fonte: TSE (dados oficiais, sem projeção)\n📲 Baixe o app grátis: ").append(PAGE_URL).toString();
    }

    private java.io.File writeShareFile(Bitmap b) throws Exception {
        java.io.File dir = new java.io.File(getCacheDir(), "share");
        dir.mkdirs();
        java.io.File[] old = dir.listFiles();
        if (old != null && old.length > 12) for (java.io.File f : old) if (f.getName().startsWith("central_status_")) f.delete();
        java.io.File f = new java.io.File(dir, "central_status_" + System.currentTimeMillis() + ".png");
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        o.close();
        return f;
    }

    private void shareStatusImage() {
        if (studioBmp == null) { Toast.makeText(this, "A imagem ainda está sendo gerada", Toast.LENGTH_SHORT).show(); return; }
        try {
            java.io.File f = writeShareFile(studioBmp);
            Uri uri = new Uri.Builder().scheme("content").authority(StatusProvider.AUTHORITY).appendPath(f.getName()).build();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_TEXT, statusCaption());
            i.setClipData(ClipData.newRawUri("imagem", uri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Compartilhar imagem"));
        } catch (Throwable t) { lastError = "compartilhar: " + t; Toast.makeText(this, "Não foi possível compartilhar a imagem", Toast.LENGTH_SHORT).show(); }
    }

    private void saveStatusImage() {
        if (studioBmp == null) { Toast.makeText(this, "A imagem ainda está sendo gerada", Toast.LENGTH_SHORT).show(); return; }
        if (Build.VERSION.SDK_INT < 29) { Toast.makeText(this, "Neste Android use Compartilhar → Salvar", Toast.LENGTH_LONG).show(); return; }
        try {
            ContentValues cv = new ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Eleicoes2026_" + new SimpleDateFormat("yyyyMMdd_HHmmss", BR).format(new Date()) + ".png");
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CentralEleicoes");
            cv.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
            Uri u = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new IllegalStateException("sem destino");
            java.io.OutputStream o = getContentResolver().openOutputStream(u);
            studioBmp.compress(Bitmap.CompressFormat.PNG, 100, o);
            o.close();
            ContentValues done = new ContentValues();
            done.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(u, done, null, null);
            Toast.makeText(this, "Salvo em Imagens/CentralEleicoes", Toast.LENGTH_LONG).show();
        } catch (Throwable t) { lastError = "salvar: " + t; Toast.makeText(this, "Não foi possível salvar a imagem", Toast.LENGTH_SHORT).show(); }
    }

    /** Automação (teste no emulador): grava as 6 variantes em cache/share/test-*.png para conferência. */
    private void genAllStatusForTest() {
        for (int k = 0; k < 16; k++) {
            final int ty = k < 6 ? k / 2 : 3, fm = k < 6 ? k % 2 : (k - 6) % 2, cg = k < 6 ? 0 : 1 + (k - 6) / 2;
            final StatusCard.Data d = statusData();
            d.type = ty; d.format = fm; d.dfCargo = ty == 3 ? cg - 1 : 0;
            final int fty = ty == 3 ? 10 + d.dfCargo : ty, ffm = fm;
            generateStatus(b -> {
                if (b == null) return;
                try {
                    java.io.File dir = new java.io.File(getCacheDir(), "share");
                    dir.mkdirs();
                    java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, "test-" + fty + "-" + ffm + ".png"));
                    b.compress(Bitmap.CompressFormat.PNG, 90, o);
                    o.close();
                } catch (Throwable ignored) { }
            }, d);
        }
    }

    // ============================================================ boletim / compartilhar / jogo
    private String inviteText() {
        return "📊 Acompanhe as eleições 2026 em tempo real com dados oficiais do TSE: mapa por estado, resultado do DF, notícias e lives de TV.\n\n📲 Baixe o app grátis (Android):\n" + PAGE_URL;
    }

    private void copyLink() {
        try {
            ((android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Link do app", PAGE_URL));
            Toast.makeText(this, "Link copiado", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Toast.makeText(this, "Não foi possível copiar", Toast.LENGTH_SHORT).show(); }
    }

    private String snapshotText() {
        Model.Result d = snap().pres;
        if (d == null) return "Brasil Decide — dados ainda não carregados.";
        StringBuilder s = new StringBuilder("Brasil Decide — " + new SimpleDateFormat("dd/MM/yyyy HH:mm", BR).format(new Date()) + "\n" + turn + "º turno • Brasil: " + pc(d.progress) + " das seções totalizadas.\n");
        for (int i = 0; i < Math.min(3, d.cands.size()); i++) { Model.Cand c = d.cands.get(i); s.append(i + 1).append("º ").append(c.nome).append(" (").append(c.partido).append("): ").append(pc(c.pct)).append(" — ").append(n(c.votos)).append(" votos.\n"); }
        if (d.cands.size() > 1) s.append("Diferença 1º–2º: ").append(n(d.cands.get(0).votos - d.cands.get(1).votos)).append(" votos.\n");
        s.append("Fonte: TSE. Sem projeção.\nApp: ").append(PAGE_URL);
        return s.toString();
    }

    private void share(String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Compartilhar"));
        } catch (Throwable t) { Toast.makeText(this, "Não foi possível compartilhar", Toast.LENGTH_SHORT).show(); }
    }

    /** 7 toques rápidos no título abrem o minijogo escondido. */
    private void easterEgg() {
        long now = System.currentTimeMillis();
        if (now - firstTap > 4000) { titleTaps = 0; firstTap = now; }
        titleTaps++;
        if (titleTaps >= 4 && titleTaps < 7) Toast.makeText(this, "🤫 " + (7 - titleTaps) + "…", Toast.LENGTH_SHORT).show();
        if (titleTaps >= 7) { titleTaps = 0; startActivity(new Intent(this, GameActivity.class)); }
    }

    // ============================================================ dados
    private void setTurn(int t) {
        if (t == turn) return;
        turn = t;
        prefs.edit().putInt("turn", t).apply();
        delayMs = intervalMs;
        animateNext = true;
        render();
        refresh();
    }

    private Callable<Object[]> task(final String key, final String url, final long ttl, final String photoBase) {
        return () -> {
            Long at;
            synchronized (absentAt) { at = absentAt.get(url); }
            if (at != null && System.currentTimeMillis() - at < ttl) { Tse.Fetch f = new Tse.Fetch(); f.absent = true; return new Object[]{key, f}; }
            Tse.Fetch f = Tse.fetch(url, photoBase);
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
        final String lu = localUf;
        ui.post(() -> { statusText = "Consultando o TSE…"; if (!tv) render(); });
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
                List<Object[]> br = runAll(java.util.Collections.singletonList(task("br", Tse.url(fed, "br", 1), 60000, Tse.photoBase(fed, "br"))));
                Tse.Fetch bf = br.isEmpty() ? null : (Tse.Fetch) br.get(0)[1];
                if (bf == null || bf.error != null) { failures++; if (bf != null) lastError = Tse.url(fed, "br", 1) + " → " + bf.error; }
                else if (bf.absent) wait[0] = true;
                else pres[0] = bf.result;
                if (!wait[0]) {
                    List<Callable<Object[]>> ts = new ArrayList<>();
                    for (String[] u : UFS) ts.add(task("s:" + u[0], Tse.url(fed, u[0].toLowerCase(Locale.ROOT), 1), 300000, Tse.photoBase(fed, "br")));
                    if (wantGov) for (String[] u : UFS) ts.add(task("g:" + u[0], Tse.url(est, u[0].toLowerCase(Locale.ROOT), 3), 300000, Tse.photoBase(est, u[0].toLowerCase(Locale.ROOT))));
                    ts.addAll(localTasks(t, lu));
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
                    localAt.put(t + lu, System.currentTimeMillis());
                } catch (Throwable th) { lastError = "commit: " + th; }
                busy = false;
                if (!tv || resumed) render();
            });
        });
    }

    private void addEvent(String icon, String title, String detail) {
        Event e = new Event();
        e.icon = icon; e.title = title; e.detail = detail; e.at = System.currentTimeMillis();
        events.add(0, e);
        while (events.size() > 50) events.remove(events.size() - 1);
    }

    private void commit(int t, Model.Result pres, Map<String, Model.Result> s, Map<String, Model.Result> g, Map<String, Model.Result> d,
                        Map<String, Boolean> gAbs, Map<String, Boolean> dAbs, boolean wait, int failures) {
        Snap sn = snaps[t - 1];
        String oldSig = sn.presSig;
        Model.Result oldPres = sn.pres;
        Map<String, Model.Result> oldStates = new HashMap<>(sn.states), oldGov = new HashMap<>(sn.gov);
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
        if (changed) {
            if (oldPres != null) {
                for (int i = 0; i < Math.min(3, pres.cands.size()); i++) {
                    Model.Cand c = pres.cands.get(i);
                    for (Model.Cand o : oldPres.cands) if (o.nome.equals(c.nome) && c.votos > o.votos) { addEvent("🗳️", c.nome + " recebeu +" + n(c.votos - o.votos) + " votos", "Brasil • " + pc(c.pct) + " dos válidos"); break; }
                }
                if (!oldPres.cands.isEmpty() && !pres.cands.isEmpty() && oldPres.cands.get(0).votos > 0 && !oldPres.cands.get(0).nome.equals(pres.cands.get(0).nome))
                    addEvent("↕️", "Mudança de líder nacional", pres.cands.get(0).nome + " assumiu a 1ª posição");
            }
            LinkedHashMap<String, Float> h = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(3, pres.cands.size()); i++) h.put(pres.cands.get(i).nome, (float) pres.cands.get(i).pct);
            sn.hist.add(h);
            while (sn.hist.size() > 100) sn.hist.remove(0);
        }
        for (Map.Entry<String, Model.Result> e : s.entrySet()) {
            Model.Result o = oldStates.get(e.getKey());
            if (o != null && o.lead() != null && e.getValue().lead() != null && !o.lead().nome.equals(e.getValue().lead().nome))
                addEvent("🗺️", ufName(e.getKey()) + " mudou de líder", e.getValue().lead().nome + " passou à frente • Presidente • " + t + "º turno");
        }
        for (Map.Entry<String, Model.Result> e : g.entrySet()) {
            Model.Result o = oldGov.get(e.getKey());
            if (o != null && o.lead() != null && e.getValue().lead() != null && !o.lead().nome.equals(e.getValue().lead().nome))
                addEvent("🗺️", ufName(e.getKey()) + " mudou de líder", e.getValue().lead().nome + " passou à frente • Governador • " + t + "º turno");
        }
        if (t != turn) return;
        if (wait) {
            delayMs = Math.max(intervalMs, 60000);
            statusText = turn + "º turno • aguardando arquivos do TSE";
            statusSub = "o cargo ainda não tem arquivo publicado";
        } else {
            delayMs = intervalMs;
            boolean offline = pres == null && s.isEmpty() && failures > 0;
            statusText = offline ? "Sem conexão • último resultado salvo" : failures > 0 ? "Online • dados parciais" : changed ? "Ao vivo • dados novos" : "Ao vivo • TSE sem mudança";
            statusSub = failures > 0 ? failures + " arquivos serão tentados de novo" : "dados oficiais • sem projeção";
        }
        if (failures == 0 || pres != null || !s.isEmpty()) saveCache(t);
        if (genStatusPending && snap().pres != null) { genStatusPending = false; genAllStatusForTest(); }
    }

    // ============================================================ cache do último snapshot válido
    private void saveCache(int t) {
        try {
            Snap sn = snaps[t - 1];
            JSONObject o = new JSONObject();
            if (sn.pres != null) o.put("pres", sn.pres.toJson());
            JSONArray hist = new JSONArray();
            for (LinkedHashMap<String, Float> h : sn.hist) {
                JSONObject ho = new JSONObject();
                for (Map.Entry<String, Float> e : h.entrySet()) ho.put(e.getKey(), (double) e.getValue());
                hist.put(ho);
            }
            o.put("states", mapJson(sn.states)).put("gov", mapJson(sn.gov)).put("df", mapJson(sn.df)).put("hist", hist)
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
                JSONArray hist = o.optJSONArray("hist");
                for (int i = 0; hist != null && i < hist.length(); i++) {
                    JSONObject ho = hist.optJSONObject(i);
                    if (ho == null) continue;
                    LinkedHashMap<String, Float> h = new LinkedHashMap<>();
                    java.util.Iterator<String> it = ho.keys();
                    while (it.hasNext()) { String k = it.next(); h.put(k, (float) ho.optDouble(k)); }
                    sn.hist.add(h);
                }
                sn.lastChange = o.optLong("lc");
                sn.presSig = o.optString("sig");
                sn.at = o.optLong("at");
            } catch (Throwable th) { lastError = "cache ilegível: " + th; }
        }
        if (snaps[turn - 1].at != 0) { statusText = "Último resultado salvo"; statusSub = "atualizando…"; }
    }
}
