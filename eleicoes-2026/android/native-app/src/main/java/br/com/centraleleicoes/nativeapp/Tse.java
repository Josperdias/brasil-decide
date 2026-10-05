package br.com.centraleleicoes.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Cliente do TSE (resultados.tse.jus.br, ciclo ele2026). Nunca lança: devolve Fetch com erro/ausência. */
final class Tse {
    private Tse() {}

    static final String BASE = "https://resultados.tse.jus.br/oficial";
    static final String CICLO = "ele2026";
    static final String[][] DEFAULT_CODES = {{"6257", "6259"}, {"6258", "6260"}}; // [turno-1]{federal, estadual}

    static final class Fetch {
        Model.Result result;
        boolean absent;   // 404: sem disputa / ainda não publicado
        String error;     // falha de rede/JSON
        int status;
    }

    static String url(String eleicao, String abr, int cargo) {
        return BASE + "/" + CICLO + "/" + eleicao + "/dados/" + abr + "/" + abr + "-c"
                + String.format(Locale.ROOT, "%04d", cargo) + "-e"
                + String.format(Locale.ROOT, "%06d", Integer.parseInt(eleicao)) + "-u.json";
    }

    static String configUrl() { return BASE + "/comum/config/ele-c.json"; }

    static String httpGet(String spec, int[] statusOut) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "CentralEleicoes2026-Nativo/1.0");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Accept-Encoding", "gzip");
            int status = c.getResponseCode();
            statusOut[0] = status;
            if (status >= 400) return "";
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 12 * 1024 * 1024) throw new IllegalStateException("resposta grande demais");
                out.write(buf, 0, n);
            }
            in.close();
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    static String photoBase(String eleicao, String abr) {
        return BASE + "/" + CICLO + "/" + eleicao + "/fotos/" + abr + "/";
    }

    static Fetch fetch(String spec, String photoBase) {
        Fetch f = fetch(spec);
        if (f.result != null && photoBase != null) f.result.photoBase = photoBase;
        return f;
    }

    static Fetch fetch(String spec) {
        Fetch f = new Fetch();
        try {
            int[] st = {0};
            String body = httpGet(spec, st);
            f.status = st[0];
            if (st[0] == 404) f.absent = true;
            else if (st[0] >= 400) f.error = "HTTP " + st[0];
            else f.result = Model.parse(body);
        } catch (Throwable t) {
            f.error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        return f;
    }

    /** Descobre/valida códigos 2026 em ele-c.json (melhor esforço; mantém padrões se algo não bater). Retorna null se não leu o arquivo. */
    static String[][] discoverCodes() {
        String[][] codes = {DEFAULT_CODES[0].clone(), DEFAULT_CODES[1].clone()};
        try {
            int[] st = {0};
            String body = httpGet(configUrl(), st);
            if (st[0] != 200) return null;
            JSONArray pl = new JSONObject(body).optJSONArray("pl");
            for (int i = 0; pl != null && i < pl.length(); i++) {
                JSONObject p = pl.optJSONObject(i);
                if (p == null || !"ele2026".equals(p.optString("c"))) continue;
                JSONArray es = p.optJSONArray("e");
                for (int j = 0; es != null && j < es.length(); j++) {
                    JSONObject e = es.optJSONObject(j);
                    if (e == null) continue;
                    String cd = e.optString("cd"), nm = e.optString("nm").toLowerCase(Locale.ROOT), t2 = e.optString("cdt2");
                    boolean fed = cd.equals(DEFAULT_CODES[0][0]) || (nm.contains("federal") && nm.contains("2026") && !nm.contains("estadual"));
                    boolean est = cd.equals(DEFAULT_CODES[0][1]) || (nm.contains("estadua") && nm.contains("2026"));
                    if (fed && cd.matches("\\d{4}")) { codes[0][0] = cd; if (t2.matches("\\d{4}")) codes[1][0] = t2; }
                    if (est && cd.matches("\\d{4}")) { codes[0][1] = cd; if (t2.matches("\\d{4}")) codes[1][1] = t2; }
                }
            }
        } catch (Throwable ignored) { return null; }
        return codes;
    }
}
