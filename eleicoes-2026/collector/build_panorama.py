#!/usr/bin/env python3
"""Panorama político: candidaturas por partido/federação (TSE, dados abertos) e bancadas atuais (Câmara e Senado).
Só contagens oficiais; nenhuma classificação ideológica. Saída: {out}/panorama.json"""
import argparse, csv, io, json, os, sys, tempfile, time, urllib.request, zipfile
from collections import defaultdict

CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele"
csv.field_size_limit(1 << 24)
UA = {"User-Agent": "Mozilla/5.0 BrasilDecide-dados", "Accept": "application/json"}
CARGOS = {"PRESIDENTE": "pres", "GOVERNADOR": "gov", "SENADOR": "sen", "DEPUTADO FEDERAL": "df",
          "DEPUTADO ESTADUAL": "de", "DEPUTADO DISTRITAL": "dd"}
NULOS = {"", "#NULO", "#NULO#", "#NE", "NÃO DIVULGÁVEL"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))


def candidatos(ano):
    tmp = tempfile.mkdtemp()
    dest = os.path.join(tmp, "c.zip")
    req = urllib.request.Request(f"{CDN}/consulta_cand/consulta_cand_{ano}.zip", headers=UA)
    with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
        while True:
            b = r.read(1 << 20)
            if not b:
                break
            f.write(b)
    z = zipfile.ZipFile(dest)
    nome = [n for n in z.namelist() if n.upper().endswith("_BRASIL.CSV")][0]
    vistos = set()
    with z.open(nome) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="latin-1", newline=""), delimiter=";", quotechar='"')
        hdr = next(rd)
        ix = {h: i for i, h in enumerate(hdr)}
        for row in rd:
            if len(row) < len(hdr):
                continue
            sq = row[ix["SQ_CANDIDATO"]]
            if sq in vistos:
                continue
            vistos.add(sq)
            yield {k: row[ix[k]].strip() for k in ("DS_CARGO", "SG_PARTIDO", "NM_PARTIDO", "NR_PARTIDO", "SG_FEDERACAO",
                                                     "NM_FEDERACAO", "DS_COMPOSICAO_FEDERACAO", "DS_GENERO")}


def bancada_camara():
    cont = defaultdict(int)
    pag = 1
    while True:
        d = get(f"https://dadosabertos.camara.leg.br/api/v2/deputados?itens=100&pagina={pag}&ordem=ASC&ordenarPor=nome").get("dados", [])
        if not d:
            break
        for x in d:
            cont[x.get("siglaPartido") or "—"] += 1
        pag += 1
        if pag > 12:
            break
    return dict(cont)


def bancada_senado():
    d = get("https://legis.senado.leg.br/dadosabertos/senador/lista/atual")
    arr = d["ListaParlamentarEmExercicio"]["Parlamentares"]["Parlamentar"]
    cont = defaultdict(int)
    for p in arr:
        cont[p["IdentificacaoParlamentar"].get("SiglaPartidoParlamentar") or "—"] += 1
    return dict(cont)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ano", default="2026")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    part = defaultdict(lambda: {"cand": defaultdict(int), "mul": 0, "tot": 0})
    fed = {}
    nome_part = {}
    for c in candidatos(a.ano):
        cg = CARGOS.get(c["DS_CARGO"])
        if not cg:
            continue
        sg = c["SG_PARTIDO"]
        if sg in NULOS:
            continue
        p = part[sg]
        p["cand"][cg] += 1
        p["tot"] += 1
        if c["DS_GENERO"].upper().startswith("FEM"):
            p["mul"] += 1
        nome_part[sg] = {"nome": c["NM_PARTIDO"], "num": c["NR_PARTIDO"]}
        if c["SG_FEDERACAO"] not in NULOS:
            fed[c["SG_FEDERACAO"]] = {"nome": c["NM_FEDERACAO"], "comp": c["DS_COMPOSICAO_FEDERACAO"]}
    cam, sen = {}, {}
    try:
        cam = bancada_camara()
    except Exception as e:
        print("Câmara indisponível:", e)
    try:
        sen = bancada_senado()
    except Exception as e:
        print("Senado indisponível:", e)
    siglas = set(part) | set(cam) | set(sen)
    out = {}
    for s in sorted(siglas):
        p = part.get(s)
        out[s] = {"nome": nome_part.get(s, {}).get("nome", ""), "num": nome_part.get(s, {}).get("num", ""),
                  "cand": dict(p["cand"]) if p else {}, "tot": p["tot"] if p else 0, "mul": p["mul"] if p else 0,
                  "cam": cam.get(s, 0), "sen": sen.get(s, 0)}
    os.makedirs(a.out, exist_ok=True)
    doc = {"gerado": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "ano": a.ano,
           "fonte": "TSE (candidaturas), Câmara dos Deputados e Senado Federal (bancadas atuais)",
           "partidos": out, "federacoes": fed, "totCam": sum(cam.values()), "totSen": sum(sen.values())}
    with open(os.path.join(a.out, "panorama.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print(f"partidos: {len(out)} | federações: {len(fed)} | Câmara {doc['totCam']} | Senado {doc['totSen']}")
    top = sorted(out.items(), key=lambda kv: -kv[1]["tot"])[:5]
    for s, v in top:
        print(" ", s, v["tot"], "candidaturas | cam", v["cam"], "| sen", v["sen"])


if __name__ == "__main__":
    sys.exit(main())
