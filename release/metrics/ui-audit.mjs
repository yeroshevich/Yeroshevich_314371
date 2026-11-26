#!/usr/bin/env node
/**
 * ЛР 11, автоматический обход всех экранов клиента в Google Chrome (клиент раздаёт установленный сервер).
 *  1) Понятность: элементы форм (поля ввода, списки выбора, даты, переключатели, флажки) на всех экранах и в формах,
 *     открываемых кнопками экранов («Новое правило», «Новая кампания», «Добавить пользователя», «Корректировать»,
 *     «Заблокировать», шаг 2 мастера начисления). Видимая подпись — текст <label for>, подпись поля формы
 *     (ant-form-item-label), охватывающий <label> с текстом или aria-labelledby на видимый текст.
 *     Отдельно учитывается доступное имя (aria-label) без видимой подписи и подсказка-placeholder.
 *  2) Адаптируемость: каждый экран при ширине 375, 768, 1024 и 1440 пикселей — нет ли горизонтального переполнения
 *     страницы (document.scrollingElement.scrollWidth > innerWidth).
 *   node release/metrics/ui-audit.mjs       # BASE_URL=http://localhost:8080
 */
import { BASE, chromium, round2, save, nowIso } from './lib.mjs';

const LOGIN = { employee: 'Войти как сотрудник', manager: 'Войти как руководитель', hr: 'Войти как HR',
  finance: 'Войти как финотдел', admin: 'Войти как администратор' };
// Экран → роль, которой он доступен (каждый экран клиента один раз)
const SCREENS = [
  { path: '/login', role: null, title: 'Вход' },
  { path: '/me', role: 'employee', title: 'Мой баланс' },
  { path: '/history', role: 'employee', title: 'История операций' },
  { path: '/programs', role: 'employee', title: 'Программы мотивации' },
  { path: '/team', role: 'manager', title: 'Моя команда' },
  { path: '/accruals/new', role: 'manager', title: 'Начисление баллов' },
  { path: '/control', role: 'finance', title: 'Контроль начислений' },
  { path: '/operations', role: 'hr', title: 'Операции' },
  { path: '/rules', role: 'hr', title: 'Правила начисления' },
  { path: '/campaigns', role: 'hr', title: 'Бонусные кампании' },
  { path: '/reports', role: 'hr', title: 'Отчёты' },
  { path: '/analytics', role: 'hr', title: 'Аналитика' },
  { path: '/admin/users', role: 'admin', title: 'Пользователи' },
  { path: '/admin/integrations', role: 'admin', title: 'Интеграции' },
  { path: '/admin/audit', role: 'admin', title: 'Журнал аудита' },
];
// Формы, открываемые на экранах
const FORMS = {
  '/rules': [{ name: 'Новое правило', open: (p) => p.getByRole('button', { name: 'Новое правило' }).first().click() }],
  '/campaigns': [{ name: 'Новая кампания', open: (p) => p.getByRole('button', { name: 'Новая кампания' }).first().click() }],
  '/admin/users': [{ name: 'Добавить пользователя', open: (p) => p.getByRole('button', { name: 'Добавить пользователя' }).first().click() }],
  '/operations': [{ name: 'Корректировать', open: (p) => p.getByRole('button', { name: 'Корректировать' }).first().click() }],
  '/control': [{ name: 'Заблокировать', open: (p) => p.getByRole('button', { name: 'Заблокировать' }).first().click() }],
  '/accruals/new': [{ name: 'Мастер начисления, шаг 2', open: async (p) => {
    await p.getByRole('checkbox', { name: /^Выбрать: / }).first().check({ force: true });
    await p.getByRole('button', { name: /Далее: сумма/ }).click();
  } }],
};
const WIDTHS = [375, 768, 1024, 1440];

