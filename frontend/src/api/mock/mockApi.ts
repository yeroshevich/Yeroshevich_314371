import dayjs from 'dayjs';
import { ApiError, type BonusApi } from '../client';
import type {
  AccountSummary,
  AccrualPreview,
  AccrualRequest,
  AnalyticsDashboard,
  AnalyticsPeriod,
  AuditEntry,
  AuthSession,
  BonusAccount,
  Campaign,
  CampaignStatus,
  Operation,
  OperationFilter,
  Report,
  ReportRow,
  Role,
  RuleSaveResult,
  TeamMember,
  User,
} from '../types';
import { OPERATION_STATUS_LABELS } from '../types';
import * as db from './db';

/**
 * Мок-реализация BonusApi: in-memory данные из db.ts, задержка ответа 200–400 мс,
 * те же проверки прав и бизнес-правил, что будут на сервере Spring Boot.
 */

const delay = () => new Promise((r) => setTimeout(r, 200 + Math.random() * 200));
const clone = <T,>(v: T): T => structuredClone(v);
const fmt = (n: number) => `${n.toLocaleString('ru-RU')} Б`;

/** Статусы, которые меняют баланс счёта. */
const SETTLED: Operation['status'][] = ['accrued', 'written_off', 'corrected'];

let currentUser: User | null = null;

function requireUser(...roles: Role[]): User {
  if (!currentUser) throw new ApiError(401, 'Сессия закончилась. Войдите снова.');
  if (roles.length && !roles.includes(currentUser.role)) {
    throw new ApiError(403, 'Для этого действия у вашей роли нет прав.');
  }
  return currentUser;
}

function requireEmployeeId(user: User): string {
  if (!user.employeeId) throw new ApiError(404, 'К учётной записи не привязан бонусный счёт.');
  return user.employeeId;
}

function log(action: string, objectType: string, objectLabel: string) {
  const u = currentUser;
  db.audit.unshift({
    id: db.nextId('a'),
    timestamp: new Date().toISOString(),
    userName: u?.fullName ?? 'Система',
    role: u?.role ?? 'system',
    action,
    objectType,
    objectLabel,
    ip: '10.20.4.117',
  });
}

function notify(employeeId: string, title: string, text: string) {
  const user = db.users.find((u) => u.employeeId === employeeId);
  if (!user) return;
  const list = db.notifications.get(user.id) ?? [];
  list.unshift({ id: db.nextId('n'), title, text, createdAt: new Date().toISOString(), read: false });
  db.notifications.set(user.id, list);
}

function balanceOf(employeeId: string, until?: dayjs.Dayjs): number {
  return db.operations
    .filter((o) => o.employeeId === employeeId && SETTLED.includes(o.status))
    .filter((o) => !until || !dayjs(o.createdAt).isAfter(until))
    .reduce((s, o) => s + o.amount, 0);
}

function accountOf(employeeId: string): BonusAccount {
  const pending = db.operations
    .filter((o) => o.employeeId === employeeId && o.status === 'pending' && o.amount > 0 && o.type === 'accrual')
    .reduce((s, o) => s + o.amount, 0);
  const last = db.operations.find((o) => o.employeeId === employeeId);
  return {
    employeeId,
    status: db.BLOCKED_ACCOUNTS.has(employeeId) ? 'blocked' : 'active',
    balance: balanceOf(employeeId),
    pending,
    updatedAt: last?.createdAt ?? new Date().toISOString(),
  };
}

function matches(o: Operation, f: OperationFilter = {}): boolean {
  if (f.employeeId && o.employeeId !== f.employeeId) return false;
  if (f.departmentId && o.departmentId !== f.departmentId) return false;
  if (f.type && o.type !== f.type) return false;
  if (f.status?.length && !f.status.includes(o.status)) return false;
  if (f.from && dayjs(o.createdAt).isBefore(dayjs(f.from).startOf('day'))) return false;
  if (f.to && dayjs(o.createdAt).isAfter(dayjs(f.to).endOf('day'))) return false;
  if (f.query) {
    const q = f.query.trim().toLowerCase();
    const hay = `${o.number} ${o.employeeName} ${o.reason}`.toLowerCase();
    if (!hay.includes(q)) return false;
  }
  return true;
}

