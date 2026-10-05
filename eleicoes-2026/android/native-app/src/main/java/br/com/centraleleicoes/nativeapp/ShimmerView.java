package br.com.centraleleicoes.nativeapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Barra "esqueleto" com brilho para estados de carregamento. */
final class ShimmerView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float t;
    private ValueAnimator anim;

    ShimmerView(Context c, int heightDp, int radiusDp) {
        super(c);
        setLayoutParams(Ui.margins(Ui.lp(-1, Ui.dp(heightDp)), 0, 0, 0, 8));
        setTag(radiusDp);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1300);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = Ui.dp((Integer) getTag());
        p.setShader(new LinearGradient(w * (t * 2 - 1), 0, w * (t * 2), 0, new int[]{0xFF0D1F34, 0xFF17304E, 0xFF0D1F34}, null, Shader.TileMode.CLAMP));
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, p);
    }
}
