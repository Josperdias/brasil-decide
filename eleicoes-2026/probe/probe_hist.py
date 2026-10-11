#!/usr/bin/env python3
"""Sonda 11: o que o TSE publica agora — ele-c.json (2026) e arquivos BR/AM/ZZ nos códigos 6257 e 6258 (+ estaduais 6259/6260)."""
import json, urllib.request
BASE = "https://resultados.tse.jus.br/oficial"
H = {"User-Agent": "Mozilla/5.0 BrasilDecide-probe"}
def get(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u, headers=H), timeout=60) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, ""
    except Exception as e:
        return 0, str(e)
st, body = get(BASE + "/comum/config/ele-c.json")
print("ele-c.json", st, len(body))
try:
    cfg = json.loads(body)
    for p in cfg.get("pl", []):
        if p.get("c") == "ele2026":
            print("PLEITO", {k: v for k, v in p.items() if k != "e"})
            for e in p.get("e", []):
                print("  ELE", {k: e.get(k) for k in ("cd", "cdt2", "nm", "t", "tp", "dt", "esp", "sqele")})
except Exception as ex:
    print("config ilegível:", ex)
def info(ele, abr, cargo):
    u = f"{BASE}/ele2026/{ele}/dados/{abr}/{abr}-c{cargo:04d}-e{int(ele):06d}-u.json"
    st, b = get(u)
    if st != 200:
        return f"HTTP {st}"
    j = json.loads(b[1:] if b.startswith("﻿") else b)
    cands = []
    for ag in j["carg"][0]["agr"]:
        for par in ag.get("par", []):
            for c in par.get("cand", []):
                cands.append((c.get("nmu") or c.get("nm"), c.get("vap"), c.get("sqcand"), c.get("st")))
    s = j.get("s", {}); e = j.get("e", {})
    return f"dg={j.get('dg')} hg={j.get('hg')} pst={s.get('pst')} st={s.get('st')}/{s.get('ts')} te={e.get('te')} c={e.get('c')} candidatos={len(cands)} top={cands[:3]}"
for ele, abr, cargo in (("6257", "br", 1), ("6258", "br", 1), ("6257", "am", 1), ("6258", "am", 1), ("6257", "zz", 1), ("6258", "zz", 1),
                        ("6259", "am", 3), ("6260", "am", 3), ("6260", "df", 3), ("6260", "ac", 3)):
    print(ele, abr, cargo, info(ele, abr, cargo))
print("FIM")
