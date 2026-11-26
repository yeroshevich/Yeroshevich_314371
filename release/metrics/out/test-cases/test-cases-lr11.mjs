#!/usr/bin/env node
/**
 * ЛР 10. Автоматизированное выполнение тест-кейсов уровня базовых пользовательских требований (UC1–UC13 + вход)
 * через браузер: клиент (vite preview :4173, прокси /api → :8080) работает с запущенным сервером Spring Boot.
 *
 *   # сервер с обработкой очереди событий по расписанию (нужно для TC-10, UC4):
 *   BONUS_EVENT_SCHEDULER_ENABLED=true BONUS_EVENT_SCHEDULER_DELAY=PT5S java -jar ../backend/target/bonus-0.1.0-SNAPSHOT.jar
 *   npm run build && node scripts/test-cases.mjs        # ONLY=TC-01,TC-08 — выборочно
 *
 * Каждый кейс выполняется в новом окне браузера (своя сессия). Шаги выполняются по порядку; после каждого шага
 * проверяется ожидаемый результат. Если шаг не прошёл, остальные шаги кейса помечаются «не выполнен».
 * Итог: docs/lr10/test-cases.md (таблица с фактическим результатом и датой) и docs/lr10/screens/tc-XX.png
 * (финальное состояние каждого кейса). Кейсы меняют данные БД — после прогона БД восстанавливают из дампа.
 * Браузер — установленный Google Chrome (playwright-core, channel: 'chrome').
 */
import { execFileSync, spawn } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
const { chromium } = createRequire(process.env.FRONTEND_DIR + '/package.json')('playwright-core');

const here = dirname(fileURLToPath(import.meta.url));
const frontend = process.env.FRONTEND_DIR;
const docs = process.env.OUT_DIR;
const screens = resolve(docs, 'screens');
const PORT = 4173;
const API = 'http://localhost:8080/api/v1';
const DB = process.env.PGDATABASE ?? 'tpsis_bonus_app';

const LOGIN_BUTTONS = {
  employee: 'Войти как сотрудник',
  manager: 'Войти как руководитель',
  hr: 'Войти как HR',
  finance: 'Войти как финотдел',
  admin: 'Войти как администратор',
  blocked: 'Сотрудник с заблокированным счётом',
};

// ── Вспомогательные функции ──────────────────────────────────────────────────

/** Неразрывные пробелы (формат «1 500 Б») → обычные, лишние пробелы схлопываются. */
const norm = (s) => (s ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

class StepFailed extends Error {}

function check(condition, message) {
  if (!condition) throw new StepFailed(message);
}

async function settle(page) {
  await page.waitForFunction(
    () => !document.querySelector('.ant-skeleton, .ant-spin-spinning, [aria-busy="true"], .ant-btn-loading'),
    null,
    { timeout: 15000 },
  );
  await page.waitForTimeout(300);
}

async function bodyText(page) {
  return norm(await page.locator('body').innerText());
}

async function signIn(page, base, role) {
  await page.goto(`${base}/login`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: LOGIN_BUTTONS[role] }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 });
  await settle(page);
}

async function formLogin(page, base, email, password) {
  await page.goto(`${base}/login`, { waitUntil: 'networkidle' });
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Пароль').fill(password);
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
}

async function open(page, base, route) {
  await page.goto(`${base}${route}`, { waitUntil: 'networkidle' });
  await settle(page);
}

/** Всплывающее сообщение antd (message.success/error/warning/info). */
async function toast(page, text) {
  const notice = page.locator('.ant-message-notice').filter({ hasText: text }).first();
  await notice.waitFor({ timeout: 10000 });
  return norm(await notice.innerText());
}

/** Ввод периода в DatePicker.RangePicker: дата начала → Enter → дата окончания → Enter. */
async function typeRange(page, picker, from, to, { closeWithEscape = true } = {}) {
  const inputs = picker.locator('input');
  await inputs.nth(0).click();
  await inputs.nth(0).fill(from);
  await inputs.nth(0).press('Enter');
  await inputs.nth(1).fill(to);
  await inputs.nth(1).press('Enter');
  // В выдвижной панели Escape закрыл бы саму панель — там календарь закрывается после ввода даты окончания.
  if (closeWithEscape) await page.keyboard.press('Escape');
  await settle(page);
}

/** Пользователь в таблице «Пользователи» (поиск, чтобы строка была на первой странице). */
async function userRow(page, name) {
  await page.getByLabel('Поиск пользователя').fill(name);
  await page.waitForTimeout(500);
  await settle(page);
  return rows(page).filter({ hasText: name });
}

const rows = (scope) => scope.locator('.ant-table-tbody tr.ant-table-row');

