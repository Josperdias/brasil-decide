#!/usr/bin/env python3
"""
Gera native-app/.../ExteriorData.java: geometria do mapa-múndi (Natural Earth 1:50m, domínio público, via npm world-atlas),
nomes em português, continentes e a tabela localidade -> país do voto no exterior.

IMPORTANTE: o TSE NÃO informa o país das localidades do exterior; esta tabela (nome da localidade -> país) é do Brasil Decide
e o app avisa isso. Uso: (cd tools && npm i world-atlas@2 topojson-client i18n-iso-countries && node build_world.js) &&
      python3 gen_exterior_data.py world.json ../native-app/src/main/java/br/com/centraleleicoes/nativeapp/ExteriorData.java
"""
import json, re, sys, unicodedata

# ---- localidade (nome oficial no arquivo do TSE) -> ISO-3166 alpha-2
LOC = {
"ABIDJÃ":"CI","ABU DHABI":"AE","ABUJA":"NG","ACCRA":"GH","ADIS ABEBA":"ET","AMSTERDÃ":"NL","AMÃ":"JO","ANCARA":"TR","ARGEL":"DZ","ARTIGAS":"UY",
"ASSUNÇÃO":"PY","ASTANA":"KZ","ATENAS":"GR","ATLANTA":"US","BAGDÁ":"IQ","BAKU":"AZ","BAMAKO":"ML","BANGKOK":"TH","BARCELONA":"ES","BAREIN":"BH",
"BEIRUTE":"LB","BELGRADO":"RS","BELMOPAN":"BZ","BERLIM":"DE","BISSAU":"GW","BOGOTÁ":"CO","BOSTON":"US","BRATISLAVA":"SK","BRAZZAVILLE":"CG",
"BRIDGETOWN":"BB","BRUXELAS":"BE","BUCARESTE":"RO","BUDAPESTE":"HU","BUENOS AIRES":"AR","CAIENA":"GF","CAIRO":"EG","CAMBERRA":"AU","CANTÃO":"CN",
"CARACAS":"VE","CASTRIES":"LC","CHICAGO":"US","CHUY":"UY","CIDADE DO CABO":"ZA","CIUDAD DEL ESTE":"PY","CIUDAD GUAYANA":"VE","COBIJA":"BO",
"COCHABAMBA":"BO","COLOMBO":"LK","CONACRI":"GN","CONCEPCIÓN":"PY","COPENHAGUE":"DK","COTONOU":"BJ","CÓRDOBA":"AR","DACAR":"SN","DACCA":"BD",
"DAMASCO":"SY","DAR ES SALAAM":"TZ","DOHA":"QA","DUBLIN":"IE","DÍLI":"TL","EDIMBURGO":"GB","ENCARNACIÓN":"PY","ESTOCOLMO":"SE","FARO":"PT",
"FRANKFURT":"DE","GABORONE":"BW","GENEBRA":"CH","GEORGETOWN":"GY","GUATEMALA":"GT","HAMAMATSU":"JP","HANÓI":"VN","HARARE":"ZW","HARTFORD":"US",
"HAVANA":"CU","HELSINQUE":"FI","HONG KONG":"HK","HOUSTON":"US","IAUNDÊ":"CM","IEREVAN":"AM","IQUITOS":"PE","ISLAMABADE":"PK","ISTAMBUL":"TR",
"JACARTA":"ID","KATMANDU":"NP","KIEV":"UA","KINGSTON-JAMAICA":"JM","KINSHASA":"CD","KUAITE":"KW","KUALA LUMPUR":"MY","LA PAZ":"BO","LAGOS":"NG",
"LIBREVILLE":"GA","LILONGUE":"MW","LIMA":"PE","LISBOA":"PT","LIUBLIANA":"SI","LOMÉ":"TG","LONDRES":"GB","LOS ANGELES":"US","LUANDA":"AO","LUSACA":"ZM",
"MADRI":"ES","MALABO":"GQ","MANILA":"PH","MANÁGUA":"NI","MAPUTO":"MZ","MARSELHA":"FR","MASCATE":"OM","MENDOZA":"AR","MEXICO":"MX","MIAMI":"US",
"MILÃO":"IT","MONTEVIDÉU":"UY","MONTREAL":"CA","MOSCOU":"RU","MUMBAI":"IN","MUNIQUE":"DE","NAGÓIA":"JP","NAIRÓBI":"KE","NASSAU":"BS","NICOSIA":"CY",
"NOVA DELHI":"IN","NOVA YORK":"US","ORLANDO":"US","OSLO":"NO","OTTAWA":"CA","PANAMA":"PA","PARAMARIBO":"SR","PARIS":"FR","PASO LOS LIBRES":"AR",
"PEDRO JUAN CABALLERO":"PY","PEQUIM":"CN","PORT OF SPAIN":"TT","PORTO":"PT","PORTO PRÍNCIPE":"HT","PRAGA":"CZ","PRAIA":"CV","PRETÓRIA":"ZA",
"PUERTO IGUAZÚ":"AR","PUERTO QUIJARRO":"BO","PYONGYANG":"KP","QUITO":"EC","RABAT":"MA","RAMALLAH":"PS","RIADE":"SA","RIO BRANCO":"UY","RIVERA":"UY",
"ROMA":"IT","SAINT JOHNS":"AG","SALTO DEL GUAIRÁ":"PY","SANTA CRUZ DE LA SIERRA":"BO","SANTA ELENA DE UAIRÉN":"VE","SANTIAGO":"CL","SARAJEVO":"BA",
"SEUL":"KR","SINGAPURA":"SG","ST GEORGES DE LOYAPOCK":"GF","SYDNEY":"AU","SÃO DOMINGOS":"DO","SÃO FRANCISCO":"US","SÃO JOSÉ":"CR","SÃO SALVADOR":"SV",
"SÃO TOMÉ":"ST","SÓFIA":"BG","TAIPÉ":"TW","TALIN":"EE","TBILISI":"GE","TEERÃ":"IR","TEGUCIGALPA":"HN","TEL AVIV":"IL","TIRANA":"AL","TORONTO":"CA",
"TRÍPOLI":"LY","TUNIS":"TN","TÓQUIO":"JP","UAGADUGU":"BF","VANCOUVER":"CA","VARSÓVIA":"PL","VIENA":"AT","WASHINGTON":"US","WELLINGTON":"NZ",
"WINDHOEK":"NA","XANGAI":"CN","YANGON":"MM","ZAGREB":"HR","ZURIQUE":"CH"}

