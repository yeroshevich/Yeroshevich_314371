import dayjs, { type Dayjs } from 'dayjs';
import type {
  AuditEntry,
  Campaign,
  Department,
  Employee,
  Integration,
  Notification,
  Operation,
  OperationBasis,
  Program,
  Rule,
  User,
} from '../types';

/**
 * In-memory база мок-сервера. Данные детерминированы (генератор с фиксированным
 * зерном) и привязаны к текущей дате: шесть месяцев истории всегда заканчиваются «сегодня».
 * Таблицы повторяют будущую схему PostgreSQL (CON-2).
 */

/** Лимит разового начисления на сотрудника; выше — согласование финотдела (UC5). */
export const ACCRUAL_LIMIT = 1000;

/** Счета, заблокированные HR (альтернативный поток UC1). */
export const BLOCKED_ACCOUNTS = new Set<string>(['e09']);

/** Пароль всех демо-учётных записей. */
export const DEMO_PASSWORD = 'demo';

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const rand = mulberry32(46);
const pick = <T,>(list: readonly T[]): T => list[Math.floor(rand() * list.length)];
const between = (min: number, max: number) => Math.floor(min + rand() * (max - min + 1));
/** Округление до 50 баллов — так выглядят реальные начисления. */
const round50 = (n: number) => Math.round(n / 50) * 50;

const NOW = dayjs();
const monthStart = (offset: number) => NOW.startOf('month').subtract(offset, 'month');
/** Случайный рабочий момент внутри месяца, но не позже «сейчас». */
function momentIn(offset: number, dayFrom = 1, dayTo = 28): Dayjs {
  const start = monthStart(offset);
  let d = start.date(between(dayFrom, Math.min(dayTo, start.daysInMonth()))).hour(between(9, 18)).minute(between(0, 59));
  if (d.day() === 0) d = d.add(1, 'day');
  if (d.day() === 6) d = d.subtract(1, 'day');
  return d.isAfter(NOW) ? NOW.subtract(between(1, 5), 'hour') : d;
}

// ─── Подразделения ────────────────────────────────────────────────────────
export const departments: Department[] = [
  { id: 'd-dev', name: 'Разработка ПО', headId: 'e02' },
  { id: 'd-qa', name: 'Обеспечение качества', headId: 'e08' },
  { id: 'd-ba', name: 'Бизнес-анализ', headId: 'e12' },
  { id: 'd-ops', name: 'DevOps и инфраструктура', headId: 'e16' },
  { id: 'd-hr', name: 'Управление персоналом', headId: 'e21' },
  { id: 'd-fin', name: 'Финансы', headId: 'e24' },
];

// ─── Сотрудники ───────────────────────────────────────────────────────────
type Seed = [id: string, fullName: string, email: string, position: string, dept: string, managerId: string | undefined, hiredYear: number];