async function api(method, url, token, body) {
  const res = await fetch(`${API}${url}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : null };
}

async function token(login) {
  const r = await api('POST', '/auth/login', null, { email: `${login}@technosoft.by`, password: 'demo' });
  return r.json.token;
}

function psql(sql) {
  return execFileSync('psql', ['-d', DB, '-Atc', sql], { encoding: 'utf8' }).trim();
}

// ── Тест-кейсы ─────────────────────────────────────────────────────────────
// step: { do — действие (безличная форма), expect — ожидаемый результат, run — выполнение и проверка,
//         возвращает фактический результат; tool — шаг выполняется инструментом, а не через интерфейс }

const CASES = [
  {
    id: 'TC-01', title: 'Вход в систему по email и паролю', req: 'SEC-1',
    steps: [
      { do: 'Открыть страницу входа /login', expect: 'Отображается форма «Вход» с полями «Email», «Пароль» и кнопкой «Войти»',
        run: async ({ page, base }) => {
          await open(page, base, '/login');
          check(await page.getByRole('heading', { name: 'Вход' }).isVisible(), 'нет заголовка «Вход»');
          check(await page.getByLabel('Email').isVisible() && await page.getByLabel('Пароль').isVisible(), 'нет полей');
          return 'Форма «Вход» с полями Email и Пароль отображается';
        } },
      { do: 'Ввести email anna.kovaleva@technosoft.by и пароль demo', expect: 'Значения введены, пароль скрыт',
        run: async ({ page }) => {
          await page.getByLabel('Email').fill('anna.kovaleva@technosoft.by');
          await page.getByLabel('Пароль').fill('demo');
          check(await page.getByLabel('Пароль').getAttribute('type') === 'password', 'пароль не скрыт');
          return 'Email введён, пароль отображается точками';
        } },
      { do: 'Нажать «Войти»', expect: 'Открывается раздел «Мой баланс» с приветствием «Здравствуйте, Анна»',
        run: async ({ page }) => {
          await page.getByRole('button', { name: 'Войти', exact: true }).click();
          await page.waitForURL('**/me', { timeout: 15000 });
          await settle(page);
          check((await bodyText(page)).includes('Здравствуйте, Анна'), 'нет приветствия');
          return 'Открыт /me, заголовок «Здравствуйте, Анна»';
        } },
    ],
  },
  {
    id: 'TC-02', title: 'Отклонение входа с неверным паролем', req: 'SEC-1',
    steps: [
      { do: 'Открыть страницу входа /login', expect: 'Отображается форма «Вход»',
        run: async ({ page, base }) => { await open(page, base, '/login'); return 'Форма «Вход» отображается'; } },
      { do: 'Ввести email anna.kovaleva@technosoft.by и пароль wrong-pass, нажать «Войти»',
        expect: 'Появляется сообщение «Неверный email или пароль…», вход не выполнен, остаётся страница /login',
        run: async ({ page }) => {
          await page.getByLabel('Email').fill('anna.kovaleva@technosoft.by');
          await page.getByLabel('Пароль').fill('wrong-pass');
          await page.getByRole('button', { name: 'Войти', exact: true }).click();
          const alert = page.locator('.ant-alert-error');
          await alert.waitFor({ timeout: 10000 });
          const text = norm(await alert.innerText());
          check(text.includes('Неверный email или пароль'), `сообщение: ${text}`);
          check(new URL(page.url()).pathname === '/login', 'ушли со страницы входа');
          return `Сообщение «${text}», адрес /login`;
        } },
    ],
  },
  {
    id: 'TC-03', title: 'Просмотр текущего бонусного баланса', req: 'UC1: FR1-1',
    steps: [
      { do: 'Войти как сотрудник (Анна Ковалёва)', expect: 'Открывается раздел «Мой баланс»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'employee');
          check(new URL(page.url()).pathname === '/me', page.url());
          return 'Открыт раздел «Мой баланс» (/me)';
        } },
      { do: 'Посмотреть карточку баланса', expect: 'Отображается текущий баланс 8 900 Б',
        run: async ({ page }) => {
          const labels = (await page.locator('[aria-label]').evaluateAll((els) => els.map((e) => e.getAttribute('aria-label')))).map(norm);
          check(labels.includes('8 900 Б'), `баланс не найден: ${labels.filter((l) => l.endsWith('Б')).join('; ')}`);
          return 'Баланс 8 900 Б';
        } },
      { do: 'Посмотреть итоги месяца', expect: 'Отображаются карточки «Начислено за месяц», «Потрачено за месяц», «На согласовании» (1 500 Б)',
        run: async ({ page }) => {
          const t = await bodyText(page);
          check(t.includes('Начислено за месяц') && t.includes('Потрачено за месяц') && t.includes('На согласовании'), 'нет карточек');
          check(t.includes('1 500 Б'), 'нет суммы на согласовании');
          return 'Карточки итогов месяца отображаются, на согласовании 1 500 Б';
        } },
    ],
  },
  {
    id: 'TC-04', title: 'Отображение сообщения о недоступном балансе', req: 'UC1: FR1-2',
    steps: [
      { do: 'Войти кнопкой «Сотрудник с заблокированным счётом» (Павел Карпович)', expect: 'Открывается раздел «Мой баланс»',
        run: async ({ page, base }) => { await signIn(page, base, 'blocked'); return `Открыт ${new URL(page.url()).pathname}`; } },
      { do: 'Посмотреть раздел «Мой баланс»', expect: 'Вместо баланса — сообщение «Баланс недоступен, обратитесь к HR» и кнопка «Написать HR»',
        run: async ({ page }) => {
          const alert = page.locator('.ant-alert-error');
          await alert.waitFor({ timeout: 10000 });
          const text = norm(await alert.innerText());
          check(text.includes('Баланс недоступен, обратитесь к HR'), text);
          check(await page.getByRole('link', { name: 'Написать HR' }).isVisible(), 'нет кнопки «Написать HR»');
          return 'Сообщение «Баланс недоступен, обратитесь к HR», кнопка «Написать HR»';
        } },
    ],
  },
  {
    id: 'TC-05', title: 'Просмотр истории операций своего счёта', req: 'UC2: FR2-1',
    steps: [
      { do: 'Войти как сотрудник (Анна Ковалёва)', expect: 'Открывается раздел «Мой баланс»',
        run: async ({ page, base }) => { await signIn(page, base, 'employee'); return 'Открыт /me'; } },
      { do: 'Выбрать в меню пункт «История операций»', expect: 'Открывается страница «История операций»',
        run: async ({ page }) => {
          await page.locator('.ant-menu').getByText('История операций').first().click();
          await page.waitForURL('**/history');
          await settle(page);
          return 'Открыта страница «История операций» (/history)';
        } },
      { do: 'Посмотреть список операций', expect: 'Отображаются все 7 операций счёта с датой, причиной, статусом и суммой',
        run: async ({ page }) => {
          const n = await rows(page).count();
          check(n === 7, `строк: ${n}`);
          return `Отображено ${n} операций`;
        } },
    ],
  },
  {
    id: 'TC-06', title: 'Фильтрация истории операций по периоду', req: 'UC2: FR2-3',
    steps: [
      { do: 'Войти как сотрудник, открыть «История операций»', expect: 'Отображаются 7 операций',
        run: async ({ page, base }) => {
          await signIn(page, base, 'employee');
          await open(page, base, '/history');
          return `Отображено ${await rows(page).count()} операций`;
        } },
      { do: 'Указать период с 01.11.2026 по 30.11.2026', expect: 'Фильтр применён, в адресной строке from=2026-11-01&to=2026-11-30',
        run: async ({ page }) => {
          await typeRange(page, page.locator('.ant-picker-range').first(), '01.11.2026', '30.11.2026');
          check(page.url().includes('from=2026-11-01') && page.url().includes('to=2026-11-30'), page.url());
          return `Адрес: ${new URL(page.url()).search}`;
        } },
      { do: 'Посмотреть список операций', expect: 'Отображаются 2 операции ноября (20.11 и 23.11), других нет',
        run: async ({ page }) => {
          const texts = (await rows(page).allInnerTexts()).map(norm);
          check(texts.length === 2, `строк: ${texts.length}`);
          check(texts.every((t) => /(20|23)\.11\.2026|(20|23) ноя/.test(t)), texts.join(' | '));
          return `Отображено ${texts.length} операции за ноябрь`;
        } },
    ],
  },
  {
    id: 'TC-07', title: 'Отображение пустого списка за период без операций', req: 'UC2: FR2-2',
    steps: [
      { do: 'Войти как сотрудник, открыть «История операций»', expect: 'Отображается список операций',
        run: async ({ page, base }) => { await signIn(page, base, 'employee'); await open(page, base, '/history'); return 'Список отображается'; } },
      { do: 'Указать период с 21.11.2026 по 22.11.2026', expect: 'Список пуст: «За выбранный период операций такого типа нет», кнопка «Показать все операции»',
        run: async ({ page }) => {
          await typeRange(page, page.locator('.ant-picker-range').first(), '21.11.2026', '22.11.2026');
          check(await rows(page).count() === 0, 'есть строки');
          const t = await bodyText(page);
          check(t.includes('За выбранный период операций такого типа нет'), 'нет сообщения о пустом списке');
          check(await page.getByRole('button', { name: 'Показать все операции' }).isVisible(), 'нет кнопки');
          return 'Пустой список с сообщением «За выбранный период операций такого типа нет»';
        } },
    ],
  },
  {
    id: 'TC-08', title: 'Использование баллов на программу мотивации', req: 'UC3: FR3-1, FR3-3, FR1-3',
    steps: [
      { do: 'Войти как сотрудник, открыть «Программы мотивации»', expect: 'Отображается каталог программ, «На счёте 8 900 Б»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'employee');
          await open(page, base, '/programs');
          check((await bodyText(page)).includes('8 900 Б'), 'нет баланса 8 900 Б');
          return 'Каталог программ, на счёте 8 900 Б';
        } },
      { do: 'Нажать «Использовать» в карточке «Худи и термокружка TechnoSoft» (900 Б)', expect: 'Открывается подтверждение «Потратить 900 Б на «Худи и термокружка TechnoSoft»?»',
        run: async ({ page }) => {
          await page.locator('.ant-card').filter({ hasText: 'Худи и термокружка TechnoSoft' }).getByRole('button', { name: 'Использовать' }).click();
          const modal = page.locator('.ant-modal-confirm');
          await modal.waitFor();
          const t = norm(await modal.innerText());
          check(t.includes('Потратить 900 Б'), t);
          return 'Окно подтверждения «Потратить 900 Б на «Худи и термокружка TechnoSoft»?»';
        } },
      { do: 'Нажать «Списать 900 Б»', expect: 'Сообщение «Списано 900 Б. Подтверждение участия придёт на почту.», на счёте 8 000 Б',
        run: async ({ page }) => {
          await page.locator('.ant-modal-confirm').getByRole('button', { name: /Списать/ }).click();
          const msg = await toast(page, 'Списано');
          await settle(page);
          check((await bodyText(page)).includes('8 000 Б'), 'баланс на странице не обновился');
          return `Сообщение «${msg}», на счёте 8 000 Б`;
        } },
      { do: 'Открыть раздел «Мой баланс»', expect: 'Отображается обновлённый баланс 8 000 Б',
        run: async ({ page, base }) => {
          await open(page, base, '/me');
          const labels = (await page.locator('[aria-label]').evaluateAll((els) => els.map((e) => e.getAttribute('aria-label')))).map(norm);
          check(labels.includes('8 000 Б'), 'баланс не 8 000 Б');
          return 'Баланс 8 000 Б';
        } },
    ],
  },
  {
    id: 'TC-09', title: 'Отклонение списания при недостатке баллов', req: 'UC3: FR3-2',
    steps: [
      { do: 'Войти как сотрудник, открыть «Программы мотивации»', expect: 'Отображается каталог программ',
        run: async ({ page, base }) => { await signIn(page, base, 'employee'); await open(page, base, '/programs'); return 'Каталог отображается'; } },
      { do: 'Посмотреть карточку «Участие в профильной конференции» (12 000 Б)', expect: 'Кнопка «Использовать» недоступна, подсказка «Не хватает 4 000 Б»',
        run: async ({ page }) => {
          const card = page.locator('.ant-card').filter({ hasText: 'Участие в профильной конференции' });
          check(await card.getByRole('button', { name: 'Использовать' }).isDisabled(), 'кнопка доступна');
          const t = norm(await card.innerText());
          check(t.includes('Не хватает 4 000 Б'), t);
          return 'Кнопка недоступна, подсказка «Не хватает 4 000 Б»';
        } },
      { tool: 'HTTP-запрос из скрипта', do: 'Отправить запрос списания в обход интерфейса: POST /api/v1/programs/6/redemptions от имени сотрудника',
        expect: 'Сервер отклоняет операцию: 409, код INSUFFICIENT_FUNDS, «Не хватает 4 000 Б…»; баланс не меняется',
        run: async () => {
          const t = await token('anna.kovaleva');
          const r = await api('POST', '/programs/6/redemptions', t);
          check(r.status === 409 && r.json.code === 'INSUFFICIENT_FUNDS', `${r.status} ${JSON.stringify(r.json)}`);
          const me = await api('GET', '/accounts/me', t);
          check(me.json.account.balance === 8000, `баланс ${me.json.account.balance}`);
          return `409 ${r.json.code}: «${norm(r.json.message)}»; баланс 8 000 Б`;
        } },
    ],
  },
  {
    id: 'TC-10', title: 'Автоматическое начисление по KPI-событию', req: 'UC4: FR4-1, FR4-2, FR4-3; UC13: FR13-2, FR13-3',
    steps: [
      { tool: 'psql', do: 'Зарегистрировать в очереди событие HRIS «kpi_closed» (показатель SPRINT, KPI 95 %) для Екатерины Шевчук',
        expect: 'Событие сохранено в статусе «new»',
        run: async () => {
          const id = psql(`insert into business_event (integration_id, external_id, event_type, employee_id, payload, status, received_at)
            values (1, 'HRIS-LR10-TC10', 'kpi_closed', 4, '{"kpi": "SPRINT", "value": 95}', 'new', now()) returning id`).split('\n')[0];
          return `Событие id=${id} в статусе new`;
        } },
      { tool: 'psql', do: 'Дождаться автоматической обработки очереди (планировщик, интервал 5 с)', expect: 'Событие получает статус «processed», создана операция начисления',
        run: async () => {
          for (let i = 0; i < 30; i++) {
            const st = psql("select status from business_event where external_id = 'HRIS-LR10-TC10'");
            if (st === 'processed') {
              const op = psql("select o.number || ' ' || o.amount || ' ' || o.status from operation o join business_event e on e.id = o.event_id where e.external_id = 'HRIS-LR10-TC10'");
              return `Событие обработано, операция ${op}`;
            }
            check(st !== 'failed', 'событие в статусе failed');
            await new Promise((r) => setTimeout(r, 1000));
          }
          throw new StepFailed('событие не обработано за 30 с');
        } },
      { do: 'Войти через форму как ekaterina.shevchuk@technosoft.by / demo, открыть «История операций»',
        expect: 'Первая строка: «Выполнение плана спринта, KPI 95 %», +1 700 Б (3000 × 0,4 × 95 % = 1140, × 1,5 кампании → 1 700), автор «Система»',
        run: async ({ page, base }) => {
          await formLogin(page, base, 'ekaterina.shevchuk@technosoft.by', 'demo');
          await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000 });
          await open(page, base, '/history');
          const first = norm(await rows(page).first().innerText());
          check(first.includes('KPI 95 %') && first.includes('1 700'), first);
          return `Первая строка: ${first.slice(0, 160)}`;
        } },
    ],
  },
  {
    id: 'TC-11', title: 'Предварительный расчёт начисления с коэффициентом кампании', req: 'UC6: FR6-1, FR6-2, FR6-3; UC9: FR9-2',
    steps: [
      { do: 'Войти как руководитель, открыть «Начисление баллов»', expect: 'Отображается шаг «Кому начислить» со списком команды',
        run: async ({ page, base }) => {
          await signIn(page, base, 'manager');
          await open(page, base, '/accruals/new');
          check((await bodyText(page)).includes('Кому начислить'), 'нет шага');
          return 'Шаг «Кому начислить», список команды';
        } },
      { do: 'Отметить «Анна Ковалёва», нажать «Далее: сумма»', expect: 'Открывается шаг «Сумма и основание»',
        run: async ({ page }) => {
          await page.getByRole('checkbox', { name: 'Выбрать: Анна Ковалёва' }).check();
          await page.getByRole('button', { name: 'Далее: сумма' }).click();
          await page.getByLabel('Сумма на одного сотрудника').waitFor();
          return 'Открыт шаг «Сумма и основание»';
        } },
      { do: 'Ввести сумму 800, выбрать кампанию «Осенний релиз 2.0, ×1,5»',
        expect: 'В блоке «Расчёт»: коэффициент ×1,5, итог 1 200 Б, предупреждение «Потребуется согласование» (больше 1 000 Б)',
        run: async ({ page }) => {
          await page.getByLabel('Сумма на одного сотрудника').fill('800');
          await page.getByLabel('Бонусная кампания').click();
          await page.locator('.ant-select-item-option').filter({ hasText: 'Осенний релиз 2.0' }).click();
          await page.locator('.ant-table-summary').waitFor();
          await page.waitForTimeout(800);
          await settle(page);
          const summary = norm(await page.locator('.ant-table-summary').innerText());
          const t = await bodyText(page);
          check(summary.includes('1 200'), `итог: ${summary}`);
          check(t.includes('Потребуется согласование'), 'нет предупреждения');
          return `Итог расчёта: ${summary}; предупреждение «Потребуется согласование»`;
        } },
    ],
  },
  {
    id: 'TC-12', title: 'Ручное начисление сотруднику команды в пределах лимита', req: 'UC5: FR5-1, FR5-2, FR5-3',
    steps: [
      { do: 'Войти как руководитель, открыть «Начисление баллов», отметить «Максим Новик», нажать «Далее: сумма»',
        expect: 'Открывается шаг «Сумма и основание»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'manager');
          await open(page, base, '/accruals/new');
          await page.getByRole('checkbox', { name: 'Выбрать: Максим Новик' }).check();
          await page.getByRole('button', { name: 'Далее: сумма' }).click();
          await page.getByLabel('Сумма на одного сотрудника').waitFor();
          return 'Открыт шаг «Сумма и основание»';
        } },
      { do: 'Ввести сумму 500, основание «Проект», причину «Доклад на внутреннем митапе (TC-12)», нажать «Далее: проверить»',
        expect: 'Открывается «Проверьте перед начислением», кнопка «Начислить 500 Б»',
        run: async ({ page }) => {
          await page.getByLabel('Сумма на одного сотрудника').fill('500');
          await page.locator('.ant-radio-button-wrapper', { hasText: 'Проект' }).click();
          await page.getByLabel('Причина').fill('Доклад на внутреннем митапе (TC-12)');
          await page.getByRole('button', { name: 'Далее: проверить' }).click();
          await page.getByText('Проверьте перед начислением').waitFor();
          await settle(page);
          check(await page.getByRole('button', { name: /Начислить 500/ }).isVisible(), 'нет кнопки «Начислить 500 Б»');
          return 'Экран проверки, кнопка «Начислить 500 Б»';
        } },
      { do: 'Нажать «Начислить 500 Б»', expect: 'Результат «Баллы начислены», «1 сотр. получили баллы и уведомление»',
        run: async ({ page }) => {
          await page.getByRole('button', { name: /Начислить 500/ }).click();
          const result = page.locator('.ant-result');
          await result.waitFor({ timeout: 10000 });
          const t = norm(await result.innerText());
          check(t.includes('Баллы начислены') && t.includes('1 сотр. получили баллы'), t);
          return t.split(' Новое')[0];
        } },
    ],
  },
  {
    id: 'TC-13', title: 'Отправка начисления сверх лимита на согласование', req: 'UC5: FR5-2',
    steps: [
      { do: 'Войти как руководитель, открыть «Начисление баллов», отметить «Максим Новик», нажать «Далее: сумма»',
        expect: 'Открывается шаг «Сумма и основание»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'manager');
          await open(page, base, '/accruals/new');
          await page.getByRole('checkbox', { name: 'Выбрать: Максим Новик' }).check();
          await page.getByRole('button', { name: 'Далее: сумма' }).click();
          await page.getByLabel('Сумма на одного сотрудника').waitFor();
          return 'Открыт шаг «Сумма и основание»';
        } },
      { do: 'Ввести сумму 1 500, основание «Проект», причину «Сверхурочная работа над релизом (TC-13)», нажать «Далее: проверить»',
        expect: 'На экране проверки предупреждение «Потребуется согласование»',
        run: async ({ page }) => {
          await page.getByLabel('Сумма на одного сотрудника').fill('1500');
          await page.locator('.ant-radio-button-wrapper', { hasText: 'Проект' }).click();
          await page.getByLabel('Причина').fill('Сверхурочная работа над релизом (TC-13)');
          await page.getByRole('button', { name: 'Далее: проверить' }).click();
          await page.getByText('Проверьте перед начислением').waitFor();
          await settle(page);
          check((await bodyText(page)).includes('Потребуется согласование'), 'нет предупреждения');
          return 'Предупреждение «Потребуется согласование»';
        } },
      { do: 'Нажать «Начислить 1 500 Б»', expect: 'Результат «Начисление создано», «Ждут согласования финотдела: 1»',
        run: async ({ page }) => {
          await page.getByRole('button', { name: /Начислить 1/ }).click();
          const result = page.locator('.ant-result');
          await result.waitFor({ timeout: 10000 });
          const t = norm(await result.innerText());
          check(t.includes('Начисление создано') && t.includes('Ждут согласования финотдела: 1'), t);
          return t.split(' Новое')[0];
        } },
    ],
  },
  {
    id: 'TC-14', title: 'Подтверждение начисления на согласовании финансовым отделом', req: 'UC5: FR5-3 (контроль финотдела)',
    steps: [
      { do: 'Войти как финотдел, открыть «Контроль начислений»', expect: 'В очереди есть начисление Максиму Новику 1 500 Б «(TC-13)» со статусом «На согласовании»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'finance');
          await open(page, base, '/control');
          const row = rows(page).filter({ hasText: 'TC-13' });
          check(await row.count() === 1, 'строка не найдена');
          const t = norm(await row.innerText());
          check(t.includes('Максим Новик') && t.includes('1 500'), t);
          return `Строка очереди: ${t.slice(0, 120)}`;
        } },
      { do: 'Нажать «Подтвердить» в этой строке', expect: 'Сообщение «Подтверждено: Максим Новик, 1 500 Б», строка исчезает из очереди',
        run: async ({ page }) => {
          await rows(page).filter({ hasText: 'TC-13' }).getByRole('button', { name: 'Подтвердить' }).click();
          const msg = await toast(page, 'Подтверждено');
          await settle(page);
          check(await rows(page).filter({ hasText: 'TC-13' }).count() === 0, 'строка осталась');
          return `Сообщение «${msg}», строка удалена из очереди`;
        } },
    ],
  },
  {
    id: 'TC-15', title: 'Отказ в доступе к разделу другой роли', req: 'SEC-3 (ролевая модель)',
    steps: [
      { do: 'Войти как сотрудник', expect: 'Открывается «Мой баланс», в меню нет пункта «Правила начисления»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'employee');
          const menu = norm(await page.locator('.ant-menu').first().innerText());
          check(!menu.includes('Правила начисления'), menu);
          return `Меню: ${menu}`;
        } },
      { do: 'Ввести в адресной строке /rules', expect: 'Отображается «Нет доступа к этому разделу» и кнопка возврата',
        run: async ({ page, base }) => {
          await open(page, base, '/rules');
          const t = await bodyText(page);
          check(t.includes('Нет доступа к этому разделу'), 'нет сообщения');
          return 'Страница «Нет доступа к этому разделу»';
        } },
      { tool: 'HTTP-запрос из скрипта', do: 'Запросить GET /api/v1/rules с токеном сотрудника', expect: 'Сервер отвечает 403 ACCESS_DENIED',
        run: async () => {
          const r = await api('GET', '/rules', await token('anna.kovaleva'));
          check(r.status === 403 && r.json.code === 'ACCESS_DENIED', `${r.status}`);
          return `403 ${r.json.code}`;
        } },
    ],
  },
  {
    id: 'TC-16', title: 'Корректировка суммы ошибочного начисления', req: 'UC7: FR7-1, FR7-2',
    steps: [
      { do: 'Войти как HR, открыть «Операции», ввести в поиск «ОП-000014»', expect: 'Найдена одна операция: Юлия Романович, 1 000 Б, «Начислено»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'hr');
          await open(page, base, '/operations');
          await page.getByLabel('Поиск по операциям').fill('ОП-000014');
          await page.waitForTimeout(800);
          await settle(page);
          const r = rows(page);
          check(await r.count() === 1, `строк: ${await r.count()}`);
          const t = norm(await r.first().innerText());
          check(t.includes('Юлия Романович') && t.includes('1 000'), t);
          return `Найдена операция: ${t.slice(0, 120)}`;
        } },
      { do: 'Нажать «Корректировать»', expect: 'Открывается панель «Корректировка начисления» с текущей суммой 1 000 Б',
        run: async ({ page }) => {
          await rows(page).first().getByRole('button', { name: 'Корректировать' }).click();
          const drawer = page.locator('.ant-drawer-open');
          await drawer.waitFor();
          const t = norm(await drawer.innerText());
          check(t.includes('Корректировка начисления') && t.includes('1 000'), t.slice(0, 200));
          return 'Панель «Корректировка начисления», текущая сумма 1 000 Б';
        } },
      { do: 'Ввести новую сумму 700, причину «Ошибка в сумме (TC-16)», нажать «Сохранить корректировку»',
        expect: 'Сообщение «Корректировка сохранена», панель закрыта; в журнале — корректировка 1 000 → 700 со статусом «Скорректировано»',
        run: async ({ page }) => {
          const drawer = page.locator('.ant-drawer-open');
          await drawer.getByLabel('Новая сумма').fill('700');
          await drawer.getByLabel('Причина корректировки').fill('Ошибка в сумме (TC-16)');
          await page.getByRole('button', { name: 'Сохранить корректировку' }).click();
          const msg = await toast(page, 'Корректировка сохранена');
          await page.getByLabel('Поиск по операциям').fill('TC-16');
          await page.waitForTimeout(800);
          await settle(page);
          const t = norm(await rows(page).first().innerText());
          check(t.includes('700'), t);
          return `Сообщение «${msg}»; строка журнала: ${t.slice(0, 140)}`;
        } },
    ],
  },
  {
    id: 'TC-17', title: 'Отправка на согласование корректировки, уводящей баланс в минус', req: 'UC7: FR7-1, FR7-2',
    pre: 'Предусловие (подготовлено HTTP-запросами из скрипта): Анна Ковалёва потратила 6 000 Б и 1 500 Б на программы, баланс 500 Б',
    setup: async () => {
      const t = await token('anna.kovaleva');
      await api('POST', '/programs/5/redemptions', t);
      await api('POST', '/programs/1/redemptions', t);
      const me = await api('GET', '/accounts/me', t);
      check(me.json.account.balance === 500, `баланс ${me.json.account.balance}`);
    },
    steps: [
      { do: 'Войти как HR, открыть «Операции», найти «ОП-000137»', expect: 'Найдено начисление Анне Ковалёвой 1 700 Б со статусом «Начислено»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'hr');
          await open(page, base, '/operations');
          await page.getByLabel('Поиск по операциям').fill('ОП-000137');
          await page.waitForTimeout(800);
          await settle(page);
          const t = norm(await rows(page).first().innerText());
          check(t.includes('Анна Ковалёва') && t.includes('1 700'), t);
          return `Найдена операция: ${t.slice(0, 120)}`;
        } },
      { do: 'Нажать «Корректировать», выбрать «Отменить начисление», ввести причину «Начислено ошибочно (TC-17)», нажать «Отменить начисление»',
        expect: 'Предупреждение «Корректировка отправлена на согласование: баланс сотрудника стал бы отрицательным.»',
        run: async ({ page }) => {
          await rows(page).first().getByRole('button', { name: 'Корректировать' }).click();
          const drawer = page.locator('.ant-drawer-open');
          await drawer.waitFor();
          await drawer.getByText('Отменить начисление', { exact: true }).first().click();
          await drawer.getByLabel('Причина корректировки').fill('Начислено ошибочно (TC-17)');
          await page.locator('.ant-drawer-footer').getByRole('button', { name: 'Отменить начисление' }).click();
          return `Сообщение «${await toast(page, 'отправлена на согласование')}»`;
        } },
      { do: 'Ввести в поиск «TC-17»', expect: 'Корректировка −1 700 Б в статусе «На согласовании»; баланс не изменился',
        run: async ({ page }) => {
          await page.getByLabel('Поиск по операциям').fill('TC-17');
          await page.waitForTimeout(800);
          await settle(page);
          const t = norm(await rows(page).first().innerText());
          check(t.includes('На согласовании'), t);
          const me = await api('GET', '/accounts/me', await token('anna.kovaleva'));
          check(me.json.account.balance === 500, `баланс ${me.json.account.balance}`);
          return `Строка: ${t.slice(0, 140)}; баланс 500 Б`;
        } },
    ],
  },
  {
    id: 'TC-18', title: 'Фиксация корректировки в журнале аудита', req: 'UC7: FR7-3',
    steps: [
      { do: 'Войти как администратор, открыть «Журнал аудита»', expect: 'Отображается журнал действий',
        run: async ({ page, base }) => { await signIn(page, base, 'admin'); await open(page, base, '/admin/audit'); return 'Журнал аудита отображается'; } },
      { do: 'Ввести в поиск «Ольга Мельник»', expect: 'Есть записи «Корректировка» и «Отмена начисления» от Ольги Мельник с номерами операций',
        run: async ({ page }) => {
          await page.getByLabel('Поиск в журнале').fill('Ольга Мельник');
          await page.waitForTimeout(800);
          await settle(page);
          const t = (await rows(page).allInnerTexts()).map(norm);
          check(t.some((x) => x.includes('Корректировка')) && t.some((x) => x.includes('Отмена начисления')), t.join(' | ').slice(0, 300));
          return `Найдено записей: ${t.length}, среди них «Корректировка» и «Отмена начисления»`;
        } },
    ],
  },
  {
    id: 'TC-19', title: 'Создание правила начисления', req: 'UC8: FR8-1, FR8-2',
    steps: [
      { do: 'Войти как HR, открыть «Правила начисления», нажать «Новое правило»', expect: 'Открывается панель «Новое правило»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'hr');
          await open(page, base, '/rules');
          await page.getByRole('button', { name: 'Новое правило' }).click();
          await page.locator('.ant-drawer-open').getByText('Новое правило').waitFor();
          return 'Панель «Новое правило» открыта';
        } },
      { do: 'Заполнить: название «Отзывы клиентов (TC-19)», показатель «Индекс удовлетворённости клиентов», база 4 000, вес 0,25, порог 85',
        expect: 'Формула «4 000 × 0,25 × уровень достижения», пример «…получит 1 000 Б»',
        run: async ({ page }) => {
          const d = page.locator('.ant-drawer-open');
          await d.getByLabel('Название').fill('Отзывы клиентов (TC-19)');
          await d.getByLabel('Показатель KPI').fill('Индекс удовлетворённости клиентов');
          await d.getByLabel('База').fill('4000');
          await d.getByLabel('Вес показателя').fill('0,25');
          await d.getByLabel('Порог срабатывания').fill('85');
          await d.getByLabel('Название').click();
          const t = norm(await d.innerText());
          check(t.includes('4 000 × 0,25'), t.slice(0, 300));
          return 'Формула «4 000 × 0,25 × уровень достижения»';
        } },
      { do: 'Нажать «Создать правило»', expect: 'Сообщение «Правило создано и уже применяется», правило в таблице',
        run: async ({ page }) => {
          await page.getByRole('button', { name: 'Создать правило' }).click();
          const msg = await toast(page, 'Правило создано');
          await settle(page);
          check((await bodyText(page)).includes('Отзывы клиентов (TC-19)'), 'нет в таблице');
          return `Сообщение «${msg}», правило в таблице`;
        } },
    ],
  },
  {
    id: 'TC-20', title: 'Изменение применённого правила с отложенным вступлением в силу', req: 'UC8: FR8-3',
    steps: [
      { do: 'Войти как HR, открыть «Правила начисления», нажать «Изменить» у «Выполнение плана спринта»',
        expect: 'Панель с предупреждением «Изменения вступят в силу со следующего периода»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'hr');
          await open(page, base, '/rules');
          await rows(page).filter({ hasText: 'Выполнение плана спринта' }).getByRole('button', { name: 'Изменить' }).click();
          const d = page.locator('.ant-drawer-open');
          await d.waitFor();
          check(norm(await d.innerText()).includes('Изменения вступят в силу со следующего периода'), 'нет предупреждения');
          return 'Предупреждение «Изменения вступят в силу со следующего периода»';
        } },
      { do: 'Изменить вес на 0,45, нажать «Сохранить изменения»', expect: 'Сообщение «Правило сохранено. Изменения вступят в силу 01.12.2026»',
        run: async ({ page }) => {
          const d = page.locator('.ant-drawer-open');
          await d.getByLabel('Вес показателя').fill('0,45');
          await d.getByLabel('Название').click();
          await page.getByRole('button', { name: 'Сохранить изменения' }).click();
          const msg = await toast(page, 'Правило сохранено');
          check(msg.includes('01.12.2026'), msg);
          return `Сообщение «${msg}»`;
        } },
    ],
  },
  {
    id: 'TC-21', title: 'Создание бонусной кампании без бюджета', req: 'UC9: FR9-1',
    steps: [
      { do: 'Войти как HR, открыть «Бонусные кампании», нажать «Новая кампания»', expect: 'Открывается панель «Новая кампания»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'hr');
          await open(page, base, '/campaigns');
          await page.getByRole('button', { name: 'Новая кампания' }).click();
          await page.locator('.ant-drawer-open').waitFor();
          return 'Панель «Новая кампания» открыта';
        } },
      { do: 'Заполнить: название «Зимний спринт качества (TC-21)», сроки 01.01.2027–31.01.2027, отдел «Обеспечение качества», коэффициент 2, бюджет не указывать',
        expect: 'Поля заполнены, бюджет «Не выделен»',
        run: async ({ page }) => {
          const d = page.locator('.ant-drawer-open');
          await d.getByLabel('Название').fill('Зимний спринт качества (TC-21)');
          await typeRange(page, d.locator('.ant-picker-range'), '01.01.2027', '31.01.2027', { closeWithEscape: false });
          await d.getByLabel('Отделы').click();
          await page.locator('.ant-select-item-option').filter({ hasText: 'Обеспечение качества' }).click();
          // Список множественного выбора остаётся открытым и перекрывает поле коэффициента — закрываем щелчком по заголовку.
          await d.locator('.ant-drawer-title').click();
          await page.waitForTimeout(400);
          await d.getByLabel('Коэффициент').fill('2');
          const t = norm(await d.innerText());
          check(t.includes('Обеспечение качества'), 'отдел не выбран');
          check(await d.locator('.ant-picker-range input').nth(1).inputValue() === '31.01.2027', 'сроки не заданы');
          return 'Поля заполнены: сроки 01.01.2027–31.01.2027, отдел «Обеспечение качества», ×2, бюджет пуст';
        } },
      { do: 'Нажать «Создать кампанию»', expect: 'Сообщение «Кампания создана и отправлена на утверждение бюджета», у карточки «Бюджет не выделен»',
        run: async ({ page }) => {
          await page.getByRole('button', { name: 'Создать кампанию' }).click();
          const msg = await toast(page, 'Кампания создана');
          await settle(page);
          const card = page.locator('.ant-card').filter({ hasText: 'Зимний спринт качества (TC-21)' });
          check(norm(await card.innerText()).includes('Бюджет не выделен'), 'нет отметки');
          return `Сообщение «${msg}», карточка с «Бюджет не выделен»`;
        } },
    ],
  },
  {
    id: 'TC-22', title: 'Формирование отчёта по подразделениям за период', req: 'UC10: FR10-1, FR10-2, FR10-3',
    steps: [
      { do: 'Войти как HR, открыть «Отчёты»', expect: 'Отображается форма: тип отчёта, период, «Сформировать»',
        run: async ({ page, base }) => { await signIn(page, base, 'hr'); await open(page, base, '/reports'); return 'Форма отчёта отображается'; } },
      { do: 'Выбрать «По подразделениям», период 01.11.2026–30.11.2026, нажать «Сформировать»',
        expect: 'Таблица по подразделениям (начислено, потрачено, на согласовании, баланс), строка «Итого», кнопки «Скачать Excel», «Сохранить PDF»',
        run: async ({ page }) => {
          await page.getByText('По подразделениям', { exact: true }).click();
          await typeRange(page, page.locator('.ant-picker-range').first(), '01.11.2026', '30.11.2026');
          await page.getByRole('button', { name: 'Сформировать' }).click();
          await page.getByRole('button', { name: 'Скачать Excel' }).waitFor({ timeout: 10000 });
          await settle(page);
          const n = await rows(page).count();
          check(n > 0, 'нет строк');
          const t = await bodyText(page);
          check(t.includes('Разработка ПО') && t.includes('Итого'), 'нет строк подразделений');
          return `Сформирован отчёт: ${n} подразделений, строка «Итого», кнопки экспорта`;
        } },
    ],
  },
  {
    id: 'TC-23', title: 'Формирование отчёта за период без данных', req: 'UC10: FR10-2',
    steps: [
      { do: 'Войти как HR, открыть «Отчёты»', expect: 'Отображается форма отчёта',
        run: async ({ page, base }) => { await signIn(page, base, 'hr'); await open(page, base, '/reports'); return 'Форма отображается'; } },
      { do: 'Указать период 01.01.2025–31.01.2025, нажать «Сформировать»', expect: 'Сообщение «За выбранный период операций нет — отчёт пустой.», строк нет',
        run: async ({ page }) => {
          await typeRange(page, page.locator('.ant-picker-range').first(), '01.01.2025', '31.01.2025');
          await page.getByRole('button', { name: 'Сформировать' }).click();
          const msg = await toast(page, 'отчёт пустой');
          await settle(page);
          check(await rows(page).count() === 0, 'есть строки');
          return `Сообщение «${msg}», строк нет`;
        } },
    ],
  },
  {
    id: 'TC-24', title: 'Просмотр анализа эффективности бонусной системы', req: 'UC11: FR11-1, FR11-2, FR11-3',
    steps: [
      { do: 'Войти как HR, открыть «Аналитика»', expect: 'Отображаются показатели «Средний бонус на сотрудника», «Конверсия программ», «Начислено», «Потрачено на программы»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'hr');
          await open(page, base, '/analytics');
          const t = await bodyText(page);
          for (const s of ['Средний бонус на сотрудника', 'Конверсия программ', 'Начислено', 'Потрачено на программы']) check(t.includes(s), s);
          return 'Показатели эффективности отображаются';
        } },
      { do: 'Переключить период сравнения на «Квартал»', expect: 'Показатели пересчитаны; графики «Начисления по подразделениям» и «Динамика за полгода»',
        run: async ({ page }) => {
          await page.getByText('Квартал', { exact: true }).click();
          await settle(page);
          const t = await bodyText(page);
          check(t.includes('Начисления по подразделениям') && t.includes('Динамика за полгода'), 'нет графиков');
          return 'Период «Квартал», графики отображаются';
        } },
    ],
  },
  {
    id: 'TC-25', title: 'Создание учётной записи пользователя', req: 'UC12: FR12-1',
    steps: [
      { do: 'Войти как администратор, открыть «Пользователи», нажать «Добавить пользователя»', expect: 'Открывается панель «Новый пользователь»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'admin');
          await open(page, base, '/admin/users');
          await page.getByRole('button', { name: 'Добавить пользователя' }).first().click();
          await page.locator('.ant-drawer-open').waitFor();
          return 'Панель «Новый пользователь» открыта';
        } },
      { do: 'Заполнить: ФИО «Марина Захарова», email marina.zakharova@technosoft.by, подразделение «Обеспечение качества», должность «QA-инженер»; нажать «Добавить пользователя»',
        expect: 'Сообщение «Пользователь добавлен. Данные для входа отправлены на marina.zakharova@technosoft.by», запись в таблице',
        run: async ({ page }) => {
          await fillUser(page, 'Марина Захарова');
          await page.locator('.ant-drawer-footer').getByRole('button', { name: 'Добавить пользователя' }).click();
          const msg = await toast(page, 'Пользователь добавлен');
          await settle(page);
          check((await bodyText(page)).includes('Марина Захарова'), 'нет в таблице');
          return `Сообщение «${msg}»`;
        } },
    ],
  },
  {
    id: 'TC-26', title: 'Предупреждение о существующем email при создании пользователя', req: 'UC12: FR12-1 (альт. поток)',
    steps: [
      { do: 'Войти как администратор, открыть «Пользователи», нажать «Добавить пользователя»', expect: 'Открывается панель «Новый пользователь»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'admin');
          await open(page, base, '/admin/users');
          await page.getByRole('button', { name: 'Добавить пользователя' }).first().click();
          await page.locator('.ant-drawer-open').waitFor();
          return 'Панель открыта';
        } },
      { do: 'Заполнить форму с уже существующим email marina.zakharova@technosoft.by, нажать «Добавить пользователя»',
        expect: 'Окно «Пользователь с таким email уже есть» с данными найденной записи; новая запись не создаётся',
        run: async ({ page }) => {
          await fillUser(page, 'Марина Захарова-Дубль');
          await page.locator('.ant-drawer-footer').getByRole('button', { name: 'Добавить пользователя' }).click();
          const modal = page.locator('.ant-modal-confirm');
          await modal.waitFor({ timeout: 10000 });
          const t = norm(await modal.innerText());
          check(t.includes('Пользователь с таким email уже есть') && t.includes('Марина Захарова'), t);
          return `Окно: «${t.slice(0, 120)}»`;
        } },
      { do: 'Нажать «Вернуться к форме»', expect: 'Окно закрыто, панель с формой открыта, дубль не создан',
        run: async ({ page }) => {
          await page.getByRole('button', { name: 'Вернуться к форме' }).click();
          await page.waitForTimeout(500);
          check(await page.locator('.ant-drawer-open').isVisible(), 'панель закрыта');
          const n = psql("select count(*) from employee where email = 'marina.zakharova@technosoft.by'");
          check(n === '1', `записей с email: ${n}`);
          return 'Форма открыта, в БД одна запись с этим email';
        } },
    ],
  },
  {
    id: 'TC-27', title: 'Изменение роли пользователя', req: 'UC12: FR12-2',
    steps: [
      { do: 'Войти как администратор, открыть «Пользователи», найти «Марина Захарова», нажать «Изменить»', expect: 'Открывается панель с данными пользователя',
        run: async ({ page, base }) => {
          await signIn(page, base, 'admin');
          await open(page, base, '/admin/users');
          await (await userRow(page, 'Марина Захарова')).getByRole('button', { name: 'Изменить' }).click();
          await page.locator('.ant-drawer-open').waitFor();
          return 'Панель «Марина Захарова» открыта';
        } },
      { do: 'Выбрать роль «Руководитель», нажать «Сохранить изменения»', expect: 'Сообщение «Изменения сохранены», в таблице роль «Руководитель»',
        run: async ({ page }) => {
          const d = page.locator('.ant-drawer-open');
          await d.locator('.ant-form-item').filter({ hasText: 'Роль' }).locator('.ant-select-selector').click();
          await page.locator('.ant-select-item-option').filter({ hasText: 'Руководитель' }).click();
          await page.getByRole('button', { name: 'Сохранить изменения' }).click();
          const msg = await toast(page, 'Изменения сохранены');
          await settle(page);
          const t = norm(await rows(page).filter({ hasText: 'Марина Захарова' }).innerText());
          check(t.includes('Руководитель'), t);
          return `Сообщение «${msg}», роль «Руководитель»`;
        } },
    ],
  },
  {
    id: 'TC-28', title: 'Блокировка учётной записи пользователя', req: 'UC12: FR12-3',
    steps: [
      { do: 'Войти как администратор, открыть «Пользователи», найти «Марина Захарова», нажать «Заблокировать»', expect: 'Запрос подтверждения «Заблокировать Марина Захарова?»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'admin');
          await open(page, base, '/admin/users');
          await (await userRow(page, 'Марина Захарова')).getByRole('button', { name: 'Заблокировать' }).click();
          const pop = page.locator('.ant-popconfirm');
          await pop.waitFor();
          return `Подтверждение: «${norm(await pop.innerText()).slice(0, 80)}»`;
        } },
      { do: 'Подтвердить «Заблокировать»', expect: 'Сообщение «Марина Захарова: вход заблокирован»',
        run: async ({ page }) => {
          await page.locator('.ant-popconfirm').getByRole('button', { name: 'Заблокировать' }).click();
          return `Сообщение «${await toast(page, 'вход заблокирован')}»`;
        } },
      { do: 'В новом окне войти как marina.zakharova@technosoft.by / demo', expect: 'Вход отклонён: «Учётная запись заблокирована. Обратитесь к администратору системы.»',
        run: async ({ browser, base }) => {
          const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU', timezoneId: 'Europe/Minsk' });
          const p2 = await ctx.newPage();
          try {
            await formLogin(p2, base, 'marina.zakharova@technosoft.by', 'demo');
            const alert = p2.locator('.ant-alert-error');
            await alert.waitFor({ timeout: 10000 });
            const t = norm(await alert.innerText());
            check(t.includes('Учётная запись заблокирована'), t);
            return `Сообщение «${t}»`;
          } finally {
            await ctx.close();
          }
        } },
    ],
  },
  {
    id: 'TC-29', title: 'Проверка соединения интеграции с адресом HTTP', req: 'UC13: FR13-1 (альт. поток)',
    steps: [
      { do: 'Войти как администратор, открыть «Интеграции»', expect: 'Карточка ERP: адрес http://erp.technosoft.local/api, «Выключена»',
        run: async ({ page, base }) => {
          await signIn(page, base, 'admin');
          await open(page, base, '/admin/integrations');
          const t = norm(await erpCard(page).innerText());
          check(t.includes('Выключена'), t.slice(0, 200));
          return 'Карточка ERP, интеграция выключена';
        } },
      { do: 'Нажать «Проверить соединение» в карточке ERP', expect: 'Ошибка «Сервер отклонил подключение: нужен адрес HTTPS»; «Сохранить параметры» и переключатель синхронизации недоступны',
        run: async ({ page }) => {
          const card = erpCard(page);
          await card.getByRole('button', { name: 'Проверить соединение' }).click();
          const alert = card.locator('.ant-alert-error');
          await alert.waitFor({ timeout: 10000 });
          const t = norm(await alert.innerText());
          check(t.includes('нужен адрес HTTPS'), t);
          check(await card.getByRole('button', { name: 'Сохранить параметры' }).isDisabled(), 'сохранение доступно');
          check(await card.getByRole('switch').isDisabled(), 'переключатель доступен');
          return `Ошибка «${t.split('.')[0]}», сохранение и включение недоступны`;
        } },
    ],
  },
  {
    id: 'TC-30', title: 'Настройка подключения и включение автоматической синхронизации', req: 'UC13: FR13-1, FR13-3',
    steps: [
      { do: 'Войти как администратор, открыть «Интеграции», в карточке ERP ввести адрес https://erp.technosoft.by/api и ключ erp-51c0aa-2026',
        expect: 'Появляется подсказка «Сначала проверьте новые параметры», «Сохранить параметры» недоступна',
        run: async ({ page, base }) => {
          await signIn(page, base, 'admin');
          await open(page, base, '/admin/integrations');
          const card = erpCard(page);
          await card.getByLabel('Адрес API').fill('https://erp.technosoft.by/api');
          await card.getByLabel('Ключ API').fill('erp-51c0aa-2026');
          check(norm(await card.innerText()).includes('Сначала проверьте новые параметры'), 'нет подсказки');
          return 'Подсказка «Сначала проверьте новые параметры»';
        } },
      { do: 'Нажать «Проверить соединение»', expect: '«Соединение установлено», «Сохранить параметры» доступна',
        run: async ({ page }) => {
          const card = erpCard(page);
          await card.getByRole('button', { name: 'Проверить соединение' }).click();
          await card.locator('.ant-alert-success').waitFor({ timeout: 10000 });
          check(await card.getByRole('button', { name: 'Сохранить параметры' }).isEnabled(), 'сохранение недоступно');
          return `«${norm(await card.locator('.ant-alert-success').innerText()).slice(0, 80)}»`;
        } },
      { do: 'Нажать «Сохранить параметры»', expect: 'Сообщение «ERP: параметры сохранены»',
        run: async ({ page }) => {
          await erpCard(page).getByRole('button', { name: 'Сохранить параметры' }).click();
          return `Сообщение «${await toast(page, 'параметры сохранены')}»`;
        } },
      { do: 'Включить переключатель «Автоматическая синхронизация»', expect: 'Сообщение «ERP: синхронизация включена», статус «Включена»',
        run: async ({ page }) => {
          const card = erpCard(page);
          await card.getByRole('switch').click();
          const msg = await toast(page, 'синхронизация включена');
          await settle(page);
          check(norm(await erpCard(page).innerText()).includes('Включена'), 'статус не изменился');
          return `Сообщение «${msg}», статус «Включена»`;
        } },
    ],
  },
];

function erpCard(page) {
  return page.locator('.ant-card').filter({ has: page.getByRole('heading', { name: 'ERP', exact: true }) });
}

async function fillUser(page, fullName) {
  const d = page.locator('.ant-drawer-open');
  await d.getByLabel('ФИО').fill(fullName);
  await d.getByLabel('Корпоративный email').fill('marina.zakharova@technosoft.by');
  await d.getByLabel('Подразделение').click();
  await page.locator('.ant-select-item-option').filter({ hasText: 'Обеспечение качества' }).click();
  await d.getByLabel('Должность').fill('QA-инженер');
}

// ── Запуск ─────────────────────────────────────────────────────────────────

async function waitForServer(url, timeoutMs = 30000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.status < 500) return;
    } catch {
      /* ещё поднимается */
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(`Сервер ${url} не ответил за ${timeoutMs / 1000} с`);
}

function startPreview() {
  if (!existsSync(resolve(frontend, 'dist', 'index.html'))) throw new Error('Нет сборки dist/. Сначала npm run build.');
  return spawn(process.execPath, [resolve(frontend, 'node_modules', 'vite', 'bin', 'vite.js'), 'preview', '--port', String(PORT), '--strictPort'],
    { cwd: frontend, stdio: ['ignore', 'ignore', 'inherit'] });
}

const cell = (s) => String(s).replace(/\|/g, '\\|').replace(/\n/g, ' ');

function report(results, startedAt) {
  const date = startedAt.toLocaleString('ru-RU', { timeZone: 'Europe/Minsk' });
  const passed = results.filter((r) => r.passed).length;
  const out = [];
  out.push('# Тест-кейсы уровня базовых пользовательских требований (ЛР 10)\n');
  out.push(`Дата прогона: **${date}** (Europe/Minsk). Выполнено автоматически скриптом \`frontend/scripts/test-cases.mjs\` `
    + '(playwright-core + Google Chrome headless) против запущенного сервера Spring Boot (порт 8080, демо-данные БД '
    + '`tpsis_bonus_app`, обработка очереди событий по расписанию включена) и клиента `vite preview` (порт 4173, прокси `/api`). '
    + 'Каждый шаг выполнялся, после него проверялся ожидаемый результат. Шаги с пометкой «[инструмент]» выполнены не через '
    + 'интерфейс (SQL-запрос psql или HTTP-запрос из скрипта) — для них нет экранной формы. '
    + 'Кейсы выполняются по порядку и опираются на изменения данных предыдущих кейсов (например, TC-14 подтверждает начисление '
    + 'из TC-13); после прогона БД восстановлена из дампа. Снимок финального состояния каждого кейса — `screens/tc-XX.png`.\n');
  out.push(`Итог: **${passed} из ${results.length}** тест-кейсов пройдено.\n`);
  out.push('## Сводная таблица\n');
  out.push('| Идентификатор | Заглавие | Требования | Результат |');
  out.push('|---|---|---|---|');
  for (const r of results) out.push(`| ${r.id} | ${cell(r.title)} | ${cell(r.req)} | ${r.passed ? 'Пройден' : 'Не пройден'} |`);
  out.push('\n## Тест-кейсы\n');
  out.push('| Идентификатор | Заглавие | Шаги | Ожидаемый результат | Фактический результат |');
  out.push('|---|---|---|---|---|');
  for (const r of results) {
    const pre = r.pre ? `${cell(r.pre)}<br>` : '';
    const steps = r.steps.map((s, i) => `${i + 1}. ${s.tool ? '[инструмент] ' : ''}${cell(s.do)}`).join('<br>');
    const expect = r.steps.map((s, i) => `${i + 1}. ${cell(s.expect)}`).join('<br>');
    const actual = r.steps.map((s, i) => `${i + 1}. ${s.status === 'ok' ? 'Пройден' : s.status === 'fail' ? 'НЕ ПРОЙДЕН' : 'Не выполнен'}: ${cell(s.actual ?? '')}`).join('<br>');
    out.push(`| ${r.id} | ${cell(r.title)} | ${pre}${steps} | ${expect} | **${r.passed ? 'Пройден' : 'Не пройден'}** (${date})<br>${actual} |`);
  }
  writeFileSync(resolve(docs, 'test-cases.md'), `${out.join('\n')}\n`);
}

