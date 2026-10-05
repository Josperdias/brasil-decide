package br.com.centraleleicoes.nativeapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gera imagens prontas para status/stories (1080x1920) e posts (1080x1080) com o resultado oficial:
 * placar do Brasil, mapa por UF e detalhe de uma UF. Sempre com a fonte (TSE) e o horário.
 */
final class StatusCard {
    private StatusCard() {}

    static final int PLACAR = 0, MAPA = 1, UF = 2, STORY = 0, POST = 1;
    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);

    static final class Data {
        int type, format, turn;
        Model.Result pres, ufRes;
        Map<String, Model.Result> states = new HashMap<>();
        String ufCode = "DF", ufName = "Distrito Federal";
        long at = System.currentTimeMillis();
    }

    private static String pc(double v) { return String.format(BR, "%.2f%%", v); }

    private static Paint paint(int color, float size, boolean black, Paint.Align a) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(black ? Ui.BLACK : Ui.MEDIUM);
        p.setTextAlign(a);
        return p;
    }

    private static String fit(String s, Paint p, float maxW) {
        return TextUtils.ellipsize(s, new android.text.TextPaint(p), maxW, TextUtils.TruncateAt.END).toString();
    }

    static Bitmap render(Data d) {
        final int W = 1080, H = d.format == STORY ? 1920 : 1080;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        background(c, W, H);
        boolean story = d.format == STORY;
        float m = 72;
        // cabeçalho
        String kicker = "ELEIÇÕES 2026  •  " + d.turn + "º TURNO";
        Paint k = paint(Ui.MINT, 30, true, Paint.Align.LEFT);
        k.setLetterSpacing(0.18f);
        c.drawText(kicker, m, story ? 128 : 84, k);
        String title = d.type == PLACAR ? "Presidente — Brasil" : d.type == MAPA ? "Quem lidera em cada UF" : d.ufName;
        String sub = d.type == UF ? "Presidente • " + d.ufCode : d.type == MAPA ? "Presidente • mapa por estado" : "Resultado oficial do TSE";
        Paint tp = paint(Ui.TEXT, story ? 88 : 64, true, Paint.Align.LEFT);
        c.drawText(fit(title, tp, W - 2 * m), m, story ? 232 : 160, tp);
        c.drawText(sub, m, story ? 290 : 208, paint(Ui.MUTED, story ? 38 : 30, false, Paint.Align.LEFT));
        Model.Result r = d.type == UF ? d.ufRes : d.pres;
        float y = story ? 380 : 250;
        if (d.type == MAPA) mapBlock(c, d, W, H, y, story);
        else {
            if (r == null) { c.drawText("Aguardando dados do TSE…", m, y + 120, paint(Ui.MUTED, 44, false, Paint.Align.LEFT)); }
            else {
                y = progress(c, r, m, W, y, story);
                candidates(c, r, m, W, y, H, story);
            }
        }
        footer(c, d, W, H, m);
        return bmp;
    }

    private static void background(Canvas c, int W, int H) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{0xFF0B1E34, 0xFF050B16, 0xFF02060D}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.15f, 0, W * 0.9f, 0x40_50D5FF, 0x0050D5FF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.95f, H, W * 0.8f, 0x30_64F5CB, 0x0064F5CB, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        p.setColor(0x14FFFFFF);
        for (int i = 0; i < 28; i++) c.drawCircle((i * 397) % W, (i * 613) % H, 2 + (i % 3), p);
    }

    private static float progress(Canvas c, Model.Result r, float m, int W, float y, boolean story) {
        float big = story ? 190 : 120;
        Paint pp = paint(Ui.TEXT, big, true, Paint.Align.LEFT);
        c.drawText(pc(r.progress), m, y + big * 0.82f, pp);
        float lx = m + pp.measureText(pc(r.progress)) + 28;
        Paint lab = paint(Ui.SOFT, story ? 38 : 30, false, Paint.Align.LEFT);
        c.drawText("das seções", lx, y + big * 0.45f, lab);
        c.drawText("totalizadas", lx, y + big * 0.45f + (story ? 46 : 36), lab);
        float by = y + big + (story ? 26 : 16);
        bar(c, m, by, W - 2 * m, story ? 30 : 22, (float) r.progress, Ui.CYAN, Ui.MINT);
        c.drawText(INT.format(r.sections) + " de " + INT.format(r.sectionsTotal) + " seções  •  " + INT.format(r.valid) + " votos válidos", m, by + (story ? 82 : 62), paint(Ui.MUTED, story ? 32 : 27, false, Paint.Align.LEFT));
        return by + (story ? 150 : 110);
    }

    private static void bar(Canvas c, float x, float y, float w, float h, float pct, int c1, int c2) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFF020914);
        RectF rf = new RectF(x, y, x + w, y + h);
        c.drawRoundRect(rf, h / 2, h / 2, t);
        float fw = Math.max(h, w * Math.max(0, Math.min(100, pct)) / 100f);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new LinearGradient(x, 0, x + w, 0, c1, c2, Shader.TileMode.CLAMP));
        if (pct > 0.01f) c.drawRoundRect(new RectF(x, y, x + fw, y + h), h / 2, h / 2, f);
    }

    private static void candidates(Canvas c, Model.Result r, float m, int W, float y, int H, boolean story) {
        int n = Math.min(3, r.cands.size());
        float rowH = story ? 290 : 200, av = story ? 150 : 104;
        for (int i = 0; i < n; i++) {
            Model.Cand cd = r.cands.get(i);
            int col = Model.color(cd.nome);
            float top = y + i * rowH;
            // cartão
            Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
            card.setShader(new LinearGradient(m, top, W - m, top + rowH, 0xFF0E1F34, 0xFF06101D, Shader.TileMode.CLAMP));
            RectF rf = new RectF(m, top, W - m, top + rowH - 20);
            c.drawRoundRect(rf, 40, 40, card);
            Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
            st.setStyle(Paint.Style.STROKE);
            st.setStrokeWidth(2);
            st.setColor(i == 0 ? Ui.alpha(col, 0xB0) : 0xFF1D3858);
            c.drawRoundRect(rf, 40, 40, st);
            float cx = m + 36 + av / 2, cy = top + (rowH - 20) / 2 - (story ? 18 : 12);
            avatar(c, r, cd, cx, cy, av / 2, col);
            float tx = m + 36 + av + 30, right = W - m - 36;
            Paint pct = paint(Ui.TEXT, story ? 84 : 60, true, Paint.Align.RIGHT);
            c.drawText(pc(cd.pct), right, cy + (story ? 6 : 2), pct);
            float nameW = right - pct.measureText(pc(cd.pct)) - tx - 20;
            Paint np = paint(Ui.TEXT, story ? 50 : 36, true, Paint.Align.LEFT);
            c.drawText(fit((i + 1) + "º  " + cd.nome, np, nameW), tx, cy - (story ? 14 : 8), np);
            c.drawText(fit(cd.partido + (cd.numero.isEmpty() ? "" : "  •  nº " + cd.numero) + (cd.sit.isEmpty() ? "" : "  •  " + cd.sit), paint(Ui.MUTED, 30, false, Paint.Align.LEFT), nameW), tx, cy + (story ? 34 : 28), paint(Ui.MUTED, story ? 32 : 26, false, Paint.Align.LEFT));
            c.drawText(INT.format(cd.votos) + " votos", right, cy + (story ? 52 : 40), paint(Ui.MUTED, story ? 32 : 26, false, Paint.Align.RIGHT));
            bar(c, m + 36, top + rowH - 20 - (story ? 52 : 40), W - 2 * m - 72, story ? 18 : 14, (float) cd.pct, col, Model.lighten(col));
        }
        if (r.cands.size() >= 2) {
            float gy = y + n * rowH + (story ? 20 : 2);
            long gap = r.cands.get(0).votos - r.cands.get(1).votos;
            if (gy < (story ? 1700 : 960)) c.drawText("Diferença 1º × 2º: " + INT.format(gap) + " votos (" + String.format(BR, "%.2f", r.cands.get(0).pct - r.cands.get(1).pct) + " p.p.)", m, gy + 30, paint(Ui.MINT, story ? 36 : 28, true, Paint.Align.LEFT));
        }
    }

    private static void avatar(Canvas c, Model.Result r, Model.Cand cd, float cx, float cy, float rad, int col) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF122A47);
        c.drawCircle(cx, cy, rad, bg);
        Bitmap ph = r.photoBase.isEmpty() || cd.sq.isEmpty() ? null : Photos.cached(r.photoBase + cd.sq + ".jpeg");
        if (ph != null) {
            c.save();
            Path clip = new Path();
            clip.addCircle(cx, cy, rad - 2, Path.Direction.CW);
            c.clipPath(clip);
            float s = Math.max(2 * rad / ph.getWidth(), 2 * rad / ph.getHeight());
            float w = ph.getWidth() * s, h = ph.getHeight() * s;
            RectF dst = new RectF(cx - w / 2, cy - rad, cx - w / 2 + w, cy - rad + h);
            c.drawBitmap(ph, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            c.restore();
        } else {
            Paint t = paint(0xFFD1DEEC, rad * 0.8f, true, Paint.Align.CENTER);
            c.drawText(Ui.initials(cd.nome), cx, cy + rad * 0.28f, t);
        }
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(6);
        ring.setColor(col);
        c.drawCircle(cx, cy, rad - 3, ring);
    }

    private static void mapBlock(Canvas c, Data d, int W, int H, float y, boolean story) {
        Map<String, Integer> fills = new HashMap<>();
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        int loaded = 0;
        for (Map.Entry<String, Model.Result> e : d.states.entrySet()) {
            Model.Cand l = e.getValue().lead();
            if (l == null) continue;
            loaded++;
            fills.put(e.getKey(), Model.color(l.nome));
            counts.merge(l.nome, 1, Integer::sum);
        }
        float m = 72;
        float mw = story ? W - 2 * m : 560, s = mw / MapData.VIEW_W, mh = MapData.VIEW_H * s;
        float mx = story ? m : 52, my = y + (story ? 10 : -20);
        c.save();
        c.translate(mx, my);
        c.scale(s, s);
        BrazilMapView.drawMap(c, fills, null, new BrazilMapView.Pens(), true);
        c.restore();
        // legenda: líderes e quantidade de UFs
        java.util.List<Map.Entry<String, Integer>> es = new java.util.ArrayList<>(counts.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        float lx = story ? m : 660, ly = story ? my + mh + 40 : y + 40, lw = story ? W - 2 * m : W - 660 - 60;
        c.drawText(loaded + " de 27 UFs com líder definido", lx, ly, paint(Ui.MUTED, story ? 32 : 26, false, Paint.Align.LEFT));
        ly += story ? 30 : 24;
        int shown = 0;
        for (Map.Entry<String, Integer> e : es) {
            if (shown++ >= (story ? 5 : 4)) break;
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Model.color(e.getKey()));
            float ry = ly + (story ? 64 : 58) * shown - 8;
            c.drawCircle(lx + 18, ry - 12, 16, dot);
            Paint np = paint(Ui.TEXT, story ? 40 : 32, true, Paint.Align.LEFT);
            Paint cp = paint(Ui.SOFT, story ? 40 : 32, true, Paint.Align.RIGHT);
            String cnt = e.getValue() + (e.getValue() > 1 ? " UFs" : " UF");
            c.drawText(fit(e.getKey(), np, lw - 60 - cp.measureText(cnt) - 20), lx + 52, ry, np);
            c.drawText(cnt, lx + lw, ry, cp);
        }
        if (d.pres != null && !story) c.drawText("Apuração " + pc(d.pres.progress), lx, H - 190, paint(Ui.MINT, 38, true, Paint.Align.LEFT));
        else if (d.pres != null) c.drawText("Apuração nacional: " + pc(d.pres.progress) + " das seções", m, H - 230, paint(Ui.MINT, 38, true, Paint.Align.LEFT));
    }

    private static void footer(Canvas c, Data d, int W, int H, float m) {
        float fy = H - 96;
        Paint line = new Paint();
        line.setColor(0xFF1D3858);
        c.drawRect(m, fy - 44, W - m, fy - 42, line);
        c.drawText("Fonte: TSE (resultados.tse.jus.br) • dados oficiais, sem projeção", m, fy, paint(Ui.MUTED, 26, false, Paint.Align.LEFT));
        c.drawText("Atualizado em " + new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BR).format(new Date(d.at)), m, fy + 40, paint(Ui.MUTED, 26, false, Paint.Align.LEFT));
        // marca
        float cx = W - m - 28, cy = fy + 10;
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setShader(new LinearGradient(cx - 28, cy - 28, cx + 28, cy + 28, Ui.CYAN, Ui.MINT, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 28, dot);
        Paint ck = new Paint(Paint.ANTI_ALIAS_FLAG);
        ck.setStyle(Paint.Style.STROKE);
        ck.setStrokeWidth(7);
        ck.setStrokeCap(Paint.Cap.ROUND);
        ck.setStrokeJoin(Paint.Join.ROUND);
        ck.setColor(Ui.INK);
        Path p = new Path();
        p.moveTo(cx - 12, cy + 1);
        p.lineTo(cx - 3, cy + 11);
        p.lineTo(cx + 14, cy - 11);
        c.drawPath(p, ck);
        Paint bp = paint(Ui.TEXT, 28, true, Paint.Align.RIGHT);
        c.drawText("Central Eleições 2026", cx - 44, cy + 10, bp);
    }
}
