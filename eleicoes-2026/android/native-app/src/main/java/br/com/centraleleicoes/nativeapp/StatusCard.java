package br.com.centraleleicoes.nativeapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imagens prontas para status/stories (1080x1920) e posts (1080x1080) com o resultado oficial do TSE:
 * placar do Brasil, mapa por UF, detalhe de uma UF e cargos do Distrito Federal (Presidente, Governador,
 * Senador, Deputado Federal e Distrital). Sempre com a fonte e o horário.
 */
final class StatusCard {
    private StatusCard() {}

    static final int PLACAR = 0, MAPA = 1, UF = 2, DFC = 3, STORY = 0, POST = 1;
    static final String[] DF_KEYS = {"pres", "gov", "sen", "depf", "depd"};
    static final String[] DF_LABELS = {"Presidente", "Governador", "Senador", "Dep. Federal", "Dep. Distrital"};
    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);
    private static final int W = 1080, M = 72;

    static final class Data {
        int type, format, turn, dfCargo;
        Model.Result pres, ufRes;
        Map<String, Model.Result> states = new HashMap<>(), df = new HashMap<>();
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

    private static String ell(String s, Paint p, float maxW) {
        return TextUtils.ellipsize(s, new android.text.TextPaint(p), maxW, TextUtils.TruncateAt.END).toString();
    }

    /** Desenha o texto reduzindo o tamanho até caber na largura (sem cortar com "…" a não ser no mínimo). */
    private static void fitText(Canvas c, String s, float x, float y, float maxW, float size, float min, int color, boolean black, Paint.Align a) {
        Paint p = paint(color, size, black, a);
        while (p.measureText(s) > maxW && p.getTextSize() > min) p.setTextSize(p.getTextSize() - 2);
        c.drawText(ell(s, p, maxW), x, y, p);
    }

    /** Quebra o nome em até 2 linhas (na palavra mais próxima do meio) se não couber numa linha com o tamanho dado. */
    private static String[] nameLines(String name, Paint p, float maxW) {
        if (p.measureText(name) <= maxW) return new String[]{name};
        String[] w = name.split(" ");
        if (w.length < 2) return new String[]{ell(name, p, maxW)};
        int best = 1;
        float bd = 1e9f;
        for (int i = 1; i < w.length; i++) {
            float a = p.measureText(TextUtils.join(" ", java.util.Arrays.copyOfRange(w, 0, i))), b = p.measureText(TextUtils.join(" ", java.util.Arrays.copyOfRange(w, i, w.length)));
            float d = Math.abs(a - b);
            if (d < bd) { bd = d; best = i; }
        }
        return new String[]{ell(TextUtils.join(" ", java.util.Arrays.copyOfRange(w, 0, best)), p, maxW), ell(TextUtils.join(" ", java.util.Arrays.copyOfRange(w, best, w.length)), p, maxW)};
    }

    // ------------------------------------------------------------------ dados usados em cada cartão
    static Model.Result mainResult(Data d) {
        if (d.type == UF) return d.ufRes;
        if (d.type == DFC) return d.df.get(DF_KEYS[d.dfCargo]);
        return d.pres;
    }

    /** Candidatos que aparecem desenhados (para baixar as fotos antes). */
    static List<Model.Cand> shown(Data d) {
        List<Model.Cand> out = new ArrayList<>();
        Model.Result r = mainResult(d);
        if (r == null || d.type == MAPA) return out;
        int key = d.type == DFC ? d.dfCargo : -1;
        if (key == 3 || key == 4) out.addAll(elected(r, gridCount(d.dfCargo, d.format)));
        else if (key == 1 || key == 2) out.addAll(r.cands.subList(0, Math.min(5, r.cands.size())));
        else out.addAll(r.cands.subList(0, Math.min(3, r.cands.size())));
        return out;
    }

    private static int gridCount(int cargo, int format) { return cargo == 3 ? 8 : (format == STORY ? 12 : 8); }

    /** Eleitos (situação começa com "Eleito"); se o TSE ainda não marcou ninguém, os mais votados. */
    static List<Model.Cand> elected(Model.Result r, int n) {
        List<Model.Cand> el = new ArrayList<>();
        for (Model.Cand c : r.cands) if (c.sit.toLowerCase(Locale.ROOT).startsWith("eleito")) el.add(c);
        if (el.isEmpty()) el.addAll(r.cands);
        return new ArrayList<>(el.subList(0, Math.min(n, el.size())));
    }

    // ------------------------------------------------------------------ render
    static Bitmap render(Data d) {
        final int H = d.format == STORY ? 1920 : 1080;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        background(c, H);
        boolean story = d.format == STORY;
        Model.Result r = mainResult(d);
        String kicker = "ELEIÇÕES 2026  •  " + d.turn + "º TURNO" + (d.type == DFC ? "  •  DISTRITO FEDERAL" : "");
        Paint k = paint(Ui.MINT, story ? 30 : 26, true, Paint.Align.LEFT);
        k.setLetterSpacing(0.16f);
        c.drawText(kicker, M, story ? 128 : 82, k);
        String title, sub;
        switch (d.type) {
            case PLACAR: title = "Presidente — Brasil"; sub = "Resultado oficial do TSE"; break;
            case MAPA: title = "Quem lidera cada UF"; sub = "Presidente • mapa por estado"; break;
            case UF: title = d.ufName; sub = "Presidente • " + d.ufCode; break;
            default: {
                String[] t = {"Presidente no DF", "Governo do DF", "Senado Federal — DF", "Câmara dos Deputados — DF", "Câmara Legislativa (CLDF)"};
                String[] vg = {"votação no Distrito Federal", "1 vaga", "2 vagas", "8 vagas", "24 vagas"};
                title = t[d.dfCargo];
                sub = vg[d.dfCargo] + (r == null ? "" : "  •  " + pc(r.progress) + " das seções");
            }
        }
        fitText(c, title, M, story ? 232 : 156, W - 2 * M, story ? 88 : 64, 44, Ui.TEXT, true, Paint.Align.LEFT);
        c.drawText(sub, M, story ? 292 : 206, paint(Ui.MUTED, story ? 38 : 30, false, Paint.Align.LEFT));
        float y = story ? 372 : 250;
        if (d.type == MAPA) mapBlock(c, d, H, y, story);
        else if (r == null) c.drawText("Aguardando dados do TSE…", M, y + 120, paint(Ui.MUTED, 44, false, Paint.Align.LEFT));
        else if (d.type == DFC && d.dfCargo >= 3) electedGrid(c, d, r, y, H, story);
        else if (d.type == DFC && d.dfCargo >= 1) duelBlock(c, d, r, y, H, story);
        else {
            if (d.type != DFC) y = progress(c, r, y, story);
            candidates(c, r, y, H, story);
        }
        footer(c, d, H);
        return bmp;
    }

    private static void background(Canvas c, int H) {
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

    private static float progress(Canvas c, Model.Result r, float y, boolean story) {
        float big = story ? 190 : 120;
        Paint pp = paint(Ui.TEXT, big, true, Paint.Align.LEFT);
        c.drawText(pc(r.progress), M, y + big * 0.82f, pp);
        float lx = M + pp.measureText(pc(r.progress)) + 28;
        Paint lab = paint(Ui.SOFT, story ? 38 : 30, false, Paint.Align.LEFT);
        c.drawText("das seções", lx, y + big * 0.45f, lab);
        c.drawText("totalizadas", lx, y + big * 0.45f + (story ? 46 : 36), lab);
        float by = y + big + (story ? 26 : 16);
        bar(c, M, by, W - 2 * M, story ? 30 : 22, (float) r.progress, Ui.CYAN, Ui.MINT);
        c.drawText(INT.format(r.sections) + " de " + INT.format(r.sectionsTotal) + " seções  •  " + INT.format(r.valid) + " votos válidos", M, by + (story ? 82 : 62), paint(Ui.MUTED, story ? 32 : 27, false, Paint.Align.LEFT));
        return by + (story ? 150 : 110);
    }

    private static void bar(Canvas c, float x, float y, float w, float h, float pct, int c1, int c2) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFF020914);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2, h / 2, t);
        float fw = Math.max(h, w * Math.max(0, Math.min(100, pct)) / 100f);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new LinearGradient(x, 0, x + w, 0, c1, c2, Shader.TileMode.CLAMP));
        if (pct > 0.01f) c.drawRoundRect(new RectF(x, y, x + fw, y + h), h / 2, h / 2, f);
    }

    private static void cardBox(Canvas c, RectF rf, float radius, int accent) {
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        card.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom, 0xFF0E1F34, 0xFF06101D, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf, radius, radius, card);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(2);
        st.setColor(accent != 0 ? Ui.alpha(accent, 0xB0) : 0xFF1D3858);
        c.drawRoundRect(rf, radius, radius, st);
    }

    private static void badge(Canvas c, String text, float x, float y, float size, int color, Paint.Align a) {
        Paint t = paint(color, size, true, Paint.Align.LEFT);
        t.setLetterSpacing(0.08f);
        float tw = t.measureText(text), pad = size * 0.55f, h = size * 1.7f;
        float left = a == Paint.Align.CENTER ? x - (tw + 2 * pad) / 2 : a == Paint.Align.RIGHT ? x - tw - 2 * pad : x;
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Ui.alpha(color, 0x26));
        c.drawRoundRect(new RectF(left, y - h * 0.72f, left + tw + 2 * pad, y + h * 0.28f), h / 2, h / 2, bg);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(2);
        st.setColor(Ui.alpha(color, 0x99));
        c.drawRoundRect(new RectF(left, y - h * 0.72f, left + tw + 2 * pad, y + h * 0.28f), h / 2, h / 2, st);
        c.drawText(text, left + pad, y, t);
    }

    private static int sitColor(String sit) {
        String s = sit.toLowerCase(Locale.ROOT);
        return s.startsWith("eleito") ? Ui.GREEN : s.contains("2º") || s.contains("segundo") ? Ui.AMBER : 0;
    }

    private static String sitLabel(String sit) {
        String s = sit.toLowerCase(Locale.ROOT);
        return s.startsWith("eleito") ? "ELEITO" : s.contains("2º") || s.contains("segundo") ? "2º TURNO" : "";
    }

    // linhas largas: presidente / UF (top 3)
    private static void candidates(Canvas c, Model.Result r, float y, int H, boolean story) {
        int n = Math.min(3, r.cands.size());
        float rowH = story ? 290 : 200, av = story ? 150 : 104;
        for (int i = 0; i < n; i++) {
            Model.Cand cd = r.cands.get(i);
            int col = Model.color(cd.nome);
            float top = y + i * rowH;
            RectF rf = new RectF(M, top, W - M, top + rowH - 20);
            cardBox(c, rf, 40, i == 0 ? col : 0);
            float cx = M + 36 + av / 2, cy = top + (rowH - 20) / 2 - (story ? 18 : 12);
            avatar(c, r, cd, cx, cy, av / 2, col);
            float tx = M + 36 + av + 30, right = W - M - 36;
            Paint pct = paint(Ui.TEXT, story ? 84 : 60, true, Paint.Align.RIGHT);
            c.drawText(pc(cd.pct), right, cy + (story ? 6 : 2), pct);
            float nameW = right - pct.measureText(pc(cd.pct)) - tx - 24;
            Paint np = paint(Ui.TEXT, story ? 46 : 34, true, Paint.Align.LEFT);
            String[] ln = nameLines(cd.nome, np, nameW);
            float ny = cy - (story ? 22 : 14) - (ln.length - 1) * (story ? 26 : 20);
            for (int k = 0; k < ln.length; k++) c.drawText((k == 0 ? (i + 1) + "º  " : "") + ln[k], tx, ny + k * (story ? 52 : 40), np);
            float my = ny + (ln.length - 1) * (story ? 52 : 40) + (story ? 48 : 38);
            Paint mp = paint(Ui.MUTED, story ? 30 : 25, false, Paint.Align.LEFT);
            String meta = cd.partido + (cd.numero.isEmpty() ? "" : "  •  nº " + cd.numero);
            c.drawText(meta, tx, my, mp);
            String sl = sitLabel(cd.sit);
            if (!sl.isEmpty()) badge(c, sl, tx + mp.measureText(meta) + 22, my, story ? 22 : 18, sitColor(cd.sit), Paint.Align.LEFT);
            c.drawText(INT.format(cd.votos) + " votos", right, cy + (story ? 52 : 40), paint(Ui.MUTED, story ? 30 : 25, false, Paint.Align.RIGHT));
            bar(c, M + 36, top + rowH - 20 - (story ? 52 : 40), W - 2 * M - 72, story ? 18 : 14, (float) cd.pct, col, Model.lighten(col));
        }
        if (r.cands.size() >= 2) {
            float gy = y + n * rowH + (story ? 20 : 2);
            long gap = r.cands.get(0).votos - r.cands.get(1).votos;
            if (gy < (story ? 1690 : 940)) c.drawText("Diferença 1º × 2º: " + INT.format(gap) + " votos (" + String.format(BR, "%.2f", r.cands.get(0).pct - r.cands.get(1).pct) + " p.p.)", M, gy + 30, paint(Ui.MINT, story ? 36 : 28, true, Paint.Align.LEFT));
        }
    }

    // governador / senador: 2 cartões grandes + próximos colocados
    private static void duelBlock(Canvas c, Data d, Model.Result r, float y, int H, boolean story) {
        int n = Math.min(2, r.cands.size());
        float gap = 28, cw = (W - 2 * M - gap) / 2, ch = story ? 760 : 470;
        for (int i = 0; i < n; i++) {
            Model.Cand cd = r.cands.get(i);
            int col = Model.color(cd.nome);
            float x = M + i * (cw + gap);
            RectF rf = new RectF(x, y, x + cw, y + ch);
            cardBox(c, rf, 44, col);
            float cx = x + cw / 2, rad = story ? 130 : 84, cy = y + (story ? 70 : 44) + rad;
            avatar(c, r, cd, cx, cy, rad, col);
            String sl = sitLabel(cd.sit);
            if (!sl.isEmpty()) badge(c, sl, cx, y + (story ? 46 : 32), story ? 24 : 19, sitColor(cd.sit), Paint.Align.CENTER);
            float ty = cy + rad + (story ? 70 : 50);
            Paint np = paint(Ui.TEXT, story ? 44 : 32, true, Paint.Align.CENTER);
            String[] ln = nameLines(cd.nome, np, cw - 40);
            for (int k = 0; k < ln.length; k++) c.drawText(ln[k], cx, ty + k * (story ? 50 : 38), np);
            ty += (ln.length - 1) * (story ? 50 : 38) + (story ? 46 : 36);
            c.drawText(cd.partido + (cd.numero.isEmpty() ? "" : "  •  nº " + cd.numero), cx, ty, paint(Ui.MUTED, story ? 28 : 22, false, Paint.Align.CENTER));
            ty += story ? 100 : 72;
            c.drawText(pc(cd.pct), cx, ty, paint(Ui.TEXT, story ? 88 : 60, true, Paint.Align.CENTER));
            c.drawText(INT.format(cd.votos) + " votos", cx, ty + (story ? 48 : 36), paint(Ui.MUTED, story ? 30 : 24, false, Paint.Align.CENTER));
        }
        // próximos colocados
        float ry = y + ch + (story ? 40 : 24);
        int more = Math.min(story ? 3 : 2, Math.max(0, r.cands.size() - 2));
        if (more > 0) c.drawText("DEMAIS CANDIDATOS", M, ry + 20, labelPaint(story));
        ry += story ? 44 : 34;
        for (int i = 0; i < more; i++) {
            Model.Cand cd = r.cands.get(2 + i);
            float rowH = story ? 104 : 74, ay = ry + i * (rowH + 12);
            RectF rf = new RectF(M, ay, W - M, ay + rowH);
            cardBox(c, rf, 28, 0);
            avatar(c, r, cd, M + 22 + rowH * 0.36f, ay + rowH / 2, rowH * 0.36f, Model.color(cd.nome));
            float tx = M + 22 + rowH * 0.72f + 22;
            Paint pp = paint(Ui.TEXT, story ? 40 : 30, true, Paint.Align.RIGHT);
            c.drawText(pc(cd.pct), W - M - 28, ay + rowH / 2 + (story ? 14 : 11), pp);
            fitText(c, (3 + i) + "º  " + cd.nome, tx, ay + rowH / 2 - (story ? 4 : 2), W - M - 28 - tx - pp.measureText(pc(cd.pct)) - 30, story ? 34 : 26, 22, Ui.TEXT, true, Paint.Align.LEFT);
            c.drawText(cd.partido + "  •  " + INT.format(cd.votos) + " votos", tx, ay + rowH / 2 + (story ? 34 : 26), paint(Ui.MUTED, story ? 24 : 19, false, Paint.Align.LEFT));
        }
    }

    private static Paint labelPaint(boolean story) {
        Paint p = paint(Ui.MUTED, story ? 26 : 22, true, Paint.Align.LEFT);
        p.setLetterSpacing(0.14f);
        return p;
    }

    // deputados: grade de eleitos (foto, nome, partido, votos)
    private static void electedGrid(Canvas c, Data d, Model.Result r, float y, int H, boolean story) {
        int count = gridCount(d.dfCargo, d.format);
        List<Model.Cand> list = elected(r, count);
        boolean anyEl = false;
        for (Model.Cand cd : r.cands) if (cd.sit.toLowerCase(Locale.ROOT).startsWith("eleito")) { anyEl = true; break; }
        int cols = 2, rows = (list.size() + 1) / 2;
        float gap = 18, areaBottom = H - 180;
        c.drawText((anyEl ? "ELEITOS • " : "MAIS VOTADOS • ") + list.size() + (d.dfCargo == 4 && anyEl ? " DE 24 VAGAS (MAIS VOTADOS)" : d.dfCargo == 3 && anyEl ? " DE 8 VAGAS" : ""), M, y + 16, labelPaint(story));
        y += story ? 44 : 34;
        float cw = (W - 2 * M - gap) / 2, ch = Math.min((areaBottom - y - gap * (rows - 1)) / Math.max(1, rows), story ? 250 : 170);
        for (int i = 0; i < list.size(); i++) {
            Model.Cand cd = list.get(i);
            int col = Model.color(cd.nome), rr = i / cols, cc = i % cols;
            float x = M + cc * (cw + gap), top = y + rr * (ch + gap);
            RectF rf = new RectF(x, top, x + cw, top + ch);
            cardBox(c, rf, 34, 0);
            float rad = ch * 0.36f, cx = x + 20 + rad, cy = top + ch / 2;
            avatar(c, r, cd, cx, cy, rad, col);
            float tx = cx + rad + 20, mw = x + cw - 18 - tx;
            float ns = story ? 31 : 25;
            Paint np = paint(Ui.TEXT, ns, true, Paint.Align.LEFT);
            String[] ln = nameLines(cd.nome, np, mw);
            float ny = cy - (ln.length == 2 ? ns * 0.55f : ns * 0.1f) - (story ? 14 : 8);
            for (int k = 0; k < ln.length; k++) c.drawText(ln[k], tx, ny + k * (ns * 1.18f), np);
            float my = ny + (ln.length - 1) * ns * 1.18f + ns * 1.1f;
            c.drawText(cd.partido + (cd.numero.isEmpty() ? "" : " • " + cd.numero), tx, my, paint(Ui.MUTED, story ? 23 : 19, false, Paint.Align.LEFT));
            c.drawText(INT.format(cd.votos) + " votos", tx, my + (story ? 36 : 28), paint(Ui.SOFT, story ? 25 : 21, true, Paint.Align.LEFT));
            c.drawText(pc(cd.pct), x + cw - 20, top + (story ? 42 : 34), paint(Ui.MINT, story ? 25 : 20, true, Paint.Align.RIGHT));
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
            c.drawText(Ui.initials(cd.nome), cx, cy + rad * 0.28f, paint(0xFFD1DEEC, rad * 0.8f, true, Paint.Align.CENTER));
        }
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(4, rad * 0.045f));
        ring.setColor(col);
        c.drawCircle(cx, cy, rad - 3, ring);
    }

    private static void mapBlock(Canvas c, Data d, int H, float y, boolean story) {
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
        float mw = story ? W - 2 * M : 560, s = mw / MapData.VIEW_W, mh = MapData.VIEW_H * s;
        float mx = story ? M : 52, my = y + (story ? 10 : -20);
        c.save();
        c.translate(mx, my);
        c.scale(s, s);
        BrazilMapView.drawMap(c, fills, null, new BrazilMapView.Pens(), true);
        c.restore();
        List<Map.Entry<String, Integer>> es = new ArrayList<>(counts.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        float lx = story ? M : 660, ly = story ? my + mh + 40 : y + 40, lw = story ? W - 2 * M : W - 660 - 60;
        c.drawText(loaded + " de 27 UFs com líder definido", lx, ly, paint(Ui.MUTED, story ? 32 : 26, false, Paint.Align.LEFT));
        ly += story ? 30 : 24;
        int shown = 0;
        for (Map.Entry<String, Integer> e : es) {
            if (shown++ >= (story ? 5 : 4)) break;
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Model.color(e.getKey()));
            float ry = ly + (story ? 64 : 58) * shown - 8;
            c.drawCircle(lx + 18, ry - 12, 16, dot);
            Paint cp = paint(Ui.SOFT, story ? 40 : 32, true, Paint.Align.RIGHT);
            String cnt = e.getValue() + (e.getValue() > 1 ? " UFs" : " UF");
            fitText(c, e.getKey(), lx + 52, ry, lw - 60 - cp.measureText(cnt) - 20, story ? 40 : 32, 24, Ui.TEXT, true, Paint.Align.LEFT);
            c.drawText(cnt, lx + lw, ry, cp);
        }
        if (d.pres != null && !story) c.drawText("Apuração " + pc(d.pres.progress), lx, H - 200, paint(Ui.MINT, 38, true, Paint.Align.LEFT));
        else if (d.pres != null) c.drawText("Apuração nacional: " + pc(d.pres.progress) + " das seções", M, H - 240, paint(Ui.MINT, 38, true, Paint.Align.LEFT));
    }

    private static void footer(Canvas c, Data d, int H) {
        float ly = H - 156;
        Paint line = new Paint();
        line.setColor(0xFF1D3858);
        c.drawRect(M, ly, W - M, ly + 2, line);
        c.drawText("Fonte: TSE (dados oficiais) • sem projeção", M, ly + 50, paint(Ui.MUTED, 26, false, Paint.Align.LEFT));
        float ry = ly + 108;
        c.drawText("Atualizado em " + new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BR).format(new Date(d.at)), M, ry, paint(Ui.MUTED, 26, false, Paint.Align.LEFT));
        float cx = W - M - 26, cy = ry - 9;
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setShader(new LinearGradient(cx - 26, cy - 26, cx + 26, cy + 26, Ui.CYAN, Ui.MINT, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 26, dot);
        Paint ck = new Paint(Paint.ANTI_ALIAS_FLAG);
        ck.setStyle(Paint.Style.STROKE);
        ck.setStrokeWidth(7);
        ck.setStrokeCap(Paint.Cap.ROUND);
        ck.setStrokeJoin(Paint.Join.ROUND);
        ck.setColor(Ui.INK);
        Path p = new Path();
        p.moveTo(cx - 11, cy + 1);
        p.lineTo(cx - 3, cy + 10);
        p.lineTo(cx + 13, cy - 10);
        c.drawPath(p, ck);
        c.drawText("Central Eleições 2026", cx - 42, cy + 10, paint(Ui.TEXT, 28, true, Paint.Align.RIGHT));
    }
}
