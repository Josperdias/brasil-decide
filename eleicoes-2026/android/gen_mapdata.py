#!/usr/bin/env python3
"""Gera native-app/.../MapData.java a partir do mapa embutido no HTML final (MAP_PATHS / MAP_LABELS)."""
import json, re, sys
html = open(sys.argv[1], encoding='utf-8').read()
paths = json.loads(re.search(r'const MAP_PATHS=(\{.*?\});\n', html, re.S).group(1))
labels = json.loads(re.search(r'const MAP_LABELS=(\{.*?\});\n', html, re.S).group(1))
out = ['package br.com.centraleleicoes.nativeapp;', '',
       '/** Gerado a partir de @svg-maps/brazil (CC BY 4.0). Caminhos SVG simplificados (somente comandos m/z). */',
       'final class MapData {', '    private MapData() {}', '    static final float VIEW_W = 613f, VIEW_H = 639f;',
       '    static final String[][] PATHS = {']
out += ['        {"%s", "%s"},' % (k, v) for k, v in paths.items()]
out += ['    };', '    /** {UF, x, y, r} */', '    static final Object[][] LABELS = {']
out += ['        {"%s", %sf, %sf, %sf},' % (k, v['x'], v['y'], v['r']) for k, v in labels.items()]
out += ['    };', '}', '']
open(sys.argv[2], 'w', encoding='utf-8').write('\n'.join(out))
