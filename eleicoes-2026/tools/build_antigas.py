#!/usr/bin/env python3
"""Gera android/native-app/src/main/assets/historico_antigas.json (eleições presidenciais 1894–1989).
Cada número tem fonte; onde as fontes divergem ou o placar completo não é conhecido, o registro diz isso na nota.
Rode: python3 eleicoes-2026/tools/build_antigas.py"""
import json, os

TSE_DIRETAS = "TSE — tabela histórica das eleições presidenciais diretas (votos do candidato eleito)"
PDBA = "TSE, via Political Database of the Americas (Georgetown/OEA)"


def c(n, p="", v=0, x=None, w=False, s=""):
    d = {"n": n}
    if p: d["p"] = p
    if v: d["v"] = v
    if x is not None: d["x"] = x
    if w: d["w"] = 1
    if s: d["s"] = s
    return d


def pres(turnos):
    return {"pres": {t: {"BR": l} for t, l in turnos.items()}}


E = []
def add(ano, tipo, turnos, fonte, nota=""):
    e = {"ano": ano, "tipo": tipo, "fonte": fonte, "nota": nota}
    e.update(pres(turnos))
    E.append(e)


# --- República Velha (voto direto; só o eleito consta na tabela do TSE) ---
add(1894, "direta", {"1": [c("Prudente de Morais", v=276583, x=84.29, w=True)]}, TSE_DIRETAS,
    "Primeira eleição direta para presidente. A tabela do TSE traz só o candidato eleito: 84,29% dos votos apurados.")
add(1898, "direta", {"1": [c("Campos Sales", v=420286, x=90.93, w=True)]}, TSE_DIRETAS, "A tabela do TSE traz só o candidato eleito: 90,93% dos votos apurados.")
add(1902, "direta", {"1": [c("Rodrigues Alves", v=592039, x=91.71, w=True)]}, TSE_DIRETAS, "A tabela do TSE traz só o candidato eleito: 91,71% dos votos apurados.")
add(1906, "direta", {"1": [c("Afonso Pena", v=288285, x=97.22, w=True)]}, TSE_DIRETAS,
    "A tabela do TSE traz só o candidato eleito: 97,22% dos votos apurados (outras fontes citam 97,9%).")
add(1910, "direta", {"1": [c("Hermes da Fonseca", v=403867, x=57.07, w=True)]}, TSE_DIRETAS,
    "Disputa com Rui Barbosa. A tabela do TSE traz só o candidato eleito: 57,07% dos votos apurados.")
add(1914, "direta", {"1": [c("Venceslau Brás", v=532107, w=True)]}, TSE_DIRETAS, "A tabela do TSE traz só o candidato eleito.")
add(1918, "direta", {"1": [c("Rodrigues Alves", v=386467, w=True)]}, TSE_DIRETAS,
    "Eleito, mas não tomou posse por doença; nova eleição foi realizada em 1919. A tabela do TSE traz só o candidato eleito.")
add(1919, "direta", {"1": [c("Epitácio Pessoa", v=286373, w=True)]}, TSE_DIRETAS,
    "Eleição de abril de 1919, convocada após o impedimento de Rodrigues Alves. Disputa com Rui Barbosa. A tabela do TSE traz só o candidato eleito.")
add(1922, "direta", {"1": [c("Artur Bernardes", v=466877, w=True)]}, TSE_DIRETAS, "A tabela do TSE traz só o candidato eleito.")
add(1926, "direta", {"1": [c("Washington Luís", v=688528, w=True)]}, TSE_DIRETAS, "A tabela do TSE traz só o candidato eleito.")
add(1930, "direta", {"1": [c("Júlio Prestes", v=1091709, w=True)]}, TSE_DIRETAS,
    "Júlio Prestes foi declarado eleito, mas não tomou posse: o movimento de 1930 levou Getúlio Vargas ao poder. A tabela do TSE traz só o candidato eleito.")
add(1934, "indireta", {"1": [c("Getúlio Vargas", w=True)]}, "TSE — Eleições anteriores (glossário: eleição indireta)",
    "Eleito pela Assembleia Nacional Constituinte, em 17/07/1934. As eleições previstas para 1938 não ocorreram.")
# --- República de 1946 ---
add(1945, "direta", {"1": [c("Eurico Gaspar Dutra", "PSD", 3250516, w=True), c("Eduardo Gomes", "UDN", 2040123), c("Iedo Fiúza", "Indep.", 600348)]},
    PDBA, "Turno único. Primeira eleição direta desde 1930. Há fontes com números ligeiramente diferentes (a Wikipédia, p. ex., cita 3.251.507 para Dutra).")
