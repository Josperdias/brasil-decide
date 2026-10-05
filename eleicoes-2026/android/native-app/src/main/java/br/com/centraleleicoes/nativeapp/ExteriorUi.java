package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tela "Mundo" (voto no exterior): só apresenta números oficiais do TSE e contas feitas sobre eles.
 * Neutra: nenhum texto interpreta o motivo de um resultado.
 */
final class ExteriorUi {
    interface Host {
        Context ctx();
        void showSheet(View v);
        void rerender();
        int turn();
        Model.Result nationalPres();
        void event(String icon, String title, String detail);
    }

    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);
    private static final String[] FILTERS = {"Todos", "Europa", "Américas", "Ásia", "África", "Oceania"};
    private static final String[] METRICS = {"Mais votos", "Maior eleitorado", "Maior vantagem (%)", "Maior vantagem (votos)", "Disputas apertadas",
            "Maior abstenção", "100% apurado"};

    private final Host host;
    private Exterior.Snapshot snap;
    private int filter, metric;
    private String selected = "";
    private String query = "";
    private final Map<String, String> lastLeader = new HashMap<>();
    private WorldMapView mapView;

    ExteriorUi(Host h) { host = h; }

    // ------------------------------------------------------------------ dados
    void setSnapshot(Exterior.Snapshot s) {
        if (s != null && s.total != null) detectFlips(s);
        snap = s;
    }

    private long flipAt;

    /** Atualiza a leitura e só procura viradas quando é uma leitura nova. */
    void setSnapshotIfChanged(Exterior.Snapshot s) {
        if (s != null && s.total != null && s.at != flipAt) { detectFlips(s); flipAt = s.at; }
        snap = s;
    }

    Exterior.Snapshot snapshot() { return snap; }

    /** Troca de líder por país entre duas leituras (guardadas para o futuro Replay/Linha do tempo). */
    private void detectFlips(Exterior.Snapshot s) {
        for (Exterior.Agg c : s.countries.values()) {
            Model.Cand l = c.lead();
            if (l == null) continue;
            String prev = lastLeader.get(s.turn + c.key);
            if (prev != null && !prev.equals(l.nome))
                host.event("🔄", "Virada no exterior: " + c.name, l.nome + " passou à frente • diferença atual: " + INT.format(c.gap()) + " votos • " + s.turn + "º turno");
            lastLeader.put(s.turn + c.key, l.nome);
        }
    }

    private static String pc(double v) { return String.format(BR, "%.2f%%", v); }
    private static String n(long v) { return INT.format(v); }
    private static String hhmm(long t) { return new SimpleDateFormat("HH:mm", BR).format(new Date(t)); }

    private boolean inFilter(Exterior.Agg c) {
        switch (filter) {
            case 1: return "Europa".equals(c.continent);
            case 2: return c.continent.startsWith("América");
            case 3: return "Ásia".equals(c.continent);
            case 4: return "África".equals(c.continent);
            case 5: return "Oceania".equals(c.continent);
            default: return true;
        }
    }

    // ------------------------------------------------------------------ tela
    View build() {
        Context cx = host.ctx();
        LinearLayout root = Ui.col(cx);
        Exterior.Snapshot s = snap;
        if (s == null || s.total == null) {
            LinearLayout e = Ui.card(cx);
            e.addView(Ui.text(cx, "🌍 Voto dos brasileiros no exterior", 16, Ui.TEXT, true));
            e.addView(Ui.text(cx, s != null && !s.error.isEmpty() ? s.error : "Carregando os dados oficiais do TSE (abrangência ZZ)…", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
            root.addView(e);
            return root;
        }
        root.addView(overview(s));
        root.addView(sectionTitle("Mapa-múndi eleitoral", "Cada país mostra a cor do candidato que lidera entre os brasileiros que votaram lá. Cinza: sem dados."), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        root.addView(mapCard(s));
        root.addView(sectionTitle("O mundo votou assim", "Frases geradas só a partir dos números oficiais."), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        root.addView(worldSays(s));
        root.addView(sectionTitle("Brasil × Exterior", "Comparação matemática entre o resultado nacional e o do exterior (diferença em pontos percentuais)."), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        root.addView(brazilVsWorld(s));
        root.addView(sectionTitle("Países e localidades", "Busque por país, cidade ou continente, ou veja os rankings."), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        root.addView(searchAndRank(s));
        root.addView(sectionTitle("Continentes", "Votos somados; percentuais calculados sobre os votos válidos agregados (não é média de percentuais)."), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        root.addView(continents(s));
        root.addView(note(cx, "Fonte: TSE (arquivos oficiais da abrangência ZZ). O TSE publica o resultado por localidade (consulado/posto) e não informa o país: o agrupamento por país e continente é feito pelo Brasil Decide a partir do nome da localidade. Dados sem projeção."));
        return root;
    }

    private View sectionTitle(String t, String sub) {
        Context cx = host.ctx();
        LinearLayout h = Ui.col(cx);
        h.setPadding(Ui.dp(2), Ui.dp(8), 0, Ui.dp(8));
        h.addView(Ui.text(cx, t, 18, Ui.TEXT, true));
        h.addView(Ui.text(cx, sub, 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 0));
        return h;
    }

    private View note(Context cx, String t) {
        TextView v = Ui.text(cx, t, 10, Ui.MUTED, false);
        v.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), Ui.dp(12));
        return v;
    }

    private View statBox(String value, String label) {
        Context cx = host.ctx();
        LinearLayout b = Ui.col(cx);
        b.setBackground(Ui.fill(0xFF08182A, 12, Ui.LINE));
        b.setPadding(Ui.dp(9), Ui.dp(8), Ui.dp(9), Ui.dp(8));
        TextView v = Ui.text(cx, value, 13, Ui.TEXT, true);
        v.setSingleLine();
        v.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(v);
        b.addView(Ui.text(cx, label, 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        return b;
    }

    private View overview(Exterior.Snapshot s) {
        Context cx = host.ctx();
        Exterior.Agg t = s.total;
        Model.Result z = s.zz;
        LinearLayout c = Ui.card(cx);
        c.addView(Ui.text(cx, "🌍 VOTO DOS BRASILEIROS NO EXTERIOR • " + host.turn() + "º TURNO", 10, Ui.MUTED, true));
        LinearLayout pr = Ui.row(cx);
        pr.setPadding(0, Ui.dp(8), 0, 0);
        pr.addView(Ui.big(cx, pc(z.progress), 24));
        pr.addView(Ui.text(cx, "  das seções", 11, Ui.MUTED, false), Ui.lp(0, -2, 1f));
        pr.addView(Ui.text(cx, n(z.sections) + "/" + n(z.sectionsTotal), 11, Ui.SOFT, true));
        c.addView(pr);
        c.addView(new GradientBar(cx, 7).value(z.progress));
        if (s.fromCache && !s.error.isEmpty())
            c.addView(Ui.text(cx, "Últimos dados do exterior: " + hhmm(s.at) + " • atualização oficial temporariamente indisponível.", 10, Ui.AMBER, true), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        LinearLayout r1 = Ui.row(cx);
        r1.addView(statBox(n(z.electorate), "eleitorado"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
        r1.addView(statBox(n(z.turnout) + " • " + pc(z.electorate > 0 ? 100.0 * z.turnout / z.electorate : 0), "comparecimento"), Ui.margins(Ui.lp(0, -2, 1.3f), 3, 0, 3, 0));
        r1.addView(statBox(n(z.absent) + " • " + pc(z.electorate > 0 ? 100.0 * z.absent / z.electorate : 0), "abstenção"), Ui.margins(Ui.lp(0, -2, 1.3f), 3, 0, 0, 0));
        c.addView(r1, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        LinearLayout r2 = Ui.row(cx);
        r2.addView(statBox(n(z.valid), "votos válidos"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
        r2.addView(statBox(n(z.blank), "brancos"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 3, 0));
        r2.addView(statBox(n(z.nulls), "nulos"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 0, 0));
        c.addView(r2, Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        View sep = new View(cx);
        sep.setBackgroundColor(0xCC1D3858);
        c.addView(sep, Ui.margins(Ui.lp(-1, 1), 0, 12, 0, 6));
        for (int i = 0; i < Math.min(3, z.cands.size()); i++) c.addView(candBar(z.cands.get(i), i));
        if (z.cands.size() > 1)
            c.addView(Ui.text(cx, "Diferença entre 1º e 2º: " + n(z.cands.get(0).votos - z.cands.get(1).votos) + " votos • " + String.format(BR, "%.2f p.p.", z.cands.get(0).pct - z.cands.get(1).pct), 11, Ui.SOFT, true), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        int cWith = 0;
        for (Exterior.Agg a : s.countries.values()) if (a.localsWithData > 0) cWith++;
        c.addView(Ui.text(cx, cWith + " países/territórios • " + t.localsWithData + " de " + t.locals + " localidades com dados • baixado às " + hhmm(s.at)
                + " • arquivo TSE " + ((z.date + " " + z.time).trim()), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return c;
    }

    private View candBar(Model.Cand cd, int idx) {
        Context cx = host.ctx();
        int col = Model.color(cd.nome);
        LinearLayout w = Ui.col(cx);
        w.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        LinearLayout row = Ui.row(cx);
        TextView nm = Ui.text(cx, (idx + 1) + "º  " + cd.nome + (cd.partido.isEmpty() ? "" : " (" + cd.partido + ")"), 13, Ui.TEXT, true);
        nm.setMaxLines(2);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(nm, Ui.lp(0, -2, 1f));
        LinearLayout right = Ui.col(cx);
        right.setGravity(Gravity.END);
        TextView p = Ui.big(cx, pc(cd.pct), 15);
        p.setTextColor(col);
        right.addView(p);
        right.addView(Ui.text(cx, n(cd.votos) + " votos", 10, Ui.MUTED, false));
        row.addView(right, Ui.margins(Ui.lp(-2, -2), 8, 0, 0, 0));
        w.addView(row);
        w.addView(new GradientBar(cx, 5).colors(col, Model.lighten(col)).value(cd.pct), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        return w;
    }

    // ------------------------------------------------------------------ mapa
    private TextView pill(String label, boolean on, View.OnClickListener l) {
        Context cx = host.ctx();
        TextView t = Ui.text(cx, label, 12, on ? Ui.INK : Ui.MUTED, true);
        t.setPadding(Ui.dp(13), Ui.dp(8), Ui.dp(13), Ui.dp(8));
        t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
        t.setOnClickListener(l);
        return t;
    }

    private View chipRow(String[] labels, int current, java.util.function.IntConsumer onPick) {
        Context cx = host.ctx();
        HorizontalScrollView hs = new HorizontalScrollView(cx);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(cx);
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            row.addView(pill(labels[i], current == i, v -> onPick.accept(k)), Ui.margins(Ui.lp(-2, -2), 0, 0, 7, 0));
        }
        hs.addView(row);
        return hs;
    }

    private View mapCard(Exterior.Snapshot s) {
        Context cx = host.ctx();
        LinearLayout c = Ui.card(cx);
        c.addView(chipRow(FILTERS, filter, k -> { filter = k; host.rerender(); }), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        mapView = new WorldMapView(cx);
        Map<String, Integer> colors = new HashMap<>();
        Set<String> with = new HashSet<>(), dim = new HashSet<>();
        Map<String, Integer> leads = new LinkedHashMap<>();
        for (Exterior.Agg a : s.countries.values()) {
            if (a.localsWithData == 0) continue;
            with.add(a.key);
            if (!inFilter(a)) dim.add(a.key);
            Model.Cand l = a.lead();
            if (l != null) {
                colors.put(a.key, Model.color(l.nome));
                if (inFilter(a)) leads.merge(l.nome, 1, Integer::sum);
            }
        }
        mapView.setData(colors, with, dim);
        mapView.setSelected(selected);
        mapView.setOnCountry(iso -> {
            selected = iso;
            mapView.setSelected(iso);
            Exterior.Agg a = s.countries.get(iso);
            host.showSheet(a == null ? emptyCountry(iso) : aggView(a, true, s));
        });
        c.addView(mapView, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 6));
        FlowLayout legend = new FlowLayout(cx, 6);
        List<Map.Entry<String, Integer>> es = new ArrayList<>(leads.entrySet());
        Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : es) legend.addView(legendPill(Model.color(e.getKey()), e.getKey(), e.getValue() + (e.getValue() == 1 ? " país" : " países")));
        c.addView(legend);
        c.addView(Ui.text(cx, "Toque num país para ver o detalhamento e as localidades.", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        return c;
    }

    private View legendPill(int color, String name, String count) {
        Context cx = host.ctx();
        LinearLayout p = Ui.row(cx);
        p.setBackground(Ui.fill(0xFF09182A, 99, Ui.LINE));
        p.setPadding(Ui.dp(9), Ui.dp(5), Ui.dp(11), Ui.dp(5));
        View d = new View(cx);
        d.setBackground(Ui.fill(color, 99, 0));
        p.addView(d, Ui.margins(Ui.lp(Ui.dp(10), Ui.dp(10)), 0, 0, 7, 0));
        p.addView(Ui.text(cx, name, 11, Ui.TEXT, true));
        p.addView(Ui.text(cx, "  " + count, 11, Ui.MUTED, false));
        return p;
    }

    private View emptyCountry(String iso) {
        Context cx = host.ctx();
        LinearLayout c = Ui.col(cx);
        String[] inf = Exterior.info(iso);
        c.addView(Ui.text(cx, Exterior.flag(iso) + " " + inf[0], 20, Ui.TEXT, true));
        c.addView(Ui.text(cx, "Sem localidades de votação deste país nos arquivos do TSE.", 12, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return c;
    }

    // ------------------------------------------------------------------ "o mundo votou assim"
    private View worldSays(Exterior.Snapshot s) {
        Context cx = host.ctx();
        LinearLayout c = Ui.card(cx);
        List<String> lines = new ArrayList<>();
        Map<String, Integer> leads = new LinkedHashMap<>();
        Exterior.Agg most = null, tight = null, bigEl = null;
        for (Exterior.Agg a : s.countries.values()) {
            Model.Cand l = a.lead();
            if (l != null) leads.merge(l.nome, 1, Integer::sum);
            if (most == null || a.valid > most.valid) most = a;
            if (a.valid >= 50 && a.cands.size() > 1 && (tight == null || a.gapPct() < tight.gapPct())) tight = a;
        }
        for (Exterior.Agg k : s.continents.values()) if (bigEl == null || k.electorate > bigEl.electorate) bigEl = k;
        List<Map.Entry<String, Integer>> es = new ArrayList<>(leads.entrySet());
        Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : es) lines.add("🗳️ " + e.getKey() + " lidera em " + e.getValue() + (e.getValue() == 1 ? " país/território." : " países/territórios."));
        if (most != null && s.total.valid > 0)
            lines.add("🌐 " + most.name + " concentra o maior número de votos válidos no exterior: " + n(most.valid) + " (" + pc(100.0 * most.valid / s.total.valid) + " do total).");
        if (tight != null)
            lines.add("⚖️ A disputa mais apertada entre os países com ao menos 50 votos válidos está em " + tight.name + ": " + n(tight.gap()) + " votos (" + String.format(BR, "%.2f p.p.", tight.gapPct()) + ") entre 1º e 2º.");
        if (bigEl != null && s.total.electorate > 0)
            lines.add("🧭 " + bigEl.name + " é o continente com maior eleitorado brasileiro no exterior: " + n(bigEl.electorate) + " eleitores (" + pc(100.0 * bigEl.electorate / s.total.electorate) + ").");
        lines.add("📍 " + s.total.localsWithData + " de " + s.total.locals + " localidades já têm dados; " + countDone(s) + " países/territórios com apuração concluída.");
        for (String l : lines) {
            TextView t = Ui.text(cx, l, 12, Ui.TEXT, false);
            t.setLineSpacing(0, 1.15f);
            t.setPadding(0, Ui.dp(5), 0, Ui.dp(5));
            c.addView(t);
        }
        return c;
    }

    private int countDone(Exterior.Snapshot s) {
        int k = 0;
        for (Exterior.Agg a : s.countries.values()) if (a.done()) k++;
        return k;
    }

    // ------------------------------------------------------------------ Brasil × Exterior
    private View brazilVsWorld(Exterior.Snapshot s) {
        Context cx = host.ctx();
        LinearLayout c = Ui.card(cx);
        Model.Result br = host.nationalPres();
        Model.Result z = s.zz;
        if (br == null) {
            c.addView(Ui.text(cx, "Aguardando o resultado nacional para comparar.", 12, Ui.MUTED, false));
            return c;
        }
        LinearLayout head = Ui.row(cx);
        head.addView(Ui.text(cx, "Candidato", 10, Ui.MUTED, true), Ui.lp(0, -2, 1.4f));
        head.addView(Ui.text(cx, "Brasil", 10, Ui.MUTED, true), Ui.lp(0, -2, 1f));
        head.addView(Ui.text(cx, "Exterior", 10, Ui.MUTED, true), Ui.lp(0, -2, 1f));
        head.addView(Ui.text(cx, "Dif.", 10, Ui.MUTED, true), Ui.lp(0, -2, 1f));
        c.addView(head);
        List<String> sentences = new ArrayList<>();
        for (int i = 0; i < Math.min(3, br.cands.size()); i++) {
            Model.Cand b = br.cands.get(i), x = null;
            for (Model.Cand k : z.cands) if (k.numero.equals(b.numero)) x = k;
            double d = x == null ? 0 : x.pct - b.pct;
            LinearLayout row = Ui.row(cx);
            row.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
            TextView nm = Ui.text(cx, b.nome, 12, Model.color(b.nome), true);
            nm.setMaxLines(2);
            row.addView(nm, Ui.lp(0, -2, 1.4f));
            row.addView(Ui.text(cx, pc(b.pct), 12, Ui.TEXT, true), Ui.lp(0, -2, 1f));
            row.addView(Ui.text(cx, x == null ? "—" : pc(x.pct), 12, Ui.TEXT, true), Ui.lp(0, -2, 1f));
            row.addView(Ui.text(cx, x == null ? "—" : String.format(BR, "%+.2f p.p.", d), 11, d >= 0 ? Ui.GREEN : Ui.AMBER, true), Ui.lp(0, -2, 1f));
            c.addView(row);
            if (x != null) sentences.add(b.nome + " tem " + String.format(BR, "%.2f", Math.abs(d)) + " p.p. " + (d >= 0 ? "a mais" : "a menos") + " no exterior do que no resultado nacional.");
        }
        View sep = new View(cx);
        sep.setBackgroundColor(0xCC1D3858);
        c.addView(sep, Ui.margins(Ui.lp(-1, 1), 0, 4, 0, 8));
        double pbr = br.electorate > 0 ? 100.0 * br.turnout / br.electorate : 0, pz = z.electorate > 0 ? 100.0 * z.turnout / z.electorate : 0;
        double abr = br.electorate > 0 ? 100.0 * br.absent / br.electorate : br.abstPct, az = z.electorate > 0 ? 100.0 * z.absent / z.electorate : 0;
        c.addView(kv("Participação (comparecimento)", "Brasil " + pc(pbr) + " • Exterior " + pc(pz)));
        c.addView(kv("Abstenção", "Brasil " + pc(abr) + " • Exterior " + pc(az)));
        c.addView(kv("Eleitorado", "Brasil " + n(br.electorate) + " • Exterior " + n(z.electorate) + " (" + pc(br.electorate > 0 ? 100.0 * z.electorate / br.electorate : 0) + " do total)"));
        for (String t : sentences) {
            TextView v = Ui.text(cx, "• " + t, 11, Ui.SOFT, false);
            v.setPadding(0, Ui.dp(4), 0, 0);
            c.addView(v);
        }
        c.addView(Ui.text(cx, "Comparação matemática; o app não interpreta o motivo das diferenças.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        return c;
    }

    private View kv(String k, String v) {
        Context cx = host.ctx();
        LinearLayout row = Ui.col(cx);
        row.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        row.addView(Ui.text(cx, k, 10, Ui.MUTED, false));
        row.addView(Ui.text(cx, v, 12, Ui.TEXT, true), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        return row;
    }

    // ------------------------------------------------------------------ busca e rankings
    private LinearLayout rankBox, searchBox;

    private View searchAndRank(final Exterior.Snapshot s) {
        Context cx = host.ctx();
        LinearLayout c = Ui.card(cx);
        EditText et = new EditText(cx);
        et.setHint("Buscar país, cidade ou continente (ex.: Portugal, Lisboa, Japão, Europa)");
        et.setHintTextColor(Ui.MUTED);
        et.setTextColor(Ui.TEXT);
        et.setTextSize(13);
        et.setSingleLine();
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(query);
        et.setBackground(Ui.fill(0xFF08182A, 12, Ui.LINE));
        et.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        c.addView(et, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        searchBox = Ui.col(cx);
        c.addView(searchBox);
        c.addView(chipRow(FILTERS, filter, k -> { filter = k; host.rerender(); }), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 8));
        c.addView(chipRow(METRICS, metric, k -> { metric = k; host.rerender(); }), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        rankBox = Ui.col(cx);
        c.addView(rankBox);
        fillSearch(s);
        fillRank(s);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c2, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c2, int d) { }
            @Override public void afterTextChanged(Editable e) { query = e.toString(); fillSearch(s); }
        });
        return c;
    }

    private void fillSearch(Exterior.Snapshot s) {
        Context cx = host.ctx();
        searchBox.removeAllViews();
        String q = Exterior.norm(query);
        if (q.isEmpty()) return;
        int shown = 0;
        for (Exterior.Agg k : s.continents.values()) {
            if (shown >= 12) break;
            if (Exterior.norm(k.name).contains(q)) { searchBox.addView(aggRow("🧭 " + k.name, k, s, false)); shown++; }
        }
        for (Exterior.Agg a : s.countries.values()) {
            if (shown >= 12) break;
            if (Exterior.norm(a.name).contains(q)) { searchBox.addView(aggRow(Exterior.flag(a.key) + " " + a.name, a, s, true)); shown++; }
        }
        for (Exterior.Locality l : s.locs.values()) {
            if (shown >= 12) break;
            if (l.r != null && Exterior.norm(l.name).contains(q)) {
                final Exterior.Locality fl = l;
                LinearLayout row = simpleRow("📍 " + l.name + " • " + Exterior.info(l.iso2)[0], l.r.lead() == null ? "sem votos" : l.r.lead().nome + " " + pc(l.r.lead().pct), l.r.lead() == null ? Ui.MUTED : Model.color(l.r.lead().nome));
                row.setOnClickListener(v -> host.showSheet(localityView(fl)));
                searchBox.addView(row);
                shown++;
            }
        }
        if (shown == 0) searchBox.addView(Ui.text(cx, "Nada encontrado para “" + query + "”.", 11, Ui.MUTED, false));
        View sep = new View(cx);
        sep.setBackgroundColor(0xCC1D3858);
        searchBox.addView(sep, Ui.margins(Ui.lp(-1, 1), 0, 6, 0, 6));
    }

    private LinearLayout simpleRow(String title, String right, int rightColor) {
        Context cx = host.ctx();
        LinearLayout row = Ui.row(cx);
        row.setPadding(Ui.dp(2), Ui.dp(9), Ui.dp(2), Ui.dp(9));
        TextView t = Ui.text(cx, title, 12.5f, Ui.TEXT, true);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, Ui.lp(0, -2, 1f));
        TextView r = Ui.text(cx, right, 11.5f, rightColor, true);
        r.setGravity(Gravity.END);
        row.addView(r, Ui.margins(Ui.lp(-2, -2), 8, 0, 0, 0));
        return row;
    }

    private View aggRow(String title, final Exterior.Agg a, final Exterior.Snapshot s, final boolean country) {
        Model.Cand l = a.lead();
        LinearLayout row = simpleRow(title, l == null ? "sem votos" : l.nome + " " + pc(l.pct), l == null ? Ui.MUTED : Model.color(l.nome));
        row.setOnClickListener(v -> host.showSheet(aggView(a, country, s)));
        return row;
    }

    private void fillRank(Exterior.Snapshot s) {
        Context cx = host.ctx();
        rankBox.removeAllViews();
        List<Exterior.Agg> list = new ArrayList<>();
        for (Exterior.Agg a : s.countries.values()) if (a.localsWithData > 0 && a.valid + a.blank + a.nulls >= 0 && inFilter(a)) list.add(a);
        Comparator<Exterior.Agg> cmp;
        String note = "";
        switch (metric) {
            case 1: cmp = (x, y) -> Long.compare(y.electorate, x.electorate); break;
            case 2: list.removeIf(a -> a.valid < 50); cmp = (x, y) -> Double.compare(y.gapPct(), x.gapPct()); note = "Países com ao menos 50 votos válidos."; break;
            case 3: cmp = (x, y) -> Long.compare(y.gap(), x.gap()); break;
            case 4: list.removeIf(a -> a.valid < 50 || a.cands.size() < 2); cmp = (x, y) -> Double.compare(x.gapPct(), y.gapPct()); note = "Menor diferença entre 1º e 2º, só países com ao menos 50 votos válidos."; break;
            case 5: list.removeIf(a -> a.electorate < 100); cmp = (x, y) -> Double.compare(y.abstPct(), x.abstPct()); note = "Países com ao menos 100 eleitores."; break;
            case 6: list.removeIf(a -> !a.done()); cmp = (x, y) -> Long.compare(y.valid, x.valid); break;
            default: cmp = (x, y) -> Long.compare(y.valid, x.valid);
        }
        Collections.sort(list, cmp);
        if (!note.isEmpty()) rankBox.addView(Ui.text(cx, note, 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 0, 4));
        int limit = metric == 6 ? 30 : 12;
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            Exterior.Agg a = list.get(i);
            Model.Cand l = a.lead();
            String main;
            switch (metric) {
                case 1: main = n(a.electorate) + " eleitores"; break;
                case 2: case 4: main = String.format(BR, "%.2f p.p.", a.gapPct()); break;
                case 3: main = n(a.gap()) + " votos"; break;
                case 5: main = pc(a.abstPct()); break;
                case 6: main = pc(a.progress()); break;
                default: main = n(a.valid) + " votos válidos";
            }
            LinearLayout row = Ui.col(cx);
            row.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), Ui.dp(8));
            LinearLayout top = Ui.row(cx);
            top.addView(Ui.text(cx, (i + 1) + "º ", 11, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 0, 0, 6, 0));
            TextView t = Ui.text(cx, Exterior.flag(a.key) + " " + a.name, 13, Ui.TEXT, true);
            top.addView(t, Ui.lp(0, -2, 1f));
            top.addView(Ui.text(cx, main, 12, Ui.CYAN, true));
            row.addView(top);
            row.addView(Ui.text(cx, (l == null ? "sem votos" : "Líder: " + l.nome + " (" + pc(l.pct) + ")") + " • " + a.localsWithData + " localidade" + (a.localsWithData == 1 ? "" : "s") + " • apuração " + pc(a.progress()), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 22, 3, 0, 0));
            final Exterior.Agg fa = a;
            row.setOnClickListener(v -> host.showSheet(aggView(fa, true, snap)));
            rankBox.addView(row);
        }
        if (list.isEmpty()) rankBox.addView(Ui.text(cx, "Nenhum país neste filtro ainda.", 11, Ui.MUTED, false));
    }

    // ------------------------------------------------------------------ continentes
    private View continents(Exterior.Snapshot s) {
        Context cx = host.ctx();
        LinearLayout c = Ui.col(cx);
        List<Exterior.Agg> ks = new ArrayList<>();
        for (String name : ExteriorData.CONTINENTS) {
            Exterior.Agg k = s.continents.get(name);
            if (k != null) ks.add(k);
        }
        for (final Exterior.Agg k : ks) {
            LinearLayout card = Ui.card(cx);
            card.addView(Ui.text(cx, "🧭 " + k.name, 15, Ui.TEXT, true));
            card.addView(Ui.text(cx, n(k.electorate) + " eleitores • comparecimento " + pc(k.turnoutPct()) + " • abstenção " + pc(k.abstPct()) + " • seções " + n(k.sec) + "/" + n(k.secTotal), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 6));
            for (int i = 0; i < Math.min(2, k.cands.size()); i++) card.addView(candBar(k.cands.get(i), i));
            card.setOnClickListener(v -> host.showSheet(aggView(k, false, s)));
            c.addView(card, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        }
        return c;
    }

    // ------------------------------------------------------------------ painéis
    View aggView(final Exterior.Agg a, boolean country, final Exterior.Snapshot s) {
        Context cx = host.ctx();
        LinearLayout v = Ui.col(cx);
        v.addView(Ui.text(cx, (country ? Exterior.flag(a.key) + " " : "🧭 ") + a.name, 20, Ui.TEXT, true));
        v.addView(Ui.text(cx, (country ? a.continent + " • " : "") + host.turn() + "º turno • " + a.localsWithData + " de " + a.locals + " localidades com dados", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 10));
        LinearLayout pr = Ui.row(cx);
        pr.addView(Ui.text(cx, "Apuração", 12, Ui.MUTED, false), Ui.lp(0, -2, 1f));
        pr.addView(Ui.big(cx, pc(a.progress()), 18));
        v.addView(pr);
        v.addView(new GradientBar(cx, 6).value(a.progress()), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 4));
        v.addView(Ui.text(cx, n(a.sec) + "/" + n(a.secTotal) + " seções totalizadas", 10, Ui.MUTED, false));
        LinearLayout r1 = Ui.row(cx);
        r1.addView(statBox(n(a.electorate), "eleitorado"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
        r1.addView(statBox(n(a.turnout) + " • " + pc(a.turnoutPct()), "comparecimento"), Ui.margins(Ui.lp(0, -2, 1.3f), 3, 0, 3, 0));
        r1.addView(statBox(n(a.absent) + " • " + pc(a.abstPct()), "abstenção"), Ui.margins(Ui.lp(0, -2, 1.3f), 3, 0, 0, 0));
        v.addView(r1, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        LinearLayout r2 = Ui.row(cx);
        r2.addView(statBox(n(a.valid), "válidos"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
        r2.addView(statBox(n(a.blank), "brancos"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 3, 0));
        r2.addView(statBox(n(a.nulls), "nulos"), Ui.margins(Ui.lp(0, -2, 1f), 3, 0, 0, 0));
        v.addView(r2, Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 8));
        for (int i = 0; i < Math.min(3, a.cands.size()); i++) v.addView(candBar(a.cands.get(i), i));
        if (a.cands.size() > 1)
            v.addView(Ui.text(cx, "Diferença entre 1º e 2º: " + n(a.gap()) + " votos • " + String.format(BR, "%.2f p.p.", a.gapPct()), 11, Ui.SOFT, true), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 4));
        if (country && !a.locs.isEmpty()) {
            v.addView(Ui.text(cx, "LOCALIDADES DE VOTAÇÃO", 10, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 2, 12, 0, 4));
            for (final Exterior.Locality l : a.locs) {
                Model.Cand ld = l.r == null ? null : l.r.lead();
                LinearLayout row = simpleRow(l.name, l.r == null ? "sem dados" : (ld == null ? "sem votos" : ld.nome + " " + pc(ld.pct)), ld == null ? Ui.MUTED : Model.color(ld.nome));
                row.setOnClickListener(x -> host.showSheet(localityView(l)));
                v.addView(row);
            }
        } else if (!country) {
            v.addView(Ui.text(cx, "PAÍSES", 10, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 2, 12, 0, 4));
            List<Exterior.Agg> cs = new ArrayList<>();
            for (Exterior.Agg c : s.countries.values()) if (c.continent.equals(a.name) && c.localsWithData > 0) cs.add(c);
            Collections.sort(cs, (x, y) -> Long.compare(y.valid, x.valid));
            for (Exterior.Agg c : cs) v.addView(aggRow(Exterior.flag(c.key) + " " + c.name + " • " + n(c.valid) + " válidos", c, s, true));
        }
        v.addView(Ui.text(cx, "Fonte: TSE. Percentuais calculados sobre a soma dos votos válidos das localidades.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 0));
        return v;
    }

    View localityView(Exterior.Locality l) {
        Context cx = host.ctx();
        LinearLayout v = Ui.col(cx);
        Model.Result r = l.r;
        String[] inf = Exterior.info(l.iso2);
        v.addView(Ui.text(cx, "📍 " + l.name, 20, Ui.TEXT, true));
        v.addView(Ui.text(cx, Exterior.flag(l.iso2) + " " + inf[0] + " • " + host.turn() + "º turno", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 10));
        if (r == null) {
            v.addView(Ui.text(cx, "O TSE ainda não publicou dados desta localidade.", 12, Ui.MUTED, false));
            return v;
        }
        LinearLayout pr = Ui.row(cx);
        pr.addView(Ui.text(cx, "Apuração", 12, Ui.MUTED, false), Ui.lp(0, -2, 1f));
        pr.addView(Ui.big(cx, pc(r.progress), 18));
        v.addView(pr);
        v.addView(new GradientBar(cx, 6).value(r.progress), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 4));
        v.addView(Ui.text(cx, n(r.sections) + "/" + n(r.sectionsTotal) + " seções totalizadas", 10, Ui.MUTED, false));
        LinearLayout r1 = Ui.row(cx);
        r1.addView(statBox(n(r.electorate), "eleitorado"), Ui.margins(Ui.lp(0, -2, 1f), 0, 0, 3, 0));
        r1.addView(statBox(n(r.turnout) + " • " + pc(r.electorate > 0 ? 100.0 * r.turnout / r.electorate : 0), "comparecimento"), Ui.margins(Ui.lp(0, -2, 1.3f), 3, 0, 3, 0));
        r1.addView(statBox(n(r.absent) + " • " + pc(r.electorate > 0 ? 100.0 * r.absent / r.electorate : 0), "abstenção"), Ui.margins(Ui.lp(0, -2, 1.3f), 3, 0, 0, 0));
        v.addView(r1, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 8));
        for (int i = 0; i < Math.min(3, r.cands.size()); i++) v.addView(candBar(r.cands.get(i), i));
        if (r.cands.size() > 1)
            v.addView(Ui.text(cx, "Diferença entre 1º e 2º: " + n(r.cands.get(0).votos - r.cands.get(1).votos) + " votos • " + String.format(BR, "%.2f p.p.", r.cands.get(0).pct - r.cands.get(1).pct), 11, Ui.SOFT, true), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        v.addView(Ui.text(cx, "Arquivo TSE: " + ((r.date + " " + r.time).trim()), 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        return v;
    }
}
