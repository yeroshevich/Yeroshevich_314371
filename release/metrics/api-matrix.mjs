#!/usr/bin/env node
/**
 * ЛР 11, защищённость и согласованность. Матрица «34 метода REST API × 6 субъектов (без токена + 5 ролей)».
 * Ожидаемые права — из документации API (docs/lr9, таблица эндпоинтов): кому метод разрешён.
 * Каждый запрос отправляется с корректным телом, чтобы отказ мог дать только проверка прав.
 *  - Несанкционированный доступ: субъекту метод не разрешён, а сервер НЕ ответил 401 (без токена) / 403 (роль).
 *  - Ложный отказ: метод разрешён, а сервер ответил 401/403 (дефект функциональности, не защищённости).
 * Согласованность (соглашения REST) по каждому методу: (1) метод HTTP соответствует смыслу операции (чтение — GET,
 * создание — POST + 201, изменение — PUT/PATCH, действие — POST); (2) код успешного ответа разрешённой роли
 * равен ожидаемому (200/201/204); (3) все ответы-ошибки метода в едином формате {status, code, message, path,
 * timestamp}; (4) метод и этот код успеха описаны в спецификации OpenAPI (/v3/api-docs).
 *   node release/metrics/api-matrix.mjs     # BASE_URL=http://localhost:8080; данные БД меняются
 */
import { BASE, ROLES, http, isApiError, login, round2, save, nowIso } from './lib.mjs';

const T = await (async () => {
  const t = { none: null };
  for (const r of ROLES) t[r] = await login(r);
  return t;
})();
const SUBJECTS = ['none', ...ROLES];
const uniq = Date.now().toString(36);
const ANY = ['employee', 'manager', 'hr', 'finance', 'admin'];
const HOLDER = ['employee', 'manager', 'hr'];

// ── Подготовка данных для успешных вызовов разрешёнными ролями ──────────────────────────────────
async function must(r, what) {
  if (r.status >= 300) throw new Error(`${what}: ${r.status} ${r.text}`);
  return r.json;
}
const accrue = async (role, amount) => (await must(await http('POST', '/accruals', {
  token: T[role], body: { employeeIds: ['3'], amount, basis: 'project', reason: `Подготовка проверки прав ${uniq}` },
}), 'начисление')).operations[0].id;
const fixtures = {
  opForHr: await accrue('hr', 300),
  opForManager: await accrue('manager', 300),
  pendingA: await accrue('manager', 1500),
  pendingB: await accrue('manager', 1500),
};
const programs = await must(await http('GET', '/programs', { token: T.employee }), 'программы');
const cheapest = programs.filter((p) => p.active).sort((a, b) => a.cost - b.cost)[0];
fixtures.programId = cheapest.id;
const newUser = await must(await http('POST', '/users', {
  token: T.admin,
  body: { fullName: 'Проверка Прав', email: `rights.${uniq}@technosoft.by`, departmentId: '2', position: 'QA-инженер', role: 'employee' },
}), 'пользователь');
fixtures.userId = newUser.id;
const rule = (name) => ({ name, kpi: `Показатель проверки ${name}`, weight: 0.3, basePoints: 1000, periodicity: 'quarter', threshold: 80, active: true });
const rules = await must(await http('GET', '/rules', { token: T.hr }), 'правила');
fixtures.ruleId = rules[0].id;
const ruleBody = { name: rules[0].name, kpi: rules[0].kpi, weight: rules[0].weight, basePoints: rules[0].basePoints,
  periodicity: rules[0].periodicity, threshold: rules[0].threshold, active: rules[0].active };

let seq = 0;
const next = () => `${uniq}-${++seq}`;
const period = 'from=2026-11-01&to=2026-11-30';

