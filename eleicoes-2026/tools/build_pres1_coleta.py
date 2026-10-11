#!/usr/bin/env python3
"""Gera os arquivos de recuperação do 1º turno de 2026 a partir da coleta do TSE (branch eleicao-data, pasta 2026/t1-test).

São dados reais coletados do TSE (nada simulado): totais finais por UF, governadores, Brasil e exterior, com os primeiros
colocados de cada disputa. Servem só como reserva quando o TSE publica arquivos zerados e o app não tem o resultado final salvo.

Uso (na raiz do repositório):  python3 eleicoes-2026/tools/build_pres1_coleta.py [ref-da-branch]   (padrão: origin/eleicao-data)
Gera:
  eleicoes-2026/android/native-app/src/main/assets/pres1_coleta_2026.json   (app nativo: Brasil, UFs, governadores e exterior)
  eleicoes-2026/html/part-04.html                                           (site/WebView: bloco entre os marcadores COLETA)
"""
import json, re, subprocess, sys

ref = sys.argv[1] if len(sys.argv) > 1 else 'origin/eleicao-data'
base = '2026/t1-test/'

def show(path):
    return subprocess.check_output(['git', 'show', f'{ref}:{base}{path}']).decode('utf-8')

def last_line(text):
    return json.loads([l for l in text.split('\n') if l.strip()][-1])

meta = json.loads(show('meta.json'))
snap = last_line(show('snapshots.ndjson'))
zz = last_line(show('zz.ndjson'))
assert snap['turn'] == 1 and not snap.get('err'), 'coleta inesperada'
assert len(snap['uf']) == 27 and all(v['p'] >= 99.99 and v['s'][0] == v['s'][1] for v in snap['uf'].values()), 'coleta incompleta'
cadastro = lambda c: {k: [v['nm'], v['sg'], v['sq']] for k, v in c.items()}
data = {
    'v': 1, 't': snap['t'], 'd': snap['d'], 'h': snap['h'], 'fed': meta['fed'], 'est': meta['est'],
    'pres': cadastro(meta['pres']),
    'gov': {uf: cadastro(c) for uf, c in meta['gov'].items()},
    'br': snap['br'], 'uf': snap['uf'], 'gv': snap['gv'], 'zz': snap['zz'],
}
web = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
data['loc'] = zz['loc']
data['zzn'] = meta['zz_names']
native = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
open('eleicoes-2026/android/native-app/src/main/assets/pres1_coleta_2026.json', 'w', encoding='utf-8').write(native)

p = 'eleicoes-2026/html/part-04.html'
s = open(p, encoding='utf-8').read()
a, b = '/* COLETA-BEGIN */', '/* COLETA-END */'
block = f"{a}\nconst PRES1_COLETA={web};\n{b}"
if a in s:
    s = re.sub(re.escape(a) + r'.*?' + re.escape(b), lambda m: block, s, flags=re.S)
else:
    k = "const cacheKey=t=>"
    s = s.replace(k, block + '\n' + k, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok: nativo', len(native), 'bytes; web', len(web), 'bytes; coleta', snap['d'], snap['h'])