const employeeSeeds: Seed[] = [
  ['e01', 'Анна Ковалёва', 'anna.kovaleva', 'Frontend-разработчик', 'd-dev', 'e02', 2021],
  ['e02', 'Дмитрий Савицкий', 'dmitry.savitsky', 'Руководитель группы разработки', 'd-dev', undefined, 2015],
  ['e03', 'Максим Новик', 'maxim.novik', 'Backend-разработчик (Java)', 'd-dev', 'e02', 2020],
  ['e04', 'Екатерина Шевчук', 'ekaterina.shevchuk', 'Backend-разработчик (Java)', 'd-dev', 'e02', 2022],
  ['e05', 'Артём Василевич', 'artem.vasilevich', 'Ведущий разработчик', 'd-dev', 'e02', 2017],
  ['e06', 'Юлия Романович', 'yulia.romanovich', 'Frontend-разработчик', 'd-dev', 'e02', 2023],
  ['e07', 'Никита Гончаров', 'nikita.goncharov', 'Mobile-разработчик', 'd-dev', 'e02', 2024],
  ['e08', 'Наталья Кравченко', 'natalia.kravchenko', 'Руководитель отдела QA', 'd-qa', undefined, 2016],
  ['e09', 'Павел Карпович', 'pavel.karpovich', 'QA-инженер', 'd-qa', 'e08', 2022],
  ['e10', 'Виктория Лис', 'victoria.lis', 'Инженер по автоматизации тестирования', 'd-qa', 'e08', 2021],
  ['e11', 'Андрей Козлов', 'andrey.kozlov', 'QA-инженер', 'd-qa', 'e08', 2025],
  ['e12', 'Ирина Соколова', 'irina.sokolova', 'Руководитель отдела аналитики', 'd-ba', undefined, 2014],
  ['e13', 'Алексей Бондаренко', 'alexey.bondarenko', 'Бизнес-аналитик', 'd-ba', 'e12', 2019],
  ['e14', 'Мария Тарасевич', 'maria.tarasevich', 'Системный аналитик', 'd-ba', 'e12', 2020],
  ['e15', 'Кирилл Лукашевич', 'kirill.lukashevich', 'Аналитик данных', 'd-ba', 'e12', 2023],
  ['e16', 'Владимир Ермаков', 'vladimir.ermakov', 'Руководитель DevOps', 'd-ops', undefined, 2013],
  ['e17', 'Сергей Литвинов', 'sergey.litvinov', 'Системный администратор', 'd-ops', 'e16', 2018],
  ['e18', 'Денис Пашкевич', 'denis.pashkevich', 'DevOps-инженер', 'd-ops', 'e16', 2021],
  ['e19', 'Евгений Михалевич', 'evgeny.mikhalevich', 'SRE-инженер', 'd-ops', 'e16', 2022],
  ['e20', 'Ольга Мельник', 'olga.melnik', 'HR-бизнес-партнёр', 'd-hr', 'e21', 2019],
  ['e21', 'Елена Шидловская', 'elena.shidlovskaya', 'Директор по персоналу', 'd-hr', undefined, 2012],
  ['e22', 'Татьяна Климович', 'tatiana.klimovich', 'Специалист по обучению', 'd-hr', 'e21', 2024],
  ['e23', 'Игорь Жук', 'igor.zhuk', 'Финансовый контролёр', 'd-fin', 'e24', 2018],
  ['e24', 'Людмила Сидоренко', 'lyudmila.sidorenko', 'Финансовый директор', 'd-fin', undefined, 2011],
  ['e25', 'Ангелина Островская', 'angelina.ostrovskaya', 'Экономист', 'd-fin', 'e24', 2023],
];

export const employees: Employee[] = employeeSeeds.map(([id, fullName, login, position, departmentId, managerId, year]) => ({
  id,
  fullName,
  email: `${login}@technosoft.by`,
  position,
  departmentId,
  managerId,
  hiredAt: dayjs(`${year}-0${between(1, 9)}-1${between(0, 9)}`).toISOString(),
  kpiCompletion: id === 'e01' ? 112 : between(68, 128),
}));

const employeeById = new Map(employees.map((e) => [e.id, e]));
export const findEmployee = (id: string) => employeeById.get(id);

// ─── Пользователи (UC12) ──────────────────────────────────────────────────
const roleByEmployee: Record<string, User['role']> = {
  e02: 'manager', e08: 'manager', e12: 'manager', e16: 'manager',
  e20: 'hr', e21: 'hr', e22: 'hr',
  e23: 'finance', e24: 'finance',
  e17: 'admin',
};

export const users: User[] = [
  ...employees.map((e, i): User => ({
    id: `u${e.id.slice(1)}`,
    fullName: e.fullName,
    email: e.email,
    departmentId: e.departmentId,
    position: e.position,
    role: roleByEmployee[e.id] ?? 'employee',
    status: 'active',
    employeeId: e.id,
    lastLoginAt: NOW.subtract(i % 7, 'day').subtract(between(1, 9), 'hour').toISOString(),
  })),
  {
    id: 'u26',
    fullName: 'Глеб Сорокин',
    email: 'gleb.sorokin@technosoft.by',
    departmentId: 'd-dev',
    position: 'Стажёр-разработчик',
    role: 'employee',
    status: 'blocked',
    lastLoginAt: NOW.subtract(64, 'day').toISOString(),
  },
];

