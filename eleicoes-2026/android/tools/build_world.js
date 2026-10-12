const topo = require('topojson-client');
const iso = require('i18n-iso-countries');
const w = require('./node_modules/world-atlas/countries-50m.json');
const fc = topo.feature(w, w.objects.countries);
// projeção Natural Earth (Šavrič et al.)
function ne(lon, lat) {
  const l = lon * Math.PI / 180, p = lat * Math.PI / 180, p2 = p * p, p4 = p2 * p2;
  const x = l * (0.8707 - 0.131979 * p2 + p4 * (-0.013791 + p4 * (0.003971 * p2 - 0.001529 * p4)));
  const y = p * (1.007226 + p2 * (0.015085 + p4 * (-0.044475 + 0.028874 * p2 - 0.005916 * p4)));
  return [x, -y];
}
function dp(pts0, tol) { // Douglas-Peucker; anel fechado: divide no ponto mais distante do primeiro
  if (pts0.length < 6) return pts0;
  let fi = 1, fd = 0; for (let i = 1; i < pts0.length - 1; i++) { const d = Math.hypot(pts0[i][0] - pts0[0][0], pts0[i][1] - pts0[0][1]); if (d > fd) { fd = d; fi = i; } }
  const a1 = dpLine(pts0.slice(0, fi + 1), tol), a2 = dpLine(pts0.slice(fi), tol);
  return a1.concat(a2.slice(1));
}
function dpLine(pts, tol) {
  if (pts.length < 4) return pts;
  const keep = new Array(pts.length).fill(false); keep[0] = keep[pts.length - 1] = true;
  const st = [[0, pts.length - 1]];
  while (st.length) {
    const [a, b] = st.pop(); let md = 0, mi = -1;
    const [ax, ay] = pts[a], [bx, by] = pts[b]; const dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy) || 1e-9;
    for (let i = a + 1; i < b; i++) { const d = Math.abs((pts[i][0] - ax) * dy - (pts[i][1] - ay) * dx) / len; if (d > md) { md = d; mi = i; } }
    if (md > tol) { keep[mi] = true; st.push([a, mi], [mi, b]); }
  }
  return pts.filter((_, i) => keep[i]);
}
let tol = 0.6;
const raw = [];
for (const f of fc.features) {
  if (String(f.id) === '010') continue; // Antártida
  const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
  const rings = [];
  for (const poly of polys) {
    const ring = poly[0];
    // anel que cruza o meridiano 180: desembrulha a longitude e desenha também uma cópia deslocada de 360°
    let jump = false; for (let i = 1; i < ring.length; i++) if (Math.abs(ring[i][0] - ring[i - 1][0]) > 180) { jump = true; break; }
    if (!jump) { rings.push(ring.map(([lo, la]) => ne(lo, la))); continue; }
    const un = []; let off = 0;
    for (let i = 0; i < ring.length; i++) { if (i > 0) { const d = ring[i][0] - ring[i - 1][0]; if (d > 180) off -= 360; else if (d < -180) off += 360; } un.push([ring[i][0] + off, ring[i][1]]); }
    const avg = un.reduce((a, p) => a + p[0], 0) / un.length;
    rings.push(un.map(([lo, la]) => ne(lo, la)));
    rings.push(un.map(([lo, la]) => ne(lo + (avg > 0 ? -360 : 360), la)));
  }
  raw.push({ id: String(f.id).padStart(3, '0'), name: f.properties.name, rings });
}
let minx = ne(-180, 0)[0], maxx = ne(180, 0)[0], miny = 1e9, maxy = -1e9;
for (const c of raw) for (const r of c.rings) for (const [x, y] of r) { miny = Math.min(miny, y); maxy = Math.max(maxy, y); }
const S = 1000 / (maxx - minx), H = (maxy - miny) * S;
const out = [];
for (const c of raw) {
  let rings = c.rings.map(r => r.map(([x, y]) => [(x - minx) * S, (y - miny) * S]));
  rings = rings.map(r => dp(r, tol)).filter(r => r.length >= 3);
  // área e bbox
  const info = rings.map(r => { let a = 0, x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9; for (let i = 0; i < r.length; i++) { const [x, y] = r[i], [u, v] = r[(i + 1) % r.length]; a += x * v - u * y; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); } return { r, a: Math.abs(a / 2), cx: (x0 + x1) / 2, cy: (y0 + y1) / 2 }; });
  info.sort((a, b) => b.a - a.a);
  if (!info.length) { // país minúsculo sem anel após simplificação: usa original maior
    const big = c.rings.map(r => r.map(([x, y]) => [(x - minx) * S, (y - miny) * S])).sort((a, b) => b.length - a.length)[0];
    if (!big) continue; info.push({ r: big, a: 0, cx: big[0][0], cy: big[0][1] });
  }
  const keep = info.filter((o, i) => i === 0 || o.a >= 3);
  const alpha2 = iso.numericToAlpha2(c.id) || '';
  out.push({ iso2: alpha2, num: c.id, name: c.name, cx: +info[0].cx.toFixed(1), cy: +info[0].cy.toFixed(1), area: +info[0].a.toFixed(1), rings: keep.map(o => o.r.map(([x, y]) => [+x.toFixed(1), +y.toFixed(1)])) });
}
const gfp = ne(-53, 4.0); require('fs').writeFileSync('world.json', JSON.stringify({ w: 1000, h: +H.toFixed(1), gf: [+((gfp[0] - minx) * S).toFixed(1), +((gfp[1] - miny) * S).toFixed(1)], countries: out }));
const pt = {}; for (const c of out) if (c.iso2) pt[c.iso2] = iso.getName(c.iso2, 'pt') || c.name;
require('fs').writeFileSync('names_pt.json', JSON.stringify(pt));
console.log('países', out.length, 'altura', H.toFixed(1), 'tamanho', JSON.stringify(out).length);
console.log('sem iso2:', out.filter(c => !c.iso2).map(c => c.name + ':' + c.num).join(', '));
