package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mapa SVG real do Brasil desenhado nativamente (sem WebView); 27 UFs clicáveis coloridas por líder. */
final class BrazilMapView extends View {
    interface OnUf { void onUf(String uf); }

    private final Map<String, Path> paths = new LinkedHashMap<>();
    private final Map<String, Region> regions = new HashMap<>();
    private final Map<String, Integer> fills = new HashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), textStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix(), inv = new Matrix();
    private String selected = "DF";
    private OnUf listener;
    private static final Pattern TOK = Pattern.compile("[mz]|-?\\d*\\.?\\d+(?:[eE]-?\\d+)?");

    BrazilMapView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        textStroke.setColor(0xCC020914);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setFakeBoldText(true);
        textStroke.setStyle(Paint.Style.STROKE);
        Region clip = new Region(0, 0, (int) MapData.VIEW_W + 2, (int) MapData.VIEW_H + 2);
        for (String[] p : MapData.PATHS) {
            Path path = parse(p[1]);
            paths.put(p[0], path);
            Region r = new Region();
            r.setPath(path, clip);
            regions.put(p[0], r);
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
    void setFills(Map<String, Integer> f) { fills.clear(); fills.putAll(f); invalidate(); }

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
        stroke.setStrokeWidth(0.9f);
        text.setTextSize(9f);
        textStroke.setTextSize(9f);
        textStroke.setStrokeWidth(2.4f);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.save();
        c.concat(m);
        for (Map.Entry<String, Path> e : paths.entrySet()) {
            Integer col = fills.get(e.getKey());
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(col == null ? 0xFF17304F : col);
            c.drawPath(e.getValue(), fill);
            stroke.setColor(0xFFCFE3F7);
            stroke.setStrokeWidth(0.9f);
            c.drawPath(e.getValue(), stroke);
        }
        Path sel = paths.get(selected);
        if (sel != null) { stroke.setColor(Color.WHITE); stroke.setStrokeWidth(2.6f); c.drawPath(sel, stroke); }
        for (Object[] l : MapData.LABELS) {
            float r = (Float) l[3];
            if (r < 6.5f) continue;
            float size = Math.max(7f, Math.min(15f, r * 1.05f));
            text.setTextSize(size); textStroke.setTextSize(size);
            float y = (Float) l[2] + size * 0.35f;
            c.drawText((String) l[0], (Float) l[1], y, textStroke);
            c.drawText((String) l[0], (Float) l[1], y, text);
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
        for (Map.Entry<String, Region> e : regions.entrySet())
            if (e.getValue().contains((int) pt[0], (int) pt[1])) { hit = e.getKey(); break; }
        if (hit == null) { // tolerância: UF cujo centro está a menos de 14 unidades (ex.: DF, SE, AL)
            double best = 14 * 14;
            for (Object[] l : MapData.LABELS) {
                double dx = (Float) l[1] - pt[0], dy = (Float) l[2] - pt[1], d2 = dx * dx + dy * dy;
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

    @Override
    public boolean performClick() { return super.performClick(); }
}