/** Демо-учётные записи для быстрого входа (по одной на роль + пример заблокированного счёта). */
export const DEMO_ACCOUNTS = {
  employee: 'anna.kovaleva@technosoft.by',
  manager: 'dmitry.savitsky@technosoft.by',
  hr: 'olga.melnik@technosoft.by',
  finance: 'igor.zhuk@technosoft.by',
  admin: 'sergey.litvinov@technosoft.by',
  blocked: 'pavel.karpovich@technosoft.by',
} as const;

// ─── Правила начисления (UC8) ─────────────────────────────────────────────
export const formatFormula = (basePoints: number, weight: number) =>
  `${basePoints.toLocaleString('ru-RU')} × ${weight.toLocaleString('ru-RU')} × уровень достижения`;

const ruleSeeds: Omit<Rule, 'formula' | 'updatedAt'>[] = [
  { id: 'r1', name: 'Выполнение плана спринта', kpi: 'Закрытые задачи спринта, %', weight: 0.4, basePoints: 3000, periodicity: 'month', threshold: 80, active: true, usedInCurrentPeriod: true },
  { id: 'r2', name: 'Качество релиза', kpi: 'Релиз без критических дефектов, %', weight: 0.3, basePoints: 3000, periodicity: 'month', threshold: 90, active: true, usedInCurrentPeriod: true },
  { id: 'r3', name: 'Сдача этапа проекта', kpi: 'Этап принят заказчиком в срок', weight: 0.5, basePoints: 6000, periodicity: 'event', threshold: 100, active: true, usedInCurrentPeriod: false },
  { id: 'r4', name: 'Наставничество', kpi: 'Новички, прошедшие адаптацию', weight: 0.2, basePoints: 5000, periodicity: 'quarter', threshold: 100, active: true, usedInCurrentPeriod: false },
  { id: 'r5', name: 'Годовщина работы в компании', kpi: 'Полный год стажа', weight: 1, basePoints: 1000, periodicity: 'year', threshold: 100, active: true, usedInCurrentPeriod: true },
  { id: 'r6', name: 'Профессиональная сертификация', kpi: 'Полученный сертификат', weight: 0.25, basePoints: 8000, periodicity: 'event', threshold: 100, active: false, usedInCurrentPeriod: false },
];

export const rules: Rule[] = ruleSeeds.map((r, i) => ({
  ...r,
  formula: formatFormula(r.basePoints, r.weight),
  updatedAt: NOW.subtract(40 + i * 11, 'day').toISOString(),
}));

// ─── Кампании (UC9) ───────────────────────────────────────────────────────
export const campaigns: Campaign[] = [
  {
    id: 'c1',
    name: 'Осенний релиз 2.0',
    description: 'Полуторный коэффициент к KPI-начислениям команд, которые выпускают релиз платформы 2.0.',
    startDate: monthStart(2).toISOString(),
    endDate: monthStart(-1).date(15).toISOString(),
    departmentIds: ['d-dev', 'd-qa'],
    coefficient: 1.5,
    budget: 60000,
    spent: 0,
    status: 'active',
  },
  {
    id: 'c2',
    name: 'Неделя наставничества',
    description: 'Двойные баллы за наставничество новых сотрудников в первую неделю месяца.',
    startDate: monthStart(-1).toISOString(),
    endDate: monthStart(-1).date(7).toISOString(),
    departmentIds: departments.map((d) => d.id),
    coefficient: 2,
    budget: 15000,
    spent: 0,
    status: 'planned',
  },
  {
    id: 'c3',
    name: 'Квартал без инцидентов',
    description: 'Бонус дежурным инженерам за квартал без инцидентов первой категории.',
    startDate: monthStart(-1).toISOString(),
    endDate: monthStart(-4).subtract(1, 'day').toISOString(),
    departmentIds: ['d-ops'],
    coefficient: 2,
    budget: null,
    spent: 0,
    status: 'needs_approval',
  },
  {
    id: 'c4',
    name: 'Летний хакатон',
    description: 'Призовые баллы участникам и победителям внутреннего хакатона.',
    startDate: monthStart(4).toISOString(),
    endDate: monthStart(4).endOf('month').toISOString(),
    departmentIds: ['d-dev', 'd-ba'],
    coefficient: 1.2,
    budget: 40000,
    spent: 0,
    status: 'finished',
  },
];

