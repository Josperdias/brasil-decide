#!/usr/bin/env python3
"""Baixa as fotos oficiais dos candidatos (presidente e governador) do TSE para uma pasta, para o vídeo da apuração.

Lê os meta.json já coletados (candidatos e códigos de eleição) e grava <sq>.jpg + index.json.
Só dado público do TSE; falha de uma foto não derruba as outras (o vídeo usa as iniciais no lugar)."""
import json, os, sys, time, urllib.request, urllib.error

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
BASE = "https://resultados.tse.jus.br/oficial/ele2026"


def baixar(url):
    for i in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "image/*"})
            with urllib.request.urlopen(req, timeout=30) as r:
                b = r.read()
            if len(b) > 800 and b[:2] == b"\xff\xd8":
                return b
            return None
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(2 * (i + 1))
        except Exception:
            time.sleep(2 * (i + 1))
    return None


def main(metas, out):
    os.makedirs(out, exist_ok=True)
    idx, tentados = {}, 0
    for path in metas:
        try:
            m = json.load(open(path, encoding="utf-8"))
        except Exception as e:
            print("sem meta:", path, e)
            continue
        feds = [str(m.get("fed", "")), "6257", "6258"]
        ests = [str(m.get("est", "")), "6259", "6260"]
        alvos = [("pres", "br", num, c, feds) for num, c in (m.get("pres") or {}).items()]
        for uf, cs in (m.get("gov") or {}).items():
            alvos += [("gov", uf.lower(), num, c, ests) for num, c in cs.items()]
        for cargo, abr, num, c, codes in alvos:
            sq = str(c.get("sq", ""))
            if not sq or sq in idx:
                continue
            tentados += 1
            for e in dict.fromkeys(x for x in codes if x):
                b = baixar(f"{BASE}/{e}/fotos/{abr}/{sq}.jpeg")
                if b:
                    open(os.path.join(out, sq + ".jpg"), "wb").write(b)
                    idx[sq] = {"nm": c.get("nm", ""), "sg": c.get("sg", ""), "cargo": cargo, "uf": abr.upper() if cargo == "gov" else "BR"}
                    break
            else:
                print("sem foto:", cargo, abr, c.get("nm"), sq)
    json.dump({"fotos": idx}, open(os.path.join(out, "index.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"{len(idx)} fotos de {tentados} candidatos")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[2:], sys.argv[1]))