# ---- nomes em português do Brasil
PT = {"CI":"Costa do Marfim","AE":"Emirados Árabes Unidos","NG":"Nigéria","GH":"Gana","ET":"Etiópia","NL":"Países Baixos","JO":"Jordânia","TR":"Turquia",
"DZ":"Argélia","UY":"Uruguai","PY":"Paraguai","KZ":"Cazaquistão","GR":"Grécia","US":"Estados Unidos","IQ":"Iraque","AZ":"Azerbaijão","ML":"Mali","TH":"Tailândia",
"ES":"Espanha","BH":"Bahrein","LB":"Líbano","RS":"Sérvia","BZ":"Belize","DE":"Alemanha","GW":"Guiné-Bissau","CO":"Colômbia","SK":"Eslováquia","CG":"Congo",
"BB":"Barbados","BE":"Bélgica","RO":"Romênia","HU":"Hungria","AR":"Argentina","GF":"Guiana Francesa","EG":"Egito","AU":"Austrália","CN":"China","VE":"Venezuela",
"LC":"Santa Lúcia","ZA":"África do Sul","BO":"Bolívia","LK":"Sri Lanka","GN":"Guiné","DK":"Dinamarca","BJ":"Benin","SN":"Senegal","BD":"Bangladesh","SY":"Síria",
"TZ":"Tanzânia","QA":"Catar","IE":"Irlanda","TL":"Timor-Leste","GB":"Reino Unido","SE":"Suécia","PT":"Portugal","CH":"Suíça","GE":"Geórgia","GT":"Guatemala",
"JP":"Japão","VN":"Vietnã","ZW":"Zimbábue","FI":"Finlândia","HK":"Hong Kong","CU":"Cuba","HN":"Honduras","CM":"Camarões","AM":"Armênia","PE":"Peru","PK":"Paquistão",
"ID":"Indonésia","NP":"Nepal","UA":"Ucrânia","JM":"Jamaica","CD":"RD Congo","KW":"Kuwait","MY":"Malásia","GA":"Gabão","MW":"Malaui","SI":"Eslovênia","TG":"Togo",
"AO":"Angola","ZM":"Zâmbia","IT":"Itália","MX":"México","FR":"França","OM":"Omã","MZ":"Moçambique","PH":"Filipinas","NI":"Nicarágua","CY":"Chipre","NZ":"Nova Zelândia",
"RU":"Rússia","IN":"Índia","KE":"Quênia","BS":"Bahamas","NO":"Noruega","CA":"Canadá","PA":"Panamá","SR":"Suriname","HT":"Haiti","PL":"Polônia","CZ":"República Tcheca",
"CV":"Cabo Verde","TT":"Trinidad e Tobago","MA":"Marrocos","PS":"Palestina","SA":"Arábia Saudita","AG":"Antígua e Barbuda","KR":"Coreia do Sul","SG":"Singapura",
"SV":"El Salvador","CR":"Costa Rica","ST":"São Tomé e Príncipe","BG":"Bulgária","TW":"Taiwan","EE":"Estônia","IL":"Israel","AL":"Albânia","TN":"Tunísia","LY":"Líbia",
"BF":"Burkina Faso","NA":"Namíbia","MM":"Mianmar","HR":"Croácia","AT":"Áustria","KP":"Coreia do Norte","CL":"Chile","EC":"Equador","DO":"República Dominicana",
"BA":"Bósnia e Herzegovina","IR":"Irã","GQ":"Guiné Equatorial","BW":"Botsuana","GY":"Guiana","BR":"Brasil"}