// ─── Программы мотивации (UC3) ────────────────────────────────────────────
export const programs: Program[] = [
  { id: 'p1', name: 'Сертификат в книжный магазин', category: 'gift', description: 'Электронный сертификат номиналом 100 BYN в «Белкниге» или OZ.by.', cost: 1500, active: true },
  { id: 'p2', name: 'Худи и термокружка TechnoSoft', category: 'merch', description: 'Комплект фирменного мерча. Выдача в офисе на Притыцкого в течение недели.', cost: 900, active: true },
  { id: 'p3', name: 'Абонемент в бассейн', category: 'health', description: 'Месяц безлимитного посещения бассейна в одном из клубов-партнёров.', cost: 2500, active: true },
  { id: 'p4', name: 'Дополнительный выходной', category: 'dayoff', description: 'Оплачиваемый день отдыха. Дату согласуйте с руководителем.', cost: 4000, active: true },
  { id: 'p5', name: 'Курс английского языка', category: 'education', description: 'Три месяца занятий в группе по вашему уровню, два раза в неделю.', cost: 6000, active: true },
  { id: 'p6', name: 'Участие в профильной конференции', category: 'education', description: 'Билет и командировочные на конференцию по вашему направлению.', cost: 12000, active: true },
  { id: 'p7', name: 'Наушники с шумоподавлением', category: 'gift', description: 'Беспроводные наушники для работы в open space.', cost: 9500, active: true },
  { id: 'p8', name: 'Корпоративный сплав по Неману', category: 'event', description: 'Двухдневный выезд с командой, проживание и питание включены.', cost: 3500, active: false, inactiveReason: 'Сезон завершён. Запись откроется в мае.' },
];

// ─── Операции ─────────────────────────────────────────────────────────────
export const operations: Operation[] = [];
let opSeq = 1000;
export const nextOperationNumber = () => `ОП-${String(++opSeq).padStart(6, '0')}`;
export const nextId = (prefix: string) => `${prefix}-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e6).toString(36)}`;

interface OpSeed {
  employeeId: string;
  at: Dayjs;
  type: Operation['type'];
  amount: number;
  status: Operation['status'];
  basis: OperationBasis;
  reason: string;
  ruleId?: string;
  campaignId?: string;
  programId?: string;
  createdBy?: string;
  comment?: string;
  disputed?: boolean;
  originalAmount?: number;
}

function pushOp(s: OpSeed) {
  const e = findEmployee(s.employeeId)!;
  operations.push({
    id: `op-${opSeq + 1}`,
    number: nextOperationNumber(),
    employeeId: e.id,
    employeeName: e.fullName,
    departmentId: e.departmentId,
    type: s.type,
    amount: s.amount,
    originalAmount: s.originalAmount,
    status: s.status,
    basis: s.basis,
    reason: s.reason,
    ruleId: s.ruleId,
    campaignId: s.campaignId,
    programId: s.programId,
    createdAt: s.at.toISOString(),
    createdBy: s.createdBy ?? 'Система (автоначисление)',
    comment: s.comment,
    disputed: s.disputed,
  });
}

const monthName = (offset: number) => monthStart(offset).format('MMMM');

