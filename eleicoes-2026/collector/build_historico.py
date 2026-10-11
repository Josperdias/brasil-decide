#!/usr/bin/env python3
"""Eleições passadas (1994–2022): soma, por UF, os votos de presidente e governador a partir de
"votacao_candidato_munzona_{ano}.zip" (TSE, dados abertos). Saída: {out}/{ano}.json e {out}/index.json.
Só resultado oficial; percentuais são calculados no app sobre a soma dos votos dos candidatos."""
import argparse, csv, io, json, os, sys, tempfile, time, urllib.request, zipfile
from collections import defaultdict

CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele/votacao_candidato_munzona"
UAS = [{"User-Agent": "Mozilla/5.0 BrasilDecide-dados", "Accept": "*/*"},
       {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36", "Accept": "*/*"}]
csv.field_size_limit(1 << 24)
ANOS = list(range(1994, 2023, 4))
CARGOS = {"PRESIDENTE": "pres", "GOVERNADOR": "gov"}
SEM_NOME = {"", "#NULO", "#NULO#", "#NE", "#NE#"}
PEQUENAS = {"DA", "DE", "DO", "DAS", "DOS", "E"}


def bonito(nome):
    """JOSÉ DA SILVA -> José da Silva (siglas curtas ficam como estão)."""
    pal = []
    for i, p in enumerate(nome.strip().split()):
        pal.append(p.lower() if i and p in PEQUENAS else (p if len(p) <= 3 and p.isupper() and "." in p else p.capitalize()))
    return " ".join(pal)


def baixar(url, dest):
    ultimo = None
    for i in range(6):
        try:
            req = urllib.request.Request(url, headers=UAS[i % len(UAS)])
            with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
                while True:
                    b = r.read(1 << 20)
                    if not b:
                        break
                    f.write(b)
            return
        except Exception as e:  # o CDN do TSE às vezes responde 403 de forma intermitente
            ultimo = e
            print(f"  falha ao baixar (tentativa {i + 1}/6): {e}", flush=True)
            time.sleep(15 * (i + 1))
    raise ultimo


def processar(ano):
    tmp = tempfile.mkdtemp()
    dest = os.path.join(tmp, "z.zip")
    print(f"{ano}: baixando…", flush=True)
    baixar(f"{CDN}/votacao_candidato_munzona_{ano}.zip", dest)
    z = zipfile.ZipFile(dest)
    nome = next(n for n in z.namelist() if n.upper().endswith("_BRASIL.CSV"))
    votos = defaultdict(int)      # (cargo, turno, uf, nr, nome) -> votos
    info = {}                     # (cargo, turno, uf, nr, nome) -> (urna, partido, situação)
    with z.open(nome) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="latin-1", newline=""), delimiter=";", quotechar='"')
        hdr = next(rd)
        ix = {h: i for i, h in enumerate(hdr)}
        col_v = "QT_VOTOS_NOMINAIS_VALIDOS" if "QT_VOTOS_NOMINAIS_VALIDOS" in ix else "QT_VOTOS_NOMINAIS"
        iv, ic, it, iu = ix[col_v], ix["DS_CARGO"], ix["NR_TURNO"], ix["SG_UF"]
        inr, inm, iur, ip, isit = ix["NR_CANDIDATO"], ix["NM_CANDIDATO"], ix["NM_URNA_CANDIDATO"], ix["SG_PARTIDO"], ix["DS_SIT_TOT_TURNO"]
        n = len(hdr)
        for row in rd:
            if len(row) < n:
                continue
            cargo = CARGOS.get(row[ic].strip().upper())
            if not cargo:
                continue
            try:
                v = int(row[iv] or 0)
            except ValueError:
                continue
            k = (cargo, row[it].strip(), row[iu].strip().upper(), row[inr].strip(), row[inm].strip())
            votos[k] += v
            if k not in info:
                urna = row[iur].strip()
                info[k] = (urna if urna.upper() not in SEM_NOME else row[inm].strip(), row[ip].strip(), row[isit].strip().upper())
    os.remove(dest)
    return votos, info


def montar(ano, votos, info):
    out = {"ano": ano, "tipo": "direta", "fonte": f"TSE — dados abertos, votação por candidato em {ano} (soma dos municípios/zonas; percentuais sobre a soma dos votos dos candidatos)", "nota": ""}
    brasil = defaultdict(int)
    binfo = {}
    por = defaultdict(list)   # (cargo, turno, uf) -> [candidatos]
    for (cargo, turno, uf, nr, nm), v in votos.items():
        if v <= 0:
            continue
        urna, partido, sit = info[(cargo, turno, uf, nr, nm)]
        eleito = sit == "ELEITO"
        d = {"n": bonito(urna), "p": partido, "v": v}
        if eleito:
            d["w"] = 1
        elif sit.startswith("2") and "TURNO" in sit:
            d["s"] = "2º turno"
        por[(cargo, turno, uf)].append(d)
        if cargo == "pres":
            bk = (turno, nr, nm)
            brasil[bk] += v
            if bk not in binfo:
                binfo[bk] = (bonito(urna), partido, eleito, sit.startswith("2") and "TURNO" in sit)
            elif eleito:
                binfo[bk] = (binfo[bk][0], binfo[bk][1], True, False)
    for (turno, nr, nm), v in brasil.items():
        urna, partido, eleito, seg = binfo[(turno, nr, nm)]
        d = {"n": urna, "p": partido, "v": v}
        if eleito:
            d["w"] = 1
        elif seg:
            d["s"] = "2º turno"
        por[("pres", turno, "BR")].append(d)
    for (cargo, turno, uf), l in por.items():
        l.sort(key=lambda d: -d["v"])
        out.setdefault(cargo, {}).setdefault(turno, {})[uf] = l
    return out


def resumo(e):
    for turno in ("2", "1"):
        l = e.get("pres", {}).get(turno, {}).get("BR")
        if not l:
            continue
        tot = sum(d["v"] for d in l)
        for d in l:
            if d.get("w"):
                return {"ano": e["ano"], "vencedor": d["n"], "partido": d["p"], "turno": turno, "votos": d["v"], "pct": round(100 * d["v"] / tot, 2)}
    return {"ano": e["ano"], "vencedor": "", "partido": "", "turno": "", "votos": 0, "pct": -1}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--anos", default="")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    anos = [int(x) for x in a.anos.split(",") if x] or ANOS
    idx = []
    falhas = 0
    for ano in anos:
        try:
            votos, info = processar(ano)
            e = montar(ano, votos, info)
            s = resumo(e)
            if not s["vencedor"]:
                raise RuntimeError("sem vencedor presidencial identificado")
            with open(os.path.join(a.out, f"{ano}.json"), "w", encoding="utf-8") as f:
                json.dump(e, f, ensure_ascii=False, separators=(",", ":"))
            print(f"{ano}: ok — {s['vencedor']} ({s['partido']}) {s['turno']}º turno, {s['pct']}% — presidente {sorted(e['pres'])} governador {sorted(e.get('gov', {}))} "
                  f"UFs(pres 1º)={len(e['pres'].get('1', {}))}", flush=True)
            idx.append(s)
        except Exception as ex:
            falhas += 1
            print(f"{ano}: ERRO {ex}", flush=True)
    # índice: junta com o que já existia (para um ano com falha não sumir da lista)
    ip = os.path.join(a.out, "index.json")
    antigos = {}
    if os.path.exists(ip):
        try:
            antigos = {x["ano"]: x for x in json.load(open(ip, encoding="utf-8"))["eleicoes"]}
        except Exception:
            antigos = {}
    for s in idx:
        antigos[s["ano"]] = s
    with open(ip, "w", encoding="utf-8") as f:
        json.dump({"gerado": time.strftime("%Y-%m-%d"), "eleicoes": [antigos[k] for k in sorted(antigos)]}, f, ensure_ascii=False, separators=(",", ":"))
    print("índice:", len(antigos), "anos; falhas:", falhas)
    sys.exit(1 if falhas and not idx else 0)


if __name__ == "__main__":
    main()
