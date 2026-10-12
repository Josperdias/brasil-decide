package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cartão "Central de análises" (aba Mais): prévia dos destaques e exportação em PDF, Word, Excel, CSV, JSON e imagem. */
final class AnalysisUi {
    interface Host {
        Context ctx();
        Analysis.Input input();
        void shareFile(File f, String mime);
        void rerender();
    }

    private static final String[][] FORMATS = {{"pdf", "📄 PDF"}, {"docx", "📝 Word"}, {"xlsx", "📊 Excel"}, {"csv", "🧾 CSV"}, {"json", "🧩 JSON"}, {"png", "🖼️ Imagem"}};

    private final Host host;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean withGov = true, withEx = true, busy;
    private String status = "";

    AnalysisUi(Host h) { host = h; }

    /** Entrada completa (governadores e exterior incluídos) para o teste automático. */
    Analysis.Input testInput() { Analysis.Input in = host.input(); in.withGov = true; in.withEx = true; return in; }

    View build() {
        final Context c = host.ctx();
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "📑 Central de análises", 15, Ui.TEXT, true));
        card.addView(Ui.text(c, "Relatório do momento com os números oficiais do TSE: resumo, candidatos, estados e, se quiser, governadores e voto no exterior. Gerado no seu aparelho; sem projeção nem interpretação.", 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 3, 0, 8));

        Analysis.Input in = host.input();
        in.withGov = withGov;
        in.withEx = withEx;
        Analysis.Report rep = Analysis.build(in);

        FlowLayout scope = new FlowLayout(c, 7);
        scope.addView(toggle(c, "🏢 Governadores", withGov, v -> { withGov = !withGov; host.rerender(); }));
        scope.addView(toggle(c, "🌍 Exterior", withEx, v -> { withEx = !withEx; host.rerender(); }));
        card.addView(scope);

        LinearLayout prev = Ui.col(c);
        prev.setBackground(Ui.fill(0xFF08182A, 12, 0x99_1D3858));
        prev.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        prev.addView(Ui.text(c, rep.subtitle.toUpperCase(), 10, Ui.MUTED, true));
        int shown = Math.min(5, rep.highlights.size());
        for (int i = 0; i < shown; i++) prev.addView(Ui.text(c, rep.highlights.get(i), 11, Ui.SOFT, false), Ui.margins(Ui.lp(-2, -2), 0, i == 0 ? 6 : 4, 0, 0));
        if (rep.highlights.size() > shown) prev.addView(Ui.text(c, "+ " + (rep.highlights.size() - shown) + " destaques e " + rep.tables.size() + " tabelas no relatório completo", 10, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 6, 0, 0));
        card.addView(prev, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));

        card.addView(Ui.text(c, "EXPORTAR E COMPARTILHAR", 10, Ui.MUTED, true), Ui.margins(Ui.lp(-2, -2), 0, 12, 0, 6));
        FlowLayout fmts = new FlowLayout(c, 7);
        for (final String[] f : FORMATS) {
            TextView t = Ui.text(c, f[1], 13, Ui.INK, true);
            t.setPadding(Ui.dp(14), Ui.dp(9), Ui.dp(14), Ui.dp(9));
            t.setBackground(Ui.accent(99));
            t.setAlpha(busy ? 0.55f : 1f);
            t.setOnClickListener(v -> export(f[0]));
            fmts.addView(t);
        }
        card.addView(fmts);
        if (!status.isEmpty()) card.addView(Ui.text(c, status, 11, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        card.addView(Ui.text(c, "Os arquivos refletem os dados carregados agora; se a apuração ainda não terminou, os números são parciais.", 9, Ui.MUTED, false), Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        return card;
    }

    private TextView toggle(Context c, String label, boolean on, View.OnClickListener l) {
        TextView t = Ui.text(c, (on ? "✓ " : "") + label, 12, on ? Ui.INK : Ui.MUTED, true);
        t.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(12), Ui.dp(7));
        t.setBackground(on ? Ui.accent(99) : Ui.fill(0xFF09182A, 99, Ui.LINE));
        t.setOnClickListener(l);
        return t;
    }

    private void export(final String fmt) {
        if (busy) return;
        final Context c = host.ctx();
        final Analysis.Input in = host.input();
        in.withGov = withGov;
        in.withEx = withEx;
        if (in.pres == null) { Toast.makeText(c, "Os dados ainda não foram carregados.", Toast.LENGTH_SHORT).show(); return; }
        busy = true;
        status = "Gerando " + fmt.toUpperCase() + "…";
        host.rerender();
        pool.execute(() -> {
            File f = null;
            String err = "";
            try { f = ReportWriters.write(c, Analysis.build(in), fmt); } catch (Throwable t) { err = t.getClass().getSimpleName() + ": " + t.getMessage(); }
            final File out = f;
            final String e = err;
            ui.post(() -> {
                busy = false;
                status = out != null ? "Pronto: " + out.getName() : "Não foi possível gerar o arquivo (" + e + ").";
                host.rerender();
                if (out != null) host.shareFile(out, ReportWriters.mime(fmt));
            });
        });
    }
}
