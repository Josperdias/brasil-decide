package br.com.centraleleicoes.nativeapp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Reserva do 1º turno de 2026: totais finais COLETADOS DO TSE em 05/10/2026 (assets/pres1_coleta_2026.json, gerado por
 * tools/build_pres1_coleta.py a partir da branch eleicao-data). Nada é simulado nem estimado. Só entra quando o TSE publica
 * arquivos zerados e o aparelho não tem o resultado final salvo; qualquer resultado útil do TSE ao vivo tem prioridade.
 * A coleta traz os primeiros colocados de cada disputa (não todos os candidatos) e não traz a situação textual (eleito / 2º turno),
 * que aqui é derivada da regra constitucional: mais de 50% dos votos válidos elege no 1º turno; senão vão os dois primeiros.
 */
final class FirstRound {
    private FirstRound() {}

    static final String ASSET = "pres1_coleta_2026.json";
    static final String SRC = "coleta";

    static final class Data {
        Model.Result pres, zz;
        final Map<String, Model.Result> states = new HashMap<>(), gov = new HashMap<>();
        Exterior.Snapshot exterior;
        String label = "";
    }

    /** Aviso exibido junto da data do arquivo quando o resultado é a reserva coletada. */
    static String srcNote(Model.Result r) {
        return r != null && SRC.equals(r.src) ? " • reserva: total final coletado do TSE" : "";
    }

    private static Data cached;

    static synchronized Data get(Context ctx) {
        if (cached != null) return cached;
        try {
            InputStream in = ctx.getAssets().open(ASSET);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            cached = parse(out.toString(StandardCharsets.UTF_8.name()));
        } catch (Throwable t) {
            cached = new Data();
        }
        return cached;
    }

    static Data parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Data d = new Data();
        String date = o.optString("d"), time = o.optString("h"), fed = o.optString("fed"), est = o.optString("est");
        long t = o.optLong("t");
        d.label = "coleta do TSE em " + date;
        Map<String, String[]> names = names(o.optJSONObject("pres"));
        d.pres = build(o.optJSONObject("br"), names, Tse.photoBase(fed, "br"), date, time, t, true);
        d.zz = build(o.optJSONObject("zz"), names, Tse.photoBase(fed, "zz"), date, time, t, true);
        JSONObject uf = o.optJSONObject("uf"), gv = o.optJSONObject("gv"), gnames = o.optJSONObject("gov");
        for (Iterator<String> it = uf == null ? null : uf.keys(); it != null && it.hasNext(); ) {
            String k = it.next();
            Model.Result r = build(uf.optJSONObject(k), names, Tse.photoBase(fed, "br"), date, time, t, false);
            if (r != null) d.states.put(k, r);
        }
        for (Iterator<String> it = gv == null ? null : gv.keys(); it != null && it.hasNext(); ) {
            String k = it.next();
            Model.Result r = build(gv.optJSONObject(k), names(gnames == null ? null : gnames.optJSONObject(k)), Tse.photoBase(est, k.toLowerCase(java.util.Locale.ROOT)), date, time, t, true);
            if (r != null) d.gov.put(k, r);
        }
        d.exterior = exterior(o, names, fed, date, time, t, d.zz);
        return d;
    }

    private static Map<String, String[]> names(JSONObject o) {
        Map<String, String[]> m = new HashMap<>();
        for (Iterator<String> it = o == null ? null : o.keys(); it != null && it.hasNext(); ) {
            String k = it.next();
            JSONArray a = o.optJSONArray(k);
            if (a != null) m.put(k, new String[]{a.optString(0), a.optString(1), a.optString(2)});
        }
        return m;
    }

    /** Monta um Model.Result a partir de {p, s:[feitas,total], e:[eleitorado,comparecimento,abstenção], v:[válidos,brancos,nulos], c:[[nº,votos]…]}. */
    static Model.Result build(JSONObject p, Map<String, String[]> names, String photoBase, String date, String time, long t, boolean deriveSit) {
        if (p == null) return null;
        Model.Result r = new Model.Result();
        JSONArray s = p.optJSONArray("s"), e = p.optJSONArray("e"), v = p.optJSONArray("v"), c = p.optJSONArray("c");
        r.progress = p.optDouble("p");
        if (s != null) { r.sections = s.optLong(0); r.sectionsTotal = s.optLong(1); }
        if (e != null) { r.electorate = e.optLong(0); r.turnout = e.optLong(1); r.absent = e.optLong(2); }
        if (v != null) { r.valid = v.optLong(0); r.blank = v.optLong(1); r.nulls = v.optLong(2); }
        r.abstPct = r.electorate > 0 ? 100.0 * r.absent / r.electorate : 0;
        for (int i = 0; c != null && i < c.length(); i++) {
            JSONArray x = c.optJSONArray(i);
            if (x == null) continue;
            Model.Cand k = new Model.Cand();
            k.numero = x.optString(0);
            k.votos = x.optLong(1);
            String[] nm = names.get(k.numero);
            k.nome = nm == null ? "Candidato " + k.numero : nm[0];
            k.partido = nm == null ? "" : nm[1];
            k.sq = nm == null ? "" : nm[2];
            k.pct = r.valid > 0 ? 100.0 * k.votos / r.valid : 0;
            r.cands.add(k);
        }
        r.cands.sort((a, b) -> Long.compare(b.votos, a.votos));
        if (deriveSit && r.valid > 0 && !r.cands.isEmpty()) {
            if (r.cands.get(0).pct > 50) r.cands.get(0).sit = "Eleito";
            else for (int i = 0; i < Math.min(2, r.cands.size()); i++) r.cands.get(i).sit = "2º turno";
        }
        r.fin = r.progress >= 99.99 && r.sectionsTotal > 0 && r.sections >= r.sectionsTotal;
        r.date = date;
        r.time = time;
        r.sig = "coleta-" + t;
        r.photoBase = photoBase;
        r.src = SRC;
        return r;
    }

    private static Exterior.Snapshot exterior(JSONObject o, Map<String, String[]> names, String fed, String date, String time, long t, Model.Result zz) {
        JSONObject loc = o.optJSONObject("loc"), zzn = o.optJSONObject("zzn");
        if (zz == null || loc == null) return null;
        Exterior.Snapshot s = new Exterior.Snapshot();
        s.turn = 1;
        s.at = t * 1000L;
        s.fromCache = true;
        s.zz = zz;
        for (Model.Cand c : zz.cands) s.registry.put(c.numero, new String[]{c.nome, c.partido});
        for (Map.Entry<String, String[]> e : names.entrySet()) if (!s.registry.containsKey(e.getKey())) s.registry.put(e.getKey(), new String[]{e.getValue()[0], e.getValue()[1]});
        for (Iterator<String> it = loc.keys(); it.hasNext(); ) {
            String code = it.next();
            Exterior.Locality l = new Exterior.Locality();
            l.code = code;
            l.name = zzn == null ? code : zzn.optString(code, code);
            l.iso2 = Exterior.isoOf(l.name);
            l.r = build(loc.optJSONObject(code), names, Tse.photoBase(fed, "zz"), date, time, t, false);
            s.locs.put(code, l);
        }
        Exterior.aggregate(s);
        return s;
    }
}
