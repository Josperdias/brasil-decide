#!/usr/bin/env python3
"""Resumo de transparência por candidato a partir dos dados abertos do TSE (patrimônio declarado,
receitas e despesas contratadas de campanha). Só agrega: nenhum doador ou fornecedor individual é publicado.

Saída: {out}/{UF}.json  (UF em minúsculas, "br" = presidente)
  {"gerado": "...", "fonte": "...", "c": {"<SQ_CANDIDATO>": {"pat": 0.0, "nb": 0, "rec": 0.0, "rO": {origem: valor}, "des": 0.0, "dO": {origem: valor}}}}
"""
import argparse, csv, io, json, os, sys, tempfile, time, urllib.request, zipfile
from collections import defaultdict

CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele"
FONTE = "TSE — Dados Abertos (consulta_cand, bem_candidato, prestação de contas)"
UAS = ["Mozilla/5.0 BrasilDecide-dados", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"]
NULOS = {"", "#NULO", "#NULO#", "#NE", "NÃO DIVULGÁVEL", "NAO DIVULGAVEL"}
csv.field_size_limit(1 << 24)


def baixar(ds, fn, dest):
    ultimo = None
    for tentativa in range(6):
        t = time.time()
        try:
            req = urllib.request.Request(f"{CDN}/{ds}/{fn}", headers={"User-Agent": UAS[tentativa % len(UAS)], "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
                while True:
                    b = r.read(1 << 20)
                    if not b:
                        break
                    f.write(b)
            print(f"baixado {fn}: {os.path.getsize(dest)/1e6:.1f} MB em {time.time()-t:.0f}s", flush=True)
            return zipfile.ZipFile(dest)
        except Exception as e:  # o CDN do TSE às vezes responde 403 de forma intermitente
            ultimo = e
            espera = 15 * (tentativa + 1)
            print(f"falha ao baixar {fn} (tentativa {tentativa + 1}/6): {e}; nova tentativa em {espera}s", flush=True)
            time.sleep(espera)
    raise ultimo


def linhas(z, nome):
    with z.open(nome) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="latin-1", newline=""), delimiter=";", quotechar='"')
        hdr = next(rd)
        idx = {h: i for i, h in enumerate(hdr)}
        for row in rd:
            if len(row) >= len(hdr):
                yield idx, row


def dinheiro(s):
    s = (s or "").strip()
    if not s or s in NULOS:
        return 0.0
    if "," in s:
        s = s.replace(".", "").replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return 0.0


def achar(z, prefixo):
    cand = [n for n in z.namelist() if n.lower().endswith(".csv") and os.path.basename(n).lower().startswith(prefixo)]
    br = [n for n in cand if n.upper().endswith("_BRASIL.CSV")]
    return br or cand


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ano", default="2026")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    tmp = tempfile.mkdtemp()
    ano = a.ano

    # 1) quem é quem: SQ_CANDIDATO -> UF (SG_UF; "BR" = presidente)
    z = baixar("consulta_cand", f"consulta_cand_{ano}.zip", os.path.join(tmp, "cand.zip"))
    uf_de = {}
    for nome in achar(z, "consulta_cand_"):
        for ix, r in linhas(z, nome):
            sq = r[ix["SQ_CANDIDATO"]].strip()
            uf = r[ix["SG_UF"]].strip().lower()
            if sq and uf:
                uf_de[sq] = uf
    print("candidatos:", len(uf_de), flush=True)
    dados = defaultdict(lambda: {"pat": 0.0, "nb": 0, "rec": 0.0, "rO": defaultdict(float), "des": 0.0, "dO": defaultdict(float)})

    # 2) patrimônio declarado
    z = baixar("bem_candidato", f"bem_candidato_{ano}.zip", os.path.join(tmp, "bem.zip"))
    for nome in achar(z, "bem_candidato_"):
        for ix, r in linhas(z, nome):
            sq = r[ix["SQ_CANDIDATO"]].strip()
            v = dinheiro(r[ix["VR_BEM_CANDIDATO"]])
            d = dados[sq]
            d["pat"] += v
            d["nb"] += 1

    # 3) prestação de contas: receitas e despesas contratadas (agregadas por origem)
    try:
        z = baixar("prestacao_contas", f"prestacao_de_contas_eleitorais_candidatos_{ano}.zip", os.path.join(tmp, "pc.zip"))
        for nome in achar(z, "receitas_candidatos_" + ano):
            for ix, r in linhas(z, nome):
                sq = r[ix["SQ_CANDIDATO"]].strip()
                if sq in ("", "#NULO", "#NULO#"):
                    continue
                v = dinheiro(r[ix["VR_RECEITA"]])
                o = r[ix["DS_ORIGEM_RECEITA"]].strip()
                o = "Não informada" if o in NULOS else o
                d = dados[sq]
                d["rec"] += v
                d["rO"][o] += v
        for nome in achar(z, "despesas_contratadas_candidatos_" + ano):
            for ix, r in linhas(z, nome):
                if "SQ_CANDIDATO" not in ix:
                    break
                sq = r[ix["SQ_CANDIDATO"]].strip()
                if sq in ("", "#NULO", "#NULO#"):
                    continue
                v = dinheiro(r[ix["VR_DESPESA_CONTRATADA"]])
                o = r[ix["DS_ORIGEM_DESPESA"]].strip()
                o = "Não informada" if o in NULOS else o
                d = dados[sq]
                d["des"] += v
                d["dO"][o] += v
    except Exception as e:
        print("prestação de contas indisponível:", e, flush=True)

    # 4) grava um arquivo por UF
    por_uf = defaultdict(dict)
    sem_uf = 0
    for sq, d in dados.items():
        uf = uf_de.get(sq)
        if not uf:
            sem_uf += 1
            continue
        top = lambda m: {k: round(v, 2) for k, v in sorted(m.items(), key=lambda kv: -kv[1])[:8] if v}
        por_uf[uf][sq] = {"pat": round(d["pat"], 2), "nb": d["nb"], "rec": round(d["rec"], 2), "rO": top(d["rO"]),
                          "des": round(d["des"], 2), "dO": top(d["dO"])}
    os.makedirs(a.out, exist_ok=True)
    agora = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    tot = 0
    for uf, m in sorted(por_uf.items()):
        with open(os.path.join(a.out, f"{uf}.json"), "w", encoding="utf-8") as f:
            json.dump({"gerado": agora, "fonte": FONTE, "ano": ano, "c": m}, f, ensure_ascii=False, separators=(",", ":"))
        tot += len(m)
    print(f"UFs: {len(por_uf)} | candidatos com dados: {tot} | sem UF: {sem_uf}", flush=True)
    for uf in ("df", "br"):
        m = por_uf.get(uf, {})
        com_pat = sum(1 for v in m.values() if v["pat"])
        com_rec = sum(1 for v in m.values() if v["rec"])
        print(f"  {uf}: {len(m)} candidatos | com patrimônio {com_pat} | com receitas {com_rec}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
