package br.com.centraleleicoes.nativeapp;

import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Radar de notícias: Google News RSS (PT/EN/ES) + RSS de veículos; sem resumo inventado (só o trecho do feed). */
final class News {
    private News() {}

    static final class Article {
        String title = "", source = "", url = "", desc = "", kind = "Notícia", lang = "", cc = "";
        long ts;
    }

    static final class Source {
        final String id, label, url, lang, cc, hint;
        final Pattern kw;
        final String filters;
        Source(String id, String label, String url, String lang, String cc, Pattern kw, String filters, String hint) {
            this.id = id; this.label = label; this.url = url; this.lang = lang; this.cc = cc; this.kw = kw; this.filters = filters; this.hint = hint;
        }
    }

    static final class Health { String label; boolean ok; int n; String err; }
    static final class Batch { List<Article> articles = new ArrayList<>(); List<Health> health = new ArrayList<>(); long at; }

    private static final Pattern KW_ELEC = Pattern.compile("elei[cç]|eleitor|candidat|presiden|\\btse\\b|urna|turno|apura[cç]|campanha|governador|senador|deputad|pesquisa", Pattern.CASE_INSENSITIVE);
    private static final Pattern KW_BR = Pattern.compile("brazil|brasil|brazilian|brasile", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP = Pattern.compile("opini[aã]o|opinion|editorial|colun[ai]|commentary|\\bop-?ed\\b|ponto de vista|\\bcolumn\\b|commentisfree|/opiniao/|/opinion/", Pattern.CASE_INSENSITIVE);
    private static final Pattern AN = Pattern.compile("an[aá]lise|analysis|explainer|entenda|explica", Pattern.CASE_INSENSITIVE);

    private static String gn(String q, String hl, String gl, String ceid) {
        try { return "https://news.google.com/rss/search?q=" + URLEncoder.encode(q, "UTF-8") + "&hl=" + hl + "&gl=" + gl + "&ceid=" + ceid; }
        catch (Exception e) { return ""; }
    }

    static final Source[] SOURCES = {
            new Source("gn-pt", "Google News (PT)", gn("eleições 2026 presidente when:2d", "pt-BR", "BR", "BR:pt-419"), "pt", "BR", null, "brasil", null),
            new Source("gn-pt2", "Google News • TSE", gn("TSE apuração resultado eleição when:1d", "pt-BR", "BR", "BR:pt-419"), "pt", "BR", null, "brasil", null),
            new Source("g1", "g1 Política", "https://g1.globo.com/rss/g1/politica/", "pt", "BR", KW_ELEC, "brasil", null),
            new Source("folha", "Folha — Poder", "https://feeds.folha.uol.com.br/poder/rss091.xml", "pt", "BR", KW_ELEC, "brasil", null),
            new Source("agbr", "Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/politica/feed.xml", "pt", "BR", KW_ELEC, "brasil", null),
            new Source("p360", "Poder360", "https://www.poder360.com.br/feed/", "pt", "BR", KW_ELEC, "brasil", null),
            new Source("bbcpt", "BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml", "pt", "GB", KW_ELEC, "brasil,mundo", null),
            new Source("gn-en", "Google News (EN)", gn("\"Brazil election\" when:2d", "en-US", "US", "US:en"), "en", "US", null, "mundo", null),
            new Source("gn-es", "Google News (ES)", gn("elecciones Brasil when:2d", "es-419", "MX", "MX:es-419"), "es", "MX", null, "mundo", null),
            new Source("bbcw", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "en", "GB", KW_BR, "mundo", null),
            new Source("guardw", "The Guardian — World", "https://www.theguardian.com/world/rss", "en", "GB", KW_BR, "mundo", null),
            new Source("nytw", "NYT — Americas", "https://rss.nytimes.com/services/xml/rss/nyt/Americas.xml", "en", "US", KW_BR, "mundo", null),
            new Source("gn-mk", "Google News • mercado", gn("eleição dólar Ibovespa juros mercado when:2d", "pt-BR", "BR", "BR:pt-419"), "pt", "BR", null, "mercados", null),
            new Source("gn-mk-en", "Google News • markets", gn("Brazil election real markets stocks when:2d", "en-US", "US", "US:en"), "en", "US", null, "mercados", null),
            new Source("infomoney", "InfoMoney", "https://www.infomoney.com.br/feed/", "pt", "BR", KW_ELEC, "mercados", null),
            new Source("gn-op", "Google News • análises", gn("(opinião OR análise OR editorial OR coluna) eleições 2026 when:3d", "pt-BR", "BR", "BR:pt-419"), "pt", "BR", null, "analises", null),
            new Source("gn-op-en", "Google News • analysis", gn("(opinion OR analysis OR editorial) Brazil election when:3d", "en-US", "US", "US:en"), "en", "US", null, "analises", null),
            new Source("folha-op", "Folha — Opinião", "https://feeds.folha.uol.com.br/opiniao/rss091.xml", "pt", "BR", KW_ELEC, "analises", "Opinião"),
            new Source("guard-op", "The Guardian — Opinion", "https://www.theguardian.com/commentisfree/rss", "en", "GB", KW_BR, "analises", "Opinião"),
    };

    private static String get(String spec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CentralEleicoes2026-Nativo/1.0");
            c.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            c.setRequestProperty("Accept-Encoding", "gzip");
            int st = c.getResponseCode();
            if (st >= 400) throw new IllegalStateException("HTTP " + st);
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) { total += n; if (total > 4 * 1024 * 1024) break; out.write(buf, 0, n); }
            in.close();
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { c.disconnect(); }
    }

