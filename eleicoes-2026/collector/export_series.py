#!/usr/bin/env python3
"""Organiza a apuração coletada (snapshots.ndjson + meta.json) em tabelas CSV abertas, para qualquer pessoa baixar.

Saída (em --out):
  serie_brasil.csv              uma linha por instante: apuração do Brasil e votos de cada candidato à Presidência
  serie_ufs_presidente.csv      formato longo: instante, UF (e exterior ZZ), apuração, candidato, partido, votos, posição
  serie_governadores.csv        o mesmo para governador, por UF
  resultado_final_por_uf.csv    último instante coletado: 1º colocado de cada UF (presidente) e de cada governo
  LEIA-ME.txt                   o que significa cada coluna, fonte e como abrir
Dados públicos do TSE, coletados minuto a minuto. Nada é estimado nem projetado. Horários: UTC e Brasília (UTC-3).
"""
import argparse, csv, json, os, sys
from datetime import datetime, timezone, timedelta

BRT = timezone(timedelta(hours=-3))


def stamp(t):
    d = datetime.fromtimestamp(int(t), timezone.utc)
    return d.strftime("%Y-%m-%dT%H:%M:%SZ"), d.astimezone(BRT).strftime("%Y-%m-%d %H:%M:%S")


def num(x, default=""):
    return default if x is None else x


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="pasta com snapshots.ndjson e meta.json")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    meta = json.load(open(os.path.join(a.src, "meta.json"), encoding="utf-8"))
    pres, gov = meta.get("pres", {}), meta.get("gov", {})
    snaps = []
    with open(os.path.join(a.src, "snapshots.ndjson"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                snaps.append(json.loads(line))
    if not snaps:
        print("sem snapshots", file=sys.stderr)
        sys.exit(1)

    def who(table, n):
        r = table.get(str(n)) or {}
        return r.get("nm", ""), r.get("sg", "")

    # candidatos que aparecem na série do Brasil (colunas da tabela larga)
    cols = []
    for s in snaps:
        for n, _ in (s.get("br") or {}).get("c", []):
            if n not in cols:
                cols.append(n)
    with open(os.path.join(a.out, "serie_brasil.csv"), "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        head = ["instante_utc", "instante_brasilia", "arquivo_tse_data", "arquivo_tse_hora", "apuracao_pct", "secoes_apuradas", "secoes_total",
                "votos_validos", "votos_brancos", "votos_nulos", "eleitorado", "comparecimento", "abstencao"]
        for n in cols:
            nm, sg = who(pres, n)
            head.append(f"votos_{n}_{(nm or 'candidato').replace(' ', '_')}_{sg}".rstrip("_"))
        w.writerow(head)
        for s in snaps:
            br = s.get("br")
            if not br:
                continue
            u, b = stamp(s["t"])
            sec, ev, vv = br.get("s", [0, 0]), br.get("e", [0, 0, 0]), br.get("v", [0, 0, 0])
            votes = {n: v for n, v in br.get("c", [])}
            w.writerow([u, b, s.get("d", ""), s.get("h", ""), br.get("p", ""), sec[0], sec[1], vv[0], vv[1], vv[2], ev[0], ev[1], ev[2]]
                       + [votes.get(n, "") for n in cols])

    def long_rows(s, kind):
        u, b = stamp(s["t"])
        block = s.get(kind) or {}
        items = list(block.items())
        if kind == "uf" and s.get("zz"):
            items.append(("ZZ", s["zz"]))
        for uf, p in items:
            sec = p.get("s", [0, 0])
            table = pres if kind == "uf" else gov.get(uf, {})
            for pos, (n, v) in enumerate(p.get("c", []), start=1):
                nm, sg = who(table, n)
                yield [u, b, uf, p.get("p", ""), sec[0], sec[1], n, nm, sg, v, pos]

    HEAD = ["instante_utc", "instante_brasilia", "uf", "apuracao_pct", "secoes_apuradas", "secoes_total", "candidato_numero", "candidato", "partido", "votos", "posicao"]
    for name, kind in (("serie_ufs_presidente.csv", "uf"), ("serie_governadores.csv", "gv")):
        with open(os.path.join(a.out, name), "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f)
            w.writerow(HEAD)
            for s in snaps:
                for r in long_rows(s, kind):
                    w.writerow(r)

    last = snaps[-1]
    u, b = stamp(last["t"])
    with open(os.path.join(a.out, "resultado_final_por_uf.csv"), "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["cargo", "uf", "instante_utc", "instante_brasilia", "apuracao_pct", "primeiro", "partido", "votos", "segundo", "votos_segundo", "diferenca_votos"])
        for cargo, kind, table_of in (("presidente", "uf", lambda uf: pres), ("governador", "gv", lambda uf: gov.get(uf, {}))):
            items = list((last.get(kind) or {}).items())
            if kind == "uf" and last.get("zz"):
                items.append(("ZZ", last["zz"]))
            if kind == "uf" and last.get("br"):
                items.insert(0, ("BR", last["br"]))
            for uf, p in sorted(items, key=lambda x: (x[0] != "BR", x[0])):
                c = p.get("c", [])
                table = table_of(uf)
                n1 = who(table, c[0][0]) if c else ("", "")
                n2 = who(table, c[1][0]) if len(c) > 1 else ("", "")
                v1 = c[0][1] if c else ""
                v2 = c[1][1] if len(c) > 1 else ""
                w.writerow([cargo, uf, u, b, p.get("p", ""), n1[0], n1[1], v1, n2[0], v2, (v1 - v2) if c and len(c) > 1 else ""])

    first_u, first_b = stamp(snaps[0]["t"])
    with open(os.path.join(a.out, "LEIA-ME.txt"), "w", encoding="utf-8") as f:
        f.write(f"""BRASIL DECIDE — APURAÇÃO COLETADA DO TSE ({meta.get('turn', '?')}º turno, eleições 2026)

O que é: o TSE só mostra a apuração "de agora". Este arquivo guarda o que o TSE publicou ao longo do tempo (uma coleta por minuto,
gravada só quando algum número mudou), para qualquer pessoa conferir, estudar ou refazer gráficos e vídeos. Dados públicos, sem projeção.
Fonte: Tribunal Superior Eleitoral (resultados.tse.jus.br). Projeto independente, sem vínculo com o TSE, partidos ou candidatos.
Uso livre, citando a fonte (TSE) e o Brasil Decide.

Cobertura: {len(snaps)} instantes, de {first_b} a {b} (horário de Brasília; os mesmos instantes em UTC estão nos arquivos).
Atenção: se a coleta falhou em algum minuto, aquele instante simplesmente não existe; o arquivo não preenche lacunas.

Arquivos:
- serie_brasil.csv: uma linha por instante. apuracao_pct = % das seções apuradas; votos_<número>_<nome>_<partido> = votos de cada candidato à Presidência.
- serie_ufs_presidente.csv: formato longo (uma linha por instante, UF e candidato). UF "ZZ" = voto no exterior. posicao = colocação na UF.
  A coleta guarda os primeiros colocados de cada UF (até 4), não todos os candidatos.
- serie_governadores.csv: o mesmo para governador, por UF.
- resultado_final_por_uf.csv: o último instante coletado: primeiro e segundo colocados, votos e diferença (BR = Brasil).
- snapshots.ndjson.gz / zz.ndjson.gz / meta.json: os dados brutos coletados (um JSON por linha), para quem quiser reprocessar.
- SHA256SUMS.txt: soma de verificação de cada arquivo.

Como abrir: os CSV usam vírgula como separador, ponto como decimal e UTF-8. No Excel, use Dados → Obter dados → De texto/CSV.
Percentuais aqui são os da apuração do TSE (% de seções), não percentuais de votos.
""")
    print(f"ok: {len(snaps)} instantes → {a.out}")


if __name__ == "__main__":
    main()
