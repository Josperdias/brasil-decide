package br.com.centraleleicoes.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
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
 * Voto no exterior (abrangência ZZ do TSE): ingestão, agregação e cache. Sem interface aqui.
 *
 * Arquivos oficiais usados (ciclo 2026, eleição federal {fed} = 6257/6258):
 *   .../dados/zz/zz-c0001-e{fed}-u.json            resultado consolidado do exterior (seções, eleitorado, votos, candidatos)
 *   .../config/mun-e{fed}-cm.json                   lista das localidades (código + nome; o TSE NÃO informa o país)
 *   .../dados/zz/zz{codigo}-c0001-e{fed}-u.json     resultado de cada localidade (mesma estrutura)
 * O país de cada localidade vem da tabela ExteriorData.LOC (do Brasil Decide); percentuais agregados são sempre
 * calculados sobre a SOMA dos votos, nunca por média de percentuais.
 */
final class Exterior {
    private Exterior() {}

    static final class Locality {
        String code = "", name = "", iso2 = "";
        Model.Result r;
    }

    /** Agregado de localidades (um país, um continente ou o total). */
    static final class Agg {
        String key = "", name = "", continent = "";
        final Map<String, Long> votes = new LinkedHashMap<>();   // número do candidato -> votos
        long valid, blank, nulls, electorate, turnout, absent, sec, secTotal;
        int locals, localsWithData;
        final List<Locality> locs = new ArrayList<>();
        final List<Model.Cand> cands = new ArrayList<>();       // ordenados por votos, % sobre os válidos somados

        double progress() { return secTotal > 0 ? 100.0 * sec / secTotal : 0; }
        double turnoutPct() { return electorate > 0 ? 100.0 * turnout / electorate : 0; }
        double abstPct() { return electorate > 0 ? 100.0 * absent / electorate : 0; }
        Model.Cand lead() { return !cands.isEmpty() && cands.get(0).votos > 0 ? cands.get(0) : null; }
        long gap() { return cands.size() > 1 ? cands.get(0).votos - cands.get(1).votos : (cands.isEmpty() ? 0 : cands.get(0).votos); }
        double gapPct() { return cands.size() > 1 ? cands.get(0).pct - cands.get(1).pct : (cands.isEmpty() ? 0 : cands.get(0).pct); }
        boolean done() { return secTotal > 0 && sec >= secTotal; }
    }

    static final class Snapshot {
        int turn;
        long at;                 // quando o app baixou
        boolean fromCache;
        boolean absent;          // arquivo ZZ ainda não publicado (404)
        String error = "";
        Model.Result zz;
        final Map<String, Locality> locs = new LinkedHashMap<>();
        final Map<String, Agg> countries = new LinkedHashMap<>();   // iso2
        final Map<String, Agg> continents = new LinkedHashMap<>();  // nome do continente
        Agg total;
        final Map<String, String[]> registry = new HashMap<>();     // número -> {nome, partido}
    }

    // ------------------------------------------------------------------ tabelas
    private static Map<String, String> locIso;
    private static Map<String, String[]> countryInfo;   // iso2 -> {nome, continente}

    static synchronized String isoOf(String locName) {
        if (locIso == null) {
            locIso = new HashMap<>();
            for (String[] p : ExteriorData.LOC) locIso.put(p[0], p[1]);
        }
        String v = locIso.get(norm(locName));
        return v == null ? "" : v;
    }

