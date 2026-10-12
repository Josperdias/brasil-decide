package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Gráfico de linhas do percentual do top 3 ao longo das atualizações do TSE. */
final class LineChartView extends View {
    private final List<float[]> series = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);

    LineChartView(Context c) {
        super(c);
        grid.setColor(0xFF183450);
        grid.setStrokeWidth(Ui.dp(1));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(2.6f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        txt.setColor(0xFF7891AD);
        txt.setTextSize(Ui.dp(9));
        setLayoutParams(Ui.lp(-1, Ui.dp(150)));
    }

    void set(List<float[]> s, List<Integer> c) { series.clear(); series.addAll(s); colors.clear(); colors.addAll(c); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), padL = Ui.dp(34), padB = Ui.dp(6), padT = Ui.dp(6);
        int n = 0;
        float min = 100, max = 0;
        for (float[] s : series) { n = Math.max(n, s.length); for (float v : s) { min = Math.min(min, v); max = Math.max(max, v); } }
        if (n < 2) {
            txt.setTextAlign(Paint.Align.CENTER);
            c.drawText("O gráfico nasce conforme chegam novos arquivos do TSE.", w / 2, h / 2, txt);
            txt.setTextAlign(Paint.Align.LEFT);
            return;
        }
        if (max - min < 5) { min = Math.max(0, min - 2.5f); max = Math.min(100, max + 2.5f); } else { min = Math.max(0, min - 1); max = Math.min(100, max + 1); }
        float ph = h - padT - padB, pw = w - padL;
        for (int i = 0; i < 4; i++) {
            float y = padT + ph * i / 3f;
            c.drawLine(padL, y, w, y, grid);
            c.drawText(String.format(java.util.Locale.ROOT, "%.0f%%", max - (max - min) * i / 3f), 0, y + Ui.dp(3), txt);
        }
        for (int k = 0; k < series.size(); k++) {
            float[] s = series.get(k);
            Path p = new Path();
            for (int i = 0; i < s.length; i++) {
                float x = padL + pw * i / (float) (s.length - 1), y = padT + ph * (1 - (s[i] - min) / Math.max(0.01f, max - min));
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            line.setColor(colors.get(k));
            c.drawPath(p, line);
        }
    }
}
