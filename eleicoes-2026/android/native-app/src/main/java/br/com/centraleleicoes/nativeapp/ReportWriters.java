package br.com.centraleleicoes.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exporta um Analysis.Report para PDF, Word (.docx), Excel (.xlsx), CSV, JSON e imagem PNG, tudo localmente. */
final class ReportWriters {
    private ReportWriters() {}

    static final String[] FORMATS = {"pdf", "docx", "xlsx", "csv", "json", "png"};
    private static final Locale BR = new Locale("pt", "BR");
    private static final NumberFormat INT = NumberFormat.getIntegerInstance(BR);

    static String mime(String fmt) {
        switch (fmt) {
            case "pdf": return "application/pdf";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv": return "text/csv";
            case "json": return "application/json";
            default: return "image/png";
        }
    }

    static String mimeForName(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "application/octet-stream" : mime(name.substring(i + 1).toLowerCase(Locale.ROOT));
    }

    /** Grava o relatório em cache/share (entregue pelo StatusProvider) e devolve o arquivo. */
    static File write(Context ctx, Analysis.Report rep, String fmt) throws Exception {
        File dir = new File(ctx.getCacheDir(), "share");
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null && old.length > 14) for (File f : old) if (f.getName().startsWith("brasil_decide_") && System.currentTimeMillis() - f.lastModified() > 15 * 60 * 1000L) f.delete();
        File f = new File(dir, "brasil_decide_relatorio_" + new SimpleDateFormat("yyyyMMdd_HHmmss", BR).format(new Date()) + "." + fmt);
        writeTo(f, rep, fmt);
        return f;
    }

    static void writeTo(File f, Analysis.Report rep, String fmt) throws Exception {
        byte[] data;
        switch (fmt) {
            case "pdf": data = pdf(rep); break;
            case "docx": data = docx(rep); break;
            case "xlsx": data = xlsx(rep); break;
            case "csv": data = csv(rep); break;
            case "json": data = json(rep); break;
            default: data = png(rep);
        }
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
    }

    // ------------------------------------------------------------------ texto de células
    static String cell(Object o) {
        if (o == null) return "";
        if (o instanceof Long || o instanceof Integer) return INT.format(((Number) o).longValue());
        if (o instanceof Double) return String.format(BR, "%.2f", (Double) o);
        return String.valueOf(o);
    }

    // ------------------------------------------------------------------ CSV (; e BOM para abrir direto no Excel brasileiro)
    private static String q(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    static byte[] csv(Analysis.Report r) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(q(r.title)).append("\r\n").append(q(r.subtitle)).append("\r\n").append(q("Gerado em " + r.generated)).append("\r\n").append(q(r.source)).append("\r\n\r\n");
        for (String h : r.highlights) sb.append(q(h)).append("\r\n");
        for (Analysis.Table t : r.tables) {
            sb.append("\r\n").append(q(t.title)).append("\r\n");
            for (int i = 0; i < t.head.length; i++) sb.append(i > 0 ? ";" : "").append(q(t.head[i]));
            sb.append("\r\n");
            for (Object[] row : t.rows) {
                for (int i = 0; i < row.length; i++) {
                    Object o = row[i];
                    sb.append(i > 0 ? ";" : "");
                    if (o instanceof Double) sb.append(String.format(BR, "%.4f", (Double) o));
                    else if (o instanceof Long || o instanceof Integer) sb.append(((Number) o).longValue());
                    else sb.append(q(String.valueOf(o)));
                }
                sb.append("\r\n");
            }
        }
        sb.append("\r\n");
        for (String c : r.caveats) sb.append(q(c)).append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ JSON
    static byte[] json(Analysis.Report r) throws Exception {
        JSONObject o = new JSONObject().put("titulo", r.title).put("subtitulo", r.subtitle).put("gerado_em", r.generated).put("fonte", r.source);
        o.put("destaques", new JSONArray(r.highlights)).put("ressalvas", new JSONArray(r.caveats));
        JSONArray ts = new JSONArray();
        for (Analysis.Table t : r.tables) {
            JSONArray rows = new JSONArray();
            for (Object[] row : t.rows) {
                JSONObject ro = new JSONObject();
                for (int i = 0; i < t.head.length && i < row.length; i++) ro.put(t.head[i], row[i]);
                rows.put(ro);
            }
            ts.put(new JSONObject().put("titulo", t.title).put("colunas", new JSONArray(java.util.Arrays.asList(t.head))).put("linhas", rows));
        }
        o.put("tabelas", ts);
        return o.toString(2).getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ OOXML comum
    private static String x(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default: if (c >= 0x20 || c == '\n' || c == '\t') b.append(c);
            }
        }
        return b.toString();
    }

    private static void put(ZipOutputStream z, String name, String body) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(body.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    // ------------------------------------------------------------------ XLSX
    private static String col(int i) {
        StringBuilder s = new StringBuilder();
        for (i++; i > 0; i = (i - 1) / 26) s.insert(0, (char) ('A' + (i - 1) % 26));
        return s.toString();
    }

    private static String sheetName(String raw, Set<String> used) {
        String s = raw.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (s.length() > 28) s = s.substring(0, 28).trim();
        if (s.isEmpty()) s = "Planilha";
        String base = s;
        for (int k = 2; !used.add(s.toLowerCase(Locale.ROOT)); k++) s = base + " " + k;
        return s;
    }

    private static void str(StringBuilder sb, int c, int row, String v, boolean bold) {
        sb.append("<c r=\"").append(col(c)).append(row).append("\" t=\"inlineStr\"").append(bold ? " s=\"1\"" : "").append("><is><t xml:space=\"preserve\">").append(x(v)).append("</t></is></c>");
    }

    static byte[] xlsx(Analysis.Report r) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        Set<String> used = new HashSet<>();
        List<String> names = new ArrayList<>();
        names.add(sheetName("Resumo", used));
        for (Analysis.Table t : r.tables) names.add(sheetName(t.title, used));
        StringBuilder ct = new StringBuilder(XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        StringBuilder wb = new StringBuilder(XML + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rel = new StringBuilder(XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < names.size(); i++) {
            ct.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            wb.append("<sheet name=\"").append(x(names.get(i))).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
            rel.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        rel.append("<Relationship Id=\"rId").append(names.size() + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
        ct.append("</Types>");
        wb.append("</sheets></workbook>");
        put(z, "[Content_Types].xml", ct.toString());
        put(z, "_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        put(z, "xl/workbook.xml", wb.toString());
        put(z, "xl/_rels/workbook.xml.rels", rel.toString());
        put(z, "xl/styles.xml", XML + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");

        StringBuilder s = new StringBuilder(XML + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"1\" width=\"110\" customWidth=\"1\"/></cols><sheetData>");
        int row = 1;
        String[] top = {r.title, r.subtitle, "Gerado em " + r.generated, r.source};
        for (int i = 0; i < top.length; i++, row++) { s.append("<row r=\"").append(row).append("\">"); str(s, 0, row, top[i], i == 0); s.append("</row>"); }
        row++;
        s.append("<row r=\"").append(row).append("\">"); str(s, 0, row, "Destaques", true); s.append("</row>"); row++;
        for (String h : r.highlights) { s.append("<row r=\"").append(row).append("\">"); str(s, 0, row, h, false); s.append("</row>"); row++; }
        row++;
        s.append("<row r=\"").append(row).append("\">"); str(s, 0, row, "Ressalvas", true); s.append("</row>"); row++;
        for (String h : r.caveats) { s.append("<row r=\"").append(row).append("\">"); str(s, 0, row, h, false); s.append("</row>"); row++; }
        s.append("</sheetData></worksheet>");
        put(z, "xl/worksheets/sheet1.xml", s.toString());
        for (int i = 0; i < r.tables.size(); i++) {
            Analysis.Table t = r.tables.get(i);
            StringBuilder sh = new StringBuilder(XML + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"" + Math.max(1, t.head.length) + "\" width=\"18\" customWidth=\"1\"/></cols><sheetData><row r=\"1\">");
            for (int c = 0; c < t.head.length; c++) str(sh, c, 1, t.head[c], true);
            sh.append("</row>");
            int rr = 2;
            for (Object[] rowv : t.rows) {
                sh.append("<row r=\"").append(rr).append("\">");
                for (int c = 0; c < rowv.length; c++) {
                    Object o = rowv[c];
                    if (o instanceof Double) sh.append("<c r=\"").append(col(c)).append(rr).append("\"><v>").append(String.format(Locale.ROOT, "%.4f", (Double) o)).append("</v></c>");
                    else if (o instanceof Long || o instanceof Integer) sh.append("<c r=\"").append(col(c)).append(rr).append("\"><v>").append(((Number) o).longValue()).append("</v></c>");
                    else str(sh, c, rr, String.valueOf(o), false);
                }
                sh.append("</row>");
                rr++;
            }
            sh.append("</sheetData></worksheet>");
            put(z, "xl/worksheets/sheet" + (i + 2) + ".xml", sh.toString());
        }
        z.close();
        return bo.toByteArray();
    }

    // ------------------------------------------------------------------ DOCX
    private static void para(StringBuilder b, String text, int halfPts, boolean bold, String color) {
        b.append("<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr><w:r><w:rPr>").append(bold ? "<w:b/>" : "")
                .append(color == null ? "" : "<w:color w:val=\"" + color + "\"/>").append("<w:sz w:val=\"").append(halfPts).append("\"/></w:rPr><w:t xml:space=\"preserve\">")
                .append(x(text)).append("</w:t></w:r></w:p>");
    }

    static byte[] docx(Analysis.Report r) throws Exception {
        StringBuilder b = new StringBuilder(XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        para(b, r.title, 36, true, "0B3A66");
        para(b, r.subtitle, 24, false, null);
        para(b, "Gerado em " + r.generated, 18, false, "666666");
        para(b, "Destaques", 26, true, "0B3A66");
        for (String h : r.highlights) para(b, "• " + h, 20, false, null);
        for (Analysis.Table t : r.tables) {
            para(b, t.title, 26, true, "0B3A66");
            b.append("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders>");
            for (String side : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) b.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BBBBBB\"/>");
            b.append("</w:tblBorders><w:tblLayout w:type=\"autofit\"/></w:tblPr><w:tr>");
            for (String h : t.head) b.append("<w:tc><w:tcPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E3ECF6\"/></w:tcPr><w:p><w:r><w:rPr><w:b/><w:sz w:val=\"16\"/></w:rPr><w:t xml:space=\"preserve\">").append(x(h)).append("</w:t></w:r></w:p></w:tc>");
            b.append("</w:tr>");
            for (Object[] row : t.rows) {
                b.append("<w:tr>");
                for (Object o : row) b.append("<w:tc><w:p><w:r><w:rPr><w:sz w:val=\"16\"/></w:rPr><w:t xml:space=\"preserve\">").append(x(cell(o))).append("</w:t></w:r></w:p></w:tc>");
                b.append("</w:tr>");
            }
            b.append("</w:tbl>");
            para(b, "", 12, false, null);
        }
        para(b, "Fonte e ressalvas", 26, true, "0B3A66");
        para(b, r.source, 18, false, null);
        for (String c : r.caveats) para(b, "• " + c, 18, false, null);
        b.append("<w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/><w:pgMar w:top=\"850\" w:right=\"850\" w:bottom=\"850\" w:left=\"850\" w:header=\"400\" w:footer=\"400\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        put(z, "[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
        put(z, "_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
        put(z, "word/document.xml", b.toString());
        z.close();
        return bo.toByteArray();
    }

    // ------------------------------------------------------------------ PDF (A4 paisagem)
    private static final int PW = 842, PH = 595, MG = 28;

    private static final class Pdf {
        final PdfDocument doc = new PdfDocument();
        PdfDocument.Page page;
        Canvas c;
        float y;
        int n;
        final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG), bold = new TextPaint(Paint.ANTI_ALIAS_FLAG), small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint line = new Paint(), fill = new Paint();

        Pdf() {
            body.setTextSize(9); body.setColor(0xFF1B2733);
            bold.setTextSize(9); bold.setTypeface(Typeface.DEFAULT_BOLD); bold.setColor(0xFF0B3A66);
            small.setTextSize(7.5f); small.setColor(0xFF666666);
            line.setColor(0xFFD0D8E0); line.setStrokeWidth(0.5f);
            fill.setColor(0xFFE3ECF6);
            newPage();
        }

        void newPage() {
            if (page != null) { footer(); doc.finishPage(page); }
            n++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(PW, PH, n).create());
            c = page.getCanvas();
            y = MG;
        }

        void footer() { c.drawText("Brasil Decide • dados oficiais do TSE, sem projeção • página " + n, MG, PH - 14, small); }

        void ensure(float h) { if (y + h > PH - MG - 6) newPage(); }

        float para(String text, TextPaint p, float size, int color) {
            TextPaint tp = new TextPaint(p);
            tp.setTextSize(size);
            tp.setColor(color);
            StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, PW - 2 * MG).setLineSpacing(1.5f, 1f).build();
            ensure(Math.min(sl.getHeight(), 60));
            c.save();
            c.translate(MG, y);
            sl.draw(c);
            c.restore();
            y += sl.getHeight() + 4;
            return sl.getHeight();
        }

        void table(Analysis.Table t) {
            int cols = t.head.length;
            float[] w = new float[cols];
            for (int i = 0; i < cols; i++) w[i] = bold.measureText(t.head[i]) + 8;
            for (Object[] row : t.rows) for (int i = 0; i < cols && i < row.length; i++) w[i] = Math.max(w[i], Math.min(150, body.measureText(cell(row[i])) + 8));
            float tot = 0;
            for (float f : w) tot += f;
            float k = (PW - 2 * MG) / tot;
            if (k < 1) for (int i = 0; i < cols; i++) w[i] *= k;
            else for (int i = 0; i < cols; i++) w[i] *= Math.min(k, 1.25f);
            ensure(40);
            para(t.title, bold, 11, 0xFF0B3A66);
            drawRow(t.head, w, true);
            for (Object[] row : t.rows) {
                if (y + 13 > PH - MG - 6) { newPage(); drawRow(t.head, w, true); }
                String[] cells = new String[cols];
                for (int i = 0; i < cols; i++) cells[i] = i < row.length ? cell(row[i]) : "";
                drawRow(cells, w, false);
            }
            y += 8;
        }

        void drawRow(String[] cells, float[] w, boolean head) {
            float x = MG, h = 13;
            if (head) c.drawRect(MG, y, PW - MG, y + h, fill);
            TextPaint p = head ? bold : body;
            for (int i = 0; i < cells.length; i++) {
                CharSequence s = TextUtils.ellipsize(cells[i], p, w[i] - 5, TextUtils.TruncateAt.END);
                c.drawText(s, 0, s.length(), x + 2, y + 9.5f, p);
                x += w[i];
            }
            c.drawLine(MG, y + h, PW - MG, y + h, line);
            y += h;
        }

        byte[] finish() throws Exception {
            footer();
            doc.finishPage(page);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            doc.writeTo(bo);
            doc.close();
            return bo.toByteArray();
        }
    }

    static byte[] pdf(Analysis.Report r) throws Exception {
        Pdf p = new Pdf();
        p.para(r.title, p.bold, 18, 0xFF0B3A66);
        p.para(r.subtitle + " • gerado em " + r.generated, p.body, 10, 0xFF444444);
        p.y += 4;
        p.para("Destaques", p.bold, 12, 0xFF0B3A66);
        for (String h : r.highlights) p.para("• " + h, p.body, 9.5f, 0xFF1B2733);
        p.y += 6;
        for (Analysis.Table t : r.tables) p.table(t);
        p.para("Fonte e ressalvas", p.bold, 11, 0xFF0B3A66);
        p.para(r.source, p.body, 8.5f, 0xFF333333);
        for (String c : r.caveats) p.para("• " + c, p.body, 8.5f, 0xFF333333);
        return p.finish();
    }

    // ------------------------------------------------------------------ PNG (cartão-resumo para compartilhar)
    static byte[] png(Analysis.Report r) throws Exception {
        int w = 1080, pad = 56;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        List<StaticLayout> blocks = new ArrayList<>();
        List<Float> gaps = new ArrayList<>();
        class B { void add(String s, float size, int color, boolean bold, float gap) {
            TextPaint p = new TextPaint(tp);
            p.setTextSize(size); p.setColor(color); p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            blocks.add(StaticLayout.Builder.obtain(s, 0, s.length(), p, w - 2 * pad).setLineSpacing(0, 1.12f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build());
            gaps.add(gap);
        } }
        B b = new B();
        b.add("BRASIL DECIDE", 30, 0xFF5CE1E6, true, 6);
        b.add(r.subtitle, 44, Color.WHITE, true, 4);
        b.add("Atualizado em " + r.generated, 28, 0xFF9FB4C8, false, 30);
        int maxH = 0;
        for (int i = 0; i < Math.min(8, r.highlights.size()); i++) b.add(r.highlights.get(i), 32, 0xFFE8F0F8, false, 22);
        b.add("Fonte: TSE • contas do app sobre números oficiais • sem projeção" + (r.partial ? " • apuração parcial pode mudar" : ""), 24, 0xFF9FB4C8, false, 0);
        int h = 2 * pad;
        for (int i = 0; i < blocks.size(); i++) h += blocks.get(i).getHeight() + gaps.get(i).intValue();
        Bitmap bmp = Bitmap.createBitmap(w, Math.max(h, 900), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF071321);
        Paint accent = new Paint();
        accent.setColor(0xFF5CE1E6);
        c.drawRect(0, 0, w, 10, accent);
        float y = pad;
        for (int i = 0; i < blocks.size(); i++) {
            c.save();
            c.translate(pad, y);
            blocks.get(i).draw(c);
            c.restore();
            y += blocks.get(i).getHeight() + gaps.get(i);
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bo);
        return bo.toByteArray();
    }
}
