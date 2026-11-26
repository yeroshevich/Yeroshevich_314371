#!/usr/bin/env node
/**
 * ЛР 11, устойчивость к ошибке: некорректные запросы к REST API (неверный JSON, неверные типы, отсутствующие поля,
 * слишком большие значения, отрицательные и нулевые суммы, чужие и несуществующие идентификаторы, неверный формат).
 * Запрос отклонён корректно, если: ответ 4xx (не 2xx и не 5xx) в едином формате ошибки, и данные не изменились
 * (число операций, сумма балансов, число пользователей, правил и кампаний до и после прогона совпадают).
 *   node release/metrics/robustness.mjs      # BASE_URL=http://localhost:8080
 */
import { execFileSync } from 'node:child_process';
import { BASE, http, isApiError, login, round2, save, nowIso } from './lib.mjs';

const DB = process.env.PGDATABASE ?? 'technosoft_bonus';
const psql = (sql) => execFileSync('psql', ['-d', DB, '-Atc', sql], { encoding: 'utf8' }).trim();
const snapshot = () => psql(`select concat_ws(' ', (select count(*) from operation), (select sum(balance) from bonus_account),
  (select count(*) from app_user), (select count(*) from accrual_rule), (select count(*) from campaign),
  (select count(*) from integration where enabled), (select count(*) from app_user where status = 'blocked'))`);

const t = { employee: await login('employee'), manager: await login('manager'), hr: await login('hr'),
  finance: await login('finance'), admin: await login('admin') };
const adminId = (await http('GET', '/auth/me', { token: t.admin })).json.user.id;
const otherTeamOp = psql(`select o.id from operation o join bonus_account a on a.id = o.account_id join employee e on e.id = a.employee_id
  where e.department_id = 2 and o.type = 'accrual' and o.status = 'accrued' order by o.id limit 1`);
const long = (n) => 'я'.repeat(n);
const acc = (over) => ({ employeeIds: ['3'], amount: 100, basis: 'project', reason: 'Проверка устойчивости', ...over });
const ruleBody = (over) => ({ name: 'Правило проверки', kpi: 'Показатель устойчивости', weight: 0.3, basePoints: 1000, periodicity: 'quarter', threshold: 80, active: true, ...over });
const camp = (over) => ({ name: 'Кампания проверки', description: '', startDate: '2027-03-01', endDate: '2027-03-31', departmentIds: ['2'], coefficient: 1.5, budget: 1000, ...over });
const user = (over) => ({ fullName: 'Проверка Устойчивости', email: 'robust.check@technosoft.by', departmentId: '2', position: 'QA-инженер', role: 'employee', ...over });
const JSONH = { 'Content-Type': 'application/json' };

