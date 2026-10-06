package br.com.centraleleicoes.nativeapp;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mapa SVG real do Brasil desenhado nativamente: 27 UFs clicáveis, cores por líder com transição suave e pulso na troca de líder. */
final class BrazilMapView extends View {
    interface OnUf { void onUf(String uf); }

    private static final int DEFAULT = 0xFF17304F;
    private static final float INSET_X = 62f, INSET_Y = 566f, INSET_R = 19f;
    private static Map<String, Path> PATHS;

    static synchronized Map<String, Path> allPaths() {
        if (PATHS == null) {
            PATHS = new LinkedHashMap<>();
            for (String[] p : MapData.PATHS) PATHS.put(p[0], parse(p[1]));
        }
        return PATHS;
    }

    /** Canetas de desenho do mapa (compartilhadas entre a View e a geração de imagens de status). */
    static final class Pens {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG), textStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        Pens() {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Ui.BLACK);
            textStroke.setColor(0xCC020914);
            textStroke.setTextAlign(Paint.Align.CENTER);
            textStroke.setTypeface(Ui.BLACK);
            textStroke.setStyle(Paint.Style.STROKE);
            textStroke.setStrokeJoin(Paint.Join.ROUND);
        }
    }

    /** Desenha o mapa em coordenadas do SVG (613x639); o chamador aplica a escala/translação no Canvas. */
    static void drawMap(Canvas c, Map<String, Integer> colors, String selected, Pens pn, boolean withLabels) {
        Map<String, Path> paths = allPaths();
        pn.stroke.setStrokeWidth(0.9f);
        pn.stroke.setColor(0xFFCFE3F7);
        for (Map.Entry<String, Path> e : paths.entrySet()) {
            Integer col = colors.get(e.getKey());
            pn.fill.setStyle(Paint.Style.FILL);
            pn.fill.setColor(col == null ? DEFAULT : col);
            c.drawPath(e.getValue(), pn.fill);
            c.drawPath(e.getValue(), pn.stroke);
        }
        Path sel = selected == null ? null : paths.get(selected);
        if (sel != null) { pn.stroke.setColor(Color.WHITE); pn.stroke.setStrokeWidth(2.6f); c.drawPath(sel, pn.stroke); }
        if (withLabels) for (Object[] l : MapData.LABELS) {
            float r = (Float) l[3];
            if (r < 6.5f) continue;
            float size = Math.max(7f, Math.min(15f, r * 1.05f));
            pn.text.setTextSize(size);
            pn.textStroke.setTextSize(size);
            pn.textStroke.setStrokeWidth(size * 0.22f);
            float y = (Float) l[2] + size * 0.35f;
            c.drawText((String) l[0], (Float) l[1], y, pn.textStroke);
            c.drawText((String) l[0], (Float) l[1], y, pn.text);
        }
        Integer dfc = colors.get("DF");
        pn.fill.setStyle(Paint.Style.FILL);
        pn.fill.setColor(dfc == null ? DEFAULT : dfc);
        c.drawCircle(INSET_X, INSET_Y, INSET_R, pn.fill);
        pn.stroke.setColor("DF".equals(selected) ? Color.WHITE : 0xFFCFE3F7);
        pn.stroke.setStrokeWidth("DF".equals(selected) ? 2.6f : 1.4f);
        c.drawCircle(INSET_X, INSET_Y, INSET_R, pn.stroke);
        pn.text.setTextSize(13f);
        pn.textStroke.setTextSize(13f);
        pn.textStroke.setStrokeWidth(3f);
        c.drawText("DF", INSET_X, INSET_Y + 4.5f, pn.textStroke);
        c.drawText("DF", INSET_X, INSET_Y + 4.5f, pn.text);
        pn.text.setTextSize(8.5f);
        pn.text.setColor(0xFF93A9C2);
        c.drawText("Distrito Federal", INSET_X, INSET_Y + INSET_R + 12f, pn.text);
        pn.text.setColor(Color.WHITE);
    }

    private final Pens pens = new Pens();
    private final Map<String, Region> regions = new HashMap<>();
    private final Map<String, Integer> shown = new HashMap<>(), from = new HashMap<>(), to = new HashMap<>();
    private final Set<String> flashing = new HashSet<>();
    private final Matrix m = new Matrix(), inv = new Matrix();
    private final ArgbEvaluator ev = new ArgbEvaluator();
    private String selected = "DF";
    private OnUf listener;
    private float flash;
    private ValueAnimator colorAnim, flashAnim;
    private static final Pattern TOK = Pattern.compile("[mz]|-?\\d*\\.?\\d+(?:[eE]-?\\d+)?");

    BrazilMapView(Context c) {
        super(c);
        Region clip = new Region(0, 0, (int) MapData.VIEW_W + 2, (int) MapData.VIEW_H + 2);
        for (Map.Entry<String, Path> e : allPaths().entrySet()) {
            Region r = new Region();
            r.setPath(e.getValue(), clip);
            regions.put(e.getKey(), r);
        }
        setContentDescription("Mapa do Brasil por UF");
    }

    /** Interpreta caminhos com apenas "m" (relativo, com lineto implícito) e "z". */
    static Path parse(String d) {
        Path path = new Path();
        Matcher mt = TOK.matcher(d);
        float cx = 0, cy = 0, sx = 0, sy = 0;
        boolean inM = false, first = true;
        float[] pair = new float[2];
        int have = 0;
        while (mt.find()) {
            String t = mt.group();
            if (t.equals("m")) { inM = true; first = true; have = 0; continue; }
            if (t.equals("z")) { path.close(); cx = sx; cy = sy; inM = false; have = 0; continue; }
            if (!inM) continue;
            pair[have++] = Float.parseFloat(t);
            if (have == 2) {
                have = 0;
                cx += pair[0]; cy += pair[1];
                if (first) { path.moveTo(cx, cy); sx = cx; sy = cy; first = false; }
                else path.lineTo(cx, cy);
            }
        }
        return path;
    }

    void setOnUf(OnUf l) { listener = l; }

    void setSelected(String uf) { selected = uf; invalidate(); }

    /** Define as cores-alvo; UFs que mudam de cor fazem transição e, se trocaram de líder, pulsam. */
    void setFills(Map<String, Integer> target) {
        boolean any = false;
        for (String[] p : MapData.PATHS) {
            String uf = p[0];
            int now = shown.containsKey(uf) ? shown.get(uf) : DEFAULT;
            int tgt = target.containsKey(uf) ? target.get(uf) : DEFAULT;
            from.put(uf, now);
            to.put(uf, tgt);
            if (now != tgt) any = true;
            if (now != tgt && now != DEFAULT && tgt != DEFAULT) flashing.add(uf);
        }
        if (!any) return;
        if (colorAnim != null) colorAnim.cancel();
        colorAnim = ValueAnimator.ofFloat(0f, 1f);
        colorAnim.setDuration(650);
        colorAnim.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            for (String uf : to.keySet()) shown.put(uf, (Integer) ev.evaluate(t, from.get(uf), to.get(uf)));
            invalidate();
        });
        colorAnim.start();
        if (!flashing.isEmpty()) {
            if (flashAnim != null) flashAnim.cancel();
            flashAnim = ValueAnimator.ofFloat(1f, 0f);
            flashAnim.setDuration(1400);
            flashAnim.addUpdateListener(a -> { flash = (Float) a.getAnimatedValue(); invalidate(); });
            flashAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator an) { flashing.clear(); flash = 0f; invalidate(); }
            });
            flashAnim.start();
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (colorAnim != null) colorAnim.cancel();
        if (flashAnim != null) flashAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (width * MapData.VIEW_H / MapData.VIEW_W));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = Math.min(w / MapData.VIEW_W, h / MapData.VIEW_H);
        m.setScale(s, s);
        m.invert(inv);
    }

    private int colorOf(String uf) { Integer c = shown.get(uf); return c == null ? DEFAULT : c; }

    @Override
    protected void onDraw(Canvas c) {
        c.save();
        c.concat(m);
        Map<String, Integer> colors = new HashMap<>();
        for (String[] p : MapData.PATHS) colors.put(p[0], colorOf(p[0]));
        drawMap(c, colors, selected, pens, true);
        Map<String, Path> paths = allPaths();
        for (String uf : flashing) {
            Path p = paths.get(uf);
            if (p == null) continue;
            pens.fill.setStyle(Paint.Style.FILL);
            pens.fill.setColor(Color.argb((int) (150 * flash), 255, 255, 255));
            c.drawPath(p, pens.fill);
        }
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (ev.getAction() != MotionEvent.ACTION_UP) return true;
        float[] pt = {ev.getX(), ev.getY()};
        inv.mapPoints(pt);
        String hit = null;
        float dx = pt[0] - INSET_X, dy = pt[1] - INSET_Y;
        if (dx * dx + dy * dy <= INSET_R * INSET_R) hit = "DF";
        if (hit == null)
            for (Map.Entry<String, Region> e : regions.entrySet())
                if (e.getValue().contains((int) pt[0], (int) pt[1])) { hit = e.getKey(); break; }
        if (hit == null) { // tolerância: UF cujo centro está a menos de 14 unidades (ex.: DF, SE, AL)
            double best = 14 * 14;
            for (Object[] l : MapData.LABELS) {
                double lx = (Float) l[1] - pt[0], ly = (Float) l[2] - pt[1], d2 = lx * lx + ly * ly;
                if (d2 < best) { best = d2; hit = (String) l[0]; }
            }
        }
        if (hit != null) {
            selected = hit;
            invalidate();
            performClick();
            if (listener != null) listener.onUf(hit);
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }
}