# ---- continentes (os sete grupos pedidos)
CONT = {}
for c, codes in {
 "Europa": "PT ES FR IT DE GB IE NL BE CH AT DK SE NO FI PL CZ SK HU RO BG GR RS HR SI BA AL EE UA RU CY",
 "América do Norte": "US CA MX",
 "América Central e Caribe": "GT BZ HN SV NI CR PA CU JM HT DO BS BB LC TT AG",
 "América do Sul": "AR UY PY BO CL PE EC CO VE GY SR GF BR",
 "Ásia": "AE IQ JO LB SY IL PS SA QA KW BH OM IR TR GE AM AZ KZ PK IN BD LK NP CN HK JP KR KP TW TH VN ID MY SG PH MM TL",
 "África": "CI NG GH ET ML DZ EG GW CG CD SN GN BJ CM ZW BW ZA ZM MZ TZ AO MW KE GA TG GQ MA LY TN BF NA CV ST",
 "Oceania": "AU NZ"}.items():
    for k in codes.split(): CONT[k] = c

def norm(s):
    return re.sub(r"[^A-Z0-9]+", " ", unicodedata.normalize("NFD", s.upper()).encode("ascii", "ignore").decode()).strip()

def ring_path(rings):
    out, cx, cy = [], 0.0, 0.0
    for r in rings:
        x0, y0 = r[0]
        out.append("m %.1f,%.1f" % (x0 - cx, y0 - cy))
        px, py = x0, y0
        pts = []
        for x, y in r[1:]:
            pts.append("%.1f,%.1f" % (x - px, y - py)); px, py = x, y
        out.append(" ".join(pts) + " z")
        cx, cy = x0, y0
    return " ".join(out)

def main():
    w = json.load(open(sys.argv[1], encoding="utf-8"))
    rows = []
    for c in sorted(w["countries"], key=lambda c: -c["area"]):
        iso = c["iso2"] or ("X-" + c["num"])
        rows.append((iso, PT.get(c["iso2"], c["name"]), CONT.get(c["iso2"], ""), ring_path(c["rings"]), c["cx"], c["cy"], c["area"]))
    gf = w["gf"]
    rows.append(("GF", PT["GF"], CONT["GF"], "", gf[0], gf[1], 0.0))
    missing = sorted({v for v in LOC.values()} - {r[0] for r in rows})
    assert not missing, missing
    nopt = sorted({v for v in LOC.values()} - set(PT))
    assert not nopt, nopt
    nocont = sorted({v for v in LOC.values()} - set(CONT))
    assert not nocont, nocont
    j = ["package br.com.centraleleicoes.nativeapp;", "",
         "/** GERADO por tools/gen_exterior_data.py. Geometria: Natural Earth 1:50m (domínio público), projeção Natural Earth, simplificada.",
         " *  O TSE não informa o país das localidades do exterior: a tabela LOC (localidade -> país) é do Brasil Decide. */",
         "final class ExteriorData {", "    private ExteriorData() {}",
         "    static final float VIEW_W = %sf, VIEW_H = %sf;" % (w["w"], w["h"]),
         "    /** {iso2, nome em português, continente, caminho (m/z relativo; vazio = só marcador)} */",
         "    static final String[][] COUNTRIES = {"]
    for iso, nm, ct, p, cx, cy, ar in rows:
        j.append('        {"%s", "%s", "%s", "%s"},' % (iso, nm, ct, p))
    j += ["    };", "    /** centro (x, y) e área aproximada (px² do mapa), na mesma ordem de COUNTRIES */", "    static final float[][] GEO = {"]
    for iso, nm, ct, p, cx, cy, ar in rows:
        j.append("        {%sf, %sf, %sf}," % (cx, cy, ar))
    j += ["    };", "    /** {nome normalizado da localidade (sem acento, maiúsculas), iso2} */", "    static final String[][] LOC = {"]
    for k, v in sorted(LOC.items()):
        j.append('        {"%s", "%s"},' % (norm(k), v))
    j += ["    };", "    static final String[] CONTINENTS = {\"Europa\", \"América do Norte\", \"América Central e Caribe\", \"América do Sul\", \"Ásia\", \"África\", \"Oceania\"};",
          "}", ""]
    open(sys.argv[2], "w", encoding="utf-8").write("\n".join(j))
    print("países:", len(rows), "| localidades mapeadas:", len(LOC), "| bytes:", sum(len(x) for x in j))

main()
