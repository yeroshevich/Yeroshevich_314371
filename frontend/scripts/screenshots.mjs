#!/usr/bin/env node
/**
 * Скриншоты экранов TechnoSoft Bonus для отчётов.
 *
 * ЛР 9 (по умолчанию) — клиент работает с сервером Spring Boot (http://localhost:8080 должен быть запущен):
 *   npm run build && npm run screenshots                 # vite preview :4173 с прокси /api → :8080
 *   BASE_URL=http://localhost:5173 npm run screenshots   # против уже запущенного vite dev
 *   ONLY=02,06 npm run screenshots                       # только выбранные кадры
 * Вход — кнопками «Войти как …» на /login (демо-учётки сервера, пароль demo). Результат: docs/lr9/screens.
 *
 * ЛР 6 (мок без сервера): VITE_API_MODE=mock npm run build && API_MODE=mock OUT_DIR=../docs/lr6/screens npm run screenshots
 *
 * Браузер — установленный Google Chrome (playwright-core, channel: 'chrome'), ничего не скачивается.
 */
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const here = dirname(fileURLToPath(import.meta.url));
const frontend = resolve(here, '..');
const outDir = process.env.OUT_DIR
  ? resolve(frontend, process.env.OUT_DIR)
  : resolve(frontend, '..', 'docs', 'lr9', 'screens');
const PORT = 4173;
const MOCK = process.env.API_MODE === 'mock';

/** Токены мок-сессий (только API_MODE=mock): роль → пользователь из src/api/mock/db.ts. */
const MOCK_TOKENS = {
  employee: 'mock-token-u01', // Анна Ковалёва
  manager: 'mock-token-u02', // Дмитрий Савицкий
  hr: 'mock-token-u20', // Ольга Мельник
  finance: 'mock-token-u23', // Игорь Жук
  admin: 'mock-token-u17', // Сергей Литвинов
  blocked: 'mock-token-u09', // Павел Карпович, счёт заблокирован
};

/** Кнопки быстрого входа на /login (LoginPage, DEMO_ROLES) — демо-учётки сервера. */
const LOGIN_BUTTONS = {
  employee: 'Войти как сотрудник',
  manager: 'Войти как руководитель',
  hr: 'Войти как HR',
  finance: 'Войти как финотдел',
  admin: 'Войти как администратор',
  blocked: 'Сотрудник с заблокированным счётом',
};

/** Сотрудники для мастера начисления: Анна Ковалёва, Максим Новик, Юлия Романович. */
const TEAM_IDS = MOCK ? 'e01,e03,e06' : '1,3,6';

const DESKTOP = { width: 1440, height: 900 };
const MOBILE = { width: 375, height: 812 };

/** Список кадров. `act` — действия перед снимком (открыть модалку, пройти шаг мастера). */
const SHOTS = [
  { id: '01', file: '01-login.png', route: '/login' },
  { id: '02', file: '02-me.png', route: '/me', role: 'employee' },
  { id: '02b', file: '02b-me-blocked.png', route: '/me', role: 'blocked' },
  { id: '03', file: '03-history.png', route: '/history', role: 'employee' },
  { id: '04', file: '04-programs.png', route: '/programs', role: 'employee' },
  {
    id: '04b', file: '04b-programs-confirm.png', route: '/programs', role: 'employee',
    act: async (page) => {
      await page.getByRole('button', { name: 'Использовать' }).first().click();
      await page.locator('.ant-modal-confirm').waitFor();
    },
  },
  { id: '05', file: '05-team.png', route: '/team', role: 'manager' },
  {
    id: '06', file: '06-accruals-new.png', route: '/accruals/new', role: 'manager',
    act: async (page) => {
      await page.getByRole('checkbox', { name: 'Выбрать: Анна Ковалёва' }).check();
      await page.getByRole('checkbox', { name: 'Выбрать: Максим Новик' }).check();
    },
  },
  {
    id: '06b', file: '06b-accruals-new-step2.png', route: `/accruals/new?employees=${TEAM_IDS}`, role: 'manager',
    act: async (page) => {
      await page.getByRole('button', { name: 'Далее: сумма' }).click();
      await page.getByLabel('Сумма на одного сотрудника').fill('800');
      await page.getByLabel('Бонусная кампания').click();
      await page.locator('.ant-select-item-option').first().click();
      await page.getByLabel('Причина').fill('Срочный релиз платформы 2.0 в выходные');
      await page.getByLabel('Причина').blur();
      await page.locator('.ant-table-summary').waitFor();
      await settle(page);
    },
  },
  {
    id: '06c', file: '06c-accruals-new-step3.png', route: `/accruals/new?employees=${TEAM_IDS}`, role: 'manager',
    act: async (page) => {
      await page.getByRole('button', { name: 'Далее: сумма' }).click();
      await page.getByLabel('Сумма на одного сотрудника').fill('800');
      await page.getByLabel('Бонусная кампания').click();
      await page.locator('.ant-select-item-option').first().click();
      await page.getByLabel('Причина').fill('Срочный релиз платформы 2.0 в выходные');
      await page.getByRole('button', { name: 'Далее: проверить' }).click();
      await page.getByText('Проверьте перед начислением').waitFor();
    },
  },
  { id: '07', file: '07-operations.png', route: '/operations', role: 'hr' },
  {
    id: '07b', file: '07b-operations-correct.png', route: '/operations', role: 'hr',
    act: async (page) => {
      await page.getByRole('button', { name: 'Корректировать' }).first().click();
      await page.locator('.ant-drawer-open').waitFor();
    },
  },
  { id: '08', file: '08-rules.png', route: '/rules', role: 'hr' },
  {
    id: '08b', file: '08b-rules-edit.png', route: '/rules', role: 'hr',
    act: async (page) => {
      await page.getByRole('button', { name: 'Изменить' }).first().click();
      await page.locator('.ant-drawer-open').waitFor();
    },
  },
  { id: '09', file: '09-campaigns.png', route: '/campaigns', role: 'hr' },
  {
    id: '10', file: '10-reports.png', route: '/reports', role: 'hr',
    act: async (page) => {
      await page.getByRole('button', { name: 'Сформировать' }).click();
      await page.getByRole('button', { name: 'Скачать Excel' }).waitFor();
    },
  },
  { id: '11', file: '11-analytics.png', route: '/analytics', role: 'hr' },
  { id: '12', file: '12-control.png', route: '/control', role: 'finance' },
  { id: '13', file: '13-admin-users.png', route: '/admin/users', role: 'admin' },
  { id: '14', file: '14-admin-integrations.png', route: '/admin/integrations', role: 'admin' },
  { id: '15', file: '15-admin-audit.png', route: '/admin/audit', role: 'admin' },
  { id: '16', file: '16-design-system.png', route: '/design-system', fullPage: true },
  { id: '17', file: '17-forbidden-403.png', route: '/rules', role: 'employee' },
  { id: '02m', file: '02-me-mobile.png', route: '/me', role: 'employee', viewport: MOBILE },
  { id: '03m', file: '03-history-mobile.png', route: '/history', role: 'employee', viewport: MOBILE },
];