// [категория, описание, метод, путь, роль, тело (объект) или raw (строка), ожидаемый статус]
const CASES = [
  // 1. Неверный JSON
  ['JSON', 'Вход: незакрытая скобка', 'POST', '/auth/login', null, { raw: '{"email": "anna.kovaleva@technosoft.by"', headers: JSONH }, 400],
  ['JSON', 'Начисление: обрезанный массив', 'POST', '/accruals', 'manager', { raw: '{"employeeIds": [', headers: JSONH }, 400],
  ['JSON', 'Правило: текст вместо JSON', 'POST', '/rules', 'hr', { raw: 'name=Правило', headers: JSONH }, 400],
  ['JSON', 'Кампания: массив вместо объекта', 'POST', '/campaigns', 'hr', { raw: '[1, 2', headers: JSONH }, 400],
  ['JSON', 'Пользователь: пропущено значение', 'POST', '/users', 'admin', { raw: '{"fullName": }', headers: JSONH }, 400],
  ['JSON', 'Тело не JSON (text/plain)', 'POST', '/accruals', 'manager', { raw: 'amount=100', headers: { 'Content-Type': 'text/plain' } }, 415],
  // 2. Неверные типы
  ['Тип', 'Сумма строкой «много»', 'POST', '/accruals', 'manager', { body: acc({ amount: 'много' }) }, 400],
  ['Тип', 'Получатели строкой вместо массива', 'POST', '/accruals', 'manager', { body: acc({ employeeIds: 'три' }) }, 400],
  ['Тип', 'Вес правила строкой', 'POST', '/rules', 'hr', { body: ruleBody({ weight: 'тяжёлый' }) }, 400],
  ['Тип', 'Коэффициент кампании строкой', 'POST', '/campaigns', 'hr', { body: camp({ coefficient: 'два' }) }, 400],
  ['Тип', 'Статус пользователя логическим значением', 'PATCH', '/users/2/status', 'admin', { body: { status: true } }, 422],
  ['Тип', 'Идентификатор операции не число', 'PUT', '/operations/abc', 'hr', { body: { action: 'adjust', newAmount: 100, reason: 'x' } }, 400],
  ['Тип', 'Параметр employeeId не число', 'GET', '/operations?employeeId=abc', 'hr', {}, 400],
  ['Тип', 'Дата периода в неверном формате', 'GET', '/accounts/me/operations?from=01.11.2026&to=30.11.2026', 'employee', {}, 400],
  ['Тип', 'Неизвестный период аналитики', 'GET', '/analytics/dashboard?period=decade', 'hr', {}, 422],
  ['Тип', 'Неизвестный тип операции в фильтре', 'GET', '/accounts/me/operations?type=bonus', 'employee', {}, 422],
  // 3. Отсутствующие поля
  ['Поля', 'Вход: пустой объект', 'POST', '/auth/login', null, { body: {} }, 422],
  ['Поля', 'Начисление: пустой объект', 'POST', '/accruals', 'manager', { body: {} }, 422],
  ['Поля', 'Начисление без причины', 'POST', '/accruals', 'manager', { body: acc({ reason: undefined }) }, 422],
  ['Поля', 'Начисление без получателей', 'POST', '/accruals', 'manager', { body: acc({ employeeIds: [] }) }, 422],
  ['Поля', 'Правило: пустой объект', 'POST', '/rules', 'hr', { body: {} }, 422],
  ['Поля', 'Кампания без дат и подразделений', 'POST', '/campaigns', 'hr', { body: { name: 'Без дат', coefficient: 1.5 } }, 422],
  ['Поля', 'Пользователь без email', 'POST', '/users', 'admin', { body: user({ email: undefined }) }, 422],
  ['Поля', 'Блокировка операции без причины', 'POST', '/control/operations/1/block', 'finance', { body: {} }, 422],
  ['Поля', 'Корректировка без действия и причины', 'PUT', `/operations/${otherTeamOp}`, 'hr', { body: {} }, 422],
  ['Поля', 'Отчёт без параметра to', 'GET', '/reports/departments?from=2026-11-01', 'hr', {}, 400],
  // 4. Слишком большие значения
  ['Размер', 'Сумма 1 000 000 000', 'POST', '/accruals', 'manager', { body: acc({ amount: 1_000_000_000 }) }, 422],
  ['Размер', 'Сумма за пределом int (2^31)', 'POST', '/accruals', 'manager', { raw: JSON.stringify(acc({})).replace('"amount":100', '"amount":2147483648'), headers: JSONH }, 400],
  ['Размер', 'Причина 301 символ', 'POST', '/accruals', 'manager', { body: acc({ reason: long(301) }) }, 422],
  ['Размер', '501 получатель в одном начислении', 'POST', '/accruals', 'hr', { body: acc({ employeeIds: Array.from({ length: 501 }, (_, i) => String(i + 1)) }) }, 422],
  ['Размер', 'Базовая сумма правила 2 000 000', 'POST', '/rules', 'hr', { body: ruleBody({ basePoints: 2_000_000 }) }, 422],
  ['Размер', 'Коэффициент кампании 99', 'POST', '/campaigns', 'hr', { body: camp({ coefficient: 99 }) }, 422],
  ['Размер', 'ФИО 151 символ', 'POST', '/users', 'admin', { body: user({ fullName: long(151) }) }, 422],
  ['Размер', 'Адрес интеграции 300 символов', 'PUT', '/integrations/2', 'admin', { body: { url: `https://${'a'.repeat(292)}` } }, 422],
  ['Размер', 'Новая сумма корректировки 2 000 000', 'PUT', `/operations/${otherTeamOp}`, 'hr', { body: { action: 'adjust', newAmount: 2_000_000, reason: 'Проверка' } }, 422],
  // 5. Отрицательные и нулевые значения
  ['Знак', 'Сумма −100', 'POST', '/accruals', 'manager', { body: acc({ amount: -100 }) }, 422],
  ['Знак', 'Сумма 0', 'POST', '/accruals', 'manager', { body: acc({ amount: 0 }) }, 422],
  ['Знак', 'Новая сумма корректировки −5', 'PUT', `/operations/${otherTeamOp}`, 'hr', { body: { action: 'adjust', newAmount: -5, reason: 'Проверка' } }, 422],
  ['Знак', 'Вес правила −0,5', 'POST', '/rules', 'hr', { body: ruleBody({ weight: -0.5 }) }, 422],
  ['Знак', 'Бюджет кампании −1000', 'POST', '/campaigns', 'hr', { body: camp({ budget: -1000 }) }, 422],
  ['Знак', 'Предварительный расчёт с суммой −1', 'POST', '/accruals/preview', 'hr', { body: acc({ amount: -1 }) }, 422],
  // 6. Чужие и несуществующие идентификаторы
  ['Чужой id', 'Руководитель начисляет сотруднику чужого отдела', 'POST', '/accruals', 'manager', { body: acc({ employeeIds: ['9'] }) }, 403],
  ['Чужой id', 'Руководитель корректирует операцию чужой команды', 'PUT', `/operations/${otherTeamOp}`, 'manager', { body: { action: 'adjust', newAmount: 100, reason: 'Проверка' } }, 403],
  ['Чужой id', 'Списание на несуществующую программу', 'POST', '/programs/999999/redemptions', 'employee', {}, 404],
  ['Чужой id', 'Корректировка несуществующей операции', 'PUT', '/operations/99999999', 'hr', { body: { action: 'cancel', reason: 'Проверка' } }, 404],
  ['Чужой id', 'Подтверждение несуществующей операции', 'POST', '/control/operations/99999999/approve', 'finance', {}, 404],
  ['Чужой id', 'Начисление несуществующему сотруднику', 'POST', '/accruals', 'hr', { body: acc({ employeeIds: ['99999'] }) }, 404],
  ['Чужой id', 'Изменение несуществующего пользователя', 'PUT', '/users/999999', 'admin', { body: user({}) }, 404],
  ['Чужой id', 'Проверка несуществующей интеграции', 'POST', '/integrations/999/test', 'admin', { body: { url: 'https://x.technosoft.by', apiKey: '12345678' } }, 404],
  ['Чужой id', 'Администратор блокирует сам себя', 'PATCH', `/users/${adminId}/status`, 'admin', { body: { status: 'blocked' } }, 409],
  ['Чужой id', 'Начисление с несуществующей кампанией', 'POST', '/accruals/preview', 'hr', { body: acc({ campaignId: 99999 }) }, 404],
  // 7. Логически неверные значения
  ['Логика', 'Период задан наоборот', 'GET', '/accounts/me/operations?from=2026-11-30&to=2026-11-01', 'employee', {}, 422],
  ['Логика', 'Окончание кампании раньше начала', 'POST', '/campaigns', 'hr', { body: camp({ startDate: '2027-03-31', endDate: '2027-03-01' }) }, 422],
  ['Логика', 'Неизвестная роль пользователя', 'POST', '/users', 'admin', { body: user({ role: 'superuser' }) }, 422],
  ['Логика', 'Неизвестное основание начисления', 'POST', '/accruals', 'manager', { body: acc({ basis: 'gift' }) }, 422],
  ['Логика', 'Email без домена', 'POST', '/users', 'admin', { body: user({ email: 'robust' }) }, 422],
  ['Логика', 'Неподдерживаемый метод DELETE', 'DELETE', '/programs', 'employee', {}, 405],
  ['Логика', 'Поддельный токен', 'GET', '/accounts/me', 'forged', {}, 401],
];

