#!/usr/bin/env node
/**
 * ЛР 11, PERF-3: массовые начисления не блокируют работу других пользователей.
 * HR отправляет CONCURRENCY (по умолчанию 20) одновременных массовых начислений (каждое — всем активным сотрудникам с активным счётом),
 * в это же время сотрудник каждые 25 мс запрашивает баланс и каталог программ (то, что видит интерфейс).
 * Фиксируется время ответа запросов сотрудника во время массовых начислений и до них (фон).
 * Тайм-аут любого запроса — 60 с (запрос, не получивший ответа, считается отказом со временем 60 с).
 *   PGDATABASE=technosoft_bonus OUT_NAME=mass-accrual.json node release/metrics/mass-accrual.mjs
 */
import { execFileSync } from 'node:child_process';
import { BASE, http, login, percentile, round2, save, nowIso } from './lib.mjs';

const DB = process.env.PGDATABASE ?? 'technosoft_bonus';
const ids = execFileSync('psql', ['-d', DB, '-Atc',
  "select a.employee_id from bonus_account a join app_user u on u.employee_id = a.employee_id where a.status = 'active' and u.status = 'active' order by 1"],
{ encoding: 'utf8' }).trim().split('\n');
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 20);
const TIMEOUT = 60_000;
const hr = await login('hr');
const emp = await login('employee');

const probeErrors = [];
async function probe(stopAt) {
  const times = [];
  while (performance.now() < stopAt()) {
    const [a, b] = await Promise.all([http('GET', '/accounts/me', { token: emp, timeoutMs: TIMEOUT }), http('GET', '/programs', { token: emp, timeoutMs: TIMEOUT })]);
    times.push(a.ms, b.ms);
    for (const x of [a, b]) if (x.status !== 200) probeErrors.push({ status: x.status, error: x.error, code: x.json?.code, ms: round2(x.ms) });
    await new Promise((r) => setTimeout(r, 25));
  }
  return times;
}

const t0 = performance.now();
const background = await probe(() => t0 + 2000);

let done = false;
const during = probe(() => (done ? 0 : Infinity));
const m0 = performance.now();
const mass = await Promise.all(Array.from({ length: CONCURRENCY }, (_, i) => http('POST', '/accruals', {
  token: hr, timeoutMs: TIMEOUT, body: { employeeIds: ids, amount: 10, basis: 'achievement', reason: `Массовое начисление PERF-3 #${i + 1}` },
})));
const massSec = (performance.now() - m0) / 1000;
await new Promise((r) => setTimeout(r, 200));
done = true;
const duringTimes = await during;

const created = mass.filter((r) => r.status === 201).reduce((s, r) => s + r.json.operations.length, 0);
const summary = {
  date: nowIso(), base: BASE, concurrency: CONCURRENCY, recipientsPerRequest: ids.length, massRequests: mass.length,
  massStatuses: [...new Set(mass.map((r) => r.status))], operationsCreated: created, massWallSec: round2(massSec),
  massRequestP95Ms: round2(percentile(mass.map((r) => r.ms), 95)),
  background: { n: background.length, p95: round2(percentile(background, 95)), max: round2(Math.max(...background)) },
  massErrors: mass.filter((r) => r.status !== 201).map((r) => ({ status: r.status, error: r.error, code: r.json?.code, ms: round2(r.ms) })),
  probeErrors,
  duringMass: { n: duringTimes.length, p95: round2(percentile(duringTimes, 95)), max: round2(Math.max(...duringTimes)), over2s: duringTimes.filter((x) => x > 2000).length },
};
summary.perf3Met = summary.duringMass.over2s === 0 && probeErrors.length === 0 && mass.every((r) => r.status === 201);
save(process.env.OUT_NAME ?? 'mass-accrual.json', summary);
console.log(JSON.stringify(summary, null, 2));
