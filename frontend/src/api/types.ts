/**
 * Типы DTO клиента TechnoSoft Bonus.
 * Совпадают с JSON-контрактами REST API серверной части (Spring Boot, CON-3, SI-2):
 * даты — строки ISO 8601 (DATA-3), суммы — целые баллы.
 */

/** Роли ролевой модели (SEC-3). */
export type Role = 'employee' | 'manager' | 'hr' | 'finance' | 'admin';

export const ROLE_LABELS: Record<Role, string> = {
  employee: 'Сотрудник',
  manager: 'Руководитель',
  hr: 'HR',
  finance: 'Финансовый отдел',
  admin: 'Администратор',
};

export interface Department {
  id: string;
  name: string;
  /** Идентификатор сотрудника-руководителя подразделения. */
  headId?: string;
}

export interface Employee {
  id: string;
  fullName: string;
  email: string;
  position: string;
  departmentId: string;
  managerId?: string;
  hiredAt: string;
  /** Выполнение KPI за текущий период, % (0–150). */
  kpiCompletion: number;
}

export type AccountStatus = 'active' | 'blocked';

/** Единый бонусный счёт сотрудника (BR-6). */
export interface BonusAccount {
  employeeId: string;
  status: AccountStatus;
  /** Доступный баланс, баллы. */
  balance: number;
  /** Сумма начислений, ожидающих согласования, баллы. */
  pending: number;
  updatedAt: string;
}

export type OperationType = 'accrual' | 'writeoff' | 'correction';

export const OPERATION_TYPE_LABELS: Record<OperationType, string> = {
  accrual: 'Начисление',
  writeoff: 'Списание',
  correction: 'Корректировка',
};

export type OperationStatus =
  | 'accrued'
  | 'written_off'
  | 'corrected'
  | 'pending'
  | 'blocked'
  | 'cancelled';

export const OPERATION_STATUS_LABELS: Record<OperationStatus, string> = {
  accrued: 'Начислено',
  written_off: 'Списано',
  corrected: 'Скорректировано',
  pending: 'На согласовании',
  blocked: 'Заблокировано',
  cancelled: 'Отменено',
};

/** Основание операции. */
export type OperationBasis = 'kpi' | 'project' | 'achievement' | 'campaign' | 'program' | 'correction';

export const BASIS_LABELS: Record<OperationBasis, string> = {
  kpi: 'Выполнение KPI',
  project: 'Проектная задача',
  achievement: 'Особое достижение',
  campaign: 'Бонусная кампания',
  program: 'Программа мотивации',
  correction: 'Корректировка',
};

export interface Operation {
  id: string;
  /** Номер для людей: «ОП-000123». */
  number: string;
  employeeId: string;
  employeeName: string;
  departmentId: string;
  type: OperationType;
  /** Сумма со знаком: «+» начисление, «−» списание. */
  amount: number;
  /** Сумма до корректировки (UC7). */
  originalAmount?: number;
  status: OperationStatus;
  basis: OperationBasis;
  reason: string;
  ruleId?: string;
  campaignId?: string;
  programId?: string;
  createdAt: string;
  createdBy: string;
  /** Комментарий финотдела или причина корректировки/блокировки. */
  comment?: string;
  /** Помечено финотделом как спорное. */
  disputed?: boolean;
}

export interface OperationFilter {
  employeeId?: string;
  departmentId?: string;
  type?: OperationType;
  status?: OperationStatus[];
  /** ISO-дата начала периода включительно. */
  from?: string;
  /** ISO-дата конца периода включительно. */
  to?: string;
  /** Поиск по ФИО, номеру или причине. */
  query?: string;
}

export type RulePeriodicity = 'month' | 'quarter' | 'year' | 'event';

export const PERIODICITY_LABELS: Record<RulePeriodicity, string> = {
  month: 'Ежемесячно',
  quarter: 'Ежеквартально',
  year: 'Ежегодно',
  event: 'По событию',
};

/** Правило начисления (UC8, BR-4: размер = вес KPI × уровень достижения). */
export interface Rule {
  id: string;
  name: string;
  kpi: string;
  /** Вес показателя, 0.05–1. */
  weight: number;
  /** Базовая сумма в баллах, к которой применяется формула. */
  basePoints: number;
  formula: string;
  periodicity: RulePeriodicity;
  /** Порог срабатывания — минимальное выполнение KPI, %. */
  threshold: number;
  active: boolean;
  /** Правило уже применялось в расчётах текущего периода. */
  usedInCurrentPeriod: boolean;
  updatedAt: string;
}

export type RuleInput = Omit<Rule, 'id' | 'usedInCurrentPeriod' | 'updatedAt' | 'formula'>;

export interface RuleSaveResult {
  rule: Rule;
  /** Изменения отложены до следующего периода (альт. поток UC8). */
  effectiveFrom: string;
  deferred: boolean;
}

export type CampaignStatus = 'active' | 'planned' | 'needs_approval' | 'finished';

