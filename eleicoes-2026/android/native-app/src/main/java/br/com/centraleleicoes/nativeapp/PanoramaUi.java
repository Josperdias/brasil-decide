package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cartão "Panorama político": ranking de partidos por candidaturas ou bancada atual. Sem rótulos ideológicos nem previsões. */
final class PanoramaUi {
    interface Host {
        Context ctx();
        void showSheet(View v);
        void rerender();
    }

    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);
    private static final String[] METRICS = {"Candidaturas", "Câmara hoje", "Senado hoje", "% mulheres"};
    private static final int TOP = 12;

    private final Host host;
    private Panorama.Data data;
    private boolean busy, failed;
    private int metric;
    private boolean all;

    PanoramaUi(Host h) { host = h; }

    void setData(Panorama.Data d, boolean busy, boolean failed) { data = d; this.busy = busy; this.failed = failed; }

    boolean needsLoad() { return data == null && !busy; }

    private static String n(long v) { return INT.format(v); }

    /** "45-PSDB/23-CIDADANIA" -> "PSDB + CIDADANIA". */
    private static String pretty(String comp) { return comp.replaceAll("\\d+-", "").replace("/", " + "); }

    private static double womenPct(Panorama.Party p) { return p.tot == 0 ? 0 : 100.0 * p.mul / p.tot; }

    private double value(Panorama.Party p) {
        switch (metric) {
            case 1: return p.cam;
            case 2: return p.sen;
            case 3: return p.tot >= 10 ? womenPct(p) : -1;
            default: return p.tot;
        }
    }

    private String valueText(Panorama.Party p) {
        switch (metric) {
            case 1: return n(p.cam) + " dep.";
            case 2: return n(p.sen) + " sen.";
            case 3: return String.format(BR, "%.1f%%", womenPct(p));
            default: return n(p.tot) + " cand.";
        }
    }

    View build() {
        final Context c = host.ctx();
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "🏛️ Panorama político", 15, Ui.TEXT, true));
        card.addView(Ui.text(c, "Partidos e federações: quantas candidaturas registradas no TSE em 2026 e como estão as bancadas hoje na Câmara e no Senado. Só contagens oficiais, sem classificação ideológica.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        if (data == null) {
            card.addView(Ui.text(c, failed ? "Não foi possível carregar agora. Tente de novo mais tarde." : "Carregando…", 11, Ui.MUTED, false));
            return card;
        }
        FlowLayout chips = new FlowLayout(c, 7);
        for (int i = 0; i < METRICS.length; i++) {
            final int mi = i;
            boolean on = mi == metric;
            TextView t = Ui.text(c, METRICS[i], 12, on ? Ui.INK : Ui.MUTED, true);
            t.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(12), Ui.dp(7));
            t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
            t.setOnClickListener(v -> { metric = mi; host.rerender(); });
            chips.addView(t);
        }
        card.addView(chips);
        List<Panorama.Party> list = new ArrayList<>();
        for (Panorama.Party p : data.parties) if (value(p) > 0) list.add(p);
        Collections.sort(list, (a, b) -> Double.compare(value(b), value(a)));
        double max = list.isEmpty() ? 1 : value(list.get(0));
        int shown = all ? list.size() : Math.min(TOP, list.size());
        for (int i = 0; i < shown; i++) card.addView(row(c, list.get(i), max, i + 1), Ui.margins(Ui.lp(-1, -2), 0, i == 0 ? 10 : 0, 0, 0));
        if (list.size() > TOP) {
            TextView more = Ui.text(c, all ? "Mostrar menos" : "Ver todos os " + list.size() + " partidos", 12, Ui.SOFT, true);
            more.setGravity(Gravity.CENTER);
            more.setOnClickListener(v -> { all = !all; host.rerender(); });
            card.addView(more, Ui.lp(-1, Ui.dp(40)));
        }
        if (!data.federations.isEmpty()) {
            TextView fed = Ui.text(c, "Federações registradas (" + data.federations.size() + ") ›", 13, Ui.TEXT, true);
            fed.setPadding(0, Ui.dp(10), 0, Ui.dp(4));
            fed.setOnClickListener(v -> host.showSheet(federationsView()));
            card.addView(fed);
        }
        card.addView(Ui.text(c, "Candidaturas: TSE (dados abertos; inclui pedidos ainda não julgados). Bancadas: Câmara dos Deputados e Senado Federal, parlamentares em exercício hoje (" + n(data.totCam) + " deputados, " + n(data.totSen) + " senadores). Partido atual do parlamentar; pode diferir do partido da candidatura.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        return card;
    }

    private View row(Context c, final Panorama.Party p, double max, int pos) {
        LinearLayout r = Ui.col(c);
        LinearLayout top = Ui.row(c);
        top.addView(Ui.text(c, pos + "º", 11, Ui.MUTED, false), Ui.margins(Ui.lp(Ui.dp(26), -2), 0, 0, 0, 0));
        top.addView(Ui.text(c, p.sigla, 14, Ui.TEXT, true), Ui.lp(0, -2, 1f));
        top.addView(Ui.text(c, valueText(p), 12, Ui.SOFT, true));
        r.addView(top);
        r.addView(new GradientBar(c, 6).value(Math.max(2, Math.min(100, 100 * value(p) / max))), Ui.margins(Ui.lp(-1, Ui.dp(6)), 26, 0, 0, 0));
        r.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        r.setOnClickListener(v -> host.showSheet(detail(p)));
        return r;
    }

    private View detail(Panorama.Party p) {
        Context c = host.ctx();
        LinearLayout v = Ui.col(c);
        v.addView(Ui.text(c, p.sigla + (p.num.isEmpty() ? "" : " · nº " + p.num), 19, Ui.TEXT, true));
        if (!p.nome.isEmpty()) v.addView(Ui.text(c, p.nome, 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 8));
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "CANDIDATURAS EM 2026 (TSE)", 10, Ui.MUTED, true));
        if (p.tot == 0) card.addView(Ui.text(c, "Sem candidaturas registradas até agora.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        for (String[] cg : Panorama.CARGOS) {
            Integer k = p.cand.get(cg[0]);
            if (k != null && k > 0) kv(c, card, cg[1], n(k));
        }
        if (p.tot > 0) kv(c, card, "Total", n(p.tot) + String.format(BR, " (%.1f%% mulheres)", womenPct(p)));
        v.addView(card, Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        LinearLayout b = Ui.card(c);
        b.addView(Ui.text(c, "BANCADA ATUAL", 10, Ui.MUTED, true));
        kv(c, b, "Câmara dos Deputados", n(p.cam) + " de " + n(data.totCam));
        kv(c, b, "Senado Federal", n(p.sen) + " de " + n(data.totSen));
        if (!p.federacao.isEmpty()) {
            String[] f = data.federations.get(p.federacao);
            kv(c, b, "Federação 2026", f == null || f[0].isEmpty() ? pretty(p.federacao) : f[0] + " (" + pretty(f[1]) + ")");
        }
        v.addView(b, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        v.addView(Ui.text(c, "Fonte: TSE, Câmara dos Deputados e Senado Federal. Contagens oficiais; o app não classifica partidos nem projeta resultados.", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 2, 12, 2, 4));
        return v;
    }

    private View federationsView() {
        Context c = host.ctx();
        LinearLayout v = Ui.col(c);
        v.addView(Ui.text(c, "Federações partidárias 2026", 19, Ui.TEXT, true));
        v.addView(Ui.text(c, "Partidos que concorrem juntos como um só bloco, com a mesma atuação em todo o país por, no mínimo, quatro anos.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));
        for (Map.Entry<String, String[]> e : data.federations.entrySet()) {
            LinearLayout card = Ui.card(c);
            String nome = e.getValue()[0].isEmpty() ? pretty(e.getKey()) : e.getValue()[0];
            card.addView(Ui.text(c, nome, 14, Ui.TEXT, true));
            card.addView(Ui.text(c, pretty(e.getValue()[1].isEmpty() ? e.getKey() : e.getValue()[1]), 11, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, 2, 0, 0));
            v.addView(card, Ui.margins(Ui.lp(-1, -2), 0, 8, 0, 0));
        }
        return v;
    }

    private void kv(Context c, LinearLayout box, String k, String val) {
        LinearLayout r = Ui.row(c);
        r.addView(Ui.text(c, k, 11, Ui.MUTED, false), Ui.lp(0, -2, 0.45f));
        r.addView(Ui.text(c, val, 12, Ui.TEXT, true), Ui.lp(0, -2, 0.55f));
        r.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        box.addView(r);
    }
}
