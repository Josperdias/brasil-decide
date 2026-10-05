#!/usr/bin/env python3
"""Sonda 5: ciclos antigos no TSE e dados por partido/federação (Câmara, Senado, Assembleias) para o Panorama Político."""
import json, urllib.request, gzip

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
BASE = "https://resultados.tse.jus.br/oficial"

def jget(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip"})
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            b = r.read()
            if r.headers.get("Content-Encoding") == "gzip": b = gzip.decompress(b)
            print(f">> {url} -> {r.status} ({len(b)} bytes)")
            return json.loads(b.decode("utf-8-sig"))
    except urllib.error.HTTPError as e:
        print(f">> {url} -> HTTP {e.code}"); return None
    except Exception as e:
        print(f">> {url} -> ERRO {e}"); return None

cfg = jget(BASE + "/comum/config/ele-c.json")
cycles = {}
for p in cfg["pl"]:
    es = [(e.get("cd"), e.get("cdt2"), e.get("t"), e.get("tp"), e.get("nm")) for e in p["e"] if e.get("tp") in ("1", "2", "8", "9")]
    print("CICLO", p["c"], p["dt"], "| eleições (cd, cdt2, turno, tipo, nome):")
    for x in es[:8]: print("    ", x)
    cycles[p["c"]] = p

def cands(j):
    out = []
    for cg in j.get("carg", []):
        for a in cg.get("agr", []):
            for pr in a.get("par", []):
                for c in pr.get("cand", []):
                    out.append((c, pr, a))
    return out

def summary(j, label):
    print(f"--- {label}")
    print("   carg:", [(c.get("cd"), c.get("nmn"), c.get("nv")) for c in j.get("carg", [])], "| s.pst:", j.get("s", {}).get("pst"))
    cs = cands(j)
    print("   n candidatos:", len(cs))
    el = [(c.get("nmu"), pr.get("sg"), c.get("st"), c.get("vap")) for c, pr, a in cs if str(c.get("st", "")).lower().startswith("eleito")]
    print("   eleitos:", len(el), el[:4])
    c0, pr0, a0 = cs[0]
    print("   agr:", {k: a0.get(k) for k in ("n", "nm", "tp", "com", "vag", "tvtn")}, "| par:", {k: pr0.get(k) for k in ("n", "sg", "nm", "nfed", "tvtn", "tvan")})
    print("   campos do 1º cand:", sorted(c0.keys()))

# 2026: deputado federal em SP
for ele, nm in (("6259", "estadual-2026")):
    pass
j = jget(f"{BASE}/ele2026/6259/dados/sp/sp-c0006-e006259-u.json")
if j: summary(j, "2026 SP dep. federal")
j = jget(f"{BASE}/ele2026/6259/dados/sp/sp-c0005-e006259-u.json")
if j: summary(j, "2026 SP senador")
j = jget(f"{BASE}/ele2026/6259/dados/sp/sp-c0007-e006259-u.json")
if j: summary(j, "2026 SP dep. estadual")

# 2022: descobrir códigos pelo config
p22 = cycles.get("ele2022")
if p22:
    est = [e for e in p22["e"] if e.get("tp") == "1" and e.get("t") == "1"]
    fed = [e for e in p22["e"] if e.get("tp") == "8" and e.get("t") == "1"]
    print("2022 estaduais 1º turno:", [(e["cd"], e["nm"]) for e in est], "| federais:", [(e["cd"], e["nm"]) for e in fed])
    for e in est[:1]:
        cd = e["cd"]; pad = "e" + cd.zfill(6)
        j = jget(f"{BASE}/ele2022/{cd}/dados/sp/sp-c0006-{pad}-u.json")
        if j: summary(j, "2022 SP dep. federal")
    for e in fed[:1]:
        cd = e["cd"]; pad = "e" + cd.zfill(6)
        j = jget(f"{BASE}/ele2022/{cd}/dados/br/br-c0001-{pad}-u.json")
        if j: summary(j, "2022 presidente BR")
else:
    print("ciclo ele2022 NÃO listado no config")
for c in ("ele2018", "ele2020", "ele2024"):
    print(c, "listado:", c in cycles)
print("FIM")
