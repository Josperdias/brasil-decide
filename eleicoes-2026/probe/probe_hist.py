#!/usr/bin/env python3
"""Sonda 7: dados abertos do TSE (histórico, candidatos, bens, prestação de contas), IBGE (malha/UF) e Portal da Transparência. Só HEAD/Range."""
import json, urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
def head(url, rng=False):
    h = {"User-Agent": UA, "Origin": "https://josperdias.github.io"}
    if rng: h["Range"] = "bytes=0-199"
    req = urllib.request.Request(url, headers=h, method="GET" if rng else "HEAD")
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            cr = r.headers.get("Content-Range", ""); cl = r.headers.get("Content-Length", "?")
            size = cr.split("/")[-1] if cr else cl
            return r.status, size, r.headers.get("Access-Control-Allow-Origin", "-"), r.headers.get("Content-Type", "?")
    except urllib.error.HTTPError as e:
        return e.code, "-", "-", "-"
    except Exception as e:
        return 0, "-", "-", "ERRO " + str(e)[:60]

CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele"
items = []
for ano in ("2018", "2020", "2022", "2024", "2026"):
    for ds, fn in (("consulta_cand", f"consulta_cand_{ano}.zip"), ("bem_candidato", f"bem_candidato_{ano}.zip"),
                   ("votacao_candidato_munzona", f"votacao_candidato_munzona_{ano}.zip"),
                   ("votacao_partido_munzona", f"votacao_partido_munzona_{ano}.zip"),
                   ("prestacao_contas", f"prestacao_de_contas_eleitorais_candidatos_{ano}.zip")):
        items.append((f"TSE {ds} {ano}", f"{CDN}/{ds}/{fn}"))
items += [
    ("TSE perfil eleitorado 2024", f"{CDN}/perfil_eleitorado/perfil_eleitorado_2024.zip"),
    ("TSE prestação contas partidos 2022", f"{CDN}/prestacao_contas/prestacao_de_contas_partidarias_2022.zip"),
    ("TSE dadosabertos (CKAN api)", "https://dadosabertos.tse.jus.br/api/3/action/package_list"),
    ("IBGE malha UFs (mínima)", "https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR?intrarregiao=UF&qualidade=minima&formato=application/vnd.geo+json"),
    ("IBGE UFs", "https://servicodados.ibge.gov.br/api/v1/localidades/estados"),
    ("IBGE municípios", "https://servicodados.ibge.gov.br/api/v1/localidades/municipios"),
    ("Portal Transparência (sem chave)", "https://api.portaldatransparencia.gov.br/api-de-dados/partidos-politicos?pagina=1"),
    ("Câmara: despesas partido? /partidos/36899", "https://dadosabertos.camara.leg.br/api/v2/partidos/36899"),
]
for label, url in items:
    st, size, cors, ct = head(url, rng=url.endswith("json") is False and "api" not in url)
    mb = f"{int(size)/1e6:.1f} MB" if str(size).isdigit() else size
    print(f"{label}: HTTP {st} | {mb} | CORS {cors} | {ct[:40]}")
print("FIM")