const before = snapshot();
const results = [];
for (const [category, title, method, path, role, opts, expected] of CASES) {
  const token = role === 'forged' ? 'eyJhbGciOiJIUzI1NiJ9.e30.forged' : role ? t[role] : undefined;
  const r = await http(method, path, { token, body: opts.body, raw: opts.raw, headers: opts.headers });
  const rejected4xx = r.status >= 400 && r.status < 500;
  results.push({ category, title, method, path: path.length > 60 ? `${path.slice(0, 57)}...` : path, role, expected, status: r.status,
    code: r.json?.code, unified: isApiError(r), correct: rejected4xx && isApiError(r), expectedMatch: r.status === expected,
    message: r.json?.message, ms: round2(r.ms) });
}
const after = snapshot();
const unchanged = before === after;
const correct = results.filter((x) => x.correct && unchanged).length;
const summary = {
  date: nowIso(), base: BASE, cases: results.length, correctlyRejected: correct,
  serverErrors5xx: results.filter((x) => x.status >= 500).length, accepted2xx: results.filter((x) => x.status < 300).length,
  expectedStatusMatches: results.filter((x) => x.expectedMatch).length,
  dataUnchanged: unchanged, snapshotBefore: before, snapshotAfter: after,
  X_faultTolerance: round2(correct / results.length),
  notCorrect: results.filter((x) => !x.correct), statusDiffers: results.filter((x) => !x.expectedMatch),
};
save('robustness.json', { summary, results });
console.log(JSON.stringify(summary, null, 2));
