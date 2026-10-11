// Teste funcional (sem navegador) da proteção do resultado final do 1º turno no HTML/WebView.
// Uso: node tools/test_pres1.js [central-eleicoes-2026.html]
// Extrai as funções reais do HTML montado e as executa com um localStorage simulado.
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const html = fs.readFileSync(process.argv[2] || 'central-eleicoes-2026.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const code = scripts.join('\n');
new vm.Script(code); // o script inteiro precisa continuar sintaticamente válido

const a = code.indexOf("const PRES1_FINAL_KEY"), b = code.indexOf("function restorePres1()");
assert(a > 0 && b > a, 'bloco do histórico do 1º turno não encontrado');
const helpers = code.slice(a, code.indexOf('\n', b));
const rp = code.match(/const roundPresData=.*\n/);
assert(rp, 'roundPresData não encontrado');

const store = {};
const ctx = { st: { turn: 1, presBR: null, roundPres: null, buckets: {} }, console,
  localStorage: { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: k => { delete store[k]; } } };
vm.createContext(ctx);
vm.runInContext(helpers + '\n' + rp[0] + '\nthis.api={usefulResult,finalResult,rejectRegression,savePres1Final,loadPres1Final,restorePres1,roundPresData,resultVotes};', ctx);
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
console.log(`${n} verificações OK`);