// kind: read | create | update | action; ok — ожидаемый код успеха
const METHODS = [
  { m: 'POST', p: '/auth/login', roles: ['none', ...ANY], kind: 'action', ok: 200, body: () => ({ email: 'anna.kovaleva@technosoft.by', password: 'demo' }) },
  { m: 'GET', p: '/auth/me', roles: ANY, kind: 'read', ok: 200 },
  { m: 'GET', p: '/accounts/me', roles: HOLDER, kind: 'read', ok: 200 },
  { m: 'GET', p: `/accounts/me/operations?${period}`, roles: HOLDER, kind: 'read', ok: 200 },
  { m: 'GET', p: '/programs', roles: HOLDER, kind: 'read', ok: 200 },
  { m: 'POST', p: `/programs/${fixtures.programId}/redemptions`, roles: HOLDER, kind: 'create', ok: 201 },
  { m: 'GET', p: '/departments', roles: ANY, kind: 'read', ok: 200 },
  { m: 'GET', p: '/employees?scope=team', roles: ['manager', 'hr', 'finance', 'admin'], kind: 'read', ok: 200 },
  { m: 'GET', p: '/employees/team', roles: ['manager', 'hr'], kind: 'read', ok: 200 },
  { m: 'POST', p: '/accruals/preview', roles: ['manager', 'hr'], kind: 'action', ok: 200,
    body: () => ({ employeeIds: ['3'], amount: 200, basis: 'project', reason: 'Проверка прав: расчёт' }) },
  { m: 'POST', p: '/accruals', roles: ['manager', 'hr'], kind: 'create', ok: 201,
    body: () => ({ employeeIds: ['3'], amount: 100, basis: 'project', reason: `Проверка прав ${next()}` }) },
  { m: 'GET', p: `/operations?${period}`, roles: ['hr', 'finance'], kind: 'read', ok: 200 },
  { m: 'PUT', p: (role) => `/operations/${role === 'manager' ? fixtures.opForManager : fixtures.opForHr}`, roles: ['hr', 'manager'], kind: 'update', ok: 200,
    body: () => ({ action: 'adjust', newAmount: 250, reason: `Проверка прав ${next()}` }) },
  { m: 'GET', p: '/control/operations', roles: ['finance'], kind: 'read', ok: 200 },
  { m: 'POST', p: `/control/operations/${fixtures.pendingA}/approve`, roles: ['finance'], kind: 'action', ok: 200, body: () => ({ comment: 'Проверка прав' }) },
  { m: 'POST', p: `/control/operations/${fixtures.pendingB}/block`, roles: ['finance'], kind: 'action', ok: 200, body: () => ({ reason: 'Проверка прав' }) },
  { m: 'GET', p: '/rules', roles: ['hr', 'manager'], kind: 'read', ok: 200 },
  { m: 'POST', p: '/rules', roles: ['hr'], kind: 'create', ok: 201, body: () => rule(next()) },
  { m: 'PUT', p: `/rules/${fixtures.ruleId}`, roles: ['hr'], kind: 'update', ok: 200, body: () => ruleBody },
  { m: 'GET', p: '/campaigns', roles: ['hr', 'manager'], kind: 'read', ok: 200 },
  { m: 'POST', p: '/campaigns', roles: ['hr'], kind: 'create', ok: 201,
    body: () => ({ name: `Кампания проверки ${next()}`, description: 'Проверка прав', startDate: '2027-02-01', endDate: '2027-02-28', departmentIds: ['2'], coefficient: 1.5, budget: 10000 }) },
  { m: 'GET', p: `/reports/departments?${period}`, roles: ['hr', 'finance'], kind: 'read', ok: 200 },
  { m: 'GET', p: '/analytics/dashboard?period=month', roles: ['hr', 'manager'], kind: 'read', ok: 200 },
  { m: 'GET', p: '/users', roles: ['admin'], kind: 'read', ok: 200 },
  { m: 'POST', p: '/users', roles: ['admin'], kind: 'create', ok: 201,
    body: () => ({ fullName: 'Проверка Прав', email: `rights.${next()}@technosoft.by`, departmentId: '2', position: 'QA-инженер', role: 'employee' }) },
  { m: 'PUT', p: `/users/${fixtures.userId}`, roles: ['admin'], kind: 'update', ok: 200,
    body: () => ({ fullName: 'Проверка Прав', email: newUser.email, departmentId: '2', position: 'Ведущий QA-инженер', role: 'employee' }) },
  { m: 'PATCH', p: `/users/${fixtures.userId}/status`, roles: ['admin'], kind: 'update', ok: 200, body: () => ({ status: 'blocked' }) },
  { m: 'GET', p: '/integrations', roles: ['admin'], kind: 'read', ok: 200 },
  { m: 'POST', p: '/integrations/3/test', roles: ['admin'], kind: 'action', ok: 200, body: () => ({ url: 'https://ad.technosoft.by/ldap', apiKey: 'ad-key-2026-check' }) },
  { m: 'PUT', p: '/integrations/3', roles: ['admin'], kind: 'update', ok: 200, body: () => ({ url: 'https://ad.technosoft.by/ldap', apiKey: 'ad-key-2026-check' }) },
  { m: 'GET', p: '/audit?q=', roles: ['admin'], kind: 'read', ok: 200 },
  { m: 'GET', p: '/notifications', roles: ANY, kind: 'read', ok: 200 },
  { m: 'POST', p: '/notifications/read', roles: ANY, kind: 'action', ok: 204 },
  { m: 'POST', p: '/auth/logout', roles: ANY, kind: 'action', ok: 204 },
];