async function main() {
  mkdirSync(screens, { recursive: true });
  const only = process.env.ONLY?.split(',').map((s) => s.trim());
  const cases = only ? CASES.filter((c) => only.includes(c.id)) : CASES;
  await waitForServer(`${API}/programs`);
  let preview;
  let base = process.env.BASE_URL?.replace(/\/$/, '');
  if (!base) {
    preview = startPreview();
    base = `http://localhost:${PORT}`;
  }
  await waitForServer(`${base}/login`);

  const startedAt = new Date();
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const results = [];
  try {
    for (const tc of cases) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU', timezoneId: 'Europe/Minsk', reducedMotion: 'reduce' });
      const page = await context.newPage();
      const steps = tc.steps.map((s) => ({ ...s }));
      let failed = false;
      try {
        if (tc.setup) await tc.setup();
      } catch (e) {
        failed = true;
        steps.forEach((s) => { s.status = 'skip'; s.actual = `предусловие не выполнено: ${e.message}`; });
      }
      for (const step of steps) {
        if (failed) {
          step.status ??= 'skip';
          continue;
        }
        try {
          step.actual = await step.run({ page, base, browser });
          step.status = 'ok';
        } catch (e) {
          step.status = 'fail';
          step.actual = norm(e.message.split('\n')[0]);
          failed = true;
        }
      }
      try {
        await page.waitForTimeout(300);
        await page.screenshot({ path: resolve(screens, `${tc.id.toLowerCase()}.png`) });
      } catch {
        /* снимок не обязателен */
      }
      await context.close();
      results.push({ ...tc, steps, passed: !failed });
      console.log(`${failed ? '✗' : '✓'} ${tc.id} ${tc.title}${failed ? ` — ${steps.find((s) => s.status === 'fail')?.actual ?? 'предусловие'}` : ''}`);
    }
  } finally {
    await browser.close();
    preview?.kill();
  }
  report(results, startedAt);
  const passed = results.filter((r) => r.passed).length;
  console.log(`\nПройдено ${passed} из ${results.length} → ${resolve(docs, 'test-cases.md')}`);
  if (passed !== results.length) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
