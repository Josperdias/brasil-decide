#!/usr/bin/env python3
"""
Coletor da apuração (Brasil Decide).

O TSE não guarda o histórico da apuração: o arquivo de resultado só mostra "agora". Este coletor consulta os arquivos
OFICIAIS do TSE a cada minuto e grava snapshots compactos, para que o app/site possam montar Replay, Momento da Virada,
Corrida dos Votos e a Linha do Tempo da noite, inclusive para quem não estava com o app aberto.

Saída (em --out):
  meta.json          cadastro dos candidatos (número -> nome/partido/sq) por cargo/UF e UFs
  snapshots.ndjson   um snapshot por linha (só quando algum dado mudou): Brasil, 27 UFs (presidente e governador) e exterior (ZZ)
  zz.ndjson          localidades do exterior (a cada --zz-every minutos)
  latest.json        o snapshot mais recente
  index.json         resumo (primeira/última hora, quantidade de snapshots)
Somente leitura do TSE; nenhum dado pessoal; nenhuma previsão.
"""
import argparse, concurrent.futures as cf, gzip, hashlib, json, os, subprocess, sys, time, urllib.request, urllib.error

BASE = "https://resultados.tse.jus.br/oficial"
UA = "BrasilDecide-Coletor/1.0 (+https://github.com/Josperdias/concursos-df)"
UFS = ["AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"]


