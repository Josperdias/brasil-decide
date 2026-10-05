package br.com.centraleleicoes.nativeapp;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Panorama político: candidaturas por partido/federação (TSE) e bancadas atuais (Câmara e Senado). Só contagens oficiais. */
final class Panorama {
    private Panorama() {}

    static final class Party {
        String sigla = "", nome = "", num = "";
        int tot, mul, cam, sen;
        Map<String, Integer> cand = new LinkedHashMap<>();
        String federacao = "";
    }

    static final class Data {
        String gerado = "", fonte = "";
        int totCam, totSen;
        List<Party> parties = new ArrayList<>();
        Map<String, String[]> federations = new LinkedHashMap<>(); // sigla -> {nome, composição}
    }

    /** Ordem e rótulos dos cargos nas telas. */
    static final String[][] CARGOS = {{"pres", "Presidente"}, {"gov", "Governador"}, {"sen", "Senador"}, {"df", "Deputado federal"},
            {"de", "Deputado estadual"}, {"dd", "Deputado distrital"}};

    static Data load() {
        try {
            int[] st = {0};
            String body = Tse.httpGet(Replay.DATA_BASE + "panorama.json", st);
            if (st[0] != 200 || body.isEmpty()) return null;
            JSONObject o = new JSONObject(body);
            Data d = new Data();
            d.gerado = o.optString("gerado");
            d.fonte = o.optString("fonte");
            d.totCam = o.optInt("totCam");
            d.totSen = o.optInt("totSen");
            JSONObject fed = o.optJSONObject("federacoes");
            if (fed != null) {
                Iterator<String> it = fed.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject f = fed.getJSONObject(k);
                    d.federations.put(k, new String[]{f.optString("nome"), f.optString("comp")});
                }
            }
            JSONObject ps = o.getJSONObject("partidos");
            Iterator<String> it = ps.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject j = ps.getJSONObject(k);
                Party p = new Party();
                p.sigla = k;
                p.nome = j.optString("nome");
                p.num = j.optString("num");
                p.tot = j.optInt("tot");
                p.mul = j.optInt("mul");
                p.cam = j.optInt("cam");
                p.sen = j.optInt("sen");
                JSONObject c = j.optJSONObject("cand");
                if (c != null) for (String[] cg : CARGOS) if (c.has(cg[0])) p.cand.put(cg[0], c.optInt(cg[0]));
                for (Map.Entry<String, String[]> e : d.federations.entrySet()) {
                    for (String s : e.getValue()[1].split("[/,;+ ]+")) if (s.replaceFirst("^\\d+-", "").trim().equalsIgnoreCase(k)) p.federacao = e.getKey();
                }
                d.parties.add(p);
            }
            return d;
        } catch (Throwable t) { return null; }
    }
}