    static synchronized String[] info(String iso2) {
        if (countryInfo == null) {
            countryInfo = new HashMap<>();
            for (String[] c : ExteriorData.COUNTRIES) countryInfo.put(c[0], new String[]{c[1], c[2]});
        }
        String[] i = countryInfo.get(iso2);
        return i == null ? new String[]{iso2, ""} : i;
    }

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u036f]", "").replaceAll("[^A-Z0-9]+", " ").trim();
    }

    static String flag(String iso2) {
        if (iso2 == null || iso2.length() != 2) return "🏳️";
        int a = Character.codePointAt(iso2, 0) - 'A' + 0x1F1E6, b = Character.codePointAt(iso2, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }

    // ------------------------------------------------------------------ ingestão
    private static String locUrl(String fed, String code) {
        return Tse.BASE + "/" + Tse.CICLO + "/" + fed + "/dados/zz/zz" + code + "-c0001-e"
                + String.format(Locale.ROOT, "%06d", Integer.parseInt(fed)) + "-u.json";
    }

    /** Baixa o consolidado ZZ e cada localidade. Falha numa localidade não derruba as demais. */
    static Snapshot load(final String fed, int turn, Snapshot previous) {
        Snapshot s = new Snapshot();
        s.turn = turn;
        s.at = System.currentTimeMillis();
        try {
            Tse.Fetch z = Tse.fetch(Tse.url(fed, "zz", 1));
            if (z.absent) { s.absent = true; return keepOld(s, previous, "Resultado do exterior ainda não publicado pelo TSE."); }
            if (z.error != null || z.result == null) return keepOld(s, previous, "TSE indisponível (" + z.error + ").");
            s.zz = z.result;
            for (Model.Cand c : z.result.cands) s.registry.put(c.numero, new String[]{c.nome, c.partido});
            int[] st = {0};
            String cfg = Tse.httpGet(Tse.BASE + "/" + Tse.CICLO + "/" + fed + "/config/mun-e" + String.format(Locale.ROOT, "%06d", Integer.parseInt(fed)) + "-cm.json", st);
            List<String[]> muns = new ArrayList<>();
            if (st[0] == 200) {
                JSONArray abr = new JSONObject(cfg).optJSONArray("abr");
                for (int i = 0; abr != null && i < abr.length(); i++) {
                    JSONObject a = abr.optJSONObject(i);
                    if (a == null || !"zz".equalsIgnoreCase(a.optString("cd"))) continue;
                    JSONArray mu = a.optJSONArray("mu");
                    for (int k = 0; mu != null && k < mu.length(); k++) {
                        JSONObject m = mu.optJSONObject(k);
                        if (m != null) muns.add(new String[]{m.optString("cd"), m.optString("nm")});
                    }
                }
            }
            if (muns.isEmpty() && previous != null) for (Locality l : previous.locs.values()) muns.add(new String[]{l.code, l.name});
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<Locality>> fs = new ArrayList<>();
                for (final String[] m : muns) {
                    fs.add(pool.submit((Callable<Locality>) () -> {
                        Locality l = new Locality();
                        l.code = m[0];
                        l.name = m[1];
                        l.iso2 = isoOf(m[1]);
                        Tse.Fetch f = Tse.fetch(locUrl(fed, m[0]));
                        l.r = f.result;
                        return l;
                    }));
                }
                for (Future<Locality> f : fs) {
                    try {
                        Locality l = f.get();
                        if (l.r == null && previous != null && previous.locs.get(l.code) != null) l.r = previous.locs.get(l.code).r; // mantém o último válido
                        s.locs.put(l.code, l);
                    } catch (Throwable ignored) { }
                }
            } finally { pool.shutdown(); }
            aggregate(s);
            return s;
        } catch (Throwable t) {
            return keepOld(s, previous, "Falha ao ler o exterior: " + t.getClass().getSimpleName());
        }
    }

    private static Snapshot keepOld(Snapshot fresh, Snapshot previous, String why) {
        if (previous != null) { previous.error = why; previous.fromCache = true; return previous; }
        fresh.error = why;
        return fresh;
    }

    // ------------------------------------------------------------------ agregação (soma de votos; nunca média de %)
    static void aggregate(Snapshot s) {
        s.countries.clear();
        s.continents.clear();
        Agg total = new Agg();
        total.key = "ZZ";
        total.name = "Exterior";
        for (Locality l : s.locs.values()) {
            if (l.iso2.isEmpty()) continue;
            String[] inf = info(l.iso2);
            Agg c = s.countries.get(l.iso2);
            if (c == null) { c = new Agg(); c.key = l.iso2; c.name = inf[0]; c.continent = inf[1]; s.countries.put(l.iso2, c); }
            c.locs.add(l);
            add(c, l.r);
        }
        for (Agg c : s.countries.values()) {
            Collections.sort(c.locs, (a, b) -> a.name.compareTo(b.name));
            Agg k = s.continents.get(c.continent);
            if (k == null) { k = new Agg(); k.key = c.continent; k.name = c.continent; k.continent = c.continent; s.continents.put(c.continent, k); }
            merge(k, c);
            merge(total, c);
        }
        for (Agg c : s.countries.values()) finish(c, s);
        for (Agg k : s.continents.values()) finish(k, s);
        finish(total, s);
        s.total = total;
    }

    private static void add(Agg a, Model.Result r) {
        a.locals++;
        if (r == null) return;
        a.localsWithData++;
        a.valid += r.valid; a.blank += r.blank; a.nulls += r.nulls; a.electorate += r.electorate; a.turnout += r.turnout;
        a.absent += r.absent; a.sec += r.sections; a.secTotal += r.sectionsTotal;
        for (Model.Cand c : r.cands) a.votes.merge(c.numero, c.votos, Long::sum);
    }

    private static void merge(Agg into, Agg from) {
        into.locals += from.locals; into.localsWithData += from.localsWithData;
        into.valid += from.valid; into.blank += from.blank; into.nulls += from.nulls; into.electorate += from.electorate;
        into.turnout += from.turnout; into.absent += from.absent; into.sec += from.sec; into.secTotal += from.secTotal;
        for (Map.Entry<String, Long> e : from.votes.entrySet()) into.votes.merge(e.getKey(), e.getValue(), Long::sum);
    }

    private static void finish(Agg a, Snapshot s) {
        a.cands.clear();
        for (Map.Entry<String, Long> e : a.votes.entrySet()) {
            Model.Cand c = new Model.Cand();
            c.numero = e.getKey();
            String[] reg = s.registry.get(e.getKey());
            c.nome = reg == null ? "Candidato " + e.getKey() : reg[0];
            c.partido = reg == null ? "" : reg[1];
            c.votos = e.getValue();
            c.pct = a.valid > 0 ? 100.0 * c.votos / a.valid : 0;
            a.cands.add(c);
        }
        Collections.sort(a.cands, (x, y) -> Long.compare(y.votos, x.votos));
    }

    // ------------------------------------------------------------------ cache em arquivo (último snapshot válido por turno)
    static void save(File dir, Snapshot s) {
        if (s == null || s.zz == null) return;
        try {
            JSONObject o = new JSONObject();
            o.put("turn", s.turn).put("at", s.at).put("zz", s.zz.toJson());
            JSONArray ls = new JSONArray();
            for (Locality l : s.locs.values()) {
                JSONObject j = new JSONObject().put("c", l.code).put("n", l.name);
                if (l.r != null) j.put("r", l.r.toJson());
                ls.put(j);
            }
            o.put("locs", ls);
            File tmp = new File(dir, "exterior_t" + s.turn + ".tmp");
            try (FileOutputStream f = new FileOutputStream(tmp)) { f.write(o.toString().getBytes(StandardCharsets.UTF_8)); }
            tmp.renameTo(new File(dir, "exterior_t" + s.turn + ".json"));
        } catch (Throwable ignored) { }
    }

    static Snapshot restore(File dir, int turn) {
        try {
            File f = new File(dir, "exterior_t" + turn + ".json");
            if (!f.exists()) return null;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) { int off = 0, n; while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n; }
            JSONObject o = new JSONObject(new String(b, StandardCharsets.UTF_8));
            Snapshot s = new Snapshot();
            s.turn = turn;
            s.at = o.optLong("at");
            s.fromCache = true;
            s.zz = Model.Result.fromJson(o.getJSONObject("zz"));
            for (Model.Cand c : s.zz.cands) s.registry.put(c.numero, new String[]{c.nome, c.partido});
            JSONArray ls = o.optJSONArray("locs");
            for (int i = 0; ls != null && i < ls.length(); i++) {
                JSONObject j = ls.getJSONObject(i);
                Locality l = new Locality();
                l.code = j.optString("c");
                l.name = j.optString("n");
                l.iso2 = isoOf(l.name);
                JSONObject r = j.optJSONObject("r");
                if (r != null) l.r = Model.Result.fromJson(r);
                s.locs.put(l.code, l);
            }
            aggregate(s);
            return s;
        } catch (Throwable t) { return null; }
    }
}