/** Ждём, пока исчезнут скелетоны, спиннеры и анимации. */
async function settle(page) {
  await page.waitForFunction(
    () => !document.querySelector('.ant-skeleton, .ant-spin-spinning, [aria-busy="true"], .ant-btn-loading'),
    null,
    { timeout: 15000 },
  );
  await page.evaluate(() => document.fonts.ready);
  // Убираем курсор с элементов, чтобы на кадре не было состояния наведения.
  await page.mouse.move(1, 1);
  await page.waitForTimeout(500);
}

/** Вход через кнопку «Войти как …»: сервер выдаёт JWT, клиент кладёт его в sessionStorage вкладки. */
async function signIn(page, base, role) {
  await page.goto(`${base}/login`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: LOGIN_BUTTONS[role] }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 });
  await settle(page);
}

async function waitForServer(url, timeoutMs = 30000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      /* сервер ещё поднимается */
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(`Сервер ${url} не ответил за ${timeoutMs / 1000} с`);
}

function startPreview() {
  if (!existsSync(resolve(frontend, 'dist', 'index.html'))) {
    throw new Error('Нет сборки dist/. Сначала выполните npm run build.');
  }
  const viteBin = resolve(frontend, 'node_modules', 'vite', 'bin', 'vite.js');
  const child = spawn(process.execPath, [viteBin, 'preview', '--port', String(PORT), '--strictPort'], {
    cwd: frontend,
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  return child;
}

async function main() {
  mkdirSync(outDir, { recursive: true });
  const only = process.env.ONLY?.split(',').map((s) => s.trim());
  const shots = only ? SHOTS.filter((s) => only.includes(s.id)) : SHOTS;

  let server;
  let base = process.env.BASE_URL?.replace(/\/$/, '');
  if (!base) {
    server = startPreview();
    base = `http://localhost:${PORT}`;
  }
  await waitForServer(`${base}/login`);

  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const failures = [];
  try {
    for (const shot of shots) {
      const viewport = shot.viewport ?? DESKTOP;
      const mobile = viewport === MOBILE;
      const context = await browser.newContext({
        viewport,
        deviceScaleFactor: mobile ? 2 : 1,
        isMobile: mobile,
        hasTouch: mobile,
        locale: 'ru-RU',
        timezoneId: 'Europe/Minsk',
        reducedMotion: 'reduce',
      });
      if (shot.role && MOCK) {
        await context.addInitScript((t) => sessionStorage.setItem('tsb.session', t), MOCK_TOKENS[shot.role]);
      }
      const page = await context.newPage();
      try {
        if (shot.role && !MOCK) await signIn(page, base, shot.role);
        await page.goto(`${base}${shot.route}`, { waitUntil: 'networkidle' });
        await settle(page);
        if (shot.act) {
          await shot.act(page);
          await settle(page);
        }
        const path = resolve(outDir, shot.file);
        await page.screenshot({ path, fullPage: Boolean(shot.fullPage) });
        console.log(`✓ ${shot.file}`);
      } catch (e) {
        failures.push(shot.file);
        console.error(`✗ ${shot.file}: ${e.message.split('\n')[0]}`);
      } finally {
        await context.close();
      }
    }
  } finally {
    await browser.close();
    server?.kill();
  }
  console.log(`\nГотово: ${shots.length - failures.length} из ${shots.length} → ${outDir}`);
  if (failures.length) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
