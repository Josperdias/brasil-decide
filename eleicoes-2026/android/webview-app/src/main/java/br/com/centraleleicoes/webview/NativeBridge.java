package br.com.centraleleicoes.webview;

import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Ponte HTTP nativa exposta ao HTML como window.CentralNative.
 * Evita bloqueio de CORS no WebView para TSE/RSS. Qualquer falha vira um JSON
 * {status:0, err:"..."}; nunca derruba o processo.
 */
final class NativeBridge {
    interface Notifier { void done(String id); }

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_]{1,40}$");
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
    private final Notifier notifier;

    NativeBridge(Notifier notifier) { this.notifier = notifier; }

    void shutdown() { pool.shutdownNow(); }

    @JavascriptInterface
    public void httpGet(final String id, final String url) {
        if (id == null || !ID.matcher(id).matches()) return;
        try {
            pool.execute(() -> {
                String json;
                try { json = fetch(url); }
                catch (Throwable t) { json = error(String.valueOf(t.getMessage())); }
                results.put(id, json);
                try { notifier.done(id); } catch (Throwable ignored) { }
            });
        } catch (Throwable t) {
            results.put(id, error("fila de rede indisponível"));
            try { notifier.done(id); } catch (Throwable ignored) { }
        }
    }

    @JavascriptInterface
    public String result(String id) {
        String r = id == null ? null : results.remove(id);
        return r == null ? error("sem resultado") : r;
    }

    @JavascriptInterface
    public String info() { return "{\"bridge\":\"android-webview\",\"v\":1}"; }

    private static String error(String msg) {
        try { return new JSONObject().put("status", 0).put("body", "").put("err", msg).toString(); }
        catch (Throwable t) { return "{\"status\":0,\"body\":\"\",\"err\":\"erro\"}"; }
    }

    private static void validate(URL u) throws Exception {
        if (!"https".equalsIgnoreCase(u.getProtocol())) throw new SecurityException("somente https");
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal"))
            throw new SecurityException("host não permitido");
        for (InetAddress a : InetAddress.getAllByName(host)) {
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress())
                throw new SecurityException("endereço privado bloqueado");
        }
    }

    private static String fetch(String spec) throws Exception {
        URL u = new URL(spec);
        HttpURLConnection c = null;
        for (int hop = 0; hop < 5; hop++) {
            validate(u);
            c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "CentralEleicoes2026/1.0 (Android WebView)");
            c.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*");
            c.setRequestProperty("Accept-Encoding", "gzip");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                u = new URL(u, c.getHeaderField("Location"));
                c.disconnect();
                continue;
            }
            break;
        }
        if (c == null) throw new IllegalStateException("sem conexão");
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = "";
        if (in != null) {
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) throw new IllegalStateException("resposta grande demais");
                out.write(buf, 0, n);
            }
            in.close();
            body = out.toString(StandardCharsets.UTF_8.name());
        }
        c.disconnect();
        return new JSONObject().put("status", status).put("body", status >= 400 ? "" : body).toString();
    }
}
