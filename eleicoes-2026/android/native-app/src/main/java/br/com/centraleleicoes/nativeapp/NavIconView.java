package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Ícones da barra de navegação desenhados em vetor (consistentes em qualquer aparelho, sem depender de emoji). */
final class NavIconView extends View {
    static final int BRASIL = 0, MAPA = 1, DF = 2, MIDIA = 3, MAIS = 4;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    private int color = Ui.MUTED;
    private boolean active;

    NavIconView(Context c, int kind) {
        super(c);
        this.kind = kind;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setLayoutParams(Ui.lp(Ui.dp(26), Ui.dp(26)));
    }

    NavIconView state(boolean on, int col) { active = on; color = col; invalidate(); return this; }

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / 24f;
        c.save();
        c.scale(s, s);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        switch (kind) {
            case BRASIL: { // pódio / barras
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(3, 12, 8.5f, 21), 1.5f, 1.5f, p);
                c.drawRoundRect(new RectF(9.25f, 4, 14.75f, 21), 1.5f, 1.5f, p);
                c.drawRoundRect(new RectF(15.5f, 9, 21, 21), 1.5f, 1.5f, p);
                break;
            }
            case MAPA: { // pino de mapa
                Path pin = new Path();
                pin.moveTo(12, 21.5f);
                pin.cubicTo(6.2f, 15.2f, 4.5f, 12.4f, 4.5f, 9.6f);
                pin.cubicTo(4.5f, 5.6f, 7.8f, 2.5f, 12, 2.5f);
                pin.cubicTo(16.2f, 2.5f, 19.5f, 5.6f, 19.5f, 9.6f);
                pin.cubicTo(19.5f, 12.4f, 17.8f, 15.2f, 12, 21.5f);
                pin.close();
                c.drawPath(pin, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 9.6f, 2.6f, p);
                break;
            }
            case DF: { // prédio com colunas (Congresso/Palácio)
                Path roof = new Path();
                roof.moveTo(2.5f, 9);
                roof.lineTo(12, 3);
                roof.lineTo(21.5f, 9);
                roof.close();
                c.drawPath(roof, p);
                for (int i = 0; i < 4; i++) c.drawLine(5.5f + i * 4.3f, 11.5f, 5.5f + i * 4.3f, 17.5f, p);
                c.drawLine(2.5f, 20.5f, 21.5f, 20.5f, p);
                break;
            }
            case MIDIA: { // tela com play
                c.drawRoundRect(new RectF(2.5f, 5, 21.5f, 19), 3.5f, 3.5f, p);
                p.setStyle(Paint.Style.FILL);
                Path tri = new Path();
                tri.moveTo(10, 8.8f);
                tri.lineTo(10, 15.2f);
                tri.lineTo(15.6f, 12);
                tri.close();
                c.drawPath(tri, p);
                break;
            }
            default: { // grade 2x2
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++)
                    c.drawRoundRect(new RectF(4 + i * 9, 4 + j * 9, 11 + i * 9, 11 + j * 9), 2f, 2f, p);
            }
        }
        c.restore();
    }
}
