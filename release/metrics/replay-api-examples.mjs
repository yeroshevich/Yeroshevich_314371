#!/usr/bin/env node
/**
 * ЛР 11, завершённость: повторное выполнение всех 96 запросов из docs/lr9/api-examples.md в исходном порядке
 * против установленного дистрибутива на ЧИСТОЙ БД (восстановленной из резервной копии дистрибутива).
 * Из каждого раздела «### N.M» берётся команда curl (метод, адрес, заголовки, тело) и ожидаемый статус
 * («Ответ: **200 OK**»). Отказ — ответ 5xx или отсутствие ответа (соединение разорвано, тайм-аут).
 *   BASE_URL=http://localhost:8081 node release/metrics/replay-api-examples.mjs
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ACCOUNTS, BASE, ROOT, http, login, round2, save, nowIso } from './lib.mjs';

const md = readFileSync(resolve(ROOT, 'docs', 'lr9', 'api-examples.md'), 'utf8');
const sections = md.split(/^### /m).slice(1);

function parseCurl(section) {
  const block = section.match(/```bash\n([\s\S]*?)```/);
  if (!block) return null;
  const cmd = block[1].replace(/\\\n\s*/g, ' ');
  const method = cmd.match(/-X (\w+)/)?.[1] ?? 'GET';
  const url = cmd.match(/curl[^']*'([^']+)'/)[1];
  const headers = {};
  for (const m of cmd.matchAll(/-H (["'])(.*?)\1/g)) {
    const [k, ...v] = m[2].split(':');
    headers[k.trim()] = v.join(':').trim();
  }
  const data = cmd.match(/-d '([\s\S]*?)'(?:\s|$)/)?.[1];
  const expected = Number(section.match(/Ответ: \*\*(\d{3})/)?.[1]);
  return { title: section.split('\n')[0].trim(), method, url, headers, data, expected };
}

const tokens = {
  EMPLOYEE: await login('employee'), MANAGER: await login('manager'), HR: await login('hr'),
  FINANCE: await login('finance'), ADMIN: await login('admin'), BLOCKED: await login('pavel.karpovich@technosoft.by'),
};
void ACCOUNTS;

/** Первая операция, созданная примерами ЛР 9 (раздел 4.2, ОП-000287). */
const FIRST_DOC_OP = 287;
let offset = null;
const results = [];
for (const s of sections) {
  const req = parseCurl(s);
  if (!req) continue;
  const headers = {};
  for (const [k, v] of Object.entries(req.headers)) {
    headers[k] = v.replace(/\$TOKEN_(\w+)/g, (_, r) => tokens[r] ?? `missing-${r}`);
  }
  // Номера операций, созданных самими примерами (в ЛР 9 — с 287), на чистой БД другие: сдвиг по первой созданной.
  const url = req.url.replace('http://localhost:8080', BASE)
    .replace(/\/operations\/(\d+)/, (m, id) => (Number(id) >= FIRST_DOC_OP && offset !== null ? `/operations/${Number(id) + offset}` : m));
  const r = await http(req.method, url, { headers, raw: req.data });
  if (offset === null && r.status === 201 && r.json?.operation?.id) offset = Number(r.json.operation.id) - FIRST_DOC_OP;
  results.push({
    title: req.title, method: req.method, path: new URL(url).pathname + new URL(url).search,
    expected: req.expected, actual: r.status, ms: round2(r.ms),
    failure: r.status === 0 || r.status >= 500, match: r.status === req.expected,
    code: r.json?.code,
  });
}

const failures = results.filter((x) => x.failure);
const mismatches = results.filter((x) => !x.match);
const summary = {
  date: nowIso(), base: BASE, requests: results.length, failures5xx: failures.length,
  statusMatches: results.length - mismatches.length, mismatches,
  X_completeness: round2(1 - failures.length / results.length), idOffset: offset,
};
save('replay-api-examples.json', { summary, results });
console.log(JSON.stringify(summary, null, 2));
