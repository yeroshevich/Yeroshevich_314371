#!/usr/bin/env node
/**
 * ЛР 11, PERF-2 и использование ресурсов: 500 одновременных клиентов. Каждый клиент — отдельный вход
 * (POST /auth/login, BCrypt) и запрос баланса (GET /accounts/me); все 500 стартуют одновременно.
 * Учётные записи — все активные владельцы счетов из БД (сотрудники, руководители, HR) по кругу.
 * Выполняется ROUNDS волн подряд (сценарий А — одновременный вход 500 клиентов). Сценарий Б — 500 уже вошедших
 * пользователей одновременно запрашивают баланс (ROUNDS волн): так проверяется PERF-1 при 500 одновременных пользователях. Во время нагрузки каждые 200 мс снимается память процесса сервера:
 * RSS (ps) и занятая куча Java (jstat -gc: S0U + S1U + EU + OU), предел кучи — -Xmx (512 МБ в start.sh).
 *   SERVER_PID=<pid java> PGDATABASE=technosoft_bonus node release/metrics/load500.mjs
 */
import { execFile, execFileSync } from 'node:child_process';
import { promisify } from 'node:util';
import { BASE, http, percentile, round2, save, nowIso } from './lib.mjs';

const run = promisify(execFile);
const CLIENTS = Number(process.env.CLIENTS ?? 500);
const ROUNDS = Number(process.env.ROUNDS ?? 3);
const PID = process.env.SERVER_PID ?? execFileSync('lsof', ['-nP', '-t', `-iTCP:${new URL(BASE).port || 80}`, '-sTCP:LISTEN'], { encoding: 'utf8' }).trim();
const DB = process.env.PGDATABASE ?? 'technosoft_bonus';
const XMX_MB = 512;

const emails = execFileSync('psql', ['-d', DB, '-Atc',
  `select e.email from app_user u join employee e on e.id = u.employee_id join bonus_account a on a.employee_id = e.id
   where u.status = 'active' and a.status = 'active' and u.role_code in ('employee','manager','hr') order by u.id`],
{ encoding: 'utf8' }).trim().split('\n');

// ── Память ──────────────────────────────────────────────────────────────────────────────────────
const mem = [];
async function sample() {
  try {
    const [{ stdout: rss }, { stdout: gc }] = await Promise.all([
      run('ps', ['-o', 'rss=', '-p', PID]), run('jstat', ['-gc', PID]),
    ]);
    const [head, vals] = gc.trim().split('\n').map((l) => l.trim().split(/\s+/));
    const v = Object.fromEntries(head.map((h, i) => [h, Number(vals[i])]));
    mem.push({ t: Date.now(), rssMb: round2(Number(rss.trim()) / 1024), heapUsedMb: round2((v.S0U + v.S1U + v.EU + v.OU) / 1024),
      heapCommittedMb: round2((v.S0C + v.S1C + v.EC + v.OC) / 1024), threads: null });
  } catch {
    /* процесс недоступен */
  }
}
let sampling = true;
const sampler = (async () => { while (sampling) { await sample(); await new Promise((r) => setTimeout(r, 200)); } })();
await new Promise((r) => setTimeout(r, 600));
const idle = { ...mem[mem.length - 1] };

// ── Нагрузка ────────────────────────────────────────────────────────────────────────────────────
async function client(i) {
  const email = emails[i % emails.length];
  const t0 = performance.now();
  const a = await http('POST', '/auth/login', { body: { email, password: 'demo' } });
  if (a.status !== 200) return { ok: false, stage: 'login', status: a.status, error: a.error, totalMs: performance.now() - t0, loginMs: a.ms };
  const b = await http('GET', '/accounts/me', { token: a.json.token });
  return { ok: b.status === 200 && typeof b.json?.account?.balance === 'number', stage: 'balance', status: b.status, error: b.error,
    loginMs: a.ms, balanceMs: b.ms, totalMs: performance.now() - t0 };
}

