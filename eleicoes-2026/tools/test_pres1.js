// Teste funcional (sem navegador) da proteção do resultado final do 1º turno no HTML/WebView.
// Uso: node tools/test_pres1.js [central-eleicoes-2026.html]
// Extrai as funções reais do HTML montado e as executa com um localStorage simulado.
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const html = fs.readFileSync(process.argv[2] || 'central-eleicoes-2026.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const code = scripts.join('\n');
new vm.Script(code); // o script inteiro precisa continuar sintaticamente válido

const a = code.indexOf("/* COLETA-BEGIN */"), b = code.indexOf("function restorePres1()");
assert(a > 0 && b > a, 'blocos do histórico do 1º turno não encontrados');
const pc = code.indexOf("const PRES1_FINAL_KEY");
assert(pc > a, 'ordem inesperada dos blocos');
const endRestore = code.indexOf('\n return ch}\n', b) + '\n return ch}\n'.length;
const helpers = code.slice(a, endRestore);
const line = re => { const m = code.match(re); assert(m, 'não encontrado: ' + re); return m[0]; };
const extra = [line(/function loadJSON\(k,f\)\{.*\n/), line(/function saveJSON\(k,v\)\{.*\n/), line(/function signature\(d\)\{.*\n/), line(/const UFS=\{.*\n/),
  line(/const roundPresData=.*\n/), line(/const roundGovData=.*\n/)].join('');

const store = {};
const ctx = { st: { turn: 1, presBR: null, roundPres: null, roundGov: {}, states: {}, gov: {}, df: {}, buckets: {} }, console,
  localStorage: { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: k => { delete store[k]; } } };
vm.createContext(ctx);
vm.runInContext(extra + helpers + '\nthis.api={usefulResult,finalResult,rejectRegression,savePres1Final,loadPres1Final,restorePres1,roundPresData,roundGovData,resultVotes,coleta1,saveUf1Final,loadUf1,PRES1_COLETA};', ctx);
const api = ctx.api;
const mk = (a_, b_, o = {}) => Object.assign({ cands: [{ nome: 'A', votos: a_ }, { nome: 'B', votos: b_ }], progress: 100, sections: 100, sectionsTotal: 100, valid: a_ + b_, final: true }, o);
let n = 0; const ok = (c, m) => { assert(c, m); n++; console.log('OK: ' + m); };

const good = mk(60000, 40000);
ok(api.finalResult(good), 'resultado totalizado é final');
ok(!api.usefulResult(mk(0, 0)) && !api.usefulResult({ cands: [] }) && !api.usefulResult(null), 'zerado/vazio/nulo não é útil');
ok(api.savePres1Final(good) && api.loadPres1Final().cands[0].votos === 60000, 'resultado final é persistido');
ok(!api.savePres1Final(mk(10, 5, { final: false, progress: 40, sections: 40 })), 'parcial não é arquivado');
ok(api.rejectRegression(good, mk(0, 0, { final: false })), 'payload zerado é rejeitado');
ok(api.rejectRegression(good, { cands: [] }), 'payload vazio é rejeitado');
ok(api.rejectRegression(good, mk(1000, 500)), 'queda absurda (>50%) é rejeitada');
ok(api.rejectRegression(good, mk(60000, 40000, { sections: 0 })), 'seções zeradas são rejeitadas');
ok(api.rejectRegression(good, mk(60000, 40000, { valid: 0 })), 'válidos zerados são rejeitados');
ok(!api.rejectRegression(good, mk(60500, 40100)), 'correção pequena do TSE é aceita');
ok(!api.rejectRegression(null, mk(0, 0)), 'sem histórico útil nada é bloqueado');
ok(!api.savePres1Final(mk(1000, 500)) && api.loadPres1Final().cands[0].votos === 60000, 'arquivo não é sobrescrito por regressão');
ok(api.savePres1Final(mk(60500, 40100)) && api.resultVotes(api.loadPres1Final()) === 100600, 'correção legítima atualiza o arquivo');
// "reinício": estado em memória perdido, localStorage mantido, sem TTL
ctx.st.presBR = mk(0, 0, { final: false });
ok(api.restorePres1() && api.resultVotes(ctx.st.presBR) === 100600, 'restaura o 1º turno depois de reiniciar / cache zerado');
ctx.st.turn = 2; ctx.st.presBR = null; ctx.st.roundPres = mk(0, 0, { final: false });
ok(api.resultVotes(api.roundPresData()) === 100600, 'no 2º turno o card usa o 1º turno arquivado, não o payload zerado');
ctx.st.roundPres = null; ctx.st.buckets = {};
ok(api.resultVotes(api.roundPresData()) === 100600, 'sem rede e sem bucket, o 1º turno arquivado continua disponível');

// ---- estados, governadores, DF e reserva coletada do TSE (05/10/2026)
const C = api.coleta1();
ok(api.resultVotes(C.pres) > 100000000, 'reserva traz o total nacional do 1º turno (primeiros colocados)');
ok(C.pres.cands[0].nome === 'FLAVIO BOLSONARO' && C.pres.cands[0].votos === 56104503 && Math.abs(C.pres.cands[0].pct - 47.03) < 0.01 && Math.abs(C.pres.cands[1].pct - 45.16) < 0.01, 'reserva: Flávio 47,03% × Lula 45,16%');
ok(Object.keys(C.states).length === 27 && Object.keys(C.gov).length === 27, 'reserva: 27 UFs e 27 governadores');
ok(C.states.AM.cands[0].numero === '13' && C.states.AM.cands[0].votos === 1046996, 'reserva: líder de AM (Lula, 1.046.996)');
const sitCount = Object.values(C.gov).reduce((o, g) => { const k = /^Eleito/.test(g.cands[0].sit) ? 'win' : 'r2'; o[k] = (o[k] || 0) + 1; return o }, {});
ok(sitCount.win === 21 && sitCount.r2 === 6, 'reserva: 21 governadores eleitos e 6 em 2º turno (regra dos 50%)');
// aparelho com 1º turno zerado e sem arquivo: tudo volta da reserva
for (const k of Object.keys(store)) delete store[k];
ctx.st.turn = 1; ctx.st.presBR = mk(0, 0, { final: false }); ctx.st.states = { AM: mk(0, 0, { final: false }) }; ctx.st.gov = {}; ctx.st.df = {};
ok(api.restorePres1() && api.finalResult(ctx.st.presBR) && ctx.st.presBR.src === 'coleta', 'zerado + sem arquivo: Brasil volta da reserva');
ok(Object.keys(ctx.st.states).length === 27 && ctx.st.states.AM.cands[0].votos === 1046996, 'zerado + sem arquivo: as 27 UFs voltam');
ok(Object.keys(ctx.st.gov).length === 27 && api.finalResult(ctx.st.df.DF.presDF) && api.finalResult(ctx.st.df.DF.gov), 'governadores e cargos do DF voltam');
ok(!api.restorePres1(), 'restaurar de novo não muda nada');
ok(api.rejectRegression(ctx.st.states.AM, mk(0, 0, { final: false })), 'UF zerada não sobrescreve a reserva');
// resultado final "ao vivo" de uma UF é arquivado e tem prioridade sobre a reserva depois de reiniciar
const spLive = mk(70000, 30000);
ctx.st.states.SP = spLive; ctx.st.gov.SP = mk(60000, 40000);
api.saveUf1Final();
ok(api.loadUf1().s.SP.cands[0].votos === 70000 && !api.loadUf1().s.AM, 'UF final ao vivo é arquivada; a reserva não é');
ctx.st.states = { SP: mk(0, 0, { final: false }) }; ctx.st.gov = {};
api.restorePres1();
ok(ctx.st.states.SP.cands[0].votos === 70000 && ctx.st.states.AM.src === 'coleta', 'após reiniciar: SP vem do arquivo, o resto da reserva');
ok(api.roundGovData('AC').cands[0].votos === 218760, 'card do 2º turno usa a reserva quando não há governador salvo');
console.log(`${n} verificações OK`);