add(1950, "direta", {"1": [c("Getúlio Vargas", "PTB", 3849040, w=True), c("Eduardo Gomes", "UDN", 2342384), c("Cristiano Machado", "PSD", 1679193)]},
    PDBA, "Turno único; vence quem tem mais votos (maioria simples, como previa a Constituição de 1946).")
add(1955, "direta", {"1": [c("Juscelino Kubitschek", "PSD/PTB", 3077411, w=True), c("Juarez Távora", "UDN", 2610462), c("Adhemar de Barros", "PSP", 2222725), c("Plínio Salgado", "PRP", 714379)]},
    "TSE — resultado oficial de 1955 (placar conforme a tabela oficial; a página do Georgetown traz um valor divergente para Plínio Salgado)",
    "Turno único. Juscelino obteve 35,7% dos votos válidos.")
add(1960, "direta", {"1": [c("Jânio Quadros", "UDN", 5636623, w=True), c("Henrique Teixeira Lott", "PSD", 3846825), c("Adhemar de Barros", "PSP", 2195709)]},
    PDBA, "Turno único.")
# --- Eleições indiretas ---
add(1964, "indireta", {"1": [c("Humberto de Alencar Castelo Branco", w=True)]}, "TSE — Eleições anteriores (glossário: eleição indireta)",
    "Eleito pelo Congresso Nacional em 11/04/1964.")
add(1966, "indireta", {"1": [c("Arthur da Costa e Silva", "ARENA", w=True)]}, "TSE — Eleições anteriores (glossário: eleição indireta)",
    "Eleito pelo Congresso Nacional em 03/10/1966, sem concorrente.")
add(1969, "indireta", {"1": [c("Emílio Garrastazu Médici", "ARENA", w=True)]}, "TSE — Eleições anteriores (glossário: eleição indireta)",
    "Eleito pelo Congresso Nacional em 25/10/1969, sem concorrente.")
add(1974, "indireta", {"1": [c("Ernesto Geisel", "ARENA", 400, w=True), c("Ulysses Guimarães", "MDB", 76)]}, "TSE e imprensa da época (Colégio Eleitoral)",
    "Eleito pelo Colégio Eleitoral em 15/01/1974. Os números são votos de delegados do Colégio Eleitoral, não de eleitores.")
add(1978, "indireta", {"1": [c("João Baptista Figueiredo", "ARENA", w=True), c("Euler Bentes Monteiro", "MDB")]}, "TSE — Eleições anteriores (glossário: eleição indireta)",
    "Eleito pelo Colégio Eleitoral em 15/10/1978. O placar não está nas fontes consultadas.")
add(1985, "indireta", {"1": [c("Tancredo Neves", "PMDB", 480, w=True), c("Paulo Maluf", "PDS", 180)]}, "Imprensa da época (Colégio Eleitoral)",
    "Última eleição indireta, em 15/01/1985. Tancredo Neves não chegou a tomar posse; José Sarney, seu vice, assumiu a Presidência. Os números são votos de delegados do Colégio Eleitoral.")
# --- 1989 ---
p1 = [("Fernando Collor de Mello", "PRN", 20611011), ("Luiz Inácio Lula da Silva", "PT", 11622673), ("Leonel Brizola", "PDT", 11168228),
      ("Mário Covas", "PSDB", 7790392), ("Paulo Maluf", "PDS", 5986575), ("Guilherme Afif Domingos", "PL", 3272462),
      ("Ulysses Guimarães", "PMDB", 3204932), ("Roberto Freire", "PCB", 769123), ("Aureliano Chaves", "PFL", 600838),
      ("Ronaldo Caiado", "PSD", 488846), ("Affonso Camargo", "PTB", 379286), ("Enéas Carneiro", "PRONA", 360561),
      ("Marronzinho", "PSP", 238425), ("P.G.", "PP", 198719), ("Zamir", "PCN", 187155), ("Lívia Maria", "PN", 179922),
      ("Eudes Mattar", "PLP", 162350), ("Fernando Gabeira", "PV", 125842), ("Celso Brant", "PMN", 109909),
      ("Pedreira", "PPB", 86114), ("Manuel Horta", "PDCDOB", 83286), ("Corrêa", "PMB", 4363)]
t1 = [c(n, p, v, s="2º turno" if i < 2 else "") for i, (n, p, v) in enumerate(p1)]
add(1989, "direta", {"1": t1, "2": [c("Fernando Collor de Mello", "PRN", 35089998, w=True), c("Luiz Inácio Lula da Silva", "PT", 31076364)]},
    PDBA + "; percentuais calculados sobre os votos dos candidatos (válidos)",
    "Primeira eleição direta para presidente em 29 anos. Na tabela de origem, os votos de Collor no 1º turno aparecem como 22.611.011; o app usa 20.611.011, único valor que fecha com os percentuais oficiais (30,48%).")

