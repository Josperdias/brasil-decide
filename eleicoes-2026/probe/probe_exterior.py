#!/usr/bin/env python3
"""Sonda 4: estrutura OFICIAL do TSE para o voto no exterior (abrangência ZZ) no ciclo 2026. Só leitura; imprime o que existe."""
import json, urllib.request, gzip, sys

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
BASE = "https://resultados.tse.jus.br/oficial"

def get(url, timeout=40):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip", "Origin": "https://josperdias.github.io"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            b = r.read()
            if r.headers.get("Content-Encoding") == "gzip": b = gzip.decompress(b)
            return r.status, r.headers.get("Access-Control-Allow-Origin", "-"), b.decode("utf-8-sig", "replace")
    except urllib.error.HTTPError as e:
        return e.code, "-", ""
    except Exception as e:
        return 0, "-", "ERRO " + str(e)

def jget(url):
    st, cors, body = get(url)
    print(f"\n>> {url}\n   status {st} | CORS {cors} | bytes {len(body)}")
    if st != 200: return None
    try: return json.loads(body)
    except Exception as e:
        print("   não é JSON:", e, body[:200]); return None

def shape(o, depth=0, maxd=3):
    if isinstance(o, dict): return {k: (shape(v, depth + 1, maxd) if depth < maxd else type(v).__name__) for k, v in list(o.items())[:40]}
    if isinstance(o, list): return [shape(o[0], depth + 1, maxd)] if o else []
    return repr(o)[:40]

for ele in ("6257", "6258"):
    print("\n" + "=" * 20, "eleição", ele, "(1º turno)" if ele == "6257" else "(2º turno)")
    pad = "e00" + ele
    # 1) resultado consolidado do exterior
    for uf in ("zz",):
        j = jget(f"{BASE}/ele2026/{ele}/dados/{uf}/{uf}-c0001-{pad}-u.json")
        if j:
            print("   campos topo:", {k: (v if not isinstance(v, (list, dict)) else type(v).__name__) for k, v in j.items()})
            for blk in ("s", "e", "v"):
                if blk in j: print(f"   bloco {blk}:", j[blk])
            carg = j["carg"][0]
            c = carg["agr"][0]["par"][0]["cand"][0]
            print("   1º cand:", c.get("nmu"), c.get("vap"), c.get("pvap"), c.get("st"))
    # 2) config de municípios (localidades) e zonas
    for name in (f"mun-{pad}-cm.json", f"mun-{pad}-cs.json", f"{pad}-cm.json"):
        j = jget(f"{BASE}/ele2026/{ele}/config/{name}")
        if j:
            print("   forma:", json.dumps(shape(j), ensure_ascii=False)[:900])
            arr = j.get("abr") or j.get("mu") or []
            zz = [a for a in arr if str(a.get("cd", "")).lower() == "zz"]
            print("   n abr:", len(arr), "| zz presente:", bool(zz))
            if zz:
                muns = zz[0].get("mu", [])
                print("   ZZ: n localidades:", len(muns))
                print("   amostra:", json.dumps(muns[:12], ensure_ascii=False)[:1400])
                print("   chaves de uma localidade:", list(muns[0].keys()) if muns else None)
                # 3) arquivo de resultado de uma localidade
                if muns:
                    for m in muns[:3]:
                        cd = str(m.get("cd"))
                        for patt in (f"zz{cd}-c0001-{pad}-u.json", f"zz-{cd}-c0001-{pad}-u.json"):
                            jj = jget(f"{BASE}/ele2026/{ele}/dados/zz/{patt}")
                            if jj:
                                print("   localidade", cd, m.get("nm"), "-> s:", jj.get("s"), "e:", jj.get("e"))
                                cc = jj["carg"][0]["agr"][0]["par"][0]["cand"][0]
                                print("   1º:", cc.get("nmu"), cc.get("vap"), cc.get("pvap"))
                                break
            break

# 4) outros arquivos de configuração que possam trazer país
for p in ("comum/config/ele-c.json",):
    pass
j = jget(f"{BASE}/ele2026/6257/config/mun-e006257-cm.json")
if j:
    print("\nprocurando campo de país/continente nas localidades ZZ…")
    s = json.dumps(j, ensure_ascii=False)
    for kw in ("pais", "País", "country", "continente"):
        print(kw, "->", kw in s)
print("\nFIM")
