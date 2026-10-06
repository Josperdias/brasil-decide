package br.com.centraleleicoes.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replay da apuração: lê os snapshots gravados pelo coletor (branch "eleicao-data": snapshots.ndjson + meta.json) e deriva a linha do
 * tempo da noite. O TSE só mostra o "agora"; sem o coletor não existe histórico. Nenhuma previsão: só o que realmente aconteceu.
 */
final class Replay {
    private Replay() {}

    static final String DATA_BASE = "https://raw.githubusercontent.com/Josperdias/concursos-df/eleicao-data/2026/";
    static final String[] UFS = {"AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"};

    static final class Part {
        double p;
        long sec, secTotal, electorate, turnout, absent, valid, blank, nulls;
        String[] nums = new String[0];
        long[] votes = new long[0];

        String leader() { return nums.length > 0 && votes[0] > 0 ? nums[0] : null; }
        long gap() { return votes.length > 1 ? votes[0] - votes[1] : (votes.length == 1 ? votes[0] : 0); }
        long votesOf(String num) { for (int i = 0; i < nums.length; i++) if (nums[i].equals(num)) return votes[i]; return 0; }
    }

    static final class Snap {
        long t;
        String date = "", hour = "";
        Part br, zz;
        final Map<String, Part> uf = new HashMap<>(), gv = new HashMap<>();
    }

    static final class Event {
        int idx;            // posição do snapshot
        long t;
        String icon, title, detail, uf = "";
        boolean flip;       // virada de liderança
    }

    static final class Series {
        int turn;
        final List<Snap> snaps = new ArrayList<>();
        final Map<String, String[]> pres = new HashMap<>();                 // número -> {nome, partido, sq}
        final Map<String, Map<String, String[]>> gov = new HashMap<>();     // UF -> número -> {...}
        final List<Event> events = new ArrayList<>();
        String error = "";
        boolean demo;   // série de demonstração (simulada) — só para teste, sempre avisada na tela

        String presName(String num) { String[] r = pres.get(num); return r == null ? "Candidato " + num : r[0]; }
        String govName(String uf, String num) {
            Map<String, String[]> m = gov.get(uf);
            String[] r = m == null ? null : m.get(num);
            return r == null ? "Candidato " + num : r[0];
        }
    }

    // ------------------------------------------------------------------ leitura
    private static Part part(JSONObject o) {
        if (o == null) return null;
        Part p = new Part();
        p.p = o.optDouble("p");
        JSONArray s = o.optJSONArray("s"), e = o.optJSONArray("e"), v = o.optJSONArray("v"), c = o.optJSONArray("c");
        if (s != null && s.length() >= 2) { p.sec = s.optLong(0); p.secTotal = s.optLong(1); }
        if (e != null && e.length() >= 3) { p.electorate = e.optLong(0); p.turnout = e.optLong(1); p.absent = e.optLong(2); }
        if (v != null && v.length() >= 3) { p.valid = v.optLong(0); p.blank = v.optLong(1); p.nulls = v.optLong(2); }
        int n = c == null ? 0 : c.length();
        p.nums = new String[n];
        p.votes = new long[n];
        for (int i = 0; i < n; i++) {
            JSONArray x = c.optJSONArray(i);
            p.nums[i] = x == null ? "" : x.optString(0);
            p.votes[i] = x == null ? 0 : x.optLong(1);
        }
        return p;
    }

    /** {@code ndjson}: um snapshot por linha; {@code meta}: cadastro dos candidatos. Linhas inválidas são ignoradas. */
    static Series parse(int turn, String ndjson, String meta) {
        Series s = new Series();
        s.turn = turn;
        try {
            JSONObject m = new JSONObject(meta);
            s.demo = m.optBoolean("demo");
            JSONObject pr = m.optJSONObject("pres");
            for (Iterator<String> it = pr == null ? new ArrayList<String>().iterator() : pr.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject c = pr.optJSONObject(k);
                if (c != null) s.pres.put(k, new String[]{c.optString("nm"), c.optString("sg"), c.optString("sq")});
            }
            JSONObject gv = m.optJSONObject("gov");
            for (Iterator<String> it = gv == null ? new ArrayList<String>().iterator() : gv.keys(); it.hasNext(); ) {
                String uf = it.next();
                JSONObject cs = gv.optJSONObject(uf);
                Map<String, String[]> mm = new HashMap<>();
                for (Iterator<String> it2 = cs == null ? new ArrayList<String>().iterator() : cs.keys(); it2.hasNext(); ) {
                    String k = it2.next();
                    JSONObject c = cs.optJSONObject(k);
                    if (c != null) mm.put(k, new String[]{c.optString("nm"), c.optString("sg"), c.optString("sq")});
                }
                s.gov.put(uf, mm);
            }
        } catch (Throwable t) { s.error = "cadastro de candidatos indisponível"; }
        for (String line : ndjson.split("\n")) {
            if (line.trim().isEmpty()) continue;
            try {
                JSONObject o = new JSONObject(line);
                Snap sn = new Snap();
                sn.t = o.optLong("t") * 1000L;
                sn.date = o.optString("d");
                sn.hour = o.optString("h");
                sn.br = part(o.optJSONObject("br"));
                sn.zz = part(o.optJSONObject("zz"));
                JSONObject uf = o.optJSONObject("uf"), gv = o.optJSONObject("gv");
                for (String u : UFS) {
                    Part a = uf == null ? null : part(uf.optJSONObject(u)), b = gv == null ? null : part(gv.optJSONObject(u));
                    if (a != null) sn.uf.put(u, a);
                    if (b != null) sn.gv.put(u, b);
                }
                if (sn.br != null || !sn.uf.isEmpty()) s.snaps.add(sn);
            } catch (Throwable ignored) { }
        }
        buildEvents(s);
        return s;
    }

