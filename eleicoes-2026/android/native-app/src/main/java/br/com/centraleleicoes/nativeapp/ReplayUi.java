package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Replay da apuração + Momento da virada + Corrida dos votos + Noite da eleição, a partir dos snapshots do coletor.
 * Só reproduz o que aconteceu nos dados oficiais; nenhuma previsão.
 */
final class ReplayUi {
    interface Host {
        Context ctx();
        Handler ui();
        void showSheet(View v);
        int turn();
        void reload();
    }

    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);
    private static final double[] SPEEDS = {0.5, 1, 2, 4, 10};
    private static final String[] SPEED_LABELS = {"0.5x", "1x", "2x", "4x", "10x"};
    private static final String[] MODES = {"🗺️ Mapa", "🏁 Corrida", "📈 Gráfico"};

    private final Host host;
    private Replay.Series series;
    private boolean loading;
    private int idx, speed = 1, mode;
    private boolean playing, govMode;
    private double acc;

    // views dinâmicas
    private SeekBar seek;
    private TextView timeTv, playBtn, mapTitle;
    private LinearLayout modeBox, sumBox, evBox;
    private BrazilMapView map;
    private FlowLayout legend;
    private LineChartView chart;
    private final List<View> evRows = new ArrayList<>();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!playing || series == null || series.snaps.isEmpty()) return;
            acc += SPEEDS[speed];
            int step = (int) acc;
            acc -= step;
            if (step > 0) {
                idx = Math.min(series.snaps.size() - 1, idx + step);
                seek.setProgress(idx);
                if (idx >= series.snaps.size() - 1) setPlaying(false);
            }
            host.ui().postDelayed(this, 200);
        }
    };

    ReplayUi(Host h) { host = h; }

    private int presetPct = -1;

    /** Atalho de teste/automação: modo inicial (0 mapa, 1 corrida, 2 gráfico) e posição inicial em % da linha do tempo. */
    void preset(int m, int pct) { mode = Math.max(0, Math.min(2, m)); presetPct = pct; }

    void setSeries(Replay.Series s, boolean isLoading) {
        boolean first = series == null || s != series;
        series = s;
        loading = isLoading;
        if (first && s != null && !s.snaps.isEmpty()) {
            idx = presetPct >= 0 ? (int) Math.round((s.snaps.size() - 1) * Math.min(100, presetPct) / 100.0) : s.snaps.size() - 1;
            presetPct = -1;
        }
    }

    void stop() { playing = false; host.ui().removeCallbacks(tick); }

    private static String n(long v) { return INT.format(v); }
    private static String pc(double v) { return String.format(BR, "%.2f%%", v); }
    private static String hhmm(long t) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", BR);
        f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        return f.format(new Date(t));
    }

    // ------------------------------------------------------------------ montagem
    View build() {
        Context cx = host.ctx();
        LinearLayout root = Ui.col(cx);
        evRows.clear();
        LinearLayout head = Ui.col(cx);
        head.setPadding(Ui.dp(2), Ui.dp(6), 0, Ui.dp(10));
        head.addView(Ui.text(cx, "▶ Replay da apuração", 18, Ui.TEXT, true));
        head.addView(Ui.text(cx, "Como a eleição chegou até aqui, a partir dos registros do coletor (a cada minuto). Só reproduz os dados oficiais; sem previsão.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        root.addView(head);
        if (series == null || series.snaps.isEmpty()) {
            LinearLayout e = Ui.card(cx);
            e.addView(Ui.text(cx, loading ? "Carregando os registros da apuração…" : "Ainda não há registros", 15, Ui.TEXT, true));
            e.addView(Ui.text(cx, loading ? "Buscando a linha do tempo na base de dados do Brasil Decide." : (series != null && !series.error.isEmpty() ? series.error + " " : "")
                    + (host.turn() == 1 ? "O 1º turno não foi registrado: o coletor só passa a gravar a apuração a partir do 2º turno (25/10/2026)." : "O coletor começa a gravar a apuração minuto a minuto na noite do 2º turno (25/10/2026). Volte aqui depois para assistir ao replay."), 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
            root.addView(e);
            return root;
        }
        if (series.demo) {
            TextView d = Ui.text(cx, "⚠️ DADOS DE DEMONSTRAÇÃO (simulados) — somente para teste do recurso; não são resultados reais.", 11, Ui.AMBER, true);
            d.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
            d.setBackground(Ui.fill(0x33FFC966, 12, 0x99FFC966));
            root.addView(d, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        }
        // controles
        LinearLayout ctl = Ui.card(cx);
        timeTv = Ui.text(cx, "", 13, Ui.TEXT, true);
        ctl.addView(timeTv);
        seek = new SeekBar(cx);
        seek.setMax(series.snaps.size() - 1);
        seek.setProgress(idx);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { idx = p; update(); }
            @Override public void onStartTrackingTouch(SeekBar s) { setPlaying(false); }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        ctl.addView(seek, Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 2));
        LinearLayout row = Ui.row(cx);
        playBtn = Ui.text(cx, "▶ Reproduzir apuração", 13, Ui.INK, true);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setBackground(Ui.accent(99));
        playBtn.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        playBtn.setOnClickListener(v -> {
            if (!playing && idx >= series.snaps.size() - 1) { idx = 0; seek.setProgress(0); }
            setPlaying(!playing);
        });
        row.addView(playBtn, Ui.lp(0, -2, 1f));
        ctl.addView(row, Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 8));
        HorizontalScrollView hs = new HorizontalScrollView(cx);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout sp = Ui.row(cx);
        for (int i = 0; i < SPEED_LABELS.length; i++) {
            final int k = i;
            TextView t = Ui.text(cx, SPEED_LABELS[i], 12, speed == i ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(13), Ui.dp(7), Ui.dp(13), Ui.dp(7));
            t.setBackground(speed == i ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { speed = k; acc = 0; host.reload(); });
            sp.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 0, 7, 0));
        }
        hs.addView(sp);
        ctl.addView(hs);
        root.addView(ctl, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        // resumo no instante
        sumBox = Ui.card(cx);
        root.addView(sumBox, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        // visualização
        HorizontalScrollView hm = new HorizontalScrollView(cx);
        hm.setHorizontalScrollBarEnabled(false);
        LinearLayout md = Ui.row(cx);
        for (int i = 0; i < MODES.length; i++) {
            final int k = i;
            TextView t = Ui.text(cx, MODES[i], 12, mode == i ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(13), Ui.dp(8), Ui.dp(13), Ui.dp(8));
            t.setBackground(mode == i ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { mode = k; host.reload(); });
            md.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 0, 7, 0));
        }
        TextView gv = Ui.text(cx, govMode ? "Governador" : "Presidente", 12, Ui.CYAN, true);
        gv.setPadding(Ui.dp(13), Ui.dp(8), Ui.dp(13), Ui.dp(8));
        gv.setBackground(Ui.fill(0xFF09182A, 99, Ui.CYAN));
        gv.setOnClickListener(v -> { govMode = !govMode; host.reload(); });
        md.addView(gv);
        hm.addView(md);
        root.addView(hm, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        modeBox = Ui.card(cx);
        root.addView(modeBox, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        buildMode();
        // virada e noite
        root.addView(flipsCard(), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        LinearLayout nh = Ui.col(cx);
        nh.setPadding(Ui.dp(2), Ui.dp(6), 0, Ui.dp(8));
        nh.addView(Ui.text(cx, "A noite da eleição", 18, Ui.TEXT, true));
        nh.addView(Ui.text(cx, "Principais acontecimentos registrados. Toque num evento para ir até aquele momento.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        root.addView(nh);
        evBox = Ui.card(cx);
        buildEvents();
        root.addView(evBox);
        update();
        setPlaying(playing);
        return root;
    }

    private void setPlaying(boolean p) {
        playing = p;
        if (playBtn != null) playBtn.setText(p ? "⏸ Pausar" : "▶ Reproduzir apuração");
        host.ui().removeCallbacks(tick);
        if (p) host.ui().postDelayed(tick, 200);
    }

    // ------------------------------------------------------------------ modos
    private void buildMode() {
        Context cx = host.ctx();
        modeBox.removeAllViews();
        map = null; legend = null; chart = null;
        if (mode == 0) {
            mapTitle = Ui.text(cx, "", 12, Ui.MUTED, false);
            modeBox.addView(mapTitle, Ui.margins(Ui.lp(-2, -2), 0, 0, 0, 6));
            map = new BrazilMapView(cx);
            map.setOnUf(uf -> host.showSheet(ufView(uf)));
            modeBox.addView(map, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 6));
            legend = new FlowLayout(cx, 6);
            modeBox.addView(legend);
        } else if (mode == 2) {
            chart = new LineChartView(cx);
            modeBox.addView(Ui.text(cx, "Percentual dos votos válidos no Brasil ao longo da apuração (até o instante escolhido)", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 0, 6));
            modeBox.addView(chart);
        }
    }

    // ------------------------------------------------------------------ atualização a cada instante
    private Replay.Part pres(int i) { return series.snaps.get(i).br; }

    private void update() {
        if (series == null || series.snaps.isEmpty() || timeTv == null) return;
        Context cx = host.ctx();
        idx = Math.max(0, Math.min(idx, series.snaps.size() - 1));
        Replay.Snap sn = series.snaps.get(idx);
        Replay.Part br = sn.br;
        timeTv.setText("🕒 " + hhmm(sn.t) + " (Brasília) • " + (br == null ? "—" : pc(br.p) + " das seções") + "  ·  registro " + (idx + 1) + "/" + series.snaps.size());
        // resumo
        sumBox.removeAllViews();
        if (br != null) {
            sumBox.addView(Ui.text(cx, "BRASIL NESTE INSTANTE • " + series.turn + "º TURNO", 10, Ui.MUTED, true));
            for (int i = 0; i < Math.min(3, br.nums.length); i++) {
                String nm = series.presName(br.nums[i]);
                int col = Model.color(nm);
                double pct = br.valid > 0 ? 100.0 * br.votes[i] / br.valid : 0;
                LinearLayout w = Ui.col(cx);
                w.setPadding(0, Ui.dp(6), 0, Ui.dp(4));
                LinearLayout r = Ui.row(cx);
                TextView t = Ui.text(cx, (i + 1) + "º  " + nm, 13, Ui.TEXT, true);
                t.setMaxLines(1);
                r.addView(t, Ui.lp(0, -2, 1f));
                TextView p = Ui.big(cx, pc(pct), 15);
                p.setTextColor(col);
                r.addView(p);
                w.addView(r);
                w.addView(Ui.text(cx, n(br.votes[i]) + " votos", 10, Ui.MUTED, false));
                w.addView(new GradientBar(cx, 5).colors(col, Model.lighten(col)).value(pct), Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
                sumBox.addView(w);
            }
            if (br.nums.length > 1) sumBox.addView(Ui.text(cx, "Diferença entre 1º e 2º: " + n(br.gap()) + " votos • válidos: " + n(br.valid), 11, Ui.SOFT, true), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        }
        if (mode == 0 && map != null) updateMap(sn);
        else if (mode == 1) updateRace();
        else if (mode == 2 && chart != null) updateChart();
        // eventos: os já ocorridos ficam opacos
        for (int i = 0; i < evRows.size(); i++) {
            Replay.Event e = series.events.get(i);
            evRows.get(i).setAlpha(e.idx <= idx ? 1f : 0.38f);
        }
    }

    private void updateMap(Replay.Snap sn) {
        Map<String, Integer> fills = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> color = new HashMap<>();
        for (String u : Replay.UFS) {
            Replay.Part p = govMode ? sn.gv.get(u) : sn.uf.get(u);
            String l = p == null ? null : p.leader();
            if (l == null) continue;
            String nm = govMode ? series.govName(u, l) : series.presName(l);
            int col = Model.color(nm);
            fills.put(u, col);
            counts.merge(nm, 1, Integer::sum);
            color.put(nm, col);
        }
        map.setFills(fills);
        mapTitle.setText((govMode ? "Governador" : "Presidente") + " — liderança por UF neste instante (" + fills.size() + "/27 com votos)");
        legend.removeAllViews();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            LinearLayout p = Ui.row(host.ctx());
            p.setBackground(Ui.fill(0xFF09182A, 99, Ui.LINE));
            p.setPadding(Ui.dp(9), Ui.dp(5), Ui.dp(11), Ui.dp(5));
            View d = new View(host.ctx());
            d.setBackground(Ui.fill(color.get(e.getKey()), 99, 0));
            p.addView(d, Ui.margins(Ui.lp(Ui.dp(10), Ui.dp(10)), 0, 0, 7, 0));
            p.addView(Ui.text(host.ctx(), e.getKey(), 11, Ui.TEXT, true));
            p.addView(Ui.text(host.ctx(), "  " + e.getValue() + " UF" + (e.getValue() > 1 ? "s" : ""), 11, Ui.MUTED, false));
            legend.addView(p);
        }
    }

    /** Corrida dos votos: barras proporcionais aos votos absolutos no instante, com o ganho desde o registro anterior. */
    private void updateRace() {
        Context cx = host.ctx();
        modeBox.removeAllViews();
        Replay.Part cur = pres(idx), prev = idx > 0 ? pres(idx - 1) : null;
        if (cur == null) return;
        long max = 1;
        for (long v : cur.votes) max = Math.max(max, v);
        modeBox.addView(Ui.text(cx, "Corrida dos votos — votos absolutos no Brasil", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 0, 6));
        for (int i = 0; i < Math.min(5, cur.nums.length); i++) {
            String nm = series.presName(cur.nums[i]);
            int col = Model.color(nm);
            long gain = prev == null ? 0 : cur.votes[i] - prev.votesOf(cur.nums[i]);
            LinearLayout r = Ui.col(cx);
            r.setPadding(0, Ui.dp(5), 0, Ui.dp(5));
            LinearLayout top = Ui.row(cx);
            TextView t = Ui.text(cx, nm, 12, Ui.TEXT, true);
            top.addView(t, Ui.lp(0, -2, 1f));
            top.addView(Ui.text(cx, n(cur.votes[i]) + (gain > 0 ? "  (+" + n(gain) + ")" : ""), 12, col, true));
            r.addView(top);
            r.addView(new GradientBar(cx, 12).colors(col, Model.lighten(col)).value(100.0 * cur.votes[i] / max), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
            r.addView(Ui.text(cx, pc(cur.valid > 0 ? 100.0 * cur.votes[i] / cur.valid : 0) + " dos válidos", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
            modeBox.addView(r);
        }
    }

    private void updateChart() {
        List<float[]> ser = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        Replay.Part last = pres(idx);
        if (last == null) return;
        for (int k = 0; k < Math.min(3, last.nums.length); k++) {
            String num = last.nums[k];
            List<Float> vals = new ArrayList<>();
            for (int i = 0; i <= idx; i++) {
                Replay.Part p = pres(i);
                if (p != null && p.valid > 0) vals.add((float) (100.0 * p.votesOf(num) / p.valid));
            }
            float[] a = new float[vals.size()];
            for (int i = 0; i < a.length; i++) a[i] = vals.get(i);
            ser.add(a);
            cols.add(Model.color(series.presName(num)));
        }
        chart.set(ser, cols);
    }

    // ------------------------------------------------------------------ viradas e eventos
    private View flipsCard() {
        Context cx = host.ctx();
        LinearLayout c = Ui.card(cx);
        c.addView(Ui.text(cx, "🔄 Momento da virada", 15, Ui.TEXT, true));
        Map<String, Integer> per = Replay.flipsPerUf(series);
        Replay.Event lastNat = null, lastAny = null;
        int total = 0;
        for (Replay.Event e : series.events) if (e.flip) { total++; lastAny = e; if (e.uf.isEmpty()) lastNat = e; }
        if (total == 0) {
            c.addView(Ui.text(cx, "Nenhuma mudança de liderança registrada até agora.", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
            return c;
        }
        c.addView(Ui.text(cx, total + " mudança" + (total > 1 ? "s" : "") + " de liderança registrada" + (total > 1 ? "s" : "") + ".", 12, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 6));
        if (lastNat != null) c.addView(linkRow("Última virada nacional", lastNat));
        if (lastAny != null) c.addView(linkRow("Última virada registrada", lastAny));
        String top = null;
        int tv = 0;
        for (Map.Entry<String, Integer> e : per.entrySet()) if (e.getValue() > tv) { tv = e.getValue(); top = e.getKey(); }
        if (top != null) c.addView(Ui.text(cx, "UF com mais viradas: " + top + " (" + tv + ")", 11, Ui.SOFT, true), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        List<String> never = new ArrayList<>();
        for (String u : Replay.UFS) if (!per.containsKey(u)) never.add(u);
        c.addView(Ui.text(cx, "UFs sem mudança de liderança (presidente/governador): " + never.size() + " de 27", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        return c;
    }

    private View linkRow(String label, final Replay.Event e) {
        Context cx = host.ctx();
        TextView t = Ui.text(cx, label + ": " + hhmm(e.t) + " — " + e.title.replace("Virada em ", "") + " ›", 12, Ui.TEXT, false);
        t.setPadding(0, Ui.dp(5), 0, Ui.dp(5));
        t.setOnClickListener(v -> { idx = e.idx; if (seek != null) seek.setProgress(idx); update(); });
        return t;
    }

    private void buildEvents() {
        Context cx = host.ctx();
        evBox.removeAllViews();
        evRows.clear();
        int shown = 0;
        for (final Replay.Event e : series.events) {
            LinearLayout row = Ui.row(cx);
            row.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), Ui.dp(8));
            TextView tm = Ui.text(cx, hhmm(e.t).substring(0, 5), 11, Ui.MUTED, true);
            row.addView(tm, Ui.lp(Ui.dp(44), -2));
            row.addView(Ui.text(cx, e.icon, 14, Ui.TEXT, false), Ui.margins(Ui.lp(Ui.dp(24), -2), 0, 0, 4, 0));
            LinearLayout col = Ui.col(cx);
            col.addView(Ui.text(cx, e.title, 12.5f, Ui.TEXT, true));
            col.addView(Ui.text(cx, e.detail, 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
            row.addView(col, Ui.lp(0, -2, 1f));
            row.setOnClickListener(v -> { idx = e.idx; if (seek != null) seek.setProgress(idx); update(); });
            evBox.addView(row);
            evRows.add(row);
            if (++shown >= 150) break;
        }
    }

    private View ufView(String uf) {
        Context cx = host.ctx();
        LinearLayout v = Ui.col(cx);
        Replay.Snap sn = series.snaps.get(idx);
        Replay.Part p = govMode ? sn.gv.get(uf) : sn.uf.get(uf);
        v.addView(Ui.text(cx, uf + " • " + (govMode ? "Governador" : "Presidente") + " às " + hhmm(sn.t).substring(0, 5), 18, Ui.TEXT, true));
        if (p == null) {
            v.addView(Ui.text(cx, "Sem dados neste instante.", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
            return v;
        }
        v.addView(Ui.text(cx, pc(p.p) + " das seções • " + n(p.sec) + "/" + n(p.secTotal), 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        for (int i = 0; i < Math.min(3, p.nums.length); i++) {
            String nm = govMode ? series.govName(uf, p.nums[i]) : series.presName(p.nums[i]);
            int col = Model.color(nm);
            double pct = p.valid > 0 ? 100.0 * p.votes[i] / p.valid : 0;
            LinearLayout r = Ui.row(cx);
            r.setPadding(0, Ui.dp(5), 0, Ui.dp(2));
            r.addView(Ui.text(cx, (i + 1) + "º  " + nm, 13, Ui.TEXT, true), Ui.lp(0, -2, 1f));
            TextView t = Ui.big(cx, pc(pct), 14);
            t.setTextColor(col);
            r.addView(t);
            v.addView(r);
            v.addView(Ui.text(cx, n(p.votes[i]) + " votos", 10, Ui.MUTED, false));
            v.addView(new GradientBar(cx, 5).colors(col, Model.lighten(col)).value(pct), Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 4));
        }
        if (p.nums.length > 1) v.addView(Ui.text(cx, "Diferença entre 1º e 2º: " + n(p.gap()) + " votos", 11, Ui.SOFT, true), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return v;
    }
}