function campaignStatus(c: Pick<Campaign, 'budget' | 'startDate' | 'endDate'>): CampaignStatus {
  if (c.budget === null) return 'needs_approval';
  const now = dayjs();
  if (now.isAfter(dayjs(c.endDate))) return 'finished';
  if (now.isBefore(dayjs(c.startDate))) return 'planned';
  return 'active';
}

function coefficientFor(employeeId: string, campaignId?: string): number {
  if (!campaignId) return 1;
  const c = db.campaigns.find((x) => x.id === campaignId);
  const e = db.findEmployee(employeeId);
  if (!c || !e || c.status !== 'active' || !c.departmentIds.includes(e.departmentId)) return 1;
  return c.coefficient;
}

/** Сотрудники, которых видит текущий пользователь: руководитель — свою команду, HR — всех. */
function visibleEmployees(user: User) {
  if (user.role === 'manager') return db.employees.filter((e) => e.managerId === user.employeeId);
  return db.employees;
}

function sumAccrued(ops: Operation[]) {
  return ops.filter((o) => o.amount > 0 && SETTLED.includes(o.status) && o.type !== 'writeoff').reduce((s, o) => s + o.amount, 0);
}
function sumSpent(ops: Operation[]) {
  return ops.filter((o) => o.type === 'writeoff' && o.status === 'written_off').reduce((s, o) => s - o.amount, 0);
}
function sumPending(ops: Operation[]) {
  return ops.filter((o) => o.status === 'pending' && o.amount > 0).reduce((s, o) => s + o.amount, 0);
}