    // ------------------------------------------------------------------ eventos (linha do tempo da noite / momento da virada)
    private static Event ev(Series s, int idx, String icon, String title, String detail, String uf, boolean flip) {
        Event e = new Event();
        e.idx = idx; e.t = s.snaps.get(idx).t; e.icon = icon; e.title = title; e.detail = detail; e.uf = uf; e.flip = flip;
        s.events.add(e);
        return e;
    }

    private static String n(long v) { return String.format(new Locale("pt", "BR"), "%,d", v); }

    static void buildEvents(Series s) {
        s.events.clear();
        if (s.snaps.isEmpty()) return;
        final int[] marks = {10, 25, 50, 75, 90, 95, 99, 100};
        int nextMark = 0;
        String natLead = null;
        boolean gapBelowMillion = false;
        Map<String, String> ufLead = new HashMap<>(), gvLead = new HashMap<>();
        Map<String, Integer> ufMark = new HashMap<>();
        ev(s, 0, "🟢", "Primeiro registro da apuração", "Brasil com " + String.format(Locale.US, "%.2f", s.snaps.get(0).br == null ? 0 : s.snaps.get(0).br.p).replace('.', ',') + "% das seções totalizadas.", "", false);
        for (int i = 0; i < s.snaps.size(); i++) {
            Snap sn = s.snaps.get(i);
            if (sn.br != null) {
                while (nextMark < marks.length && sn.br.p >= marks[nextMark]) {
                    if (i > 0) ev(s, i, marks[nextMark] == 100 ? "✅" : "📊", "Brasil atinge " + marks[nextMark] + "% das seções", n(sn.br.sec) + " de " + n(sn.br.secTotal) + " seções totalizadas.", "", false);
                    nextMark++;
                }
                String lead = sn.br.leader();
                if (lead != null) {
                    if (natLead != null && !natLead.equals(lead))
                        ev(s, i, "🔄", "Mudança de líder nacional", s.presName(lead) + " assumiu a 1ª posição • diferença: " + n(sn.br.gap()) + " votos.", "", true);
                    natLead = lead;
                }
                if (!gapBelowMillion && sn.br.votes.length > 1 && sn.br.gap() < 1_000_000L && sn.br.valid > 5_000_000L) {
                    gapBelowMillion = true;
                    ev(s, i, "📉", "Diferença nacional abaixo de 1 milhão", "Entre 1º e 2º: " + n(sn.br.gap()) + " votos.", "", false);
                }
            }
            for (String u : UFS) {
                Part p = sn.uf.get(u);
                if (p != null) {
                    String l = p.leader();
                    String prev = ufLead.get(u);
                    if (l != null) {
                        if (prev != null && !prev.equals(l) && p.votes.length > 1)
                            ev(s, i, "🔄", "Virada em " + u + " (Presidente)", s.presName(l) + " assumiu a liderança • diferença atual: " + n(p.gap()) + " votos.", u, true);
                        ufLead.put(u, l);
                    }
                    int mk = ufMark.containsKey(u) ? ufMark.get(u) : 0;
                    if (p.p >= 100 && mk < 100) { ufMark.put(u, 100); ev(s, i, "✅", u + " chega a 100% das seções", "Presidente • " + n(p.sec) + " seções.", u, false); }
                    else if (p.p >= 50 && mk < 50) { ufMark.put(u, 50); ev(s, i, "📊", u + " ultrapassa 50% das seções", "Presidente.", u, false); }
                }
                Part g = sn.gv.get(u);
                if (g != null) {
                    String l = g.leader();
                    String prev = gvLead.get(u);
                    if (l != null) {
                        if (prev != null && !prev.equals(l) && g.votes.length > 1)
                            ev(s, i, "🔄", "Virada em " + u + " (Governador)", s.govName(u, l) + " assumiu a liderança • diferença atual: " + n(g.gap()) + " votos.", u, true);
                        gvLead.put(u, l);
                    }
                }
            }
        }
        java.util.Collections.sort(s.events, (a, b) -> a.idx != b.idx ? Integer.compare(a.idx, b.idx) : 0);
    }

    // ------------------------------------------------------------------ rede
    /** Baixa o texto de um arquivo da branch de dados; devolve "" se ainda não existir. */
    static String download(String name) {
        try {
            int[] st = {0};
            String body = Tse.httpGet(DATA_BASE + name, st);
            return st[0] == 200 ? body : "";
        } catch (Throwable t) { return ""; }
    }

    static Series load(int turn, String dir) {
        String nd = download(dir + "/snapshots.ndjson"), meta = download(dir + "/meta.json");
        if (nd.isEmpty()) { Series s = new Series(); s.turn = turn; s.error = "Ainda não há registros desta apuração."; return s; }
        return parse(turn, nd, meta.isEmpty() ? "{}" : meta);
    }

    static LinkedHashMap<String, Integer> flipsPerUf(Series s) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        for (Event e : s.events) if (e.flip && !e.uf.isEmpty()) m.merge(e.uf, 1, Integer::sum);
        return m;
    }
}
