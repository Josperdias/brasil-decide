#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Coleta novidades de concursos do Distrito Federal usando o RSS do Google Notícias.
Não precisa de chave de API. Roda com a biblioteca padrão do Python.
Gera o arquivo docs/data.json, que a página docs/index.html lê para se atualizar.
"""
import urllib.request, urllib.parse, xml.etree.ElementTree as ET
import json, re, html, datetime, os

# Consultas: cada uma vira uma busca no Google Notícias. Ajuste como quiser.
QUERIES = [
    "concurso Distrito Federal edital",
    "concurso DF inscrições abertas",
    'concurso (PCDF OR PMDF OR CBMDF OR "Sefaz DF" OR TCDF OR CLDF) edital',
    'concurso (INSS OR "Banco do Brasil" OR Caixa OR IBGE) edital',
    'concurso (CGU OR TCU OR STF OR tribunais) Brasília edital',
]
RSS = "https://news.google.com/rss/search?q={q}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
DIAS = 75          # janela: ignora notícias mais antigas que isso
MAX_ITENS = 45     # máximo de itens guardados

ORGAOS = ["PCDF", "PMDF", "CBMDF", "Sefaz-DF", "Sefaz DF", "TCDF", "CLDF", "Detran-DF",
          "Sedes-DF", "SEEDF", "SEDF", "INSS", "Banco do Brasil", "Caixa", "IBGE", "CGU",
          "TCU", "STF", "STJ", "TST", "TSE", "TJDFT", "TRF1", "Câmara", "Senado", "ABGF",
          "BRB", "Metrô-DF", "Codeplan", "Terracap", "IBRAM"]
STATUS = [("inscri", "Inscrições abertas"), ("edital publicado", "Edital publicado"),
          ("saiu o edital", "Edital publicado"), ("autoriz", "Autorizado"),
          ("previst", "Previsto"), ("nomea", "Nomeação"), ("convoca", "Convocação"),
          ("banca", "Banca definida"), ("resultado", "Resultado")]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (brasil-decide-bot)"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def parse_date(s):
    for fmt in ("%a, %d %b %Y %H:%M:%S %Z", "%a, %d %b %Y %H:%M:%S %z"):
        try:
            return datetime.datetime.strptime(s, fmt)
        except Exception:
            pass
    return None


def classify(titulo):
    t = titulo.lower()
    org = next((o for o in ORGAOS if o.lower() in t), "")
    st = next((label for key, label in STATUS if key in t), "Novidade")
    return org, st


def main():
    vistos, itens = set(), []
    limite = datetime.datetime.now() - datetime.timedelta(days=DIAS)
    for q in QUERIES:
        url = RSS.format(q=urllib.parse.quote(q))
        try:
            root = ET.fromstring(fetch(url))
        except Exception as e:
            print("Falha ao buscar:", q, "->", e)
            continue
        for it in root.iter("item"):
            titulo = html.unescape((it.findtext("title") or "").strip())
            link = (it.findtext("link") or "").strip()
            pub = parse_date((it.findtext("pubDate") or "").strip())
            src_el = it.find("source")
            fonte = (src_el.text if src_el is not None else "") or ""
            # o título costuma vir como "Manchete - Fonte"; removemos o sufixo
            if fonte and titulo.endswith(" - " + fonte):
                titulo = titulo[: -(len(fonte) + 3)].strip()
            chave = re.sub(r"\W+", "", titulo.lower())[:60]
            if not titulo or chave in vistos:
                continue
            if pub and pub.replace(tzinfo=None) < limite:
                continue
            vistos.add(chave)
            org, st = classify(titulo)
            itens.append({
                "titulo": titulo, "link": link, "fonte": fonte,
                "data": pub.strftime("%Y-%m-%d") if pub else "",
                "orgao": org, "status": st,
            })
    itens.sort(key=lambda x: x["data"], reverse=True)
    itens = itens[:MAX_ITENS]
    os.makedirs("docs", exist_ok=True)
    with open("docs/data.json", "w", encoding="utf-8") as f:
        json.dump({
            "gerado_em": datetime.date.today().isoformat(),
            "fonte": "Google Notícias (RSS)",
            "itens": itens,
        }, f, ensure_ascii=False, indent=2)
    print(f"OK: {len(itens)} itens gravados em docs/data.json")


if __name__ == "__main__":
    main()
