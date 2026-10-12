// Teste (sem navegador) da escolha dos códigos de eleição no HTML/WebView, com a estrutura REAL de ele-c.json de 11/10/2026:
// além do 1º turno (6257/6259) o TSE lista "Federal/Estadual 2026 2º Turno" (6258/6260); o nome casava com os dois.
// Uso: node tools/test_codes.js [central-eleicoes-2026.html]
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const html = fs.readFileSync(process.argv[2] || 'central-eleicoes-2026.html', 'utf8');
const code = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');
const grab = re => { const m = code.match(re); assert(m, 'não encontrado: ' + re); return m[0]; };
const src = grab(/const DEFAULT_CODES=.*\n/) + grab(/function find2026Codes\(cfg\)\{.*\n/);
const ctx = {}; vm.createContext(ctx);
vm.runInContext(src + '\nthis.f=find2026Codes;', ctx);
const E = (cd, nm, t, tp) => ({ cd, cdt2: '', nm, t, tp });
const cfg = { pl: [{ c: 'ele2026', e: [
  E('6257', 'Eleição Ordinária Federal - 2026 1º Turno', '1', '8'), E('6259', 'Eleição Ordinária Estadual - 2026 1º Turno', '1', '1'),
  E('6261', 'Eleição Ordinária Municipal - 2026 - 04/10/2026 1º Turno', '1', '3'), E('6284', 'Eleição Suplementar - Narandiba', '1', '4'),
  E('6260', 'Eleição Ordinária Estadual - 2026 2º Turno', '2', '1'), E('6258', 'Eleição Ordinária Federal - 2026 2º Turno', '2', '8'),
  E('6288', 'Consulta Popular - Mato Grosso', '1', '6')] }, { c: 'ele2024', e: [E('9999', 'Federal 2024', '1', '8')] }] };
const r = ctx.f(cfg);
assert.strictEqual(r[1].fed, '6257'); assert.strictEqual(r[1].est, '6259');
assert.strictEqual(r[2].fed, '6258'); assert.strictEqual(r[2].est, '6260');
console.log('OK: 1º turno = 6257/6259 e 2º turno = 6258/6260, mesmo com as entradas de 2º turno listadas depois');
// ordem invertida e config vazia/ruim mantêm os padrões
const rev = ctx.f({ pl: [{ c: 'ele2026', e: cfg.pl[0].e.slice().reverse() }] });
assert.strictEqual(rev[1].fed, '6257'); assert.strictEqual(rev[2].est, '6260');
assert.strictEqual(ctx.f({})[1].fed, '6257'); assert.strictEqual(ctx.f(null)[2].fed, '6258');
console.log('OK: ordem invertida e configuração vazia mantêm os códigos corretos');
