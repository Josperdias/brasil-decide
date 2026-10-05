#!/usr/bin/env python3
"""Sonda 2: fontes OFICIAIS para a ficha do candidato (TSE DivulgaCand/DivulgaCandContas, Câmara, Senado) e datas do TSE."""
import json, re, urllib.request, urllib.parse, gzip, sys

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

def get(url, hdr=None, timeout=30):
    h = {"User-Agent": UA, "Accept": "application/json, text/plain, */*", "Accept-Language": "pt-BR,pt;q=0.9", "Accept-Encoding": "gzip"}
    h.update(hdr or {})
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            b = r.read()
            if r.headers.get("Content-Encoding") == "gzip": b = gzip.decompress(b)
            return r.status, dict(r.headers), b.decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), (e.read() or b"").decode("utf-8", "replace")[:300]
    except Exception as e:
        return 0, {}, "ERRO " + str(e)

def show(label, url, n=700, hdr=None):
    st, h, body = get(url, hdr)
    print(f"\n== {label}\n{url}\nstatus {st} | content-type {h.get('Content-Type','?')} | CORS {h.get('Access-Control-Allow-Origin','-')} | bytes {len(body)}")
    print(body[:n].replace("\n", " "))
    return st, body

def keys(o, d=0, maxd=2):
    if isinstance(o, dict):
        return {k: (keys(v, d + 1, maxd) if d < maxd else type(v).__name__) for k, v in list(o.items())[:60]}
    if isinstance(o, list):
        return [keys(o[0], d + 1, maxd)] if o else []
    return type(o).__name__

BASE = "https://resultados.tse.jus.br/oficial"
st, body = show("config ele-c.json", BASE + "/comum/config/ele-c.json", 1500)
try:
    cfg = json.loads(body)
    print("chaves:", json.dumps(keys(cfg), ensure_ascii=False)[:1200])
    for k in ("dt", "dtele", "data", "dataEleicao"):
        if k in cfg: print(k, "=", cfg[k])
except Exception as e:
    print("config não é JSON:", e)

# candidatos do 1º turno (presidente) para pegar sqcand e nomes
st, body = show("presidente BR 1º turno", BASE + "/ele2026/6257/dados/br/br-c0001-e006257-u.json", 300)
sq = None
try:
    j = json.loads(body)
    print("chaves topo:", json.dumps(keys(j, 0, 1), ensure_ascii=False)[:800])
    cands = j["cand"] if "cand" in j else (j.get("abr", [{}])[0].get("cand", []))
    print("cand[0]:", json.dumps(cands[0], ensure_ascii=False)[:600])
    print("situações:", sorted({c.get("st") for c in cands}))
    sq = cands[0].get("sqcand"); nm = cands[0].get("nm")
    print("primeiro:", nm, sq)
except Exception as e:
    print("parse presidente:", e)

# governador SP - situações (2º turno?)
st, body = show("governador SP 1º turno", BASE + "/ele2026/6259/dados/sp/sp-c0003-e006259-u.json", 100)
try:
    j = json.loads(body); cands = j["cand"] if "cand" in j else j.get("abr", [{}])[0].get("cand", [])
    print("SP gov situações:", [(c.get("nm"), c.get("st"), c.get("pvap")) for c in cands[:6]])
except Exception as e:
    print("parse gov:", e)

# DivulgaCandContas
DC = "https://divulgacandcontas.tse.jus.br/divulga/rest/v1"
show("DC eleicoes ordinarias", DC + "/eleicao/ordinarias", 1500)
show("DC eleicoes 2026 (variante)", DC + "/eleicao/listar/municipios/2045202026/BR/cargos", 400)
for el in ("2045202026", "2040602026", "544", "2045202022"):
    show(f"DC cargos BR {el}", f"{DC}/eleicao/listar/municipios/{el}/BR/cargos", 400)
for ano in (2026,):
    for el in ("6257", "2045202026", "544"):
        show(f"DC candidatos presidente {ano}/BR/{el}", f"{DC}/candidatura/listar/{ano}/BR/{el}/1/candidatos", 900)
if sq:
    for el in ("2045202026", "6257", "544"):
        show(f"DC buscar candidato {sq} el={el}", f"{DC}/candidatura/buscar/2026/BR/{el}/candidato/{sq}", 1800)

# Câmara / Senado
show("Câmara deputados (nome)", "https://dadosabertos.camara.leg.br/api/v2/deputados?nome=Lula&ordem=ASC&ordenarPor=nome", 500)
show("Senado lista atual", "https://legis.senado.leg.br/dadosabertos/senador/lista/atual", 600, {"Accept": "application/json"})
# notícias
q = urllib.parse.quote('"Lula" candidato presidente')
show("Google News RSS", f"https://news.google.com/rss/search?q={q}&hl=pt-BR&gl=BR&ceid=BR:pt-419", 500, {"Accept": "application/rss+xml"})
