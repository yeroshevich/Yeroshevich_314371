#!/usr/bin/env node
/**
 * ЛР 11, проверка установки: шаги пользователя в браузере Google Chrome против установленного дистрибутива.
 *   1) открыть http://localhost:8080 → страница «Вход» (снимок install-login.png);
 *   2) «Войти как сотрудник» → «Мой баланс» с текущим балансом (install-main.png);
 *   3) обновить страницу на глубокой ссылке /history (маршрут клиента отдаётся сервером);
 *   4) открыть документацию API /swagger-ui.html (install-swagger.png).
 * Время каждого шага — по часам скрипта (от действия до появления ожидаемого элемента).
 *   node release/metrics/install-browser-check.mjs        # BASE_URL=http://localhost:8080
 */
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { BASE, ROOT, chromium, round2, save, nowIso } from './lib.mjs';

const SCREENS = resolve(ROOT, 'docs', 'lr11', 'screens');
mkdirSync(SCREENS, { recursive: true });

const steps = [];
async function step(name, fn) {
  const t0 = performance.now();
  const detail = await fn();
  const sec = round2((performance.now() - t0) / 1000);
  steps.push({ name, sec, detail });
  console.log(`✓ ${name} — ${sec} с${detail ? `: ${detail}` : ''}`);
}

const browser = await chromium().launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 }, locale: 'ru-RU', timezoneId: 'Europe/Minsk', reducedMotion: 'reduce',
});
const page = await context.newPage();
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', (e) => consoleErrors.push(String(e)));

try {
  await step(`Открыть ${BASE}`, async () => {
    const res = await page.goto(BASE, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'Вход' }).waitFor();
    await page.screenshot({ path: resolve(SCREENS, 'install-login.png') });
    return `HTTP ${res.status()}, адрес ${new URL(page.url()).pathname}, заголовок «Вход»`;
  });

  await step('Войти как сотрудник', async () => {
    await page.getByRole('button', { name: /Войти как сотрудник/ }).click();
    await page.waitForURL((u) => u.pathname === '/me');
    const num = page.locator('section[aria-labelledby="balance-label"] .tsb-num[aria-live="polite"]');
    await num.waitFor();
    await page.waitForFunction(() => !document.querySelector('.ant-skeleton, .ant-spin-spinning'));
    await page.waitForTimeout(1500); // «добег» числа баланса
    const balance = await num.getAttribute('aria-label');
    const heading = await page.locator('h1, h2, h3, h4').first().innerText();
    await page.screenshot({ path: resolve(SCREENS, 'install-main.png') });
    return `адрес /me, «${heading}», баланс ${balance?.replace(/ /g, ' ')}`;
  });

  await step('Глубокая ссылка: открыть /history и обновить страницу', async () => {
    const res1 = await page.goto(`${BASE}/history`, { waitUntil: 'networkidle' });
    const res2 = await page.reload({ waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'История операций' }).first().waitFor();
    return `HTTP ${res1.status()} и ${res2.status()} (после обновления), заголовок «История операций»`;
  });

  await step('Открыть документацию API /swagger-ui.html', async () => {
    const res = await page.goto(`${BASE}/swagger-ui.html`, { waitUntil: 'networkidle' });
    await page.locator('.swagger-ui .info .title').waitFor();
    const title = (await page.locator('.swagger-ui .info .title').innerText()).split('\n')[0];
    const ops = await page.locator('.opblock').count();
    await page.screenshot({ path: resolve(SCREENS, 'install-swagger.png') });
    return `HTTP ${res.status()}, итоговый адрес ${new URL(page.url()).pathname}, «${title}», операций ${ops}`;
  });
} finally {
  await browser.close();
}

const file = save('install-browser-check.json', { date: nowIso(), base: BASE, steps, consoleErrors });
console.log(`Ошибок в консоли браузера: ${consoleErrors.length}. Результат: ${file}`);