    static String plain(String html) {
        if (html == null || html.isEmpty()) return "";
        try { return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace(' ', ' ').replaceAll("[ \\t]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim(); }
        catch (Throwable t) { return html.replaceAll("<[^>]+>", " ").trim(); }
    }

    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    static long parseDate(String s) {
        if (s == null || s.isEmpty()) return 0;
        String[] fmts = {"EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz"};
        for (String f : fmts) {
            try { return new SimpleDateFormat(f, Locale.US).parse(s.trim()).getTime(); } catch (Throwable ignored) { }
        }
        try { return java.time.OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli(); } catch (Throwable ignored) { }
        return 0;
    }

    /** Parser RSS/Atom tolerante. */
    static List<String[]> parse(String xml) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml));
        List<String[]> out = new ArrayList<>(); // {title, link, date, desc, full, source}
        String[] cur = null;
        String name = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                name = p.getName();
                if (name.equals("item") || name.equals("entry")) cur = new String[]{"", "", "", "", "", ""};
                else if (cur != null) {
                    if (name.equals("link") && p.getAttributeValue(null, "href") != null && cur[1].isEmpty()) cur[1] = p.getAttributeValue(null, "href");
                    else if (name.equals("title") || name.equals("link") || name.equals("pubDate") || name.equals("published") || name.equals("updated")
                            || name.equals("description") || name.equals("summary") || name.equals("content:encoded") || name.equals("content") || name.equals("source") || name.equals("dc:date")) {
                        String t = p.nextText();
                        int i = name.equals("title") ? 0 : name.equals("link") ? 1 : (name.equals("pubDate") || name.equals("published") || name.equals("updated") || name.equals("dc:date")) ? 2
                                : (name.equals("description") || name.equals("summary")) ? 3 : name.equals("source") ? 5 : 4;
                        if (cur[i].isEmpty()) cur[i] = t == null ? "" : t.trim();
                        name = null;
                        continue;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && cur != null && (p.getName().equals("item") || p.getName().equals("entry"))) {
                if (!cur[0].isEmpty() && !cur[1].isEmpty()) out.add(cur);
                cur = null;
            }
        }
        return out;
    }

    private static Article toArticle(String[] r, Source s) {
        Article a = new Article();
        a.title = plain(r[0]);
        a.source = r[5].isEmpty() ? s.label : plain(r[5]);
        if (r[5].isEmpty() && s.id.startsWith("gn")) {
            int i = a.title.lastIndexOf(" - ");
            if (i > 10) { a.source = a.title.substring(i + 3); a.title = a.title.substring(0, i); }
        } else if (s.id.startsWith("gn") && a.title.endsWith(" - " + a.source)) a.title = a.title.substring(0, a.title.length() - a.source.length() - 3);
        a.url = r[1];
        a.ts = parseDate(r[2]);
        String d = plain(r[4].length() > r[3].length() ? r[4] : r[3]);
        String nt = norm(a.title);
        if (d.isEmpty() || (norm(d).startsWith(nt) && norm(d).length() < nt.length() + a.source.length() + 8)) d = "";
        if (d.length() > 1200) d = d.substring(0, 1200).replaceAll("\\s+\\S*$", "") + "…";
        a.desc = d;
        a.lang = s.lang;
        a.cc = s.cc;
        String all = a.title + " " + a.url;
        a.kind = OP.matcher(all).find() ? "Opinião" : AN.matcher(all).find() ? "Análise" : (s.hint != null ? s.hint : "Notícia");
        return a;
    }

    /** Busca avulsa no Google News (PT) para a ficha do candidato; devolve só título, fonte, data e link. */
    static List<Article> about(String query) throws Exception {
        Source s = new Source("gn-ficha", "Google News", gn(query, "pt-BR", "BR", "BR:pt-419"), "pt", "BR", null, "", null);
        List<Article> out = new ArrayList<>();
        for (String[] r : parse(get(s.url))) {
            Article a = toArticle(r, s);
            if (!a.title.isEmpty()) out.add(a);
        }
        return out;
    }

    /** Busca todas as fontes do filtro em paralelo; cada fonte que cair só some, sem derrubar o resto. */
    static Batch load(String filter, ExecutorService pool) {
        Batch b = new Batch();
        List<Source> srcs = new ArrayList<>();
        for (Source s : SOURCES) if (("," + s.filters + ",").contains("," + filter + ",")) srcs.add(s);
        List<Future<Object[]>> fs = new ArrayList<>();
        for (final Source s : srcs) {
            try {
                fs.add(pool.submit((Callable<Object[]>) () -> {
                    List<Article> list = new ArrayList<>();
                    long now = System.currentTimeMillis();
                    for (String[] r : parse(get(s.url))) {
                        Article a = toArticle(r, s);
                        if (a.title.isEmpty()) continue;
                        if (a.ts != 0 && now - a.ts > 4L * 86400000L) continue;
                        if (s.kw != null && !s.kw.matcher(a.title + " " + a.desc).find()) continue;
                        list.add(a);
                    }
                    return new Object[]{s, list};
                }));
            } catch (Throwable t) { /* pool encerrado */ }
        }
        List<Article> all = new ArrayList<>();
        for (int i = 0; i < fs.size(); i++) {
            Health h = new Health();
            h.label = srcs.get(i).label;
            try {
                @SuppressWarnings("unchecked") List<Article> l = (List<Article>) fs.get(i).get()[1];
                all.addAll(l);
                h.ok = true;
                h.n = l.size();
            } catch (Throwable t) { h.err = String.valueOf(t.getCause() != null ? t.getCause().getMessage() : t.getMessage()); }
            b.health.add(h);
        }
        Collections.sort(all, (x, y) -> Long.compare(y.ts, x.ts));
        Set<String> seenU = new HashSet<>(), seenT = new HashSet<>();
        Map<String, Integer> per = new HashMap<>();
        for (Article a : all) {
            String u = a.url.replaceAll("[?#].*$", "").toLowerCase(Locale.ROOT), t = norm(a.title);
            t = t.length() > 90 ? t.substring(0, 90) : t;
            if (!seenU.add(u) || !seenT.add(t)) continue;
            int c = per.containsKey(a.source) ? per.get(a.source) : 0;
            if (c >= 3) continue;
            per.put(a.source, c + 1);
            if (filter.equals("analises") && a.kind.equals("Notícia") && !a.url.contains("opin")) { /* mantém: veio de consulta de análises */ }
            b.articles.add(a);
            if (b.articles.size() >= 60) break;
        }
        b.at = System.currentTimeMillis();
        return b;
    }
}
