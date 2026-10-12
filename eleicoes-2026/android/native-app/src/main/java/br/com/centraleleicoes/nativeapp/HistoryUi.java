package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Cartão "Eleições passadas": linha do tempo de vencedores (1894–2022) e, por ano, o resultado por turno, por estado e para governador. */
final class HistoryUi {
    interface Host {
        Context ctx();
        void showSheet(View v);
        void rerender();
        void io(Runnable r);
        void post(Runnable r);
    }

    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);
    private static final int[] BAR = {Ui.CYAN, Ui.MINT, Ui.AMBER, Ui.PURPLE, Ui.RED, Ui.GREEN};
    private static final int TOP = 8;
    private static final int RECENT = 8;

    private final Host host;
    private Map<Integer, History.Election> old;
    private List<History.Summary> index;
    private final Map<Integer, History.Election> years = new HashMap<>();
    private boolean busy, expanded;

    HistoryUi(Host h) { host = h; }

    boolean needsLoad() { return old == null && !busy; }

    void load() {
        if (busy) return;
        busy = true;
        host.io(() -> {
            final Map<Integer, History.Election> o = History.loadOld(host.ctx());
            final List<History.Summary> ix = History.loadIndex(host.ctx());
            host.post(() -> { old = o; index = ix; busy = false; host.rerender(); });
        });
    }

    private static String n(long v) { return INT.format(v); }

    private static String pct(double p) { return String.format(BR, "%.1f%%", p); }

    private static String ufLabel(String uf) { return "BR".equals(uf) ? "Brasil" : "ZZ".equals(uf) ? "Exterior" : uf; }

    /** Todas as linhas da linha do tempo, do ano mais recente ao mais antigo. */
    private List<History.Summary> timeline() {
        TreeMap<Integer, History.Summary> m = new TreeMap<>(Collections.reverseOrder());
        if (old != null) for (History.Election e : old.values()) m.put(e.ano, History.summaryOf(e));
        if (index != null) for (History.Summary s : index) m.put(s.ano, s);
        return new ArrayList<>(m.values());
    }

    View build() {
        final Context c = host.ctx();
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "📚 Eleições passadas", 15, Ui.TEXT, true));
        card.addView(Ui.text(c, "Quem venceu cada eleição presidencial desde 1894. Toque num ano para ver os candidatos, os dois turnos e, a partir de 1994, o resultado por estado e para governador.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        if (old == null) {
            card.addView(Ui.text(c, "Carregando…", 11, Ui.MUTED, false));
            return card;
        }
        List<History.Summary> tl = timeline();
        if (index == null || index.isEmpty()) {
            card.addView(Ui.text(c, "Os resultados de 1994 a 2022 ainda não estão disponíveis offline; conecte-se para carregar.", 10, Ui.AMBER, false), Ui.margins(Ui.lp(-2, -2), 0, 0, 0, 6));
        }
        int shown = expanded ? tl.size() : Math.min(RECENT, tl.size());
        for (int i = 0; i < shown; i++) card.addView(row(c, tl.get(i)));
        if (tl.size() > RECENT) {
            TextView more = Ui.text(c, expanded ? "Mostrar menos" : "Ver todas as " + tl.size() + " eleições (desde 1894)", 12, Ui.SOFT, true);
            more.setGravity(Gravity.CENTER);
            more.setOnClickListener(v -> { expanded = !expanded; host.rerender(); });
            card.addView(more, Ui.lp(-1, Ui.dp(40)));
        }
        card.addView(Ui.text(c, "O selo ao lado de cada ano (Alta, Média, Parcial) indica a confiabilidade dos dados; toque no ano para ver a justificativa e como foi coletado. Fonte: TSE (dados abertos desde 1994; tabelas históricas antes disso).", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return card;
    }

    static int confColor(String conf) { return "Alta".equals(conf) ? Ui.GREEN : "Média".equals(conf) ? Ui.AMBER : Ui.RED; }

    /** Cartão "Confiabilidade e método de coleta" do detalhe de um ano. */
    private View trustCard(Context c, History.Election e) {
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.row(c);
        head.addView(Ui.text(c, "CONFIABILIDADE DOS DADOS", 10, Ui.MUTED, true), Ui.lp(0, -2, 1f));
        if (!e.conf.isEmpty()) head.addView(Ui.chip(c, e.conf, confColor(e.conf)));
        card.addView(head);
        if (!e.just.isEmpty()) card.addView(Ui.text(c, e.just, 11, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        if (!e.met.isEmpty()) {
            card.addView(Ui.text(c, "COMO FOI COLETADO", 10, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 0));
            card.addView(Ui.text(c, e.met, 11, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        }
        card.addView(Ui.text(c, "Alta: fonte oficial primária do TSE, somada pelo app. Média: fonte oficial por intermediário ou com correção documentada. Parcial: só o vencedor, sem placar completo, ou fontes divergentes.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        return card;
    }

    private View row(Context c, final History.Summary s) {
        LinearLayout r = Ui.row(c);
        r.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
        r.addView(Ui.text(c, String.valueOf(s.ano), 14, Ui.CYAN, true), Ui.lp(Ui.dp(46), -2));
        LinearLayout mid = Ui.col(c);
        String nome = s.vencedor.isEmpty() ? "Resultado não disponível" : s.vencedor + (s.partido.isEmpty() ? "" : " (" + s.partido + ")");
        mid.addView(Ui.text(c, nome, 13, Ui.TEXT, true));
        StringBuilder sub = new StringBuilder();
        if ("indireta".equals(s.tipo)) sub.append("eleição indireta");
        else {
            if ("2".equals(s.turno)) sub.append("2º turno");
            else if ("1".equals(s.turno) && s.ano >= 1989) sub.append("venceu no 1º turno");
            if (s.pct >= 0) sub.append(sub.length() > 0 ? " • " : "").append(pct(s.pct)).append(" dos votos");
            else if (s.votos > 0) sub.append(sub.length() > 0 ? " • " : "").append(n(s.votos)).append(" votos");
        }
        if (sub.length() > 0) mid.addView(Ui.text(c, sub.toString(), 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
        r.addView(mid, Ui.lp(0, -2, 1f));
        if (!s.conf.isEmpty()) r.addView(Ui.chip(c, s.conf, confColor(s.conf)), Ui.margins(Ui.lp(-2, -2), 6, 0, 6, 0));
        r.addView(Ui.text(c, "›", 18, Ui.MUTED, false));
        r.setOnClickListener(v -> openYear(s.ano));
        return r;
    }

    // ---------------------------------------------------------------- detalhe de um ano
    private String cargo = "pres", uf = "BR";
    private boolean showAll;

    /** Abre o detalhe de um ano (usado também pelo teste do emulador). */
    void open(final int ano) {
        if (old != null) { openYear(ano); return; }
        host.io(() -> {
            final Map<Integer, History.Election> o = History.loadOld(host.ctx());
            host.post(() -> { if (old == null) old = o; openYear(ano); });
        });
    }

    private void openYear(final int ano) {
        cargo = "pres";
        uf = "BR";
        showAll = false;
        final LinearLayout root = Ui.col(host.ctx());
        host.showSheet(root);
        History.Election cached = years.get(ano);
        if (cached == null && old != null) cached = old.get(ano);
        if (cached != null) { populate(root, cached); return; }
        root.addView(Ui.text(host.ctx(), "Eleição de " + ano, 19, Ui.TEXT, true));
        root.addView(Ui.text(host.ctx(), "Carregando…", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        host.io(() -> {
            final History.Election e = History.loadYear(host.ctx(), ano);
            host.post(() -> {
                root.removeAllViews();
                if (e == null) {
                    root.addView(Ui.text(host.ctx(), "Eleição de " + ano, 19, Ui.TEXT, true));
                    root.addView(Ui.text(host.ctx(), "Não foi possível carregar este ano agora. Tente de novo mais tarde.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
                } else {
                    years.put(ano, e);
                    populate(root, e);
                }
            });
        });
    }

    private void populate(final LinearLayout root, final History.Election e) {
        final Context c = host.ctx();
        root.removeAllViews();
        root.addView(Ui.text(c, (e.tipo.equals("indireta") ? "Eleição presidencial indireta de " : "Eleição de ") + e.ano, 19, Ui.TEXT, true));
        if (!e.nota.isEmpty()) root.addView(Ui.text(c, e.nota, 11, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 4));

        if (e.hasGov()) {
            FlowLayout cg = new FlowLayout(c, 7);
            cg.addView(chip(c, "Presidente", "pres".equals(cargo), v -> { cargo = "pres"; showAll = false; populate(root, e); }));
            cg.addView(chip(c, "Governador", "gov".equals(cargo), v -> { cargo = "gov"; if ("BR".equals(uf)) uf = "DF"; showAll = false; populate(root, e); }));
            root.addView(cg, Ui.margins(Ui.lp(-1, -2), 0, 8, 0, 0));
        }
        // UFs disponíveis para o cargo selecionado
        Set<String> ufs = new LinkedHashSet<>();
        Map<String, History.Race> turnos = e.races.get(cargo);
        if (turnos != null) for (History.Race r : turnos.values()) ufs.addAll(r.byUf.keySet());
        if (!ufs.contains(uf) && !ufs.isEmpty()) uf = ufs.contains("BR") ? "BR" : ufs.contains("DF") ? "DF" : ufs.iterator().next();
        if (ufs.size() > 1) {
            List<String> ord = new ArrayList<>(ufs);
            Collections.sort(ord, (a, b) -> a.equals("BR") ? -1 : b.equals("BR") ? 1 : a.equals("ZZ") ? 1 : b.equals("ZZ") ? -1 : a.compareTo(b));
            FlowLayout uf2 = new FlowLayout(c, 6);
            for (final String u : ord) uf2.addView(chip(c, ufLabel(u), u.equals(uf), v -> { uf = u; showAll = false; populate(root, e); }));
            root.addView(uf2, Ui.margins(Ui.lp(-1, -2), 0, 8, 0, 0));
        }
        if (turnos == null || turnos.isEmpty()) {
            root.addView(Ui.text(c, "Sem dados para este cargo.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 0));
        } else {
            List<String> ts = new ArrayList<>(turnos.keySet());
            Collections.sort(ts);
            for (String t : ts) {
                List<History.Cand> l = turnos.get(t).byUf.get(uf);
                if (l == null || l.isEmpty()) continue;
                root.addView(turnoCard(c, e, t, l, ts.size() > 1), Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
            }
        }
        root.addView(trustCard(c, e), Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));
        if (!e.fonte.isEmpty()) root.addView(Ui.text(c, "Fonte: " + e.fonte, 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 6, 2, 4));
    }

    private TextView chip(Context c, String label, boolean on, View.OnClickListener l) {
        TextView t = Ui.text(c, label, 12, on ? Ui.INK : Ui.MUTED, true);
        t.setPadding(Ui.dp(11), Ui.dp(6), Ui.dp(11), Ui.dp(6));
        t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
        t.setOnClickListener(l);
        return t;
    }

    private View turnoCard(Context c, History.Election e, String turno, List<History.Cand> list, boolean multi) {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, multi ? turno + "º TURNO" : "RESULTADO", 10, Ui.MUTED, true));
        long tot = 0;
        for (History.Cand k : list) tot += k.votos;
        double max = 1;
        for (History.Cand k : list) max = Math.max(max, History.pct(list, k));
        int shown = showAll ? list.size() : Math.min(TOP, list.size());
        for (int i = 0; i < shown; i++) {
            History.Cand k = list.get(i);
            double p = History.pct(list, k);
            LinearLayout top = Ui.row(c);
            LinearLayout nm = Ui.col(c);
            nm.addView(Ui.text(c, (k.eleito ? "✔ " : "") + k.nome, 13, k.eleito ? Ui.MINT : Ui.TEXT, true));
            String sub = (k.partido.isEmpty() ? "" : k.partido) + (k.situacao.isEmpty() ? "" : (k.partido.isEmpty() ? "" : " • ") + k.situacao);
            if (!sub.isEmpty()) nm.addView(Ui.text(c, sub, 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 1, 0, 0));
            top.addView(nm, Ui.lp(0, -2, 1f));
            LinearLayout nums = Ui.col(c);
            nums.setGravity(Gravity.END);
            nums.addView(Ui.text(c, p >= 0 ? pct(p) : "—", 13, Ui.TEXT, true));
            if (k.votos > 0) nums.addView(Ui.text(c, n(k.votos), 10, Ui.MUTED, false));
            top.addView(nums);
            LinearLayout block = Ui.col(c);
            block.addView(top);
            block.addView(new GradientBar(c, 5).colors(BAR[i % BAR.length], Ui.alpha(BAR[i % BAR.length], 0x99)).value(Math.max(2, Math.min(100, p < 0 ? 2 : 100 * p / max))), Ui.margins(Ui.lp(-1, Ui.dp(5)), 0, 4, 0, 0));
            card.addView(block, Ui.margins(Ui.lp(-1, -2), 0, i == 0 ? 8 : 6, 0, 0));
        }
        if (list.size() > TOP) {
            TextView more = Ui.text(c, showAll ? "Mostrar menos" : "Ver todos os " + list.size() + " candidatos", 12, Ui.SOFT, true);
            more.setGravity(Gravity.CENTER);
            more.setOnClickListener(v -> {
                showAll = !showAll;
                View p = (View) card.getParent();
                if (p instanceof LinearLayout) populate((LinearLayout) p, e);
            });
            card.addView(more, Ui.lp(-1, Ui.dp(40)));
        }
        if (tot > 0) card.addView(Ui.text(c, "Percentuais sobre a soma dos votos dos candidatos (votos válidos). Total: " + n(tot) + " votos.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return card;
    }
}