// История Анны Ковалёвой — демо-сотрудника: насыщенная, чтобы главная выглядела живой.
const annaOps: OpSeed[] = [
  { employeeId: 'e01', at: momentIn(5, 3, 5), type: 'accrual', amount: 1200, status: 'accrued', basis: 'kpi', ruleId: 'r1', reason: `План спринта за ${monthName(6)} выполнен на 104 %` },
  { employeeId: 'e01', at: momentIn(5, 18, 22), type: 'accrual', amount: 900, status: 'accrued', basis: 'kpi', ruleId: 'r2', reason: 'Релиз 1.8 без критических дефектов' },
  { employeeId: 'e01', at: momentIn(4, 3, 5), type: 'accrual', amount: 1100, status: 'accrued', basis: 'kpi', ruleId: 'r1', reason: `План спринта за ${monthName(5)} выполнен на 97 %` },
  { employeeId: 'e01', at: momentIn(4, 20, 24), type: 'accrual', amount: 2500, status: 'accrued', basis: 'achievement', campaignId: 'c4', reason: 'Второе место на летнем хакатоне', createdBy: 'Ольга Мельник' },
  { employeeId: 'e01', at: momentIn(3, 8, 10), type: 'writeoff', amount: -2500, status: 'written_off', basis: 'program', programId: 'p3', reason: 'Программа «Абонемент в бассейн»', createdBy: 'Анна Ковалёва' },
  { employeeId: 'e01', at: momentIn(3, 3, 5), type: 'accrual', amount: 1200, status: 'accrued', basis: 'kpi', ruleId: 'r1', reason: `План спринта за ${monthName(4)} выполнен на 106 %` },
  { employeeId: 'e01', at: momentIn(2, 3, 5), type: 'accrual', amount: 1350, status: 'accrued', basis: 'kpi', ruleId: 'r1', campaignId: 'c1', reason: `План спринта за ${monthName(3)}, коэффициент кампании ×1,5` },
  { employeeId: 'e01', at: momentIn(2, 12, 14), type: 'correction', amount: 1000, originalAmount: 1200, status: 'corrected', basis: 'kpi', ruleId: 'r5', reason: 'Годовщина работы: пересчёт по стажу', createdBy: 'Ольга Мельник', comment: 'Начислено по ставке прошлого года, исправлено' },
  { employeeId: 'e01', at: momentIn(1, 3, 5), type: 'accrual', amount: 1500, status: 'accrued', basis: 'kpi', ruleId: 'r1', campaignId: 'c1', reason: `План спринта за ${monthName(2)}, коэффициент кампании ×1,5` },
  { employeeId: 'e01', at: momentIn(1, 16, 20), type: 'writeoff', amount: -900, status: 'written_off', basis: 'program', programId: 'p2', reason: 'Программа «Худи и термокружка TechnoSoft»', createdBy: 'Анна Ковалёва' },
  { employeeId: 'e01', at: momentIn(0, 3, 4), type: 'accrual', amount: 1650, status: 'accrued', basis: 'kpi', ruleId: 'r1', campaignId: 'c1', reason: `План спринта за ${monthName(1)}, коэффициент кампании ×1,5` },
  { employeeId: 'e01', at: momentIn(0, 10, 12), type: 'accrual', amount: 800, status: 'accrued', basis: 'project', reason: 'Миграция личного кабинета на новый дизайн', createdBy: 'Дмитрий Савицкий' },
  { employeeId: 'e01', at: momentIn(0, 13, 18), type: 'writeoff', amount: -1500, status: 'written_off', basis: 'program', programId: 'p1', reason: 'Программа «Сертификат в книжный магазин»', createdBy: 'Анна Ковалёва' },
  { employeeId: 'e01', at: momentIn(0, 19, 22), type: 'accrual', amount: 1500, status: 'pending', basis: 'achievement', reason: 'Доклад на внутреннем митапе фронтенда', createdBy: 'Дмитрий Савицкий', comment: `Превышен лимит ${ACCRUAL_LIMIT.toLocaleString('ru-RU')} Б на сотрудника` },
];
annaOps.forEach(pushOp);

const kpiReasons = [
  (m: string) => `План спринта за ${m} выполнен`,
  (m: string) => `KPI отдела за ${m} достигнут`,
  (m: string) => `Выполнение плана за ${m}`,
];
const projectReasons = [
  'Запуск модуля отчётности для заказчика',
  'Перенос сервисов в Kubernetes',
  'Сдача этапа «Интеграция с HRIS»',
  'Подготовка требований к модулю кампаний',
  'Автотесты регресса платёжного шлюза',
  'Аудит доступа к персональным данным',
];
const achievementReasons = [
  'Помощь коллегам в срочном релизе',
  'Лучший наставник квартала',
  'Ночное восстановление после аварии',
  'Выступление на DevDay Minsk',
];

