#!/usr/bin/env python3
"""Sonda: reproduz o que o app nativo busca (YouTube, notícias, TSE-DF) e mostra o que volta, para diagnóstico."""
import json, re, sys, urllib.request, urllib.parse, gzip

UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

def get(url, headers=None):
    h = {"User-Agent": UA, "Accept-Language": "pt-BR,pt;q=0.9", "Cookie": "CONSENT=YES+1; SOCS=CAI", "Accept-Encoding": "gzip"}
    h.update(headers or {})
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=25) as r:
        b = r.read()
        if r.headers.get("Content-Encoding") == "gzip": b = gzip.decompress(b)
        return r.status, r.geturl(), b.decode("utf-8", "replace")

def initial_data(html):
    m = re.search(r"ytInitialData\s*=\s*\{", html)
    if not m: return None
    start = m.end() - 1; depth = 0; s = False; e = False
    for i in range(start, len(html)):
        ch = html[i]
        if s:
            if e: e = False
            elif ch == "\\": e = True
            elif ch == '"': s = False
            continue
        if ch == '"': s = True
        elif ch == "{": depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0: return json.loads(html[start:i + 1])
    return None

def collect(n, out, d=0):
    if d > 14 or len(out) >= 24: return
    if isinstance(n, dict):
        if "videoRenderer" in n: out.append(n["videoRenderer"]); return
        for v in n.values(): collect(v, out, d + 1)
    elif isinstance(n, list):
        for v in n: collect(v, out, d + 1)

def txt(o):
    if not o: return ""
    return o.get("simpleText") or "".join(r.get("text", "") for r in o.get("runs", []))

def yt(q, sp):
    url = "https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + ("&sp=" + sp if sp else "")
    print("\n== YOUTUBE", q, sp)
    try:
        st, final, html = get(url)
        print("status", st, "final", final[:90], "len", len(html), "consent?", "consent.youtube" in final or "Antes de ir" in html[:5000])
        data = initial_data(html)
        print("ytInitialData:", "ok" if data else "AUSENTE", "| 'videoRenderer' no html:", html.count("videoRenderer"))
        out = []
        if data: collect(data, out)
        print("videos:", len(out))
        for v in out[:6]:
            live = any("LIVE" in json.dumps(b) for b in v.get("badges", [])) or any(o.get("thumbnailOverlayTimeStatusRenderer", {}).get("style") == "LIVE" for o in v.get("thumbnailOverlays", []))
            print(" -", v.get("videoId"), "|", txt(v.get("title"))[:70], "|", txt(v.get("ownerText"))[:30], "|", txt(v.get("viewCountText"))[:30], "|", txt(v.get("publishedTimeText")), "| live" if live else "")
    except Exception as ex:
        print("ERRO", ex)

def rss(name, url):
    try:
        st, final, body = get(url, {"Accept": "application/rss+xml, application/xml, text/xml, */*"})
        items = len(re.findall(r"<item>|<entry>", body))
        print(f"RSS {name}: status {st}, itens {items}, bytes {len(body)}")
    except Exception as ex:
        print(f"RSS {name}: ERRO {ex}")

DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
def yt2(label, q, sp, ua, extra=""):
    url = "https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + ("&sp=" + sp if sp else "") + extra
    print("\n== YT", label, "|", q, sp, extra)
    try:
        st, final, html = get(url, {"User-Agent": ua})
        print("status", st, "final", final[:80], "len", len(html))
        print("markers: ytInitialData=", len(re.findall(r"ytInitialData\s*=", html)), "| videoRenderer", html.count("videoRenderer"), "| videoWithContextRenderer", html.count("videoWithContextRenderer"), "| compactVideoRenderer", html.count("compactVideoRenderer"), "| 'ytInitialData'", html.count("ytInitialData"))
        data = initial_data(html)
        out = []
        if data: collect(data, out)
        print("videos:", len(out))
        for v in out[:5]:
            live = any("LIVE" in json.dumps(b) for b in v.get("badges", [])) or any(o.get("thumbnailOverlayTimeStatusRenderer", {}).get("style") == "LIVE" for o in v.get("thumbnailOverlays", []))
            print(" -", v.get("videoId"), "|", txt(v.get("title"))[:60], "|", txt(v.get("ownerText"))[:25], "|", txt(v.get("viewCountText"))[:25], "|", txt(v.get("publishedTimeText")), "| LIVE" if live else "")
    except Exception as ex:
        print("ERRO", ex)

