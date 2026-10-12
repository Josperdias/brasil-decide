package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.widget.ScrollView;

/** ScrollView que não passa de uma fração da altura da tela (painel deslizante). */
final class MaxHeightScrollView extends ScrollView {
    private final int max;

    MaxHeightScrollView(Context c, float fraction) {
        super(c);
        max = (int) (c.getResources().getDisplayMetrics().heightPixels * fraction);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
    }
}