def http(url, timeout=25):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                b = r.read()
                if r.headers.get("Content-Encoding") == "gzip":
                    b = gzip.decompress(b)
                return json.loads(b.decode("utf-8-sig"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None  # arquivo ainda não publicado
            time.sleep(1 + attempt)
        except Exception:
            time.sleep(1 + attempt)
    return "ERR"


def num(v, default=0.0):
    try:
        s = str(v).strip()
        if "," in s:
            s = s.replace(".", "").replace(",", ".")
        return float(s)
    except Exception:
        return default


def intv(v):
    try:
        return int("".join(ch for ch in str(v) if ch.isdigit()) or 0)
    except Exception:
        return 0


def parse(j, top):
    """Mesmo critério do app: carg[0] -> agr[] -> par[] -> cand[]; ordena por votos."""
    cands = []
    carg = (j.get("carg") or [{}])[0]
    for ag in carg.get("agr", []):
        for par in ag.get("par", []):
            for c in par.get("cand", []):
                cands.append({"n": str(c.get("n", "")), "nm": c.get("nmu") or c.get("nm") or "", "sg": par.get("sg", ""),
                              "sq": str(c.get("sqcand", "")), "v": intv(c.get("vap")), "st": str(c.get("st", ""))})
    cands.sort(key=lambda x: -x["v"])
    s, e, v = j.get("s") or {}, j.get("e") or {}, j.get("v") or {}
    snap = {"p": num(s.get("pst")), "s": [intv(s.get("st")), intv(s.get("ts"))],
            "e": [intv(e.get("te")), intv(e.get("c")), intv(e.get("a"))],  # eleitorado, comparecimento, abstenção
            "v": [intv(v.get("vv", v.get("vvc"))), intv(v.get("vb")), intv(v.get("vn"))],  # válidos, brancos, nulos
            "c": [[x["n"], x["v"]] for x in cands[:top]]}
    return snap, cands


class Collector:
    def __init__(self, turn, out, top_pres=8, top_uf=4):
        self.turn, self.out, self.tp, self.tu = turn, out, top_pres, top_uf
        os.makedirs(out, exist_ok=True)
        cfg = http(BASE + "/comum/config/ele-c.json") or {}
        fed, est = ("6257", "6259") if turn == 1 else ("6258", "6260")
        for p in cfg.get("pl", []) if isinstance(cfg, dict) else []:
            if p.get("c") == "ele2026":
                for e in p.get("e", []):
                    if e.get("tp") == "8" and e.get("t") == "1": fed = e["cd"] if turn == 1 else (e.get("cdt2") or fed)
                    if e.get("tp") == "1" and e.get("t") == "1": est = e["cd"] if turn == 1 else (e.get("cdt2") or est)
        self.fed, self.est = fed, est
        self.meta = {"turn": turn, "fed": fed, "est": est, "pres": {}, "gov": {}}
        self.last_hash = None
        self.n = 0
        self.first = None

    def url(self, ele, abr, cargo, muni=""):
        a = abr.lower()
        return f"{BASE}/ele2026/{ele}/dados/{a}/{a}{muni}-c{cargo:04d}-e{int(ele):06d}-u.json"

    def tasks(self):
        t = [("br", self.url(self.fed, "br", 1), self.tp), ("zz", self.url(self.fed, "zz", 1), self.tp)]
        for uf in UFS:
            t.append(("uf:" + uf, self.url(self.fed, uf, 1), self.tu))
            t.append(("gv:" + uf, self.url(self.est, uf, 3), self.tu))
        return t

    def tick(self):
        snap = {"t": int(time.time()), "turn": self.turn, "uf": {}, "gv": {}}
        stamp = ""
        with cf.ThreadPoolExecutor(14) as ex:
            res = list(ex.map(lambda t: (t, http(t[1])), self.tasks()))
        errs = 0
        for (key, url, top), j in res:
            if j == "ERR":
                errs += 1
                continue
            if not isinstance(j, dict):
                continue  # 404 = ainda não publicado
            sn, cands = parse(j, top)
            if key == "br":
                snap["br"] = sn
                stamp = f"{j.get('dg','')} {j.get('hg','')}"
                snap["d"], snap["h"] = j.get("dg", ""), j.get("hg", "")
                for c in cands:
                    self.meta["pres"].setdefault(c["n"], {"nm": c["nm"], "sg": c["sg"], "sq": c["sq"]})
            elif key == "zz":
                snap["zz"] = sn
            else:
                kind, uf = key.split(":")
                snap["uf" if kind == "uf" else "gv"][uf] = sn
                if kind == "gv":
                    m = self.meta["gov"].setdefault(uf, {})
                    for c in cands:
                        m.setdefault(c["n"], {"nm": c["nm"], "sg": c["sg"], "sq": c["sq"]})
        snap["err"] = errs
        h = hashlib.sha1(json.dumps({k: v for k, v in snap.items() if k not in ("t", "err")}, sort_keys=True).encode()).hexdigest()
        changed = h != self.last_hash and ("br" in snap or snap["uf"] or snap["gv"])
        return snap, changed, h, stamp

    def zz_locals(self):
        cfg = http(f"{BASE}/ele2026/{self.fed}/config/mun-e{int(self.fed):06d}-cm.json")
        if not isinstance(cfg, dict):
            return None
        muns = []
        for a in cfg.get("abr", []):
            if str(a.get("cd", "")).lower() == "zz":
                muns = a.get("mu", [])
        out = {"t": int(time.time()), "turn": self.turn, "loc": {}}
        names = {}
        with cf.ThreadPoolExecutor(14) as ex:
            res = list(ex.map(lambda m: (m, http(self.url(self.fed, "zz", 1, str(m.get("cd"))))), muns))
        for m, j in res:
            if isinstance(j, dict):
                sn, _ = parse(j, 3)
                out["loc"][str(m.get("cd"))] = sn
            names[str(m.get("cd"))] = m.get("nm", "")
        self.meta["zz_names"] = names
        return out

    def write(self, snap, h, zz):
        with open(os.path.join(self.out, "snapshots.ndjson"), "a", encoding="utf-8") as f:
            f.write(json.dumps(snap, separators=(",", ":"), ensure_ascii=False) + "\n")
        with open(os.path.join(self.out, "latest.json"), "w", encoding="utf-8") as f:
            json.dump(snap, f, separators=(",", ":"), ensure_ascii=False)
        if zz:
            with open(os.path.join(self.out, "zz.ndjson"), "a", encoding="utf-8") as f:
                f.write(json.dumps(zz, separators=(",", ":"), ensure_ascii=False) + "\n")
        self.n += 1
        self.first = self.first or snap["t"]
        self.meta["updated"] = snap["t"]
        with open(os.path.join(self.out, "meta.json"), "w", encoding="utf-8") as f:
            json.dump(self.meta, f, separators=(",", ":"), ensure_ascii=False)
        with open(os.path.join(self.out, "index.json"), "w", encoding="utf-8") as f:
            json.dump({"turn": self.turn, "n": self.n, "first": self.first, "last": snap["t"], "d": snap.get("d", ""), "h": snap.get("h", "")}, f)
        self.last_hash = h


def push(repo_dir, msg):
    if not repo_dir:
        return
    def git(*a):
        return subprocess.run(["git", "-C", repo_dir, *a], capture_output=True, text=True)
    git("add", "-A")
    if git("diff", "--cached", "--quiet").returncode == 0:
        return
    git("commit", "-q", "-m", msg)
    for i in range(4):
        r = git("push", "-q", "origin", "HEAD:eleicao-data")
        if r.returncode == 0:
            return
        git("pull", "-q", "--rebase", "origin", "eleicao-data")
        time.sleep(2 ** i)
    print("aviso: push falhou:", r.stderr[:200], file=sys.stderr)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--turn", type=int, default=1)
    ap.add_argument("--out", required=True)
    ap.add_argument("--minutes", type=int, default=330)
    ap.add_argument("--interval", type=int, default=60)
    ap.add_argument("--zz-every", type=int, default=5)
    ap.add_argument("--push-every", type=int, default=3)
    ap.add_argument("--repo", default="")
    a = ap.parse_args()
    c = Collector(a.turn, a.out)
    print(f"coletor: turno {a.turn}, eleições fed={c.fed} est={c.est}, {a.minutes} min, a cada {a.interval}s", flush=True)
    end = time.time() + a.minutes * 60
    i = 0
    while time.time() < end:
        t0 = time.time()
        try:
            snap, changed, h, stamp = c.tick()
            zz = c.zz_locals() if i % max(1, a.zz_every) == 0 else None
            if changed or zz:
                c.write(snap, h if changed else c.last_hash, zz)
            print(f"[{time.strftime('%H:%M:%S')}] tick {i}: {'novo' if changed else 'igual'} | arquivo TSE {stamp} | UFs {len(snap['uf'])} gov {len(snap['gv'])} | erros {snap['err']}", flush=True)
        except Exception as e:  # nunca derruba o coletor
            print("erro no tick:", repr(e), flush=True)
        i += 1
        if a.repo and i % a.push_every == 0:
            push(a.repo, f"coleta t{a.turn} #{c.n}")
        time.sleep(max(1, a.interval - (time.time() - t0)))
    if a.repo:
        push(a.repo, f"coleta t{a.turn} final #{c.n}")
    print("fim; snapshots gravados:", c.n)


if __name__ == "__main__":
    main()
