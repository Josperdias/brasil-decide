package br.com.centraleleicoes.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Ficha do candidato: só dados oficiais abertos (Câmara, Senado) e manchetes de terceiros com fonte e link.
 * O app não escreve opinião nem "polêmicas"; casamentos de nome são estritos (nome civil completo igual ao do TSE).
 */
final class Ficha {
    private Ficha() {}

    /** Identificador da eleição ordinária de 2026 no DivulgaCandContas (vem de comum/config/ele-c.json do TSE). */
    static final String TSE_SQELE = "20322002026";

    static final class Mandate {
        String house = "", name = "", party = "", uf = "", url = "", photo = "";
        long proposals = -1;
        double spent = -1;
    }

    private static final class Http { String body = ""; String total = ""; }

    private static Http http(String spec, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CentralEleicoes2026-Nativo/1.0");
            c.setRequestProperty("Accept", accept == null ? "application/json" : accept);
            c.setRequestProperty("Accept-Encoding", "gzip");
            int st = c.getResponseCode();
            if (st >= 400) throw new IllegalStateException("HTTP " + st);
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) { total += n; if (total > 3 * 1024 * 1024) break; out.write(buf, 0, n); }
            in.close();
            Http h = new Http();
            h.body = out.toString(StandardCharsets.UTF_8.name());
            String t = c.getHeaderField("x-total-count");
            h.total = t == null ? "" : t.trim();
            return h;
        } finally { c.disconnect(); }
    }

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u036f]", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    /** Mandato atual na Câmara ou no Senado, só se o nome civil completo bater exatamente com o do registro no TSE. */
    static Mandate mandate(Model.Cand c) {
        if (c.full.isEmpty()) return null;
        try {
            Mandate m = camara(c);
            if (m != null) return m;
        } catch (Throwable ignored) { }
        try { return senado(c); } catch (Throwable ignored) { }
        return null;
    }

    private static Mandate camara(Model.Cand c) throws Exception {
        String full = norm(c.full);
        String[] tk = full.split(" ");
        List<String> queries = new ArrayList<>();
        queries.add(c.nome);
        if (tk.length >= 2) queries.add(tk[0] + " " + tk[tk.length - 1]);
        Set<Integer> seen = new HashSet<>();
        for (String q : queries) {
            JSONArray arr = new JSONObject(http("https://dadosabertos.camara.leg.br/api/v2/deputados?nome=" + enc(q) + "&itens=20", null).body).optJSONArray("dados");
            for (int i = 0; arr != null && i < Math.min(arr.length(), 10); i++) {
                int id = arr.getJSONObject(i).optInt("id");
                if (id == 0 || !seen.add(id)) continue;
                JSONObject d = new JSONObject(http("https://dadosabertos.camara.leg.br/api/v2/deputados/" + id, null).body).optJSONObject("dados");
                if (d == null || !norm(d.optString("nomeCivil")).equals(full)) continue;
                JSONObject us = d.optJSONObject("ultimoStatus");
                Mandate m = new Mandate();
                m.house = "Câmara dos Deputados";
                m.name = us == null ? c.nome : us.optString("nomeEleitoral", c.nome);
                m.party = us == null ? "" : us.optString("siglaPartido");
                m.uf = us == null ? "" : us.optString("siglaUf");
                m.photo = us == null ? "" : us.optString("urlFoto");
                m.url = "https://www.camara.leg.br/deputados/" + id;
                try {
                    String t = http("https://dadosabertos.camara.leg.br/api/v2/proposicoes?idDeputadoAutor=" + id + "&dataInicio=2023-02-01&itens=1", null).total;
                    if (!t.isEmpty()) m.proposals = Long.parseLong(t);
                } catch (Throwable ignored) { }
                try {
                    double sum = 0;
                    for (int p = 1; p <= 8; p++) {
                        Http h = http("https://dadosabertos.camara.leg.br/api/v2/deputados/" + id + "/despesas?ano=2026&itens=100&pagina=" + p, null);
                        JSONArray ds = new JSONObject(h.body).optJSONArray("dados");
                        if (ds == null || ds.length() == 0) break;
                        for (int k = 0; k < ds.length(); k++) sum += ds.getJSONObject(k).optDouble("valorLiquido", 0);
                        if (ds.length() < 100) break;
                    }
                    m.spent = sum;
                } catch (Throwable ignored) { }
                return m;
            }
        }
        return null;
    }

    private static JSONArray senators;

    private static synchronized JSONArray senatorList() throws Exception {
        if (senators == null) {
            JSONObject o = new JSONObject(http("https://legis.senado.leg.br/dadosabertos/senador/lista/atual", "application/json").body);
            senators = o.getJSONObject("ListaParlamentarEmExercicio").getJSONObject("Parlamentares").getJSONArray("Parlamentar");
        }
        return senators;
    }

    private static Mandate senado(Model.Cand c) throws Exception {
        String full = norm(c.full);
        JSONArray arr = senatorList();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject ip = arr.getJSONObject(i).optJSONObject("IdentificacaoParlamentar");
            if (ip == null || !norm(ip.optString("NomeCompletoParlamentar")).equals(full)) continue;
            Mandate m = new Mandate();
            m.house = "Senado Federal";
            m.name = ip.optString("NomeParlamentar", c.nome);
            m.party = ip.optString("SiglaPartidoParlamentar");
            m.uf = ip.optString("UfParlamentar");
            m.photo = ip.optString("UrlFotoParlamentar");
            m.url = ip.optString("UrlPaginaParlamentar");
            return m;
        }
        return null;
    }

    /** Resumo de patrimônio e contas de campanha de um candidato (agregado do TSE; ver collector/build_transparency.py). */
    static final class Finance {
        double patrimonio, receitas, despesas;
        int bens;
        List<String[]> origensReceita = new ArrayList<>(), origensDespesa = new ArrayList<>();
        String gerado = "";
    }

    private static final java.util.Map<String, JSONObject> financeCache = new java.util.HashMap<>();

    /** Null se ainda não há resumo publicado para a UF ou para o candidato. */
    static Finance finance(String ue, String sq) {
        if (sq == null || sq.isEmpty()) return null;
        try {
            String uf = ue.toLowerCase(Locale.ROOT);
            JSONObject root;
            synchronized (financeCache) { root = financeCache.get(uf); }
            if (root == null) {
                root = new JSONObject(http(Replay.DATA_BASE + "transparencia/" + uf + ".json", null).body);
                synchronized (financeCache) { financeCache.put(uf, root); }
            }
            JSONObject o = root.getJSONObject("c").optJSONObject(sq);
            if (o == null) return null;
            Finance f = new Finance();
            f.patrimonio = o.optDouble("pat", 0);
            f.bens = o.optInt("nb", 0);
            f.receitas = o.optDouble("rec", 0);
            f.despesas = o.optDouble("des", 0);
            f.gerado = root.optString("gerado");
            fill(f.origensReceita, o.optJSONObject("rO"));
            fill(f.origensDespesa, o.optJSONObject("dO"));
            return f;
        } catch (Throwable t) { return null; }
    }

    private static void fill(List<String[]> out, JSONObject m) {
        if (m == null) return;
        java.util.Iterator<String> it = m.keys();
        while (it.hasNext()) { String k = it.next(); out.add(new String[]{k, String.valueOf(m.optDouble(k, 0))}); }
        Collections.sort(out, (a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));
    }

    /** Manchetes de terceiros (Google News RSS) sobre o candidato; "checks" restringe a agências de checagem. */
    static List<News.Article> headlines(Model.Cand c, String role, boolean checks) {
        try {
            String who = "\"" + c.nome + "\"";
            String q = checks ? who + " (site:lupa.news OR site:aosfatos.org OR site:estadao.com.br/estadao-verifica OR site:g1.globo.com/fato-ou-fake OR site:boatos.org)"
                    : who + " " + role + " 2026 when:60d";
            List<News.Article> raw = News.about(q);
            String[] tk = norm(c.nome).split(" ");
            String first = tk.length > 0 ? tk[0] : "", last = tk.length > 1 ? tk[tk.length - 1] : first;
            List<News.Article> out = new ArrayList<>();
            Set<String> seenTitles = new HashSet<>();
            for (News.Article a : raw) {
                String hay = norm(a.title + " " + a.desc);
                if (!hay.contains(first) || !hay.contains(last)) continue;
                if (!seenTitles.add(norm(a.title))) continue;
                out.add(a);
            }
            Collections.sort(out, (x, y) -> Long.compare(y.ts, x.ts));
            return out.size() > 6 ? new ArrayList<>(out.subList(0, 6)) : out;
        } catch (Throwable t) { return null; }
    }

    static String tseLink(Model.Cand c, String ue) {
        if (c.sq.isEmpty()) return "https://divulgacandcontas.tse.jus.br/divulga/";
        return "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2026/" + TSE_SQELE + "/" + ue.toUpperCase(Locale.ROOT) + "/" + c.sq;
    }
}
