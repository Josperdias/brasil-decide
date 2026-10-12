package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Layout que quebra linha (legenda do mapa, filtros). */
final class FlowLayout extends ViewGroup {
    private final int gap;

    FlowLayout(Context c, int gapDp) { super(c); gap = Ui.dp(gapDp); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int maxW = MeasureSpec.getSize(wSpec), x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            measureChild(v, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
            if (x > 0 && x + v.getMeasuredWidth() > maxW) { x = 0; y += rowH + gap; rowH = 0; }
            x += v.getMeasuredWidth() + gap;
            rowH = Math.max(rowH, v.getMeasuredHeight());
        }
        setMeasuredDimension(maxW, y + rowH);
    }

    @Override
    protected void onLayout(boolean ch, int l, int t, int r, int b) {
        int maxW = r - l, x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0; }
            v.layout(x, y, x + w, y + h);
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
    }
}