// Остальные сотрудники: по 2–4 операции за полгода, детерминированно.
const others = employees.filter((e) => e.id !== 'e01');
const balanceTrack = new Map<string, number>();
for (let offset = 5; offset >= 0; offset--) {
  for (const e of others) {
    if (rand() > 0.42) continue;
    const roll = rand();
    const inCampaign = offset <= 2 && (e.departmentId === 'd-dev' || e.departmentId === 'd-qa');
    const isHackathon = offset === 4 && (e.departmentId === 'd-dev' || e.departmentId === 'd-ba') && rand() > 0.5;
    let seed: OpSeed;
    if (roll < 0.6) {
      const base = round50(between(700, 1500));
      seed = {
        employeeId: e.id, at: momentIn(offset, 2, 6), type: 'accrual',
        amount: inCampaign ? round50(base * 1.5) : base, status: 'accrued', basis: 'kpi',
        ruleId: pick(['r1', 'r1', 'r2']), campaignId: inCampaign ? 'c1' : undefined,
        reason: pick(kpiReasons)(monthName(offset + 1)),
      };
    } else if (roll < 0.85 || isHackathon) {
      seed = {
        employeeId: e.id, at: momentIn(offset, 8, 26), type: 'accrual',
        amount: isHackathon ? round50(between(1500, 3000)) : round50(between(1000, 3000)),
        status: 'accrued', basis: isHackathon ? 'achievement' : 'project', ruleId: isHackathon ? undefined : 'r3',
        campaignId: isHackathon ? 'c4' : undefined,
        reason: isHackathon ? 'Участие в летнем хакатоне' : pick(projectReasons),
        createdBy: pick(['Ольга Мельник', 'Елена Шидловская']),
      };
    } else {
      seed = {
        employeeId: e.id, at: momentIn(offset, 8, 26), type: 'accrual', amount: round50(between(500, 1500)),
        status: 'accrued', basis: 'achievement', reason: pick(achievementReasons), createdBy: 'Ольга Мельник',
      };
    }
    pushOp(seed);
    balanceTrack.set(e.id, (balanceTrack.get(e.id) ?? 0) + seed.amount);

    // Иногда сотрудник тратит баллы на программу — только если хватает.
    const bal = balanceTrack.get(e.id) ?? 0;
    const affordable = programs.filter((p) => p.active && p.cost <= bal);
    if (affordable.length && rand() < 0.3) {
      const p = pick(affordable);
      pushOp({
        employeeId: e.id, at: momentIn(offset, 20, 28), type: 'writeoff', amount: -p.cost,
        status: 'written_off', basis: 'program', programId: p.id, reason: `Программа «${p.name}»`, createdBy: e.fullName,
      });
      balanceTrack.set(e.id, bal - p.cost);
    }
  }
}

// Операции для контроля финотдела и журнала: согласование, спорные, блокировка, отмена.
const special: OpSeed[] = [
  { employeeId: 'e05', at: momentIn(0, 14, 18), type: 'accrual', amount: 7500, status: 'pending', basis: 'project', ruleId: 'r3', reason: 'Сдача архитектуры платформы 2.0 заказчику', createdBy: 'Дмитрий Савицкий', comment: `Превышен лимит ${ACCRUAL_LIMIT.toLocaleString('ru-RU')} Б на сотрудника` },
  { employeeId: 'e18', at: momentIn(0, 17, 21), type: 'accrual', amount: 6000, status: 'pending', basis: 'achievement', reason: 'Восстановление кластера после аварии в выходные', createdBy: 'Владимир Ермаков', comment: `Превышен лимит ${ACCRUAL_LIMIT.toLocaleString('ru-RU')} Б на сотрудника` },
  { employeeId: 'e13', at: momentIn(0, 6, 9), type: 'accrual', amount: 3000, status: 'accrued', basis: 'project', reason: 'Подготовка требований к модулю кампаний', createdBy: 'Ирина Соколова', disputed: true, comment: 'Проект ещё не принят заказчиком (BR-5)' },
  { employeeId: 'e04', at: momentIn(0, 11, 15), type: 'correction', amount: 0, originalAmount: 1200, status: 'pending', basis: 'correction', reason: 'Отмена: KPI учтён дважды', createdBy: 'Ольга Мельник', comment: 'Отмена приведёт к отрицательному балансу' },
  { employeeId: 'e10', at: momentIn(1, 9, 12), type: 'accrual', amount: 2000, status: 'blocked', basis: 'achievement', reason: 'Премия за автотесты без согласования бюджета', createdBy: 'Наталья Кравченко', comment: 'Нет подтверждённого результата (BR-5)' },
  { employeeId: 'e15', at: momentIn(2, 12, 16), type: 'accrual', amount: 1000, status: 'cancelled', basis: 'kpi', ruleId: 'r1', reason: 'Начисление по ошибочному импорту из Jira', createdBy: 'Ольга Мельник', comment: 'Отменено: дубль операции' },
  { employeeId: 'e03', at: momentIn(1, 20, 24), type: 'correction', amount: 1400, originalAmount: 1100, status: 'corrected', basis: 'kpi', ruleId: 'r1', reason: 'Пересчёт KPI после закрытия хвостов спринта', createdBy: 'Ольга Мельник' },
  { employeeId: 'e14', at: momentIn(3, 5, 9), type: 'correction', amount: 800, originalAmount: 1000, status: 'corrected', basis: 'kpi', ruleId: 'r2', reason: 'Уточнение уровня достижения по релизу', createdBy: 'Елена Шидловская' },
];
special.forEach(pushOp);

