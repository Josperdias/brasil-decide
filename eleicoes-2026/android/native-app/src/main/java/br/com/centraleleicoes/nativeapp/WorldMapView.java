package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Mapa-múndi eleitoral: cada país colorido pelo candidato líder (mesma cor do resto do app); sem dados = cinza neutro. */
final class WorldMapView extends View {
    interface OnCountry { void onCountry(String iso2); }

    private static final int NEUTRAL = 0xFF17304F, DIM = 0xFF0E1F34;
    private static Map<String, Path> PATHS;
    private static Map<String, float[]> GEO;

    static synchronized Map<String, Path> paths() {
        if (PATHS == null) {
            PATHS = new LinkedHashMap<>();
            GEO = new HashMap<>();
            for (int i = 0; i < ExteriorData.COUNTRIES.length; i++) {
                String[] c = ExteriorData.COUNTRIES[i];
                if (!c[3].isEmpty()) PATHS.put(c[0], BrazilMapView.parse(c[3]));
                GEO.put(c[0], ExteriorData.GEO[i]);
            }
        }
        return PATHS;
    }

    static float[] geo(String iso2) { paths(); return GEO.get(iso2); }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix(), inv = new Matrix();
    private final Map<String, Integer> colors = new HashMap<>();
    private final Set<String> dimmed = new HashSet<>(), hasData = new HashSet<>();
    private String selected = "";
    private OnCountry listener;
    private final float[] pt = new float[2];

    WorldMapView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        paths();
        setContentDescription("Mapa-múndi do voto dos brasileiros no exterior");
    }

    void setOnCountry(OnCountry l) { listener = l; }

    /** iso2 -> cor do líder; {@code withData}: países com localidades (desenha marcador nos pequenos); {@code dim}: países fora do filtro. */
    void setData(Map<String, Integer> leaderColors, Set<String> withData, Set<String> dim) {
        colors.clear();
        colors.putAll(leaderColors);
        hasData.clear();
        hasData.addAll(withData);
        dimmed.clear();
        dimmed.addAll(dim);
        invalidate();
    }

    void setSelected(String iso2) { selected = iso2 == null ? "" : iso2; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (width * ExteriorData.VIEW_H / ExteriorData.VIEW_W));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = w / ExteriorData.VIEW_W;
        m.setScale(s, s);
        m.invert(inv);
    }

    @Override protected void onDraw(Canvas c) {
        c.save();
        c.concat(m);
        stroke.setStrokeWidth(0.7f);
        stroke.setColor(0x99CFE3F7);
        for (Map.Entry<String, Path> e : paths().entrySet()) {
            String k = e.getKey();
            Integer col = colors.get(k);
            int base = col != null ? col : (hasData.contains(k) ? 0xFF2A4A70 : NEUTRAL);
            if (dimmed.contains(k)) base = col != null ? Ui.mix(col, DIM, 0.72f) : DIM;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(base);
            c.drawPath(e.getValue(), fill);
            c.drawPath(e.getValue(), stroke);
        }
        // marcadores para países pequenos com dados (sem área visível) e para a Guiana Francesa (sem polígono próprio)
        for (String iso : hasData) {
            float[] g = geo(iso);
            if (g == null) continue;
            boolean tiny = g[2] < 60f;
            if (!tiny) continue;
            Integer col = colors.get(iso);
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(dimmed.contains(iso) ? DIM : (col != null ? col : 0xFF2A4A70));
            c.drawCircle(g[0], g[1], 4.2f, dot);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(0.9f);
            dot.setColor(0xCCCFE3F7);
            c.drawCircle(g[0], g[1], 4.2f, dot);
        }
        Path sel = paths().get(selected);
        if (sel != null) { stroke.setColor(Color.WHITE); stroke.setStrokeWidth(2f); c.drawPath(sel, stroke); }
        else if (!selected.isEmpty() && geo(selected) != null) {
            float[] g = geo(selected);
            dot.setStyle(Paint.Style.STROKE); dot.setStrokeWidth(2f); dot.setColor(Color.WHITE);
            c.drawCircle(g[0], g[1], 5.6f, dot);
        }
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (ev.getActionMasked() != MotionEvent.ACTION_UP) return true;
        pt[0] = ev.getX();
        pt[1] = ev.getY();
        inv.mapPoints(pt);
        String hit = pick(pt[0], pt[1]);
        if (hit != null && listener != null) { performClick(); listener.onCountry(hit); }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    /** Marcador pequeno mais próximo (raio generoso para o dedo) tem prioridade; senão, o país que contém o ponto. */
    private String pick(float x, float y) {
        String best = null;
        float bd = 1e9f;
        float reach = Ui.dp(16) / Math.max(0.01f, m.mapRadius(1f));
        for (String iso : hasData) {
            float[] g = geo(iso);
            if (g == null || g[2] >= 60f) continue;
            float d = (float) Math.hypot(g[0] - x, g[1] - y);
            if (d < reach && d < bd) { bd = d; best = iso; }
        }
        if (best != null) return best;
        android.graphics.Region clip = new android.graphics.Region(0, 0, (int) ExteriorData.VIEW_W + 2, (int) ExteriorData.VIEW_H + 2);
        for (Map.Entry<String, Path> e : paths().entrySet()) {
            android.graphics.RectF b = new android.graphics.RectF();
            e.getValue().computeBounds(b, true);
            if (!b.contains(x, y)) continue;
            android.graphics.Region r = new android.graphics.Region();
            r.setPath(e.getValue(), clip);
            if (r.contains((int) x, (int) y)) return e.getKey();
        }
        return null;
    }
}
