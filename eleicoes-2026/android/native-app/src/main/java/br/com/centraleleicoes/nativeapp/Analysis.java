package br.com.centraleleicoes.nativeapp;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central de Análises: monta um relatório (texto + tabelas) só com contas feitas sobre os números oficiais já baixados.
 * Nenhuma frase interpreta causas, projeta resultado ou classifica candidatos; células são Long/Double/String.
 */
final class Analysis {
    private Analysis() {}

    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);

    static final class Input {
        int turn = 1;
        Model.Result pres;
        Map<String, Model.Result> states = new LinkedHashMap<>(), gov = new LinkedHashMap<>();
        Exterior.Snapshot ex;
        String[][] ufs;               // {sigla, nome, região}
        boolean withGov = true, withEx = true;
    }

    static final class Table {
        String title, note = "";
        String[] head;
        final List<Object[]> rows = new ArrayList<>();
        Table(String title, String... head) { this.title = title; this.head = head; }
        Table row(Object... cells) { rows.add(cells); return this; }
    }

    static final class Report {
        String title = "", subtitle = "", generated = "", source = "";
        boolean partial;
        final List<String> highlights = new ArrayList<>();
        final List<String> caveats = new ArrayList<>();
        final List<Table> tables = new ArrayList<>();
    }

    static String n(long v) { return INT.format(v); }

    static String pc(double v) { return String.format(BR, "%.2f%%", v); }

    private static String who(Model.Cand c) { return c.nome + (c.partido.isEmpty() ? "" : " (" + c.partido + ")"); }

    private static boolean has(Model.Result r) { return r != null && !r.cands.isEmpty() && r.cands.get(0).votos > 0; }

    static Report build(Input in) {
        Report rep = new Report();
        rep.title = "Brasil Decide — relatório da apuração";
        rep.subtitle = in.turn + "º turno • Presidente da República";
        rep.generated = new SimpleDateFormat("dd/MM/yyyy HH:mm", BR).format(new Date());
        rep.source = "Tribunal Superior Eleitoral (resultados.tse.jus.br). Contas feitas pelo app sobre os números oficiais; sem projeção.";
        Model.Result p = in.pres;
        rep.partial = p == null || (p.progress < 100 && !p.fin);
        if (p == null) {
            rep.highlights.add("Os dados ainda não foram carregados. Abra o app com internet e tente de novo.");
            return rep;
        }
        rep.highlights.add("Apuração nacional: " + pc(p.progress) + " das seções totalizadas (" + n(p.sections) + " de " + n(p.sectionsTotal) + ")" + (p.fin ? ", totalização final." : "."));
        for (int i = 0; i < Math.min(3, p.cands.size()); i++) {
            Model.Cand c = p.cands.get(i);
            rep.highlights.add((i + 1) + "º " + who(c) + ": " + n(c.votos) + " votos (" + pc(c.pct) + " dos válidos).");
        }
        if (p.cands.size() > 1 && has(p)) {
            rep.highlights.add("Diferença entre o 1º e o 2º: " + n(p.cands.get(0).votos - p.cands.get(1).votos) + " votos ("
                    + String.format(BR, "%.2f", p.cands.get(0).pct - p.cands.get(1).pct) + " p.p.).");
        }
        if (p.electorate > 0) {
            rep.highlights.add("Comparecimento: " + n(p.turnout) + " de " + n(p.electorate) + " eleitores (" + pc(100.0 * p.turnout / p.electorate)
                    + "); abstenção: " + n(p.absent) + " (" + pc(p.abstPct) + "). Brancos: " + n(p.blank) + "; nulos: " + n(p.nulls) + ".");
        }

        Table cand = new Table("Candidatos — Brasil", "Posição", "Candidato", "Partido", "Número", "Votos", "% dos válidos", "Situação");
        for (int i = 0; i < p.cands.size(); i++) {
            Model.Cand c = p.cands.get(i);
            cand.row((long) (i + 1), c.nome, c.partido, c.numero, c.votos, c.pct, c.sit);
        }
        rep.tables.add(cand);

        // estados
        Table st = new Table("Estados — Presidente", "UF", "Estado", "Região", "Apuração (%)", "1º colocado", "Partido", "Votos do 1º",
                "% do 1º", "2º colocado", "% do 2º", "Diferença (votos)", "Diferença (p.p.)");
        Map<String, Integer> leads = new LinkedHashMap<>();
        Map<String, String> leadKeyName = new LinkedHashMap<>();
        List<Object[]> tight = new ArrayList<>();
        int withData = 0;
        for (String[] u : in.ufs) {
            Model.Result r = in.states.get(u[0]);
            if (!has(r)) { st.row(u[0], u[1], u[2], r == null ? 0.0 : r.progress, "sem dados", "", 0L, 0.0, "", 0.0, 0L, 0.0); continue; }
            withData++;
            Model.Cand a = r.cands.get(0), b = r.cands.size() > 1 ? r.cands.get(1) : null;
            long gap = b == null ? a.votos : a.votos - b.votos;
            double gapPp = b == null ? a.pct : a.pct - b.pct;
            st.row(u[0], u[1], u[2], r.progress, a.nome, a.partido, a.votos, a.pct, b == null ? "" : b.nome, b == null ? 0.0 : b.pct, gap, gapPp);
            String key = a.numero + "|" + a.nome;
            leads.put(key, leads.containsKey(key) ? leads.get(key) + 1 : 1);
            leadKeyName.put(key, who(a));
            if (b != null) tight.add(new Object[]{u[0], gapPp, r.progress});
        }
        rep.tables.add(st);
        if (withData > 0) {
            List<Map.Entry<String, Integer>> es = new ArrayList<>(leads.entrySet());
            Collections.sort(es, (x, y) -> y.getValue() - x.getValue());
            Table lt = new Table("Estados em que cada candidato está na frente", "Candidato", "Estados");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : es) {
                lt.row(leadKeyName.get(e.getKey()), (long) e.getValue());
                if (sb.length() < 220) sb.append(sb.length() > 0 ? "; " : "").append(leadKeyName.get(e.getKey())).append(": ").append(e.getValue());
            }
            rep.tables.add(lt);
            rep.highlights.add("Estados com apuração iniciada: " + withData + " de " + in.ufs.length + ". Na frente por estado — " + sb + ".");
            Collections.sort(tight, (x, y) -> Double.compare((Double) x[1], (Double) y[1]));
            StringBuilder tb = new StringBuilder();
            for (int i = 0; i < Math.min(5, tight.size()); i++) tb.append(i > 0 ? ", " : "").append(tight.get(i)[0]).append(" (").append(String.format(BR, "%.2f", (Double) tight.get(i)[1])).append(" p.p.)");
            if (tb.length() > 0) rep.highlights.add("Menores diferenças entre 1º e 2º" + (rep.partial ? " (apuração parcial pode mudar)" : "") + ": " + tb + ".");
        }

        // governadores
        if (in.withGov) {
            Table gv = new Table("Governadores — líder por UF", "UF", "Estado", "Apuração (%)", "1º colocado", "Partido", "% do 1º", "2º colocado", "% do 2º", "Diferença (p.p.)");
            int k = 0;
            for (String[] u : in.ufs) {
                Model.Result r = in.gov.get(u[0]);
                if (!has(r)) continue;
                Model.Cand a = r.cands.get(0), b = r.cands.size() > 1 ? r.cands.get(1) : null;
                gv.row(u[0], u[1], r.progress, a.nome, a.partido, a.pct, b == null ? "" : b.nome, b == null ? 0.0 : b.pct, b == null ? a.pct : a.pct - b.pct);
                k++;
            }
            if (k > 0) rep.tables.add(gv);
        }

        // exterior
        Exterior.Snapshot ex = in.ex;
        if (in.withEx && ex != null && ex.total != null && ex.total.valid > 0) {
            Exterior.Agg t = ex.total;
            Model.Cand a = t.lead(), b = t.cands.size() > 1 ? t.cands.get(1) : null;
            if (a != null) rep.highlights.add("Brasileiros no exterior (" + t.localsWithData + " de " + t.locals + " localidades com dados, apuração " + pc(t.progress()) + "): 1º "
                    + who(a) + " com " + pc(a.pct) + (b == null ? "" : "; 2º " + who(b) + " com " + pc(b.pct)) + ". Votos válidos: " + n(t.valid) + "; eleitorado: " + n(t.electorate) + ".");
            Table ct = new Table("Exterior — por país ou território", "País", "Continente", "Localidades com dados", "Apuração (%)", "Votos válidos", "1º colocado", "% do 1º", "2º colocado", "% do 2º", "Diferença (votos)");
            List<Exterior.Agg> list = new ArrayList<>(ex.countries.values());
            Collections.sort(list, (x, y) -> Long.compare(y.valid, x.valid));
            for (Exterior.Agg c : list) {
                if (c.valid <= 0) continue;
                Model.Cand c1 = c.lead(), c2 = c.cands.size() > 1 ? c.cands.get(1) : null;
                ct.row(c.name, c.continent, (long) c.localsWithData, c.progress(), c.valid, c1 == null ? "" : c1.nome, c1 == null ? 0.0 : c1.pct, c2 == null ? "" : c2.nome, c2 == null ? 0.0 : c2.pct, c.gap());
            }
            if (!ct.rows.isEmpty()) rep.tables.add(ct);
        }

        if (rep.partial) rep.caveats.add("Apuração parcial: os números mudam à medida que mais seções são totalizadas.");
        rep.caveats.add("Percentuais agregados são calculados sobre a soma dos votos oficiais, nunca por média de percentuais.");
        rep.caveats.add("Este relatório não é um documento oficial do TSE nem uma projeção; consulte resultados.tse.jus.br.");
        return rep;
    }
}
