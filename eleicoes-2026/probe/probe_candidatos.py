#!/usr/bin/env python3
"""Sonda 3: detalhes das fontes OFICIAIS abertas (config do TSE com datas, Câmara, Senado) + CORS para o site."""
import json, re, urllib.request, urllib.parse, gzip

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
ORIGIN = "https://josperdias.github.io"

def get(url, hdr=None, timeout=30):
    h = {"User-Agent": UA, "Accept": "application/json, text/plain, */*", "Accept-Language": "pt-BR,pt;q=0.9", "Accept-Encoding": "gzip", "Origin": ORIGIN}
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

def show(label, url, n=600, hdr=None):
    st, h, body = get(url, hdr)
    hl = {k.lower(): v for k, v in h.items()}
    print(f"\n== {label}\n{url}\nstatus {st} | CORS(origin={ORIGIN}) {hl.get('access-control-allow-origin','-')} | x-total-count {hl.get('x-total-count','-')} | bytes {len(body)}")
    print(body[:n].replace("\n", " "))
    return st, body

BASE = "https://resultados.tse.jus.br/oficial"
st, body = show("config ele-c.json (só ele2026)", BASE + "/comum/config/ele-c.json", 0)
try:
    cfg = json.loads(body)
    for p in cfg["pl"]:
        if p.get("c") == "ele2026":
            print("PLEITO", {k: v for k, v in p.items() if k != "e"})
            for e in p["e"]:
                if e.get("tp") in ("1", "2", "3", "4", "5", "6") or e.get("t") in ("1", "2"):
                    print("  ELE", {k: e.get(k) for k in ("cd", "cdt2", "sqele", "nm", "t", "tp", "dt")}, "abr:", [a.get("cd") for a in e.get("abr", [])][:30])
except Exception as ex:
    print("erro config:", ex)

# estrutura do arquivo de resultado (campos de candidato)
st, body = show("presidente BR 1º turno (estrutura)", BASE + "/ele2026/6257/dados/br/br-c0001-e006257-u.json", 0)
try:
    j = json.loads(body)
    carg = j["carg"][0]
    print("carg keys:", list(carg.keys()))
    agr = carg["agr"][0]
    print("agr keys:", list(agr.keys()))
    par = agr.get("par", [{}])[0]
    print("par keys:", list(par.keys()))
    c = par["cand"][0] if "cand" in par else agr["cand"][0]
    print("cand:", json.dumps(c, ensure_ascii=False)[:700])
    sts = {}
    for a in carg["agr"]:
        for pr in a.get("par", []):
            for cd in pr.get("cand", []):
                sts[cd.get("st")] = sts.get(cd.get("st"), 0) + 1
        for cd in a.get("cand", []):
            sts[cd.get("st")] = sts.get(cd.get("st"), 0) + 1
    print("situações:", sts)
except Exception as ex:
    print("erro presidente:", ex)

# Câmara
st, body = show("Câmara: deputado por nome+UF", "https://dadosabertos.camara.leg.br/api/v2/deputados?nome=Pavanato&siglaUf=SP", 500)
try:
    dep = json.loads(body)["dados"][0]; did = dep["id"]; print("dep:", dep["nome"], did)
    show("Câmara: proposições (total no header)", f"https://dadosabertos.camara.leg.br/api/v2/proposicoes?idDeputadoAutor={did}&dataInicio=2023-02-01&itens=3&ordem=DESC&ordenarPor=id", 600)
    show("Câmara: despesas 2026", f"https://dadosabertos.camara.leg.br/api/v2/deputados/{did}/despesas?ano=2026&itens=5&ordem=DESC&ordenarPor=dataDocumento", 600)
    show("Câmara: detalhe", f"https://dadosabertos.camara.leg.br/api/v2/deputados/{did}", 500)
except Exception as ex:
    print("erro câmara:", ex)

# Senado
st, body = show("Senado lista atual", "https://legis.senado.leg.br/dadosabertos/senador/lista/atual", 0, {"Accept": "application/json"})
try:
    arr = json.loads(body)["ListaParlamentarEmExercicio"]["Parlamentares"]["Parlamentar"]
    ip = arr[0]["IdentificacaoParlamentar"]; print("n senadores:", len(arr), "| chaves:", list(ip.keys())[:20], "|", ip["NomeParlamentar"], ip.get("SiglaPartidoParlamentar"), ip.get("UfParlamentar"))
    cod = ip["CodigoParlamentar"]
    show("Senado: autorias", f"https://legis.senado.leg.br/dadosabertos/senador/{cod}/autorias", 500, {"Accept": "application/json"})
except Exception as ex:
    print("erro senado:", ex)
show("Senado CEAPS 2026", "https://adm.senado.gov.br/adm-dadosabertos/api/v1/senadores/despesas_ceaps/2026", 500, {"Accept": "application/json"})

# notícias (CORS do Google News)
q = urllib.parse.quote('"Lula" candidato presidente')
show("Google News RSS", f"https://news.google.com/rss/search?q={q}&hl=pt-BR&gl=BR&ceid=BR:pt-419", 150, {"Accept": "application/rss+xml"})
fq = urllib.parse.quote('"Lula" (site:lupa.news OR site:aosfatos.org OR site:estadao.com.br/estadao-verifica OR site:g1.globo.com/fato-ou-fake)')
show("Google News RSS checagens", f"https://news.google.com/rss/search?q={fq}&hl=pt-BR&gl=BR&ceid=BR:pt-419", 300, {"Accept": "application/rss+xml"})
