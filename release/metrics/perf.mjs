#!/usr/bin/env node
/**
 * ЛР 11, поведение во времени (UI-4, PERF-1): время отклика ключевых методов API — по 50 последовательных запросов
 * на метод после 5 прогревочных; 95-й перцентиль сравнивается с порогом 2 с. Плюс время загрузки главного
 * экрана в Chrome (10 повторов в новом окне: открыть страницу входа, войти как сотрудник, дождаться баланса).
 *   node release/metrics/perf.mjs       # BASE_URL=http://localhost:8080
 */
import { BASE, chromium, http, login, percentile, round2, save, nowIso } from './lib.mjs';

const N = 50;
const WARMUP = 5;
const LIMIT_MS = 2000;
const t = { employee: await login('employee'), manager: await login('manager'), hr: await login('hr'),
  finance: await login('finance'), admin: await login('admin') };
const period = 'from=2026-11-01&to=2026-11-30';
const KEY = [
  ['POST', '/auth/login', null, { email: 'anna.kovaleva@technosoft.by', password: 'demo' }, 'Вход (BCrypt)'],
  ['GET', '/accounts/me', 'employee', null, 'Баланс (PERF-1)'],
  ['GET', `/accounts/me/operations?${period}`, 'employee', null, 'История операций'],
  ['GET', '/programs', 'employee', null, 'Программы мотивации'],
  ['GET', '/employees/team', 'manager', null, 'Команда руководителя'],
  ['POST', '/accruals/preview', 'manager', { employeeIds: ['1', '3', '4', '5', '6', '7'], amount: 800, basis: 'project', reason: 'Замер', campaignId: '1' }, 'Предварительный расчёт'],
  ['GET', `/operations?${period}`, 'hr', null, 'Журнал операций'],
  ['GET', '/control/operations', 'finance', null, 'Очередь контроля'],
  ['GET', '/rules', 'hr', null, 'Правила'],
  ['GET', '/campaigns', 'hr', null, 'Кампании'],
  ['GET', `/reports/employees?${period}`, 'hr', null, 'Отчёт по сотрудникам'],
  ['GET', '/analytics/dashboard?period=quarter', 'hr', null, 'Аналитика'],
  ['GET', '/users', 'admin', null, 'Пользователи'],
  ['GET', '/audit', 'admin', null, 'Журнал аудита'],
];

const methods = [];
for (const [m, p, role, body, title] of KEY) {
  for (let i = 0; i < WARMUP; i++) await http(m, p, { token: t[role], body: body ?? undefined });
  const times = [];
  let errors = 0;
  for (let i = 0; i < N; i++) {
    const r = await http(m, p, { token: t[role], body: body ?? undefined });
    if (r.status >= 400 || r.status === 0) errors++;
    times.push(r.ms);
  }
  const row = { title, method: m, path: p.split('?')[0], n: N, errors,
    p50: round2(percentile(times, 50)), p95: round2(percentile(times, 95)), max: round2(Math.max(...times)) };
  row.ok = row.p95 <= LIMIT_MS && errors === 0;
  methods.push(row);
  console.log(`${row.ok ? '✓' : '✗'} ${title.padEnd(24)} p50 ${row.p50} мс, p95 ${row.p95} мс, max ${row.max} мс`);
}

// Главный экран в браузере
const browser = await chromium().launch({ channel: 'chrome', headless: true });
const ui = [];
try {
  for (let i = 0; i < 10; i++) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU', reducedMotion: 'reduce' });
    const page = await context.newPage();
    const t0 = performance.now();
    await page.goto(`${BASE}/login`, { waitUntil: 'load' });
    await page.getByRole('button', { name: /Войти как сотрудник/ }).waitFor();
    const loginShown = performance.now() - t0;
    const nav = await page.evaluate(() => {
      const e = performance.getEntriesByType('navigation')[0];
      return { domContentLoaded: e.domContentLoadedEventEnd, load: e.loadEventEnd };
    });
    const t1 = performance.now();
    await page.getByRole('button', { name: /Войти как сотрудник/ }).click();
    await page.locator('section[aria-labelledby="balance-label"] .tsb-num[aria-live="polite"]').waitFor();
    await page.waitForFunction(() => !document.querySelector('.ant-skeleton, .ant-spin-spinning'));
    const mainShown = performance.now() - t1;
    ui.push({ loginScreenMs: round2(loginShown), domContentLoadedMs: round2(nav.domContentLoaded), loadMs: round2(nav.load), loginToBalanceMs: round2(mainShown) });
    await context.close();
  }
} finally {
  await browser.close();
}
const uiSummary = {
  runs: ui.length,
  loginScreenP95: round2(percentile(ui.map((x) => x.loginScreenMs), 95)),
  loginToBalanceP95: round2(percentile(ui.map((x) => x.loginToBalanceMs), 95)),
  loginToBalanceMedian: round2(percentile(ui.map((x) => x.loginToBalanceMs), 50)),
};
console.log('Главный экран:', uiSummary);

const okCount = methods.filter((x) => x.ok).length;
const summary = { date: nowIso(), base: BASE, limitMs: LIMIT_MS, methodsMeasured: methods.length, methodsOk: okCount,
  X_time: round2(okCount / methods.length), ui: uiSummary };
save('perf.json', { summary, methods, ui });
console.log(JSON.stringify(summary, null, 2));
