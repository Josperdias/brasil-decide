#!/usr/bin/env node
/* Renderiza o vídeo da apuração (MP4 H.264) no servidor: abre o site num Chromium sem tela, desenha cada quadro com o mesmo código do
   "Replay da apuração" e junta com o ffmpeg. Só lê os dados coletados; não escreve nada na branch de dados.
   uso: node render_video.js --site docs/eleicoes/index.html --data <pasta com snapshots.ndjson+meta.json> --fotos <pasta com index.json e .jpg>
                             --out <pasta> [--fmt v,q,h] [--seconds 45] [--fps 30] [--src t2|demo] [--prefix apuracao-2t] */
const fs = require('fs'), path = require('path'), { spawn } = require('child_process');
const { chromium } = require('playwright');

const arg = (k, d) => { const i = process.argv.indexOf('--' + k); return i > 0 ? process.argv[i + 1] : d; };
const GOV = arg('gov', ''), SITE = arg('site'), DATA = arg('data'), FOTOS = arg('fotos', ''), OUT = arg('out', 'out');
const FMTS = arg('fmt', 'v,q,h').split(','), SEC = +arg('seconds', 45), FPS = +arg('fps', 30), SRC = arg('src', 't2'), PREFIX = arg('prefix', 'apuracao-2t');
const NAMES = { v: 'vertical', q: 'quadrado', h: 'horizontal' }, HOLD = 2.5;

function lerSnaps() {
  const txt = fs.readFileSync(path.join(DATA, 'snapshots.ndjson'), 'utf8');
  return txt.split('\n').filter(Boolean).map(l => { try { return JSON.parse(l); } catch (e) { return null; } })
    .filter(s => s && s.br && s.t).sort((a, b) => a.t - b.t);
}

(async () => {
  const snaps = lerSnaps();
  if (GOV) { const n = snaps.filter(q => q.gv && q.gv[GOV]).length; if (n < 2) { console.log('dados insuficientes para ' + GOV + ': ' + n + ' registro(s)'); process.exit(2); } }
  if (snaps.length < 2) { console.log('dados insuficientes: ' + snaps.length + ' registro(s)'); process.exit(2); }
  const meta = JSON.parse(fs.readFileSync(path.join(DATA, 'meta.json'), 'utf8'));
  const fotos = {};
  const cands = GOV ? ((meta.gov || {})[GOV] || {}) : (meta.pres || {});
  for (const m of Object.values(cands)) {
    const f = path.join(FOTOS, (m.sq || 'x') + '.jpg');
    if (m.sq && FOTOS && fs.existsSync(f)) fotos[m.sq] = 'data:image/jpeg;base64,' + fs.readFileSync(f).toString('base64');
  }
  console.log(snaps.length + ' registros, ' + Object.keys(fotos).length + ' fotos');
  let html = fs.readFileSync(SITE, 'utf8');
  const hook = `/*RP_END*/window.__rp={
    init:function(snaps,meta,fotos,fmt,dur,src,gov){RP.gov=gov||null;RP.raw=null;RP.snaps=snaps;RP.meta=meta;RP.src=src;rpPrep();RP.dur=dur;RP.fmt=fmt;
      const F=RP_FMT[fmt];F.cw=F.dw;F.ch=F.dh;RP.photos={};
      return Promise.all(Object.keys(fotos).map(k=>new Promise(res=>{const i=new Image();i.onload=()=>{RP.photos[k]=i;res()};i.onerror=()=>{RP.photos[k]=false;res()};i.src=fotos[k]})))},
    frame:function(p){rpFrame(p);return rpCanvas().toDataURL('image/jpeg',0.93).split(',')[1]}};`;
  if (!html.includes('/*RP_END*/')) throw new Error('marcador /*RP_END*/ não encontrado no site');
  html = html.replace('/*RP_END*/', hook);
  const tmp = path.join(OUT, '_site.html');
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(tmp, html);

  const browser = await chromium.launch({ args: ['--no-sandbox', '--disable-gpu'] });
  for (const fmt of FMTS) {
    const page = await browser.newPage({ viewport: { width: 800, height: 800 } });
    page.on('pageerror', e => console.log('erro na página:', e.message));
    await page.goto('file://' + path.resolve(tmp));
    await page.waitForFunction(() => window.__rp, null, { timeout: 30000 });
    await page.evaluate(([s, m, f, fm, d, src, g]) => window.__rp.init(s, m, f, fm, d, src, g), [snaps, meta, fotos, fmt, SEC, SRC, GOV]);
    const out = path.join(OUT, PREFIX + '-' + NAMES[fmt] + '.mp4');
    const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-vcodec', 'mjpeg', '-i', '-',
      '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], { stdio: ['pipe', 'inherit', 'inherit'] });
    const done = new Promise((r, rej) => { ff.on('close', r); ff.on('error', rej); });
    ff.stdin.on('error', () => { });
    const n = Math.round(SEC * FPS), total = n + Math.round(HOLD * FPS), t0 = Date.now();
    for (let i = 0; i < total; i++) {
      const p = Math.min(1, i / n);
      const b64 = await page.evaluate(q => window.__rp.frame(q), p);
      if (!ff.stdin.write(Buffer.from(b64, 'base64'))) await new Promise(r => ff.stdin.once('drain', r));
      if (i % 150 === 0) console.log(NAMES[fmt] + ' ' + i + '/' + total + ' (' + Math.round((Date.now() - t0) / 1000) + ' s)');
    }
    ff.stdin.end();
    const code = await done;
    if (code !== 0) throw new Error('ffmpeg terminou com código ' + code);
    console.log('ok ' + out + ' ' + fs.statSync(out).size + ' bytes');
    await page.close();
  }
  await browser.close();
  fs.unlinkSync(tmp);
})().catch(e => { console.error(e); process.exit(1); });
