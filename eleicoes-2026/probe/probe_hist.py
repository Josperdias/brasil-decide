#!/usr/bin/env python3
"""Sonda 12: dados históricos — votação por município/zona (CDN do TSE, 1994–2022) e páginas de eleições antigas (Georgetown PDBA)."""
import csv, io, re, sys, tempfile, time, urllib.request, urllib.error, zipfile, os

CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele"
UAS = [{"User-Agent": "Mozilla/5.0 BrasilDecide-probe", "Accept": "*/*"},
       {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36", "Accept": "*/*"}]
csv.field_size_limit(1 << 24)


def head(url):
    for i in range(4):
        try:
            req = urllib.request.Request(url, headers=dict(UAS[i % 2], Range="bytes=0-0"))
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.status, r.headers.get("Content-Range", "") or r.headers.get("Content-Length", "")
        except urllib.error.HTTPError as e:
            if e.code in (403, 429, 503):
                time.sleep(5 * (i + 1))
                continue
            return e.code, ""
        except Exception as e:
            return 0, str(e)[:80]
    return 403, "repetido"


def baixar(url, dest):
    for i in range(6):
        try:
            req = urllib.request.Request(url, headers=UAS[i % 2])
            with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
                while True:
                    b = r.read(1 << 20)
                    if not b:
                        break
                    f.write(b)
            return True
        except Exception as e:
            print("  falha", i + 1, e, flush=True)
            time.sleep(10 * (i + 1))
    return False


print("== votacao_candidato_munzona (tamanho por ano)")
for ano in range(1994, 2026, 2):
    print(ano, head(f"{CDN}/votacao_candidato_munzona/votacao_candidato_munzona_{ano}.zip"), flush=True)

for ano in (2022, 2002, 1994):
    print(f"\n== amostra {ano}")
    dest = os.path.join(tempfile.mkdtemp(), "z.zip")
    if not baixar(f"{CDN}/votacao_candidato_munzona/votacao_candidato_munzona_{ano}.zip", dest):
        print("sem download")
        continue
    z = zipfile.ZipFile(dest)
    print("arquivos:", [n for n in z.namelist()][:40])
    nome = next((n for n in z.namelist() if n.upper().endswith("BRASIL.CSV")), None)
    if not nome:
        print("sem _BRASIL.csv")
        continue
    with z.open(nome) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="latin-1", newline=""), delimiter=";", quotechar='"')
        hdr = next(rd)
        print("colunas:", hdr)
        ix = {h: i for i, h in enumerate(hdr)}
        cargos = {}
        mostrou = 0
        for row in rd:
            if len(row) < len(hdr):
                continue
            c = (row[ix["DS_CARGO"]], row[ix["NR_TURNO"]])
            cargos[c] = cargos.get(c, 0) + 1
            if mostrou < 3 and row[ix["DS_CARGO"]].upper().startswith("PRESIDENTE"):
                print("linha:", row)
                mostrou += 1
        print("cargo/turno -> linhas:", cargos)

print("\n== páginas de eleições antigas")
for u in ("pdba.georgetown.edu/Elecdata/Brazil/pres45.html", "pdba.georgetown.edu/Elecdata/Brazil/pres50.html",
          "pdba.georgetown.edu/Elecdata/Brazil/pres55.html", "pdba.georgetown.edu/Elecdata/Brazil/pres60.html",
          "pdba.georgetown.edu/Elecdata/Brazil/pres89.html", "pdba.georgetown.edu/Elecdata/Brazil/pres89b.html"):
    try:
        req = urllib.request.Request("https://" + u, headers=UAS[1])
        with urllib.request.urlopen(req, timeout=60) as r:
            t = r.read().decode("latin-1", "replace")
        t = re.sub(r"<[^>]+>", " ", t)
        t = re.sub(r"\s+", " ", t)
        print("--", u, len(t), t[:1800])
    except Exception as e:
        print("--", u, "ERRO", e)
print("FIM")
