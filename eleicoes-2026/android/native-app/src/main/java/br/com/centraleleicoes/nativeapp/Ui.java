package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Paleta e fábrica de views no mesmo visual escuro/azul do site. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF040913, BG2 = 0xFF071321, PANEL = 0xFF0A1728, PANEL2 = 0xFF0E2138, LINE = 0xFF1D3858,
            TEXT = 0xFFF7FBFF, MUTED = 0xFF93A9C2, SOFT = 0xFFC6D5E5, CYAN = 0xFF50D5FF, MINT = 0xFF64F5CB,
            GREEN = 0xFF55DF8B, AMBER = 0xFFFFC966, RED = 0xFFFF8585, PURPLE = 0xFFB9A0FF, INK = 0xFF04111D;
    private static float density = 3f;
    static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static void init(Context c) { density = c.getResources().getDisplayMetrics().density; }

    static int dp(float v) { return (int) (v * density + 0.5f); }

    static GradientDrawable fill(int color, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    static GradientDrawable gradient(int a, int b, float radiusDp, int stroke, GradientDrawable.Orientation o) {
        GradientDrawable g = new GradientDrawable(o, new int[]{a, b});
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    static GradientDrawable cardBg(float radiusDp) {
        return gradient(0xFF0E1F34, 0xFF06101D, radiusDp, LINE, GradientDrawable.Orientation.TL_BR);
    }

    static GradientDrawable pageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0B1E34, 0xFF040913, 0xFF02060D});
    }

    static GradientDrawable accent(float radiusDp) {
        return gradient(CYAN, MINT, radiusDp, 0, GradientDrawable.Orientation.LEFT_RIGHT);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(MEDIUM, Typeface.BOLD);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.ROOT), 10f, MUTED, true);
        t.setLetterSpacing(0.1f);
        return t;
    }

    static TextView big(Context c, CharSequence s, float sp) {
        TextView t = text(c, s, sp, TEXT, false);
        t.setTypeface(BLACK);
        return t;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, float l, float t, float r, float b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(cardBg(20));
        l.setPadding(dp(14), dp(13), dp(14), dp(13));
        l.setLayoutParams(margins(lp(-1, -2), 0, 0, 0, 12));
        return l;
    }

    static TextView chip(Context c, String s, int color) {
        TextView t = text(c, s, 10f, color, true);
        t.setPadding(dp(7), dp(2), dp(7), dp(3));
        t.setBackground(fill(0x00000000, 99, (color & 0x00FFFFFF) | 0x66000000));
        return t;
    }

    static View space(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(hDp)));
        return v;
    }

    static String initials(String n) {
        String[] p = n == null ? new String[0] : n.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(2, p.length); i++) if (!p[i].isEmpty()) b.append(Character.toUpperCase(p[i].charAt(0)));
        return b.length() == 0 ? "?" : b.toString();
    }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    static void setBgIfNull(View v, int color) { if (v.getBackground() == null) v.setBackgroundColor(color); }

    static ViewGroup.LayoutParams match() { return new ViewGroup.LayoutParams(-1, -1); }

    static int mix(int a, int b, float t) {
        return Color.argb(255, (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