# --- confiabilidade, justificativa e método de coleta (mostrados no app) ---
MET_DIRETAS = ("Tabela histórica do TSE (votos do candidato eleito), digitada no gerador deste app com a fonte citada. "
               "Os demais candidatos não constam na tabela; por isso o app não mostra placar completo.")
MET_PDBA = ("Placar copiado da base Political Database of the Americas (Georgetown/OEA), que reproduz dados do TSE. "
            "O gerador soma os votos, calcula os percentuais e confere contra os percentuais publicados; o CI refaz a conferência a cada build.")
MET_IND = ("Registro histórico do resultado (data, órgão que elegeu e, quando conhecido, placar de delegados), conferido em mais de uma fonte "
           "(glossário do TSE e imprensa da época). Não há voto popular.")
CONF = {}
for a in (1894, 1898, 1902, 1906, 1910):
    CONF[a] = ("Parcial", "Só o candidato eleito consta na tabela do TSE; o voto era restrito e o apurado, de baixa abrangência. Fontes divergem em percentuais (ex.: 1906 = 97,22% no TSE e 97,9% em outra publicação).", MET_DIRETAS)
for a in (1914, 1918, 1919, 1922, 1926, 1930):
    CONF[a] = ("Parcial", "Só o candidato eleito e seus votos constam na tabela do TSE; sem percentual nem placar dos concorrentes. Eleições com histórico conhecido de fraude e baixa participação.", MET_DIRETAS)
CONF[1934] = ("Parcial", "Resultado histórico consolidado (eleito pela Assembleia Constituinte), mas sem placar nas fontes consultadas.", MET_IND)
CONF[1945] = ("Média", "Fonte oficial do TSE por intermediário; as somas fecham com os percentuais publicados (55,18 / 34,63 / 10,19), porém há fontes com números ligeiramente diferentes (3.251.507 para Dutra).", MET_PDBA)
CONF[1950] = ("Média", "Fonte oficial do TSE por intermediário; as somas fecham com os percentuais publicados (48,90 / 29,76 / 21,33).", MET_PDBA)
CONF[1955] = ("Média", "Placar da tabela oficial; confere com os 35,68% de Juscelino. A página do Georgetown divergia para Plínio Salgado, e o app descartou esse valor.", MET_PDBA)
CONF[1960] = ("Média", "Fonte oficial do TSE por intermediário; as somas fecham com os percentuais publicados (48,26 / 32,93 / 18,80).", MET_PDBA)
for a in (1964, 1966, 1969, 1978):
    CONF[a] = ("Parcial", "Vencedor e data são fatos consolidados; o placar do Colégio Eleitoral/Congresso não aparece nas fontes consultadas.", MET_IND)
CONF[1974] = ("Média", "Placar de delegados (400 x 76) confirmado em mais de uma fonte; não é voto popular.", MET_IND)
CONF[1985] = ("Média", "Placar de delegados (480 x 180) confirmado em mais de uma fonte; não é voto popular.", MET_IND)
CONF[1989] = ("Média", "Fonte oficial por intermediário, com uma correção explícita: os votos de Collor no 1º turno foram ajustados para fechar com os percentuais oficiais (30,48% e 53,03% no 2º turno). Os demais valores fecham com a soma.", MET_PDBA)
for e in E:
    e["conf"], e["just"], e["met"] = CONF[e["ano"]]

E.sort(key=lambda e: e["ano"])
out = os.path.join(os.path.dirname(__file__), "..", "android", "native-app", "src", "main", "assets", "historico_antigas.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump({"gerado": "curado à mão a partir das fontes citadas em cada eleição", "eleicoes": E}, f, ensure_ascii=False, separators=(",", ":"))
print("ok", len(E), "eleições →", os.path.normpath(out), os.path.getsize(out), "bytes")
# conferências
for e in E:
    for t, d in e["pres"].items():
        l = d["BR"]
        assert sum(1 for x in l if x.get("w")) <= 1, (e["ano"], t)
    assert any(x.get("w") for t, d in e["pres"].items() for x in d["BR"]), e["ano"]
v = {x["n"]: x["v"] for x in E[-1]["pres"]["1"]["BR"]}
tot = sum(v.values())
assert abs(100 * v["Fernando Collor de Mello"] / tot - 30.48) < 0.01, 100 * v["Fernando Collor de Mello"] / tot
t2 = E[-1]["pres"]["2"]["BR"]
assert abs(100 * t2[0]["v"] / (t2[0]["v"] + t2[1]["v"]) - 53.03) < 0.01
print("conferências de 1989 ok (30,48% / 53,03%)")
