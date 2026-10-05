package br.com.centraleleicoes.nativeapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Miniatura 16:9 com cantos arredondados, placeholder com brilho e ícone de play. */
final class ThumbView extends View implements Photos.Target {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.ANTI_ALIAS_FLAG), play = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius;
    private Bitmap bmp;
    private String tag = "";
    private float shimmer;
    private ValueAnimator anim;

    ThumbView(Context c, float radiusDp) {
        super(c);
        radius = Ui.dp(radiusDp);
        setLayoutParams(Ui.lp(-1, -2));
    }

    @Override public String tag() { return tag; }
    @Override public void setTag(String t) { tag = t; }

    @Override public void setBitmap(Bitmap b) { bmp = b; img.setShader(null); if (anim != null) anim.cancel(); invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bmp == null) {
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1300);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> { shimmer = (Float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, width * 9 / 16);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        Path clip = new Path();
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (bmp != null) {
            if (img.getShader() == null) {
                BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float s = Math.max(w / bmp.getWidth(), h / bmp.getHeight());
                Matrix m = new Matrix();
                m.setScale(s, s);
                m.postTranslate((w - bmp.getWidth() * s) / 2f, (h - bmp.getHeight() * s) / 2f);
                sh.setLocalMatrix(m);
                img.setShader(sh);
            }
            c.drawRect(r, img);
            bg.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP));
            c.drawRect(r, bg);
        } else {
            bg.setShader(new LinearGradient(w * (shimmer * 2 - 1), 0, w * (shimmer * 2), 0, new int[]{0xFF0E2138, 0xFF173250, 0xFF0E2138}, null, Shader.TileMode.CLAMP));
            c.drawRect(r, bg);
        }
        bg.setShader(null);
        c.restore();
        // ícone de play
        float cx = w / 2f, cy = h / 2f, pr = Math.min(w, h) * 0.13f;
        play.setColor(0xB0020914);
        c.drawCircle(cx, cy, pr, play);
        play.setColor(0xFFFFFFFF);
        Path tri = new Path();
        tri.moveTo(cx - pr * 0.3f, cy - pr * 0.5f);
        tri.lineTo(cx - pr * 0.3f, cy + pr * 0.5f);
        tri.lineTo(cx + pr * 0.55f, cy);
        tri.close();
        c.drawPath(tri, play);
    }
}
