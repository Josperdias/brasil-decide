package br.com.centraleleicoes.nativeapp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eleições passadas. 1994–2022: arquivos gerados no CI a partir dos dados abertos do TSE (votação por candidato, somada por UF).
 * 1894–1989: tabela curada em assets/historico_antigas.json, com a fonte de cada número. Só resultado oficial; sem comentário político.
 */
final class History {
    private History() {}

    static final String BASE = Replay.DATA_BASE.replace("/2026/", "/historico/");
    /** A partir deste ano os arquivos vêm dos dados abertos do TSE (por UF). Antes, tabela embarcada. */
    static final int PRIMEIRO_ANO_TSE = 1994;

    static final class Cand {
        String nome = "", partido = "", situacao = "";
        long votos;          // 0 = desconhecido
        double pct = -1;     // percentual informado pela fonte (quando não há votos absolutos)
        boolean eleito;
    }

    /** Resultado de um cargo num turno: UF (ou "BR") -> candidatos, do mais votado ao menos. */
    static final class Race {
        Map<String, List<Cand>> byUf = new LinkedHashMap<>();
    }

    static final class Election {
        int ano;
        String tipo = "direta", nota = "", fonte = "", vencedor = "", partido = "";
        double pctVencedor = -1;
        String turnoVencedor = "";
        /** cargo ("pres"/"gov") -> turno ("1"/"2") -> corrida. */
        Map<String, Map<String, Race>> races = new LinkedHashMap<>();

        Race race(String cargo, String turno) {
            Map<String, Race> m = races.get(cargo);
            return m == null ? null : m.get(turno);
        }

        boolean hasGov() { return races.containsKey("gov"); }
    }

    /** Linha do índice (linha do tempo de vencedores). */
    static final class Summary {
        int ano;
        String tipo = "direta", vencedor = "", partido = "", turno = "", nota = "";
        double pct = -1;
        long votos;
    }

    static List<Cand> parseCands(JSONArray a) {
        List<Cand> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.optJSONObject(i);
            if (j == null) continue;
            Cand c = new Cand();
            c.nome = j.optString("n");
            c.partido = j.optString("p");
            c.situacao = j.optString("s");
            c.votos = j.optLong("v", 0);
            c.pct = j.has("x") ? j.optDouble("x", -1) : -1;
            c.eleito = j.optInt("w", 0) == 1;
            out.add(c);
        }
        return out;
    }

    static Election parseElection(JSONObject o) {
        Election e = new Election();
        e.ano = o.optInt("ano");
        e.tipo = o.optString("tipo", "direta");
        e.nota = o.optString("nota");
        e.fonte = o.optString("fonte");
        for (String cargo : new String[]{"pres", "gov"}) {
            JSONObject turnos = o.optJSONObject(cargo);
            if (turnos == null) continue;
            Map<String, Race> byTurno = new LinkedHashMap<>();
            Iterator<String> it = turnos.keys();
            while (it.hasNext()) {
                String t = it.next();
                JSONObject ufs = turnos.optJSONObject(t);
                if (ufs == null) continue;
                Race r = new Race();
                Iterator<String> iu = ufs.keys();
                while (iu.hasNext()) {
                    String uf = iu.next();
                    List<Cand> l = parseCands(ufs.optJSONArray(uf));
                    if (!l.isEmpty()) r.byUf.put(uf, l);
                }
                if (!r.byUf.isEmpty()) byTurno.put(t, r);
            }
            if (!byTurno.isEmpty()) e.races.put(cargo, byTurno);
        }
        return e;
    }

    private static Summary summarize(Election e) {
        Summary s = new Summary();
        s.ano = e.ano;
        s.tipo = e.tipo;
        s.nota = e.nota;
        for (String t : new String[]{"2", "1"}) {
            Race r = e.race("pres", t);
            List<Cand> l = r == null ? null : r.byUf.get("BR");
            if (l == null) continue;
            for (Cand c : l) {
                if (!c.eleito) continue;
                s.vencedor = c.nome;
                s.partido = c.partido;
                s.turno = t;
                s.votos = c.votos;
                s.pct = pct(l, c);
                return s;
            }
        }
        return s;
    }

    /** Percentual informado pela fonte ou, com o placar completo, sobre a soma dos votos dos candidatos (-1 = desconhecido). */
    static double pct(List<Cand> all, Cand c) {
        if (c.pct >= 0) return c.pct;
        if (c.votos <= 0 || all.size() < 2) return -1;
        long tot = 0;
        for (Cand x : all) tot += x.votos;
        return tot > 0 ? 100.0 * c.votos / tot : -1;
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            return readAll(in);
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static File cacheFile(Context ctx, String name) { return new File(ctx.getCacheDir(), "hist-" + name); }

    private static String cached(Context ctx, String name) {
        try {
            File f = cacheFile(ctx, name);
            if (!f.isFile() || f.length() == 0) return null;
            try (FileInputStream in = new FileInputStream(f)) { return readAll(in); }
        } catch (Throwable t) { return null; }
    }

    private static void store(Context ctx, String name, String body) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(ctx, name))) { out.write(body.getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
    }

    /** Eleições antigas (assets). */
    static Map<Integer, Election> loadOld(Context ctx) {
        Map<Integer, Election> m = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONObject(readAsset(ctx, "historico_antigas.json")).getJSONArray("eleicoes");
            for (int i = 0; i < arr.length(); i++) {
                Election e = parseElection(arr.getJSONObject(i));
                if (e.ano > 0) m.put(e.ano, e);
            }
        } catch (Throwable ignored) { }
        return m;
    }

    /** Índice 1994+ (rede, com cache em disco para abrir offline). */
    static List<Summary> loadIndex(Context ctx) {
        List<Summary> out = new ArrayList<>();
        String body = null;
        try {
            int[] st = {0};
            body = Tse.httpGet(BASE + "index.json", st);
            if (st[0] == 200 && !body.isEmpty()) store(ctx, "index.json", body); else body = null;
        } catch (Throwable ignored) { body = null; }
        if (body == null) body = cached(ctx, "index.json");
        if (body == null) return out;
        try {
            JSONArray a = new JSONObject(body).getJSONArray("eleicoes");
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                Summary s = new Summary();
                s.ano = j.optInt("ano");
                s.vencedor = j.optString("vencedor");
                s.partido = j.optString("partido");
                s.turno = j.optString("turno");
                s.pct = j.optDouble("pct", -1);
                s.votos = j.optLong("votos", 0);
                out.add(s);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Eleição de um ano 1994+ (cache em disco: o resultado de uma eleição passada não muda). */
    static Election loadYear(Context ctx, int ano) {
        String name = ano + ".json";
        String body = cached(ctx, name);
        try {
            if (body == null) {
                int[] st = {0};
                String b = Tse.httpGet(BASE + name, st);
                if (st[0] == 200 && !b.isEmpty()) { body = b; store(ctx, name, b); }
            }
            return body == null ? null : parseElection(new JSONObject(body));
        } catch (Throwable t) { return null; }
    }

    static Summary summaryOf(Election e) { return summarize(e); }
}
