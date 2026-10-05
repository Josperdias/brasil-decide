#!/usr/bin/env python3
"""Sonda 8: estrutura dos CSVs de dados abertos do TSE 2026 (candidatos, bens, prestação de contas)."""
import csv, io, sys, urllib.request, zipfile, tempfile, os, time

CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele"
def fetch(url, dest):
    t = time.time()
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 BrasilDecide-probe"})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
        while True:
            b = r.read(1 << 20)
            if not b: break
            f.write(b)
    print(f"baixado {url.split('/')[-1]}: {os.path.getsize(dest)/1e6:.1f} MB em {time.time()-t:.0f}s")

def head(z, name, n=3, enc="latin-1"):
    with z.open(name) as f:
        txt = io.TextIOWrapper(f, encoding=enc, newline="")
        rd = csv.reader(txt, delimiter=";", quotechar='"')
        hdr = next(rd)
        print("  colunas:", hdr)
        for i, row in enumerate(rd):
            if i >= n: break
            print("  linha:", dict(zip(hdr, row)))

tmp = tempfile.mkdtemp()
for ds, fn in (("consulta_cand", "consulta_cand_2026.zip"), ("bem_candidato", "bem_candidato_2026.zip"), ("prestacao_contas", "prestacao_de_contas_eleitorais_candidatos_2026.zip")):
    dest = os.path.join(tmp, fn)
    try: fetch(f"{CDN}/{ds}/{fn}", dest)
    except Exception as e:
        print("ERRO baixando", fn, e); continue
    z = zipfile.ZipFile(dest)
    names = z.namelist()
    print(f"== {fn}: {len(names)} arquivos")
    for nm in names[:60]:
        print("   ", nm, z.getinfo(nm).file_size)
    pick = []
    if ds == "consulta_cand": pick = [n for n in names if n.endswith("_BRASIL.csv")] or [n for n in names if n.endswith(".csv")][:1]
    elif ds == "bem_candidato": pick = [n for n in names if n.endswith("_BRASIL.csv")] or [n for n in names if n.endswith(".csv")][:1]
    else:
        pick = [n for n in names if n.endswith(".csv") and ("BRASIL" in n or "_DF" in n)][:8]
    for nm in pick[:6]:
        print("-- amostra", nm)
        try: head(z, nm)
        except Exception as e: print("  erro lendo", e)
print("FIM")
