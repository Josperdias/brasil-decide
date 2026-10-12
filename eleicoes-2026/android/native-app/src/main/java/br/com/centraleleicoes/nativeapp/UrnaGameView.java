package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * URNA RUSH — minijogo escondido. Arraste para mover a urna e colete votos válidos;
 * fuja dos votos nulos. Dificuldade cresce com a pontuação. Recorde salvo no aparelho.
 */
final class UrnaGameView extends View implements Choreographer.FrameCallback {
    private static final int VOTE = 0, GOLD = 1, NUL = 2, HEART = 3, SLOW = 4;
    private enum State { READY, PLAYING, OVER }

    private static final class Item { float x, y, vy, rot, vr; int type; }
    private static final class Pop { float x, y, life; String s; int color; }

    private final Random rnd = new Random();
    private final List<Item> items = new ArrayList<>();
    private final List<Pop> pops = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final SharedPreferences prefs;
    private State state = State.READY;
    private float urnaX, targetX, spawnT, slowT, shake, elapsed;
    private int score, lives, best, combo, caught;
    private long last;
    private boolean running;
    private Shader bg;
    private final float[] starX = new float[40], starY = new float[40], starS = new float[40];

    UrnaGameView(Context c, SharedPreferences prefs) {
        super(c);
        this.prefs = prefs;
        best = prefs.getInt("hi_urna", 0);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Ui.BLACK);
        for (int i = 0; i < starX.length; i++) { starX[i] = rnd.nextFloat(); starY[i] = rnd.nextFloat(); starS[i] = 0.3f + rnd.nextFloat(); }
    }

    void resume() { if (!running) { running = true; last = 0; Choreographer.getInstance().postFrameCallback(this); } }

    void pause() { running = false; Choreographer.getInstance().removeFrameCallback(this); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }

    @Override protected void onDetachedFromWindow() { pause(); super.onDetachedFromWindow(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg = new LinearGradient(0, 0, 0, h, new int[]{0xFF0B1E34, 0xFF050B16, 0xFF02060D}, null, Shader.TileMode.CLAMP);
        urnaX = targetX = w / 2f;
    }

    private void start() {
        items.clear(); pops.clear();
        score = 0; lives = 3; combo = 0; caught = 0; spawnT = 0.4f; slowT = 0; elapsed = 0; shake = 0;
        state = State.PLAYING;
    }

    private void spawn() {
        Item i = new Item();
        float w = getWidth(), r = rnd.nextFloat();
        i.x = Ui.dp(24) + rnd.nextFloat() * (w - Ui.dp(48));
        i.y = -Ui.dp(30);
        float level = Math.min(1f, score / 120f);
        i.vy = Ui.dp(150) + level * Ui.dp(260) + rnd.nextFloat() * Ui.dp(70);
        i.rot = rnd.nextFloat() * 40 - 20;
        i.vr = rnd.nextFloat() * 90 - 45;
        float nul = 0.18f + level * 0.14f;
        i.type = r < 0.06f ? GOLD : r < 0.06f + nul ? NUL : r < 0.06f + nul + 0.035f ? HEART : r < 0.06f + nul + 0.035f + 0.05f ? SLOW : VOTE;
        items.add(i);
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (nanos - last) / 1e9f);
        last = nanos;
        update(dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(float dt) {
        urnaX += (targetX - urnaX) * Math.min(1f, dt * 16f);
        for (int i = pops.size() - 1; i >= 0; i--) { Pop q = pops.get(i); q.life -= dt; q.y -= Ui.dp(40) * dt; if (q.life <= 0) pops.remove(i); }
        if (shake > 0) shake = Math.max(0, shake - dt * 3f);
        if (state != State.PLAYING) return;
        elapsed += dt;
        float scale = slowT > 0 ? 0.45f : 1f;
        slowT = Math.max(0, slowT - dt);
        spawnT -= dt * scale;
        if (spawnT <= 0) { spawn(); spawnT = Math.max(0.28f, 0.85f - score / 220f) * (0.7f + rnd.nextFloat() * 0.6f); }
        float h = getHeight(), uw = getWidth() * 0.24f, uy = h - Ui.dp(96);
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            it.y += it.vy * dt * scale;
            it.rot += it.vr * dt;
            boolean inX = Math.abs(it.x - urnaX) < uw / 2f + Ui.dp(10);
            if (it.y > uy - Ui.dp(8) && it.y < uy + Ui.dp(26) && inX) { caught(it); items.remove(i); continue; }
            if (it.y > h + Ui.dp(40)) {
                if (it.type == VOTE) combo = 0; // voto válido perdido quebra o combo
                items.remove(i);
            }
        }
    }

    private void pop(Item it, String s, int color) { Pop q = new Pop(); q.x = it.x; q.y = it.y - Ui.dp(10); q.life = 0.8f; q.s = s; q.color = color; pops.add(q); }

    private void caught(Item it) {
        switch (it.type) {
            case VOTE: combo++; caught++; { int mult = 1 + combo / 5; score += mult; pop(it, "+" + mult, Ui.MINT); } break;
            case GOLD: combo += 2; score += 5 * (1 + combo / 5); pop(it, "+" + 5 * (1 + combo / 5), Ui.AMBER); break;
            case HEART: if (lives < 5) lives++; pop(it, "+vida", 0xFFFF7A9A); break;
            case SLOW: slowT = 4f; pop(it, "câmera lenta", Ui.CYAN); break;
            default:
                lives--; combo = 0; shake = 1f; pop(it, "NULO!", Ui.RED);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (lives <= 0) gameOver();
        }
    }

    private void gameOver() {
        state = State.OVER;
        if (score > best) { best = score; prefs.edit().putInt("hi_urna", best).apply(); }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN && state != State.PLAYING) { start(); return true; }
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) targetX = Math.max(getWidth() * 0.12f, Math.min(getWidth() * 0.88f, e.getX()));
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setShader(bg);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        for (int i = 0; i < starX.length; i++) c.drawCircle(starX[i] * w, (starY[i] * h + elapsed * 18 * starS[i]) % h, Ui.dp(1.2f) * starS[i], p);
        c.save();
        if (shake > 0) c.translate((rnd.nextFloat() - 0.5f) * Ui.dp(10) * shake, (rnd.nextFloat() - 0.5f) * Ui.dp(10) * shake);
        for (Item it : items) drawItem(c, it);
        drawUrna(c, w, h);
        c.restore();
        for (Pop q : pops) { t.setColor(Ui.alpha(q.color, (int) (255 * Math.min(1f, q.life * 2f)))); t.setTextSize(Ui.dp(16)); c.drawText(q.s, q.x, q.y, t); }
        drawHud(c, w, h);
        if (state != State.PLAYING) drawOverlay(c, w, h);
    }

    private void drawUrna(Canvas c, float w, float h) {
        float uw = w * 0.24f, uy = h - Ui.dp(96), x = urnaX;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0E2138);
        rf.set(x - uw / 2, uy, x + uw / 2, uy + Ui.dp(58));
        c.drawRoundRect(rf, Ui.dp(12), Ui.dp(12), p);
        p.setColor(slowT > 0 ? Ui.CYAN : Ui.MINT);
        rf.set(x - uw / 2, uy, x + uw / 2, uy + Ui.dp(8));
        c.drawRoundRect(rf, Ui.dp(6), Ui.dp(6), p);
        p.setColor(0xFF020914);
        rf.set(x - uw * 0.32f, uy + Ui.dp(2.5f), x + uw * 0.32f, uy + Ui.dp(6));
        c.drawRoundRect(rf, Ui.dp(2), Ui.dp(2), p);
        p.setColor(0xFF1D3858);
        for (int i = 0; i < 3; i++) c.drawRoundRect(x - uw * 0.3f, uy + Ui.dp(18 + i * 12), x + uw * 0.3f, uy + Ui.dp(24 + i * 12), Ui.dp(3), Ui.dp(3), p);
    }

    private void drawItem(Canvas c, Item it) {
        c.save();
        c.translate(it.x, it.y);
        c.rotate(it.rot);
        float s = Ui.dp(15);
        p.setStyle(Paint.Style.FILL);
        int bgc = it.type == NUL ? 0xFFFFE3E3 : it.type == GOLD ? 0xFFFFE08A : it.type == HEART ? 0xFFFFD6E0 : it.type == SLOW ? 0xFFD6F4FF : 0xFFF7FBFF;
        p.setColor(bgc);
        rf.set(-s, -s * 1.25f, s, s * 1.25f);
        c.drawRoundRect(rf, Ui.dp(5), Ui.dp(5), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dp(3.2f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path g = new Path();
        switch (it.type) {
            case NUL: p.setColor(0xFFE23D4F); g.moveTo(-s * .45f, -s * .45f); g.lineTo(s * .45f, s * .45f); g.moveTo(s * .45f, -s * .45f); g.lineTo(-s * .45f, s * .45f); c.drawPath(g, p); break;
            case HEART: p.setStyle(Paint.Style.FILL); p.setColor(0xFFFF4D7A); t.setColor(0xFFFF4D7A); t.setTextSize(s * 1.5f); c.drawText("♥", 0, s * .5f, t); break;
            case SLOW: p.setColor(0xFF1B8FB8); c.drawCircle(0, 0, s * .55f, p); g.moveTo(0, 0); g.lineTo(0, -s * .38f); g.moveTo(0, 0); g.lineTo(s * .3f, 0); c.drawPath(g, p); break;
            case GOLD: p.setStyle(Paint.Style.FILL); p.setColor(0xFFC9890A); t.setColor(0xFFC9890A); t.setTextSize(s * 1.6f); c.drawText("★", 0, s * .55f, t); break;
            default: p.setColor(0xFF1FB985); g.moveTo(-s * .5f, 0); g.lineTo(-s * .1f, s * .4f); g.lineTo(s * .55f, -s * .45f); c.drawPath(g, p);
        }
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private void drawHud(Canvas c, float w, float h) {
        float top = Ui.dp(52);
        t.setColor(Ui.TEXT); t.setTextSize(Ui.dp(30)); t.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.valueOf(score), Ui.dp(18), top, t);
        t.setColor(Ui.MUTED); t.setTextSize(Ui.dp(11));
        c.drawText("VOTOS" + (combo >= 5 ? "  •  COMBO x" + (1 + combo / 5) : ""), Ui.dp(18), top + Ui.dp(16), t);
        t.setTextAlign(Paint.Align.RIGHT);
        t.setColor(0xFFFF4D7A); t.setTextSize(Ui.dp(20));
        StringBuilder hs = new StringBuilder();
        for (int i = 0; i < lives; i++) hs.append("♥ ");
        c.drawText(hs.toString(), w - Ui.dp(18), top - Ui.dp(4), t);
        t.setColor(Ui.MUTED); t.setTextSize(Ui.dp(11));
        c.drawText("RECORDE " + best, w - Ui.dp(18), top + Ui.dp(14), t);
        t.setTextAlign(Paint.Align.CENTER);
        if (slowT > 0) { t.setColor(Ui.CYAN); t.setTextSize(Ui.dp(12)); c.drawText("CÂMERA LENTA " + (int) Math.ceil(slowT) + "s", w / 2, top + Ui.dp(40), t); }
    }

    private void drawOverlay(Canvas c, float w, float h) {
        p.setColor(0xB0020914);
        c.drawRect(0, 0, w, h, p);
        t.setTextAlign(Paint.Align.CENTER);
        t.setColor(Ui.MINT); t.setTextSize(Ui.dp(12));
        c.drawText(state == State.READY ? "MINIJOGO SECRETO" : "FIM DA APURAÇÃO", w / 2, h * 0.28f, t);
        t.setColor(Ui.TEXT); t.setTextSize(Ui.dp(44));
        c.drawText(state == State.READY ? "URNA RUSH" : score + " votos", w / 2, h * 0.28f + Ui.dp(52), t);
        t.setColor(Ui.SOFT); t.setTextSize(Ui.dp(14));
        String[] lines = state == State.READY
                ? new String[]{"Arraste para mover a urna.", "Colete votos válidos ✓ e estrelas ★.", "Fuja dos votos nulos ✗ — 3 vidas.", "Combo a cada 5 votos seguidos."}
                : new String[]{"Recorde: " + best + (score >= best && score > 0 ? "  (novo!)" : ""), "Votos válidos coletados: " + caught};
        float y = h * 0.28f + Ui.dp(92);
        for (String l : lines) { c.drawText(l, w / 2, y, t); y += Ui.dp(24); }
        t.setColor(Ui.MINT); t.setTextSize(Ui.dp(15));
        c.drawText("Toque para " + (state == State.READY ? "começar" : "jogar de novo"), w / 2, y + Ui.dp(28), t);
        t.setColor(Ui.MUTED); t.setTextSize(Ui.dp(11));
        c.drawText("Botão voltar fecha o jogo", w / 2, h - Ui.dp(60), t);
    }
}
