#!/usr/bin/env python3
"""Sonda 6: os resultados de 2022 ainda existem no host de resultados do TSE? E a composição atual da Câmara (Câmara API) para comparação."""
import json, urllib.request, gzip

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip"})
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            b = r.read()
            if r.headers.get("Content-Encoding") == "gzip": b = gzip.decompress(b)
            return r.status, b.decode("utf-8-sig", "replace"), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, "", {}
    except Exception as e:
        return 0, "ERRO " + str(e), {}

B = "https://resultados.tse.jus.br/oficial"
for label, url in [
    ("2022 presidente BR 1T (544)", f"{B}/ele2022/544/dados/br/br-c0001-e000544-u.json"),
    ("2022 presidente BR 2T (545)", f"{B}/ele2022/545/dados/br/br-c0001-e000545-u.json"),
    ("2022 dep. federal SP (546)", f"{B}/ele2022/546/dados/sp/sp-c0006-e000546-u.json"),
    ("2022 config ele-c", f"{B}/ele2022/comum/config/ele-c.json"),
    ("2022 mun config 544", f"{B}/ele2022/544/config/mun-e000544-cm.json"),
    ("2018 presidente BR (297)", f"{B}/ele2018/297/dados/br/br-c0001-e000297-u.json"),
]:
    st, body, h = get(url)
    print(f"{label}: HTTP {st}, {len(body)} bytes", (body[:120].replace("\n", " ") if st == 200 else ""))

# Câmara: composição por partido da legislatura anterior (57 = atual 2023-2027; 56 = anterior) e partidos
for label, url in [
    ("Câmara partidos (itens=100)", "https://dadosabertos.camara.leg.br/api/v2/partidos?itens=100&ordem=ASC&ordenarPor=sigla"),
    ("Câmara deputados legislatura 57 (total no header)", "https://dadosabertos.camara.leg.br/api/v2/deputados?idLegislatura=57&itens=1"),
    ("Câmara deputados legislatura 56 (total no header)", "https://dadosabertos.camara.leg.br/api/v2/deputados?idLegislatura=56&itens=1"),
    ("Câmara partido 36899? membros", "https://dadosabertos.camara.leg.br/api/v2/partidos/36899/membros?itens=1"),
]:
    st, body, h = get(url)
    print(f"{label}: HTTP {st}, x-total-count={h.get('x-total-count')}, {len(body)} bytes", body[:140].replace("\n", " ") if st == 200 else "")

# federações no arquivo de resultado de 2026 (procura agrupamento do tipo federação)
st, body, _ = get(f"{B}/ele2026/6259/dados/sp/sp-c0006-e006259-u.json")
if st == 200:
    j = json.loads(body)
    tps = {}
    ex = None
    for cg in j["carg"]:
        for a in cg["agr"]:
            tps[a.get("tp")] = tps.get(a.get("tp"), 0) + 1
            if a.get("tp") not in ("i",) and ex is None: ex = {k: a.get(k) for k in ("n", "nm", "tp", "com", "vag")}
    print("tipos de agrupamento (dep. federal SP):", tps, "| exemplo não-isolado:", ex)
print("FIM")