// ── Спецификация OpenAPI ───────────────────────────────────────────────────────────────────────
const spec = (await http('GET', `${BASE}/v3/api-docs`)).json;
function specOp(method, path) {
  const clean = path.split('?')[0];
  for (const [tpl, ops] of Object.entries(spec.paths)) {
    const re = new RegExp(`^${tpl.replace(/\{[^}]+\}/g, '[^/]+')}$`);
    if (re.test(`/api/v1${clean}`) && ops[method.toLowerCase()]) return { tpl, op: ops[method.toLowerCase()] };
  }
  return null;
}
const METHOD_BY_KIND = { read: ['GET'], create: ['POST'], update: ['PUT', 'PATCH'], action: ['POST'] };

// ── Прогон ─────────────────────────────────────────────────────────────────────────────────────
const checks = [];
const methods = [];
for (const def of METHODS) {
  const rows = [];
  for (const subject of SUBJECTS) {
    const path = typeof def.p === 'function' ? def.p(subject) : def.p;
    const r = await http(def.m, path, { token: T[subject], body: def.body ? def.body() : undefined });
    const allowed = def.roles.includes(subject);
    const denied = r.status === 401 || r.status === 403;
    const expectedDeny = subject === 'none' ? 401 : 403;
    const row = {
      method: def.m, path: path.split('?')[0], subject, allowed, status: r.status, code: r.json?.code,
      unauthorizedAccess: !allowed && r.status !== expectedDeny,
      falseDenial: allowed && denied,
      errorFormatOk: r.status >= 400 ? isApiError(r) : null,
      ms: round2(r.ms),
    };
    rows.push(row);
    checks.push(row);
  }
  const success = rows.filter((x) => x.allowed && x.status < 300);
  const sop = specOp(def.m, typeof def.p === 'function' ? def.p('hr') : def.p);
  const conv = {
    methodMatchesSemantics: METHOD_BY_KIND[def.kind].includes(def.m),
    successCodeOk: success.length > 0 && success.every((x) => x.status === def.ok),
    errorsUnified: rows.filter((x) => x.status >= 400).every((x) => x.errorFormatOk),
    documentedInOpenApi: Boolean(sop && sop.op.responses?.[String(def.ok)]),
  };
  methods.push({ method: def.m, path: typeof def.p === 'function' ? def.p('hr') : def.p, kind: def.kind, expectedSuccess: def.ok,
    successStatuses: [...new Set(success.map((x) => x.status))], openApiPath: sop?.tpl ?? null, ...conv,
    conforms: Object.values(conv).every(Boolean) });
}

const unauthorized = checks.filter((c) => c.unauthorizedAccess);
const falseDenials = checks.filter((c) => c.falseDenial);
const conforming = methods.filter((m) => m.conforms);
const summary = {
  date: nowIso(), base: BASE, methods: METHODS.length, subjects: SUBJECTS.length, checks: checks.length,
  deniedExpected: checks.filter((c) => !c.allowed).length,
  unauthorizedAccess: unauthorized.length, falseDenials: falseDenials.length,
  X_security: round2(1 - unauthorized.length / checks.length),
  restConforming: conforming.length, X_consistency: round2(conforming.length / METHODS.length),
  nonConforming: methods.filter((m) => !m.conforms),
  unauthorizedList: unauthorized, falseDenialList: falseDenials,
};
save('api-matrix.json', { summary, methods, checks });
console.log(JSON.stringify(summary, null, 2));