operations.sort((a, b) => dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf());

// Расход бюджетов кампаний считаем по операциям.
for (const c of campaigns) {
  c.spent = operations
    .filter((o) => o.campaignId === c.id && ['accrued', 'corrected'].includes(o.status))
    .reduce((s, o) => s + o.amount, 0);
}

// ─── Интеграции (UC13) ────────────────────────────────────────────────────
export const integrations: Integration[] = [
  {
    id: 'i-hris', kind: 'hris', name: 'HRIS', description: 'Кадровые данные: сотрудники, подразделения, должности (DATA-2).',
    url: 'https://hris.technosoft.by/api/v2', apiKey: 'hris-7f3a9c21e4b8', enabled: true,
    lastSyncAt: NOW.subtract(2, 'hour').toISOString(),
    lastCheck: { ok: true, message: 'Соединение установлено', checkedAt: NOW.subtract(2, 'hour').toISOString(), latencyMs: 182 },
  },
  {
    id: 'i-erp', kind: 'erp', name: 'ERP', description: 'Проекты и этапы для начислений по проектным задачам.',
    url: 'http://erp.technosoft.local/api', apiKey: 'erp-51c0aa', enabled: false,
    lastCheck: { ok: false, message: 'Сервер отклонил подключение: нужен адрес HTTPS', checkedAt: NOW.subtract(1, 'day').toISOString() },
  },
  {
    id: 'i-ad', kind: 'ad', name: 'Active Directory', description: 'Единый вход сотрудников и синхронизация учётных записей.',
    url: 'https://ad.technosoft.by/ldap', apiKey: 'ad-svc-bonus-2026', enabled: true,
    lastSyncAt: NOW.subtract(25, 'minute').toISOString(),
    lastCheck: { ok: true, message: 'Соединение установлено', checkedAt: NOW.subtract(25, 'minute').toISOString(), latencyMs: 64 },
  },
  {
    id: 'i-notify', kind: 'notifications', name: 'Сервис уведомлений', description: 'Письма и push-уведомления о начислениях и списаниях.',
    url: 'https://notify.technosoft.by/v1', apiKey: 'ntf-0b1d9e7c', enabled: true,
    lastSyncAt: NOW.subtract(5, 'minute').toISOString(),
    lastCheck: { ok: true, message: 'Соединение установлено', checkedAt: NOW.subtract(3, 'hour').toISOString(), latencyMs: 97 },
  },
];

