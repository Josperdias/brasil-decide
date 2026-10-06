package br.com.centraleleicoes.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Lives e vídeos sobre a eleição: lê a página pública de busca do YouTube (sem chave de API) e extrai os
 * resultados de ytInitialData. Se o formato da página mudar, devolve lista vazia e a tela mostra os atalhos de canais.
 */
final class Youtube {
    private Youtube() {}

    static final class Video {
        String id = "", title = "", channel = "", views = "", published = "", length = "";
        boolean live;
        String thumb() { return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg"; }
        String url() { return "https://www.youtube.com/watch?v=" + id; }
    }

    static final String LIVE = "EgJAAQ%3D%3D", BY_DATE = "CAI%3D", VIDEOS_BY_DATE = "CAISAhAB";

    static String searchUrl(String query, String sp) {
        try { return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + (sp == null ? "" : "&sp=" + sp); }
        catch (Exception e) { return "https://www.youtube.com/"; }
    }

    private static String get(String spec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            // UA de desktop: com UA mobile o YouTube redireciona para m.youtube.com, que usa outro formato (sem videoRenderer)
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9");
            c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
            c.setRequestProperty("Accept-Encoding", "gzip");
            if (c.getResponseCode() >= 400) throw new IllegalStateException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) { total += n; if (total > 6 * 1024 * 1024) break; out.write(buf, 0, n); }
            in.close();
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { c.disconnect(); }
    }

    private static String text(JSONObject o) {
        if (o == null) return "";
        String s = o.optString("simpleText", "");
        if (!s.isEmpty()) return s;
        JSONArray runs = o.optJSONArray("runs");
        StringBuilder b = new StringBuilder();
        for (int i = 0; runs != null && i < runs.length(); i++) b.append(runs.optJSONObject(i) == null ? "" : runs.optJSONObject(i).optString("text"));
        return b.toString();
    }

    /** Extrai o objeto JSON de "ytInitialData = {...};" contando chaves (respeitando strings). */
    static JSONObject initialData(String html) throws Exception {
        Matcher m = Pattern.compile("ytInitialData\\s*=\\s*\\{").matcher(html);
        if (!m.find()) throw new IllegalStateException("ytInitialData ausente");
        int start = m.end() - 1, depth = 0;
        boolean str = false, esc = false;
        for (int i = start; i < html.length(); i++) {
            char ch = html.charAt(i);
            if (str) { if (esc) esc = false; else if (ch == '\\') esc = true; else if (ch == '"') str = false; continue; }
            if (ch == '"') str = true;
            else if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return new JSONObject(html.substring(start, i + 1));
        }
        throw new IllegalStateException("JSON incompleto");
    }

    private static void collect(Object node, List<Video> out, int depth) {
        if (depth > 14 || out.size() >= 24) return;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject vr = o.optJSONObject("videoRenderer");
            if (vr != null) { Video v = toVideo(vr); if (v != null) out.add(v); return; }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) collect(o.opt(it.next()), out, depth + 1);
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), out, depth + 1);
        }
    }

    private static Video toVideo(JSONObject vr) {
        Video v = new Video();
        v.id = vr.optString("videoId");
        if (v.id.isEmpty()) return null;
        v.title = text(vr.optJSONObject("title"));
        v.channel = text(vr.optJSONObject("ownerText"));
        v.views = text(vr.optJSONObject("viewCountText"));
        v.published = text(vr.optJSONObject("publishedTimeText"));
        v.length = text(vr.optJSONObject("lengthText"));
        JSONArray badges = vr.optJSONArray("badges");
        for (int i = 0; badges != null && i < badges.length(); i++) {
            JSONObject b = badges.optJSONObject(i) == null ? null : badges.optJSONObject(i).optJSONObject("metadataBadgeRenderer");
            if (b != null && (b.optString("style").contains("LIVE") || b.optString("label").toLowerCase().contains("ao vivo") || b.optString("label").toLowerCase().contains("live"))) v.live = true;
        }
        JSONArray ov = vr.optJSONArray("thumbnailOverlays");
        for (int i = 0; ov != null && i < ov.length(); i++) {
            JSONObject t = ov.optJSONObject(i) == null ? null : ov.optJSONObject(i).optJSONObject("thumbnailOverlayTimeStatusRenderer");
            if (t != null && "LIVE".equals(t.optString("style"))) v.live = true;
        }
        if (v.views.toLowerCase().contains("assistindo")) v.live = true;
        return v.title.isEmpty() ? null : v;
    }

    private static final Pattern RELEVANT = Pattern.compile("elei[cç]|apura[cç]|candidat|debate|presiden|governador|senad|deputad|turno|urna|voto|lula|fl[aá]vio|tse|brasil", Pattern.CASE_INSENSITIVE);

    /** Busca, tira resultados sem relação com a eleição (se sobrar pouco, mantém tudo) e põe as lives na frente. */
    static List<Video> search(String query, String sp) throws Exception {
        List<Video> all = new ArrayList<>();
        collect(initialData(get(searchUrl(query, sp))), all, 0);
        List<Video> rel = new ArrayList<>();
        for (Video v : all) if (RELEVANT.matcher(v.title + " " + v.channel).find()) rel.add(v);
        List<Video> base = rel.size() >= 4 ? rel : all;
        List<Video> out = new ArrayList<>();
        for (Video v : base) if (v.live) out.add(v);
        for (Video v : base) if (!v.live) out.add(v);
        return out;
    }
}
