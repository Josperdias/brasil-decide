#!/usr/bin/env python3
"""Gera tools/world.json (contornos dos países + localidade -> país) a partir de ExteriorData.java, para o vídeo do voto no exterior."""
import json, re, unicodedata, sys

SRC = "android/native-app/src/main/java/br/com/centraleleicoes/nativeapp/ExteriorData.java"


def norm(s):
    s = unicodedata.normalize("NFD", s)
    return "".join(c for c in s if unicodedata.category(c) != "Mn").upper().strip()


def main():
    t = open(SRC, encoding="utf-8").read()
    a = t.index("COUNTRIES = {")
    b = t.index("};", a)
    countries = {}
    for iso, nome, cont, path in re.findall(r'\{"([A-Z]{2})", "([^"]*)", "([^"]*)", "([^"]*)"\}', t[a:b]):
        countries[iso] = {"n": nome, "p": path}
    c = t.index("LOC = {")
    d = t.index("};", c)
    loc = {norm(n): iso for n, iso in re.findall(r'\{"([^"]+)", "([A-Z]{2})"\}', t[c:d])}
    w = re.search(r"VIEW_W = ([\d.]+)f, VIEW_H = ([\d.]+)f", t)
    json.dump({"w": float(w.group(1)), "h": float(w.group(2)), "countries": countries, "loc": loc},
              open("tools/world.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(countries), "países,", len(loc), "localidades")


main()