// ─── Журнал аудита (NFR-3, SEC-4) ─────────────────────────────────────────
export const audit: AuditEntry[] = [];
const auditSeeds: [string, AuditEntry['role'], string, string, string][] = [
  ['Ольга Мельник', 'hr', 'Ручное начисление', 'Операция', 'Анна Ковалёва, +800 Б'],
  ['Игорь Жук', 'finance', 'Блокировка начисления', 'Операция', 'Виктория Лис, +2 000 Б'],
  ['Сергей Литвинов', 'admin', 'Изменение роли', 'Пользователь', 'Татьяна Климович → HR'],
  ['Система', 'system', 'Автоначисление по правилу', 'Правило', 'Выполнение плана спринта'],
  ['Ольга Мельник', 'hr', 'Изменение правила', 'Правило', 'Качество релиза'],
  ['Сергей Литвинов', 'admin', 'Проверка соединения', 'Интеграция', 'ERP — ошибка'],
  ['Елена Шидловская', 'hr', 'Создание кампании', 'Кампания', 'Квартал без инцидентов'],
  ['Дмитрий Савицкий', 'manager', 'Ручное начисление', 'Операция', 'Артём Василевич, +7 500 Б'],
  ['Анна Ковалёва', 'employee', 'Использование баллов', 'Программа', 'Сертификат в книжный магазин'],
  ['Сергей Литвинов', 'admin', 'Блокировка пользователя', 'Пользователь', 'Глеб Сорокин'],
  ['Система', 'system', 'Синхронизация', 'Интеграция', 'HRIS: 25 сотрудников'],
  ['Ольга Мельник', 'hr', 'Корректировка', 'Операция', 'Максим Новик, 1 100 → 1 400 Б'],
  ['Игорь Жук', 'finance', 'Выгрузка отчёта', 'Отчёт', 'По подразделениям, Excel'],
  ['Ольга Мельник', 'hr', 'Вход в систему', 'Сессия', 'olga.melnik@technosoft.by'],
  ['Людмила Сидоренко', 'finance', 'Подтверждение начисления', 'Операция', 'Денис Пашкевич, +2 500 Б'],
  ['Сергей Литвинов', 'admin', 'Включение интеграции', 'Интеграция', 'Сервис уведомлений'],
  ['Наталья Кравченко', 'manager', 'Ручное начисление', 'Операция', 'Виктория Лис, +2 000 Б'],
  ['Система', 'system', 'Отложенное начисление', 'Событие', 'Некорректные данные KPI из Jira'],
];
auditSeeds.forEach(([userName, role, action, objectType, objectLabel], i) => {
  audit.push({
    id: `a-${i + 1}`,
    timestamp: NOW.subtract(i * 7 + between(1, 6), 'hour').subtract(between(0, 59), 'minute').toISOString(),
    userName, role, action, objectType, objectLabel,
    ip: role === 'system' ? '10.10.0.5' : `10.20.${between(1, 9)}.${between(10, 250)}`,
  });
});

// ─── Уведомления ──────────────────────────────────────────────────────────
export const notifications = new Map<string, Notification[]>();
const note = (title: string, text: string, hoursAgo: number, read = false): Notification => ({
  id: nextId('n'), title, text, createdAt: NOW.subtract(hoursAgo, 'hour').toISOString(), read,
});
notifications.set('u01', [
  note('Начисление на согласовании', '1 500 Б за доклад на митапе ждут подтверждения финотдела.', 30),
  note('Начислено 800 Б', 'Проект «Миграция личного кабинета на новый дизайн».', 120),
  note('Списано 1 500 Б', 'Сертификат в книжный магазин придёт на почту в течение дня.', 200, true),
]);
notifications.set('u02', [note('Начисление на согласовании', 'Артём Василевич, 7 500 Б — превышен лимит, ждёт финотдел.', 40)]);
notifications.set('u20', [
  note('Кампания ждёт утверждения', '«Квартал без инцидентов» создана без бюджета.', 12),
  note('Спорное начисление', 'Финотдел пометил операцию Алексея Бондаренко.', 50),
]);
notifications.set('u23', [
  note('2 начисления выше лимита', 'Проверьте очередь контроля начислений.', 6),
  note('Отмена с отрицательным балансом', 'Екатерина Шевчук: требуется решение.', 26),
]);
notifications.set('u17', [note('ERP: соединение не установлено', 'Проверьте адрес подключения — нужен HTTPS.', 24)]);
