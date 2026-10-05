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

/** Carrega fotos dos candidatos do TSE com cache em memória; falhas são lembradas (sem repetir 404). */
final class Photos {
    private Photos() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static void load(final String url, final AvatarView v) {
        if (url == null || url.isEmpty() || FAILED.contains(url)) return;
        Bitmap hit = CACHE.get(url);
        v.tag = url;
        if (hit != null) { v.setBitmap(hit); return; }
        try {
            POOL.execute(() -> {
                Bitmap b = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("User-Agent", "CentralEleicoes2026-Nativo/1.0");
                    if (c.getResponseCode() == 200) {
                        InputStream in = c.getInputStream();
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inSampleSize = 2;
                        b = BitmapFactory.decodeStream(in, null, o);
                        in.close();
                    }
                    c.disconnect();
                } catch (Throwable ignored) { }
                final Bitmap fb = b;
                if (fb == null) { FAILED.add(url); return; }
                CACHE.put(url, fb);
                UI.post(() -> { if (url.equals(v.tag)) v.setBitmap(fb); });
            });
        } catch (Throwable ignored) { }
    }
}