yt2("desktop-live", "eleições 2026 ao vivo", "EgJAAQ%3D%3D", DESKTOP)
yt2("desktop-live-hlgl", "eleições 2026 ao vivo", "EgJAAQ%3D%3D", DESKTOP, "&hl=pt-BR&gl=BR")
yt2("desktop-globonews", "GloboNews ao vivo", "EgJAAQ%3D%3D", DESKTOP)
yt2("desktop-apuracao", "apuração eleições 2026", "CAI%3D", DESKTOP)
yt2("desktop-debate", "debate eleições 2026", "CAI%3D", DESKTOP)
yt2("mobile-live", "eleições 2026 ao vivo", "EgJAAQ%3D%3D", UA)

print("\n== NOTICIAS")
q = urllib.parse.quote("eleições 2026 presidente when:2d")
rss("gn-pt", f"https://news.google.com/rss/search?q={q}&hl=pt-BR&gl=BR&ceid=BR:pt-419")
for n, u in [("g1", "https://g1.globo.com/rss/g1/politica/"), ("folha", "https://feeds.folha.uol.com.br/poder/rss091.xml"),
             ("agbr", "https://agenciabrasil.ebc.com.br/rss/politica/feed.xml"), ("p360", "https://www.poder360.com.br/feed/"),
             ("bbcpt", "https://feeds.bbci.co.uk/portuguese/rss.xml"), ("bbcw", "https://feeds.bbci.co.uk/news/world/rss.xml"),
             ("guardian", "https://www.theguardian.com/world/rss"), ("infomoney", "https://www.infomoney.com.br/feed/")]:
    rss(n, u)

print("\n== TSE DF (6259 estadual / 6257 federal)")
base = "https://resultados.tse.jus.br/oficial/ele2026"
for e, cargo, nome in [("6259", 3, "governador"), ("6259", 5, "senador"), ("6259", 6, "dep federal"), ("6259", 8, "dep distrital"), ("6257", 1, "presidente DF")]:
    url = f"{base}/{e}/dados/df/df-c{cargo:04d}-e{int(e):06d}-u.json"
    try:
        st, final, body = get(url, {"Accept": "application/json"})
        j = json.loads(body.lstrip("﻿"))
        cands = [c for agr in j["carg"][0]["agr"] for par in agr["par"] for c in par["cand"]]
        cands.sort(key=lambda c: -int(re.sub(r"\D", "", str(c.get("vap", "0"))) or 0))
        print(f"{nome}: {len(cands)} candidatos | vagas? {j['carg'][0].get('nv')} | pst {j.get('s', {}).get('pst')}")
        for c in cands[:4]:
            print("   ", c.get("n"), c.get("nmu") or c.get("nm"), c.get("vap"), c.get("pvap"), c.get("st"), c.get("e"), c.get("sqcand"))
    except Exception as ex:
        print(nome, "ERRO", ex)

print("\n== CORS (Origin = github.io): o site no navegador consegue ler estas fontes?")
ORIGIN = "https://josperdias.github.io"
def cors(name, url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": DESKTOP, "Origin": ORIGIN, "Accept": "*/*"})
        with urllib.request.urlopen(req, timeout=25) as r:
            print(f"CORS {name}: status {r.status} | allow-origin={r.headers.get('Access-Control-Allow-Origin')!r}")
    except Exception as ex:
        print(f"CORS {name}: ERRO {ex}")
cors("TSE json", "https://resultados.tse.jus.br/oficial/ele2026/6257/dados/br/br-c0001-e006257-u.json")
cors("TSE ele-c", "https://resultados.tse.jus.br/oficial/comum/config/ele-c.json")
cors("TSE foto", "https://resultados.tse.jus.br/oficial/ele2026/6257/fotos/br/280002551544.jpeg")
cors("GDELT", "https://api.gdeltproject.org/api/v2/doc/doc?query=%22Brazil%20election%22&mode=ArtList&maxrecords=3&format=json&timespan=24h")
cors("GoogleNews RSS", f"https://news.google.com/rss/search?q={q}&hl=pt-BR&gl=BR&ceid=BR:pt-419")
cors("g1 RSS", "https://g1.globo.com/rss/g1/politica/")
cors("BBC PT RSS", "https://feeds.bbci.co.uk/portuguese/rss.xml")
cors("allorigins", "https://api.allorigins.win/raw?url=" + urllib.parse.quote("https://g1.globo.com/rss/g1/politica/"))
cors("corsproxy.io", "https://corsproxy.io/?url=" + urllib.parse.quote("https://g1.globo.com/rss/g1/politica/"))
