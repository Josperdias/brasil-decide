package br.com.centraleleicoes.nativeapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carrega imagens (fotos do TSE, miniaturas de vídeo) com cache em memória; falhas são lembradas (sem repetir 404). */
final class Photos {
    private Photos() {}

    interface Target {
        String tag();
        void setTag(String t);
        void setBitmap(Bitmap b);
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static Bitmap cached(String url) { return url == null ? null : CACHE.get(url); }

    /** Bloqueante: use fora da thread de UI. */
    static Bitmap fetch(String url, int sample) {
        if (url == null || url.isEmpty() || FAILED.contains(url)) return null;
        Bitmap hit = CACHE.get(url);
        if (hit != null) return hit;
        Bitmap b = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CentralEleicoes2026-Nativo/1.0");
            if (c.getResponseCode() == 200) {
                InputStream in = c.getInputStream();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                b = BitmapFactory.decodeStream(in, null, o);
                in.close();
            }
            c.disconnect();
        } catch (Throwable ignored) { }
        if (b == null) FAILED.add(url); else CACHE.put(url, b);
        return b;
    }

    static void load(final String url, final Target v) { load(url, v, 2); }

    static void load(final String url, final Target v, final int sample) {
        if (url == null || url.isEmpty() || FAILED.contains(url)) return;
        Bitmap hit = CACHE.get(url);
        v.setTag(url);
        if (hit != null) { v.setBitmap(hit); return; }
        try {
            POOL.execute(() -> {
                final Bitmap fb = fetch(url, sample);
                if (fb != null) UI.post(() -> { if (url.equals(v.tag())) v.setBitmap(fb); });
            });
        } catch (Throwable ignored) { }
    }
}