export const CAMPAIGN_STATUS_LABELS: Record<CampaignStatus, string> = {
  active: 'Активна',
  planned: 'Запланирована',
  needs_approval: 'Требуется утверждение',
  finished: 'Завершена',
};

export interface Campaign {
  id: string;
  name: string;
  description: string;
  startDate: string;
  endDate: string;
  departmentIds: string[];
  /** Коэффициент к начислениям в период кампании. */
  coefficient: number;
  /** Бюджет в баллах; null — бюджет не выделен. */
  budget: number | null;
  spent: number;
  status: CampaignStatus;
}

export type CampaignInput = Pick<
  Campaign,
  'name' | 'description' | 'startDate' | 'endDate' | 'departmentIds' | 'coefficient' | 'budget'
>;

export type ProgramCategory = 'gift' | 'education' | 'merch' | 'dayoff' | 'health' | 'event';

export const PROGRAM_CATEGORY_LABELS: Record<ProgramCategory, string> = {
  gift: 'Подарки',
  education: 'Обучение',
  merch: 'Мерч',
  dayoff: 'Отдых',
  health: 'Здоровье',
  event: 'События',
};

/** Программа мотивации, на которую тратятся баллы (UC3). */
export interface Program {
  id: string;
  name: string;
  category: ProgramCategory;
  description: string;
  cost: number;
  active: boolean;
  /** Пояснение, если программа недоступна. */
  inactiveReason?: string;
}

export interface Redemption {
  operation: Operation;
  account: BonusAccount;
}

export type UserStatus = 'active' | 'blocked';

export interface User {
  id: string;
  fullName: string;
  email: string;
  departmentId: string;
  position: string;
  role: Role;
  status: UserStatus;
  /** Связанный сотрудник с бонусным счётом. */
  employeeId?: string;
  lastLoginAt?: string;
}

export type UserInput = Pick<User, 'fullName' | 'email' | 'departmentId' | 'position' | 'role'>;

export interface AuthSession {
  token: string;
  user: User;
}

export type IntegrationKind = 'hris' | 'erp' | 'ad' | 'notifications';

export interface Integration {
  id: string;
  kind: IntegrationKind;
  name: string;
  description: string;
  url: string;
  apiKey: string;
  enabled: boolean;
  lastSyncAt?: string;
  lastCheck?: IntegrationCheck;
}

export interface IntegrationCheck {
  ok: boolean;
  message: string;
  checkedAt: string;
  latencyMs?: number;
}

export interface AuditEntry {
  id: string;
  timestamp: string;
  userName: string;
  role: Role | 'system';
  action: string;
  objectType: string;
  objectLabel: string;
  ip: string;
}

export interface Notification {
  id: string;
  title: string;
  text: string;
  createdAt: string;
  read: boolean;
}

/** Сводка для экрана «Мой баланс» (UC1). */
export interface AccountSummary {
  account: BonusAccount;
  accruedThisMonth: number;
  spentThisMonth: number;
  recent: Operation[];
  /** Баланс на конец каждого из последних месяцев. */
  history: { month: string; balance: number }[];
}

export interface TeamMember extends Employee {
  departmentName: string;
  balance: number;
  accountStatus: AccountStatus;
  accruedThisMonth: number;
}

/** Ручное начисление (UC5) с расчётом (UC6). */
export interface AccrualRequest {
  employeeIds: string[];
  /** Сумма на одного сотрудника до коэффициента. */
  amount: number;
  basis: Extract<OperationBasis, 'kpi' | 'project' | 'achievement'>;
  reason: string;
  ruleId?: string;
  campaignId?: string;
}

export interface AccrualPreviewLine {
  employeeId: string;
  employeeName: string;
  baseAmount: number;
  coefficient: number;
  total: number;
  needsApproval: boolean;
}

export interface AccrualPreview {
  lines: AccrualPreviewLine[];
  total: number;
  limit: number;
  approvalCount: number;
}

export interface AccrualResult {
  operations: Operation[];
  accruedCount: number;
  pendingCount: number;
}

/** Корректировка операции (UC7). */
export interface CorrectionRequest {
  action: 'adjust' | 'cancel';
  newAmount?: number;
  reason: string;
}

export type ReportType = 'employees' | 'departments' | 'campaigns';

export interface ReportRow {
  key: string;
  name: string;
  group?: string;
  accrued: number;
  spent: number;
  pending: number;
  balance?: number;
  operations: number;
}

export interface Report {
  type: ReportType;
  from: string;
  to: string;
  rows: ReportRow[];
  generatedAt: string;
}

export type AnalyticsPeriod = 'month' | 'quarter' | 'halfyear';

export interface AnalyticsDashboard {
  period: AnalyticsPeriod;
  currentLabel: string;
  previousLabel: string;
  averageBonus: { current: number; previous: number };
  programConversion: { current: number; previous: number };
  totalAccrued: { current: number; previous: number };
  totalSpent: { current: number; previous: number };
  byDepartment: { department: string; current: number; previous: number }[];
  monthly: { month: string; accrued: number; spent: number }[];
  /** Данных меньше, чем нужно для полного сравнения (альт. поток UC11). */
  partial: boolean;
}
