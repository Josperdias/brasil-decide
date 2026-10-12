package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Avatar circular: foto do candidato (TSE) ou iniciais com anel na cor do candidato. */
final class AvatarView extends View implements Photos.Target {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            txt = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bmp;
    private String initials = "?";
    private String tag = "";
    @Override public String tag() { return tag; }
    @Override public void setTag(String t) { tag = t; }

    AvatarView(Context c, int sizeDp) {
        super(c);
        bg.setColor(0xFF122A47);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(2));
        txt.setColor(0xFFD1DEEC);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Ui.BLACK);
        setLayoutParams(Ui.lp(Ui.dp(sizeDp), Ui.dp(sizeDp)));
    }

    AvatarView set(String name, int color) {
        initials = Ui.initials(name);
        ring.setColor(color);
        invalidate();
        return this;
    }

    @Override public void setBitmap(Bitmap b) { bmp = b; img.setShader(null); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), cx = w / 2f, rad = w / 2f - Ui.dp(1);
        c.drawCircle(cx, cx, rad, bg);
        if (bmp != null) {
            if (img.getShader() == null) {
                BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float s = Math.max(w / bmp.getWidth(), w / bmp.getHeight());
                Matrix m = new Matrix();
                m.setScale(s, s);
                m.postTranslate((w - bmp.getWidth() * s) / 2f, (w - bmp.getHeight() * s) / 4f);
                sh.setLocalMatrix(m);
                img.setShader(sh);
            }
            c.drawCircle(cx, cx, rad, img);
        } else {
            txt.setTextSize(w * 0.34f);
            c.drawText(initials, cx, cx + w * 0.12f, txt);
        }
        c.drawCircle(cx, cx, rad, ring);
    }
}
