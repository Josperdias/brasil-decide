package br.com.centraleleicoes.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Modelos simples + parsing tolerante do JSON de resultados do TSE. */
final class Model {
    private Model() {}

    static final class Cand {
        String nome = "", partido = "", numero = "", sit = "";
        long votos;
        double pct;
    }

    static final class Result {
        final List<Cand> cands = new ArrayList<>();
        double progress, abstPct;
        long sections, sectionsTotal, valid, blank;
        String date = "", time = "", sig = "";
        boolean fin;

        Cand lead() { return !cands.isEmpty() && cands.get(0).votos > 0 ? cands.get(0) : null; }

        JSONObject toJson() throws Exception {
            JSONArray a = new JSONArray();
            for (Cand c : cands) a.put(new JSONObject().put("n", c.nome).put("p", c.partido).put("u", c.numero)
                    .put("s", c.sit).put("v", c.votos).put("x", c.pct));
            return new JSONObject().put("c", a).put("pr", progress).put("ap", abstPct).put("se", sections)
                    .put("st", sectionsTotal).put("va", valid).put("bl", blank).put("d", date).put("t", time)
                    .put("g", sig).put("f", fin);
        }

        static Result fromJson(JSONObject o) {
            Result r = new Result();
            JSONArray a = o.optJSONArray("c");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                Cand k = new Cand();
                k.nome = c.optString("n"); k.partido = c.optString("p"); k.numero = c.optString("u");
                k.sit = c.optString("s"); k.votos = c.optLong("v"); k.pct = c.optDouble("x");
                r.cands.add(k);
            }
            r.progress = o.optDouble("pr"); r.abstPct = o.optDouble("ap"); r.sections = o.optLong("se");
            r.sectionsTotal = o.optLong("st"); r.valid = o.optLong("va"); r.blank = o.optLong("bl");
            r.date = o.optString("d"); r.time = o.optString("t"); r.sig = o.optString("g"); r.fin = o.optBoolean("f");
            return r;
        }
    }

    static long intv(Object v) {
        if (v == null) return 0;
        String s = String.valueOf(v).replaceAll("\\D", "");
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (Throwable t) { return 0; }
    }

    static double dec(Object v) {
        if (v == null) return 0;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return 0;
        try {
            if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
            return Double.parseDouble(s);
        } catch (Throwable t) { return 0; }
    }

    /** Equivalente ao normalize() do HTML. */
    static Result parse(String json) throws Exception {
        JSONObject raw = new JSONObject(json.startsWith("﻿") ? json.substring(1) : json);
        Result r = new Result();
        JSONArray carg = raw.optJSONArray("carg");
        JSONObject cargo = carg != null && carg.length() > 0 ? carg.optJSONObject(0) : null;
        if (cargo != null) {
            JSONArray agr = cargo.optJSONArray("agr");
            for (int i = 0; agr != null && i < agr.length(); i++) {
                JSONObject ag = agr.optJSONObject(i);
                JSONArray pars = ag == null ? null : ag.optJSONArray("par");
                for (int j = 0; pars != null && j < pars.length(); j++) {
                    JSONObject par = pars.optJSONObject(j);
                    JSONArray cs = par == null ? null : par.optJSONArray("cand");
                    for (int k = 0; cs != null && k < cs.length(); k++) {
                        JSONObject c = cs.optJSONObject(k);
                        if (c == null) continue;
                        Cand x = new Cand();
                        x.numero = c.optString("n");
                        x.nome = c.optString("nmu", c.optString("nm", "Candidato"));
                        x.partido = par.optString("sg");
                        x.votos = intv(c.opt("vap"));
                        x.pct = dec(c.opt("pvap"));
                        String st = c.optString("st", "").trim();
                        if (st.isEmpty()) {
                            String e = c.optString("e", "").toLowerCase(Locale.ROOT);
                            st = e.equals("s") ? "Eleito" : e.equals("2") ? "2º turno" : "";
                        }
                        x.sit = st;
                        r.cands.add(x);
                    }
                }
            }
        }
        r.cands.sort((a, b) -> Long.compare(b.votos, a.votos));
        JSONObject s = raw.optJSONObject("s"), e = raw.optJSONObject("e"), v = raw.optJSONObject("v");
        r.progress = s == null ? 0 : dec(s.opt("pst"));
        r.sections = s == null ? 0 : intv(s.opt("st"));
        r.sectionsTotal = s == null ? 0 : intv(s.opt("ts"));
        r.abstPct = e == null ? 0 : dec(e.opt("pa"));
        r.valid = v == null ? 0 : intv(v.has("vv") ? v.opt("vv") : v.opt("vvc"));
        r.blank = v == null ? 0 : intv(v.opt("vb"));
        r.date = raw.optString("dg", raw.optString("dt", ""));
        r.time = raw.optString("hg", raw.optString("ht", ""));
        String tf = raw.optString("tf", raw.optString("and", "")).toLowerCase(Locale.ROOT);
        r.fin = tf.equals("f") || tf.equals("s");
        StringBuilder sb = new StringBuilder(r.date).append('|').append(r.time).append('|').append(r.progress)
                .append('|').append(r.sections).append('|').append(r.valid);
        for (int i = 0; i < Math.min(5, r.cands.size()); i++) sb.append('|').append(r.cands.get(i).numero).append(':').append(r.cands.get(i).votos);
        r.sig = sb.toString();
        return r;
    }

    // ---- cores consistentes por candidato (mesma regra do HTML)
    private static final int[] PALETTE = {0xFFE05A62, 0xFF4287E8, 0xFF37B980, 0xFFE3A43D, 0xFF9D70E8, 0xFF31A7B8,
            0xFFDB62B0, 0xFF82B84D, 0xFFE77D43, 0xFF6378DC, 0xFFB973D1, 0xFF4BA36B};
    private static final Object[][] KNOWN = {
            {Pattern.compile("\\blula\\b", Pattern.CASE_INSENSITIVE), 0xFFE23D4F},
            {Pattern.compile("fl[aá]vio", Pattern.CASE_INSENSITIVE), 0xFF28B874},
            {Pattern.compile("bolsonaro", Pattern.CASE_INSENSITIVE), 0xFF3384E8},
            {Pattern.compile("ciro", Pattern.CASE_INSENSITIVE), 0xFFF2A93B},
            {Pattern.compile("simone", Pattern.CASE_INSENSITIVE), 0xFF9B6BEA},
            {Pattern.compile("caiado", Pattern.CASE_INSENSITIVE), 0xFF2FB3C9},
            {Pattern.compile("tarc[ií]sio", Pattern.CASE_INSENSITIVE), 0xFF7A7DF0}};
    private static final Map<String, Integer> REG = new HashMap<>();

    static synchronized int color(String name) {
        String k = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return 0xFF45648A;
        for (Object[] kn : KNOWN) if (((Pattern) kn[0]).matcher(k).find()) return (Integer) kn[1];
        Integer c = REG.get(k);
        if (c != null) return c;
        int n = REG.size();
        if (n < PALETTE.length) c = PALETTE[n];
        else c = android.graphics.Color.HSVToColor(new float[]{(float) ((n * 137.508) % 360), 0.55f, 0.85f});
        REG.put(k, c);
        return c;
    }
}