const rounds = [];
for (let r = 0; r < ROUNDS; r++) {
  const t0 = performance.now();
  const res = await Promise.all(Array.from({ length: CLIENTS }, (_, i) => client(i)));
  const wall = performance.now() - t0;
  const ok = res.filter((x) => x.ok);
  const round = {
    round: r + 1, clients: CLIENTS, success: ok.length, successRate: round2(ok.length / CLIENTS), wallSec: round2(wall / 1000),
    failures: res.filter((x) => !x.ok).map((x) => ({ stage: x.stage, status: x.status, error: x.error })).slice(0, 10),
    totalMs: { p50: round2(percentile(ok.map((x) => x.totalMs), 50)), p95: round2(percentile(ok.map((x) => x.totalMs), 95)), max: round2(Math.max(...ok.map((x) => x.totalMs))) },
    loginMs: { p50: round2(percentile(ok.map((x) => x.loginMs), 50)), p95: round2(percentile(ok.map((x) => x.loginMs), 95)) },
    balanceMs: { p50: round2(percentile(ok.map((x) => x.balanceMs), 50)), p95: round2(percentile(ok.map((x) => x.balanceMs), 95)), max: round2(Math.max(...ok.map((x) => x.balanceMs))) },
    throughputClientsPerSec: round2(CLIENTS / (wall / 1000)),
  };
  rounds.push(round);
  console.log(JSON.stringify(round));
}
// Сценарий Б: 500 вошедших пользователей одновременно запрашивают баланс (токены — по одному на учётную запись).
const tokenByEmail = {};
for (const email of emails) tokenByEmail[email] = (await http('POST', '/auth/login', { body: { email, password: 'demo' } })).json.token;
const roundsB = [];
for (let r = 0; r < ROUNDS; r++) {
  const t0 = performance.now();
  const res = await Promise.all(Array.from({ length: CLIENTS }, (_, i) => http('GET', '/accounts/me', { token: tokenByEmail[emails[i % emails.length]] })));
  const wall = performance.now() - t0;
  const ok = res.filter((x) => x.status === 200);
  const round = { round: r + 1, clients: CLIENTS, success: ok.length, successRate: round2(ok.length / CLIENTS), wallSec: round2(wall / 1000),
    balanceMs: { p50: round2(percentile(ok.map((x) => x.ms), 50)), p95: round2(percentile(ok.map((x) => x.ms), 95)), max: round2(Math.max(...ok.map((x) => x.ms))) } };
  roundsB.push(round);
  console.log('Б', JSON.stringify(round));
}
await new Promise((r) => setTimeout(r, 1000));
sampling = false;
await sampler;

const peakHeap = Math.max(...mem.map((m) => m.heapUsedMb));
const peakRss = Math.max(...mem.map((m) => m.rssMb));
const totalClients = rounds.reduce((s, r) => s + r.clients, 0);
const totalOk = rounds.reduce((s, r) => s + r.success, 0);
const summary = {
  date: nowIso(), base: BASE, pid: PID, accounts: emails.length, clientsPerRound: CLIENTS, rounds: ROUNDS,
  successTotal: totalOk, clientsTotal: totalClients, successRate: round2(totalOk / totalClients),
  worstBalanceP95Ms: Math.max(...rounds.map((r) => r.balanceMs.p95)), worstTotalP95Ms: Math.max(...rounds.map((r) => r.totalMs.p95)),
  scenarioB: { successTotal: roundsB.reduce((s, r) => s + r.success, 0), clientsTotal: CLIENTS * ROUNDS,
    worstBalanceP95Ms: Math.max(...roundsB.map((r) => r.balanceMs.p95)), worstWallSec: Math.max(...roundsB.map((r) => r.wallSec)) },
  memory: { samples: mem.length, idle, peakHeapUsedMb: peakHeap, peakRssMb: peakRss, xmxMb: XMX_MB,
    X_resources: round2(1 - peakHeap / XMX_MB) },
};
save('load500.json', { summary, rounds, roundsB, memory: mem });
console.log(JSON.stringify(summary, null, 2));