async function settle(page) {
  await page.waitForFunction(() => !document.querySelector('.ant-skeleton, .ant-spin-spinning'), null, { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(400);
}

/** Элементы форм в видимой части документа и их подписи. */
function auditControls() {
  const visible = (el) => {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
  };
  const text = (el) => (el?.innerText ?? el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const sel = 'input:not([type=hidden]):not([type=file]), textarea, select, [role=switch], [role=radio]';
  const out = [];
  for (const el of document.querySelectorAll(sel)) {
    // У флажков и переключателей Ant Design сам <input> скрыт стилем — видимость проверяем по обёртке.
    const box = el.closest('.ant-checkbox-wrapper, .ant-radio-wrapper, .ant-radio-button-wrapper, .ant-switch, .ant-select, .ant-picker') ?? el;
    if (!visible(box)) continue;
    if (el.closest('[aria-hidden=true]')) continue;
    let label = '';
    let how = '';
    if (el.id) {
      const l = document.querySelector(`label[for="${CSS.escape(el.id)}"]`);
      if (l && text(l)) { label = text(l); how = 'label[for]'; }
    }
    if (!label) {
      const item = el.closest('.ant-form-item');
      const l = item?.querySelector(':scope > .ant-row > .ant-form-item-label label, .ant-form-item-label label');
      if (l && text(l)) { label = text(l); how = 'подпись поля формы'; }
    }
    if (!label) {
      const wrap = el.closest('label');
      if (wrap && text(wrap)) { label = text(wrap); how = 'охватывающий label'; }
    }
    if (!label && el.getAttribute('aria-labelledby')) {
      const t = el.getAttribute('aria-labelledby').split(/\s+/).map((id) => document.getElementById(id)).filter((n) => n && visible(n)).map(text).join(' ');
      if (t) { label = t; how = 'aria-labelledby'; }
    }
    const kind = el.getAttribute('role') ?? (el.tagName === 'INPUT' ? `input:${el.type}` : el.tagName.toLowerCase());
    out.push({ kind, visibleLabel: label || null, how: how || null, ariaLabel: el.getAttribute('aria-label'),
      placeholder: el.getAttribute('placeholder') ?? box.querySelector?.('.ant-select-selection-placeholder')?.textContent ?? null,
      inTable: Boolean(el.closest('.ant-table')) });
  }
  return out;
}

const browser = await chromium().launch({ channel: 'chrome', headless: true });
const labelResults = [];
const overflow = [];
try {
  for (const width of WIDTHS) {
    for (const role of [null, 'employee', 'manager', 'finance', 'hr', 'admin']) {
      const screens = SCREENS.filter((s) => s.role === role);
      if (!screens.length) continue;
      const context = await browser.newContext({ viewport: { width, height: 900 }, locale: 'ru-RU', timezoneId: 'Europe/Minsk', reducedMotion: 'reduce' });
      const page = await context.newPage();
      if (role) {
        await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
        await page.getByRole('button', { name: LOGIN[role] }).click();
        await page.waitForURL((u) => !u.pathname.startsWith('/login'));
      }
      for (const s of screens) {
        await page.goto(`${BASE}${s.path}`, { waitUntil: 'networkidle' });
        await settle(page);
        const m = await page.evaluate(() => ({ scrollWidth: document.scrollingElement.scrollWidth, innerWidth: window.innerWidth }));
        overflow.push({ screen: s.title, path: s.path, width, ...m, ok: m.scrollWidth <= m.innerWidth });
        if (width === 1440) {
          labelResults.push({ screen: s.title, form: null, controls: await page.evaluate(auditControls) });
          for (const f of FORMS[s.path] ?? []) {
            try {
              await f.open(page);
              await page.locator('.ant-drawer-open, .ant-modal-wrap:not([style*="display: none"]), .ant-steps-item-active').first().waitFor({ timeout: 5000 });
              await settle(page);
              labelResults.push({ screen: s.title, form: f.name, controls: await page.evaluate(auditControls) });
            } catch (e) {
              labelResults.push({ screen: s.title, form: f.name, error: e.message.split('\n')[0], controls: [] });
            }
            await page.goto(`${BASE}${s.path}`, { waitUntil: 'networkidle' });
            await settle(page);
          }
        }
      }
      await context.close();
    }
  }
} finally {
  await browser.close();
}

// Формы, открытые поверх экрана, содержат и элементы самого экрана — их не считаем повторно.
const key = (c) => `${c.kind}|${c.visibleLabel}|${c.ariaLabel}|${c.placeholder}`;
const flat = [];
for (const r of labelResults) {
  const base = r.form ? labelResults.find((x) => x.screen === r.screen && !x.form) : null;
  const baseKeys = new Map();
  for (const c of base?.controls ?? []) baseKeys.set(key(c), (baseKeys.get(key(c)) ?? 0) + 1);
  for (const c of r.controls) {
    const k = key(c);
    if (r.form && baseKeys.get(k)) { baseKeys.set(k, baseKeys.get(k) - 1); continue; }
    flat.push({ screen: r.screen, form: r.form, ...c });
  }
}
const labelled = flat.filter((c) => c.visibleLabel);
const named = flat.filter((c) => c.visibleLabel || c.ariaLabel);
const pairsOk = overflow.filter((o) => o.ok);
const summary = {
  date: nowIso(), base: BASE, screens: SCREENS.length, formsOpened: labelResults.filter((r) => r.form && !r.error).length,
  formErrors: labelResults.filter((r) => r.error).map((r) => `${r.screen} / ${r.form}: ${r.error}`),
  controls: flat.length, withVisibleLabel: labelled.length, withAccessibleNameOnly: named.length - labelled.length,
  X_understandability: round2(labelled.length / flat.length),
  withoutVisibleLabel: flat.filter((c) => !c.visibleLabel).map((c) => ({ screen: c.screen, form: c.form, kind: c.kind, ariaLabel: c.ariaLabel, placeholder: c.placeholder, inTable: c.inTable })),
  overflowPairs: overflow.length, overflowOk: pairsOk.length, X_adaptability: round2(pairsOk.length / overflow.length),
  overflowFailures: overflow.filter((o) => !o.ok),
};
save('ui-audit.json', { summary, controls: flat, overflow });
console.log(JSON.stringify(summary, null, 2));
