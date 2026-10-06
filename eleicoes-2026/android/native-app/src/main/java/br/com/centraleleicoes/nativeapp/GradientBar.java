package br.com.centraleleicoes.nativeapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Barra de progresso arredondada com degradê (animada ao mudar de valor). */
final class GradientBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float shown, target;
    private int c1 = Ui.CYAN, c2 = Ui.MINT;
    private boolean first = true;
    private ValueAnimator anim;

    GradientBar(Context c, int heightDp) {
        super(c);
        track.setColor(0xFF020914);
        setLayoutParams(Ui.margins(Ui.lp(-1, Ui.dp(heightDp)), 0, 6, 0, 4));
    }

    GradientBar colors(int a, int b) { c1 = a; c2 = b; fill.setShader(null); return this; }

    GradientBar value(double pct) {
        float p = (float) Math.max(0, Math.min(100, Double.isNaN(pct) ? 0 : pct));
        target = p;
        if (first) { first = false; animateTo(p); } else { shown = p; invalidate(); }
        return this;
    }

    private void animateTo(float p) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, p);
        anim.setDuration(550);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { shown = (Float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, track);
        float fw = Math.max(h, w * shown / 100f);
        if (shown <= 0.01f) return;
        if (fill.getShader() == null) fill.setShader(new LinearGradient(0, 0, w, 0, c1, c2, Shader.TileMode.CLAMP));
        r.set(0, 0, fw, h);
        c.drawRoundRect(r, rad, rad, fill);
    }
}