export const mockApi: BonusApi = {
  async login(email, password) {
    await delay();
    const user = db.users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase());
    if (!user || password !== db.DEMO_PASSWORD) {
      throw new ApiError(401, 'Неверный email или пароль. Проверьте раскладку и попробуйте ещё раз.');
    }
    if (user.status === 'blocked') {
      throw new ApiError(403, 'Учётная запись заблокирована. Обратитесь к администратору системы.');
    }
    currentUser = user;
    user.lastLoginAt = new Date().toISOString();
    log('Вход в систему', 'Сессия', user.email);
    return clone<AuthSession>({ token: `mock-token-${user.id}`, user });
  },

  async restoreSession(token) {
    await delay();
    const id = token.replace('mock-token-', '');
    const user = db.users.find((u) => u.id === id && u.status === 'active');
    if (!user) throw new ApiError(401, 'Сессия закончилась. Войдите снова.');
    currentUser = user;
    return clone({ token, user });
  },

  async logout() {
    await delay();
    if (currentUser) log('Выход из системы', 'Сессия', currentUser.email);
    currentUser = null;
  },

  async getMyAccount() {
    await delay();
    const employeeId = requireEmployeeId(requireUser());
    if (db.BLOCKED_ACCOUNTS.has(employeeId)) {
      throw new ApiError(423, 'Баланс недоступен, обратитесь к HR.', 'ACCOUNT_BLOCKED');
    }
    const mine = db.operations.filter((o) => o.employeeId === employeeId);
    const monthStart = dayjs().startOf('month');
    const thisMonth = mine.filter((o) => !dayjs(o.createdAt).isBefore(monthStart));
    const history = Array.from({ length: 6 }, (_, i) => {
      const m = dayjs().subtract(5 - i, 'month');
      return { month: m.format('YYYY-MM'), balance: balanceOf(employeeId, i === 5 ? undefined : m.endOf('month')) };
    });
    const summary: AccountSummary = {
      account: accountOf(employeeId),
      accruedThisMonth: sumAccrued(thisMonth),
      spentThisMonth: sumSpent(thisMonth),
      recent: mine.slice(0, 5),
      history,
    };
    return clone(summary);
  },

  async getMyOperations(filter) {
    await delay();
    const employeeId = requireEmployeeId(requireUser());
    return clone(db.operations.filter((o) => matches(o, { ...filter, employeeId })));
  },

  async getPrograms() {
    await delay();
    requireUser();
    return clone(db.programs);
  },

  async redeemProgram(programId) {
    await delay();
    const user = requireUser();
    const employeeId = requireEmployeeId(user);
    const program = db.programs.find((p) => p.id === programId);
    if (!program) throw new ApiError(404, 'Программа не найдена. Обновите каталог.');
    if (!program.active) throw new ApiError(409, `Программа «${program.name}» сейчас недоступна. ${program.inactiveReason ?? ''}`.trim(), 'PROGRAM_INACTIVE');
    if (db.BLOCKED_ACCOUNTS.has(employeeId)) throw new ApiError(423, 'Баланс недоступен, обратитесь к HR.', 'ACCOUNT_BLOCKED');
    const balance = balanceOf(employeeId);
    if (balance < program.cost) {
      throw new ApiError(409, `Не хватает ${fmt(program.cost - balance)}: программа стоит ${fmt(program.cost)}, на счёте ${fmt(balance)}.`, 'INSUFFICIENT_FUNDS');
    }
    const e = db.findEmployee(employeeId)!;
    const op: Operation = {
      id: db.nextId('op'), number: db.nextOperationNumber(), employeeId, employeeName: e.fullName,
      departmentId: e.departmentId, type: 'writeoff', amount: -program.cost, status: 'written_off',
      basis: 'program', programId, reason: `Программа «${program.name}»`, createdAt: new Date().toISOString(), createdBy: user.fullName,
    };
    db.operations.unshift(op);
    log('Использование баллов', 'Программа', program.name);
    notify(employeeId, `Списано ${fmt(program.cost)}`, `Вы участвуете в программе «${program.name}». Детали придут на почту.`);
    return clone({ operation: op, account: accountOf(employeeId) });
  },

  async getDepartments() {
    await delay();
    requireUser();
    return clone(db.departments);
  },

  async getEmployees() {
    await delay();
    const user = requireUser('manager', 'hr', 'finance', 'admin');
    return clone(visibleEmployees(user));
  },

  async getTeam() {
    await delay();
    const user = requireUser('manager', 'hr');
    const monthStart = dayjs().startOf('month');
    const team: TeamMember[] = visibleEmployees(user).map((e) => {
      const acc = accountOf(e.id);
      const ops = db.operations.filter((o) => o.employeeId === e.id && !dayjs(o.createdAt).isBefore(monthStart));
      return {
        ...e,
        departmentName: db.departments.find((d) => d.id === e.departmentId)?.name ?? '',
        balance: acc.balance,
        accountStatus: acc.status,
        accruedThisMonth: sumAccrued(ops),
      };
    });
    return clone(team);
  },

  async previewAccrual(req) {
    await delay();
    requireUser('manager', 'hr');
    return clone(buildPreview(req));
  },

  async createAccrual(req) {
    await delay();
    const user = requireUser('manager', 'hr');
    const allowed = new Set(visibleEmployees(user).map((e) => e.id));
    if (req.employeeIds.some((id) => !allowed.has(id))) {
      throw new ApiError(403, 'Руководитель может начислять баллы только своей команде.');
    }
    if (!req.reason.trim() || req.amount <= 0) {
      throw new ApiError(422, 'Укажите сумму больше нуля и причину начисления.');
    }
    const preview = buildPreview(req);
    const created: Operation[] = preview.lines.map((line) => {
      const e = db.findEmployee(line.employeeId)!;
      const op: Operation = {
        id: db.nextId('op'), number: db.nextOperationNumber(), employeeId: e.id, employeeName: e.fullName,
        departmentId: e.departmentId, type: 'accrual', amount: line.total,
        status: line.needsApproval ? 'pending' : 'accrued', basis: req.basis, reason: req.reason.trim(),
        ruleId: req.ruleId, campaignId: line.coefficient !== 1 ? req.campaignId : undefined,
        createdAt: new Date().toISOString(), createdBy: user.fullName,
        comment: line.needsApproval ? `Превышен лимит ${fmt(db.ACCRUAL_LIMIT)} на сотрудника` : undefined,
      };
      db.operations.unshift(op);
      log('Ручное начисление', 'Операция', `${e.fullName}, +${fmt(line.total)}`);
      notify(e.id, line.needsApproval ? 'Начисление на согласовании' : `Начислено ${fmt(line.total)}`, req.reason.trim());
      return op;
    });
    return clone({
      operations: created,
      accruedCount: created.filter((o) => o.status === 'accrued').length,
      pendingCount: created.filter((o) => o.status === 'pending').length,
    });
  },

  async getOperations(filter) {
    await delay();
    requireUser('hr', 'finance');
    return clone(db.operations.filter((o) => matches(o, filter)));
  },

  async correctOperation(id, req) {
    await delay();
    requireUser('hr', 'manager');
    const op = db.operations.find((o) => o.id === id);
    if (!op) throw new ApiError(404, 'Операция не найдена. Обновите журнал.');
    if (op.type === 'writeoff' || !['accrued', 'corrected'].includes(op.status)) {
      throw new ApiError(409, `Операцию в статусе «${OPERATION_STATUS_LABELS[op.status]}» корректировать нельзя.`);
    }
    if (!req.reason.trim()) throw new ApiError(422, 'Укажите причину корректировки.');
    const newAmount = req.action === 'cancel' ? 0 : Math.round(req.newAmount ?? op.amount);
    if (newAmount < 0) throw new ApiError(422, 'Сумма не может быть отрицательной. Для отмены выберите «Отменить начисление».');
    const resulting = balanceOf(op.employeeId) - op.amount + newAmount;
    op.originalAmount = op.originalAmount ?? op.amount;
    op.amount = newAmount;
    op.type = 'correction';
    op.reason = req.reason.trim();
    if (resulting < 0) {
      op.status = 'pending';
      op.comment = `Баланс станет ${fmt(resulting)}: нужно согласование финотдела`;
    } else {
      op.status = req.action === 'cancel' ? 'cancelled' : 'corrected';
      op.comment = undefined;
    }
    log(req.action === 'cancel' ? 'Отмена начисления' : 'Корректировка', 'Операция', `${op.employeeName}, ${fmt(op.originalAmount)} → ${fmt(newAmount)}`);
    notify(op.employeeId, 'Начисление скорректировано', `${op.number}: ${fmt(op.originalAmount)} → ${fmt(newAmount)}. ${op.reason}`);
    return clone(op);
  },

  async getControlQueue() {
    await delay();
    requireUser('finance');
    return clone(db.operations.filter((o) => o.status === 'pending' || (o.disputed && SETTLED.includes(o.status))));
  },

  async approveOperation(id) {
    await delay();
    requireUser('finance');
    const op = db.operations.find((o) => o.id === id);
    if (!op) throw new ApiError(404, 'Операция не найдена. Обновите очередь.');
    if (op.status === 'pending') {
      op.status = op.type === 'correction' ? (op.amount === 0 ? 'cancelled' : 'corrected') : 'accrued';
    }
    op.disputed = false;
    op.comment = undefined;
    log('Подтверждение начисления', 'Операция', `${op.employeeName}, ${fmt(op.amount)}`);
    notify(op.employeeId, 'Начисление подтверждено', `${op.number}: ${op.reason}`);
    return clone(op);
  },

  async blockOperation(id, reason) {
    await delay();
    requireUser('finance');
    if (!reason.trim()) throw new ApiError(422, 'Укажите причину блокировки — её увидит сотрудник.');
    const op = db.operations.find((o) => o.id === id);
    if (!op) throw new ApiError(404, 'Операция не найдена. Обновите очередь.');
    if (op.type === 'correction' && op.originalAmount !== undefined && op.status === 'pending') {
      // Отклонённая корректировка: возвращаем исходную сумму.
      op.amount = op.originalAmount;
    }
    op.status = 'blocked';
    op.disputed = false;
    op.comment = reason.trim();
    log('Блокировка начисления', 'Операция', `${op.employeeName}, ${fmt(op.amount)}`);
    notify(op.employeeId, 'Начисление заблокировано', `${op.number}: ${reason.trim()}`);
    return clone(op);
  },

  async getRules() {
    await delay();
    requireUser('hr', 'manager');
    return clone(db.rules);
  },

  async saveRule(input, id) {
    await delay();
    requireUser('hr');
    const formula = db.formatFormula(input.basePoints, input.weight);
    let result: RuleSaveResult;
    if (id) {
      const rule = db.rules.find((r) => r.id === id);
      if (!rule) throw new ApiError(404, 'Правило не найдено. Обновите список.');
      const deferred = rule.usedInCurrentPeriod;
      Object.assign(rule, input, { formula, updatedAt: new Date().toISOString() });
      const nextPeriod = dayjs().add(1, 'month').startOf('month');
      result = { rule, deferred, effectiveFrom: (deferred ? nextPeriod : dayjs()).toISOString() };
      log('Изменение правила', 'Правило', rule.name);
    } else {
      const rule = { ...input, id: db.nextId('r'), formula, usedInCurrentPeriod: false, updatedAt: new Date().toISOString() };
      db.rules.push(rule);
      result = { rule, deferred: false, effectiveFrom: new Date().toISOString() };
      log('Создание правила', 'Правило', rule.name);
    }
    return clone(result);
  },

  async getCampaigns() {
    await delay();
    requireUser('hr', 'manager');
    return clone(db.campaigns);
  },

  async createCampaign(input) {
    await delay();
    requireUser('hr');
    if (dayjs(input.endDate).isBefore(dayjs(input.startDate))) {
      throw new ApiError(422, 'Дата окончания раньше даты начала. Проверьте сроки кампании.');
    }
    const c: Campaign = { ...input, id: db.nextId('c'), spent: 0, status: campaignStatus(input) };
    db.campaigns.unshift(c);
    log('Создание кампании', 'Кампания', c.name);
    return clone(c);
  },

  async buildReport(type, from, to) {
    await delay();
    requireUser('hr', 'finance');
    const inPeriod = db.operations.filter((o) => matches(o, { from, to }));
    let rows: ReportRow[];
    if (type === 'employees') {
      rows = db.employees.map((e) => {
        const ops = inPeriod.filter((o) => o.employeeId === e.id);
        return {
          key: e.id, name: e.fullName, group: db.departments.find((d) => d.id === e.departmentId)?.name,
          accrued: sumAccrued(ops), spent: sumSpent(ops), pending: sumPending(ops), balance: balanceOf(e.id), operations: ops.length,
        };
      });
    } else if (type === 'departments') {
      rows = db.departments.map((d) => {
        const ops = inPeriod.filter((o) => o.departmentId === d.id);
        return {
          key: d.id, name: d.name, group: `${db.employees.filter((e) => e.departmentId === d.id).length} сотр.`,
          accrued: sumAccrued(ops), spent: sumSpent(ops), pending: sumPending(ops),
          balance: db.employees.filter((e) => e.departmentId === d.id).reduce((s, e) => s + balanceOf(e.id), 0),
          operations: ops.length,
        };
      });
    } else {
      rows = db.campaigns.map((c) => {
        const ops = inPeriod.filter((o) => o.campaignId === c.id);
        return {
          key: c.id, name: c.name, group: c.budget === null ? 'Бюджет не выделен' : `Бюджет ${fmt(c.budget)}`,
          accrued: sumAccrued(ops), spent: 0, pending: sumPending(ops),
          balance: c.budget === null ? undefined : c.budget - c.spent, operations: ops.length,
        };
      });
    }
    rows = rows.filter((r) => r.operations > 0);
    log('Формирование отчёта', 'Отчёт', `${type}: ${dayjs(from).format('DD.MM.YYYY')}–${dayjs(to).format('DD.MM.YYYY')}`);
    const report: Report = { type, from, to, rows, generatedAt: new Date().toISOString() };
    return clone(report);
  },

  async getAnalytics(period: AnalyticsPeriod) {
    await delay();
    const user = requireUser('hr', 'manager');
    const scope = user.role === 'manager'
      ? db.employees.filter((e) => e.departmentId === user.departmentId)
      : db.employees;
    const ids = new Set(scope.map((e) => e.id));
    const ops = db.operations.filter((o) => ids.has(o.employeeId));
    const months = period === 'month' ? 1 : period === 'quarter' ? 3 : 6;
    const curStart = dayjs().startOf('month').subtract(months - 1, 'month');
    const prevStart = curStart.subtract(months, 'month');
    const oldest = ops.reduce((m, o) => (dayjs(o.createdAt).isBefore(m) ? dayjs(o.createdAt) : m), dayjs());
    const cur = ops.filter((o) => !dayjs(o.createdAt).isBefore(curStart));
    const prev = ops.filter((o) => !dayjs(o.createdAt).isBefore(prevStart) && dayjs(o.createdAt).isBefore(curStart));
    const conversion = (list: Operation[]) => {
      const users = new Set(list.filter((o) => o.type === 'writeoff' && o.status === 'written_off').map((o) => o.employeeId));
      return Math.round((users.size / scope.length) * 100);
    };
    const label = (start: dayjs.Dayjs) =>
      months === 1 ? start.format('MMMM YYYY') : `${start.format('MMM')} – ${start.add(months - 1, 'month').format('MMM YYYY')}`;
    const deptIds = user.role === 'manager' ? [user.departmentId] : db.departments.map((d) => d.id);
    const dashboard: AnalyticsDashboard = {
      period,
      currentLabel: label(curStart),
      previousLabel: label(prevStart),
      averageBonus: { current: Math.round(sumAccrued(cur) / scope.length), previous: Math.round(sumAccrued(prev) / scope.length) },
      programConversion: { current: conversion(cur), previous: conversion(prev) },
      totalAccrued: { current: sumAccrued(cur), previous: sumAccrued(prev) },
      totalSpent: { current: sumSpent(cur), previous: sumSpent(prev) },
      byDepartment: deptIds.map((id) => ({
        department: db.departments.find((d) => d.id === id)!.name,
        current: sumAccrued(cur.filter((o) => o.departmentId === id)),
        previous: sumAccrued(prev.filter((o) => o.departmentId === id)),
      })),
      monthly: Array.from({ length: 6 }, (_, i) => {
        const m = dayjs().startOf('month').subtract(5 - i, 'month');
        const list = ops.filter((o) => dayjs(o.createdAt).isSame(m, 'month'));
        return { month: m.format('MMM'), accrued: sumAccrued(list), spent: sumSpent(list) };
      }),
      partial: oldest.startOf('month').isAfter(prevStart),
    };
    return clone(dashboard);
  },

  async getUsers() {
    await delay();
    requireUser('admin');
    return clone(db.users);
  },

  async findUserByEmail(email) {
    await delay();
    requireUser('admin');
    const u = db.users.find((x) => x.email.toLowerCase() === email.trim().toLowerCase());
    return u ? clone(u) : null;
  },

  async saveUser(input, id) {
    await delay();
    requireUser('admin');
    const email = input.email.trim().toLowerCase();
    const sameEmail = db.users.find((u) => u.email.toLowerCase() === email);
    if (!id && sameEmail) {
      throw new ApiError(409, `Пользователь с email ${email} уже есть.`, 'EMAIL_EXISTS');
    }
    if (id) {
      const user = db.users.find((u) => u.id === id);
      if (!user) throw new ApiError(404, 'Пользователь не найден. Обновите список.');
      if (sameEmail && sameEmail.id !== id) throw new ApiError(409, `Email ${email} уже занят другим пользователем.`, 'EMAIL_EXISTS');
      const roleChanged = user.role !== input.role;
      Object.assign(user, input, { email });
      log(roleChanged ? 'Изменение роли' : 'Изменение пользователя', 'Пользователь', user.fullName);
      return clone(user);
    }
    const user: User = { ...input, email, id: db.nextId('u'), status: 'active' };
    db.users.unshift(user);
    log('Создание пользователя', 'Пользователь', user.fullName);
    return clone(user);
  },

  async setUserStatus(id, status) {
    await delay();
    const admin = requireUser('admin');
    const user = db.users.find((u) => u.id === id);
    if (!user) throw new ApiError(404, 'Пользователь не найден. Обновите список.');
    if (user.id === admin.id) throw new ApiError(409, 'Нельзя заблокировать собственную учётную запись.');
    user.status = status;
    log(status === 'blocked' ? 'Блокировка пользователя' : 'Разблокировка пользователя', 'Пользователь', user.fullName);
    return clone(user);
  },

  async getIntegrations() {
    await delay();
    requireUser('admin');
    return clone(db.integrations);
  },

  async testIntegration(id, params) {
    await new Promise((r) => setTimeout(r, 700 + Math.random() * 500));
    requireUser('admin');
    const integration = db.integrations.find((i) => i.id === id);
    if (!integration) throw new ApiError(404, 'Интеграция не найдена.');
    const checkedAt = new Date().toISOString();
    let check;
    if (!/^https:\/\/[\w.-]+/i.test(params.url.trim())) {
      check = { ok: false, message: 'Сервер отклонил подключение: нужен адрес HTTPS', checkedAt };
    } else if (params.apiKey.trim().length < 8) {
      check = { ok: false, message: `Ключ не принят: ${integration.name} ожидает ключ не короче 8 символов`, checkedAt };
    } else {
      check = { ok: true, message: 'Соединение установлено', checkedAt, latencyMs: 60 + Math.round(Math.random() * 180) };
    }
    integration.lastCheck = check;
    log('Проверка соединения', 'Интеграция', `${integration.name} — ${check.ok ? 'успех' : 'ошибка'}`);
    return clone(check);
  },

  async saveIntegration(id, patch) {
    await delay();
    requireUser('admin');
    const integration = db.integrations.find((i) => i.id === id);
    if (!integration) throw new ApiError(404, 'Интеграция не найдена.');
    if (patch.enabled && !integration.lastCheck?.ok) {
      throw new ApiError(409, 'Сначала добейтесь успешной проверки соединения, потом включайте интеграцию.');
    }
    Object.assign(integration, patch);
    if (patch.enabled) integration.lastSyncAt = new Date().toISOString();
    const what = patch.enabled === undefined ? 'Изменение параметров' : patch.enabled ? 'Включение интеграции' : 'Отключение интеграции';
    log(what, 'Интеграция', integration.name);
    return clone(integration);
  },

  async getAudit() {
    await delay();
    requireUser('admin');
    return clone<AuditEntry[]>(db.audit);
  },

  async getNotifications() {
    await delay();
    const user = requireUser();
    return clone(db.notifications.get(user.id) ?? []);
  },

  async markNotificationsRead() {
    await delay();
    const user = requireUser();
    (db.notifications.get(user.id) ?? []).forEach((n) => (n.read = true));
  },
};

/** Расчёт начисления (UC6): сумма × коэффициент кампании, проверка лимита. */
function buildPreview(req: AccrualRequest): AccrualPreview {
  const lines = req.employeeIds.map((id) => {
    const e = db.findEmployee(id);
    if (!e) throw new ApiError(422, 'В списке есть неизвестный сотрудник. Вернитесь к шагу 1.');
    const coefficient = coefficientFor(id, req.campaignId);
    const total = Math.round(req.amount * coefficient);
    return { employeeId: id, employeeName: e.fullName, baseAmount: req.amount, coefficient, total, needsApproval: total > db.ACCRUAL_LIMIT };
  });
  return {
    lines,
    total: lines.reduce((s, l) => s + l.total, 0),
    limit: db.ACCRUAL_LIMIT,
    approvalCount: lines.filter((l) => l.needsApproval).length,
  };
}
