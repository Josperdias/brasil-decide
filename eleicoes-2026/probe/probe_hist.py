#!/usr/bin/env python3
"""Sonda 9: só os cabeçalhos (e 1 linha curta) dos CSVs de candidatos e bens 2026."""
import csv, io, os, tempfile, urllib.request, zipfile
CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele"
tmp = tempfile.mkdtemp()
for ds, fn in (("consulta_cand", "consulta_cand_2026.zip"), ("bem_candidato", "bem_candidato_2026.zip")):
    dest = os.path.join(tmp, fn)
    urllib.request.urlretrieve(f"{CDN}/{ds}/{fn}", dest)
    z = zipfile.ZipFile(dest)
    print("==", fn, [(n, z.getinfo(n).file_size) for n in z.namelist() if n.endswith(".csv")][:6])
    nm = [n for n in z.namelist() if n.endswith("_BRASIL.csv")][0]
    with z.open(nm) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="latin-1", newline=""), delimiter=";", quotechar='"')
        hdr = next(rd)
        print("colunas:", ",".join(hdr))
        n = 0
        for row in rd:
            n += 1
            if n == 1:
                d = dict(zip(hdr, row))
                print("amostra:", {k: d[k][:30] for k in hdr if k in ("SQ_CANDIDATO","DS_CARGO","SG_UF","SG_PARTIDO","NM_URNA_CANDIDATO","DS_SIT_TOT_TURNO","DS_SITUACAO_CANDIDATURA","VR_BEM_CANDIDATO","DS_TIPO_BEM_CANDIDATO","DS_OCUPACAO","DS_GRAU_INSTRUCAO","VR_DESPESA_MAX_CAMPANHA","ST_REELEICAO","DS_GENERO","DT_NASCIMENTO")})
        print("linhas:", n)
print("FIM")
