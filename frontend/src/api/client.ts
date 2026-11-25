import type {
  AccountSummary,
  AccrualPreview,
  AccrualRequest,
  AccrualResult,
  AnalyticsDashboard,
  AnalyticsPeriod,
  AuditEntry,
  AuthSession,
  Campaign,
  CampaignInput,
  CorrectionRequest,
  Department,
  Employee,
  Integration,
  IntegrationCheck,
  Notification,
  Operation,
  OperationFilter,
  Program,
  Redemption,
  Report,
  ReportType,
  Rule,
  RuleInput,
  RuleSaveResult,
  TeamMember,
  User,
  UserInput,
} from './types';

/**
 * Ошибка вызова API. `status` повторяет HTTP-код, который вернёт сервер,
 * `message` — готовый к показу текст: что случилось и что сделать.
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Контракт клиента REST API TechnoSoft Bonus (Spring Boot, базовый путь /api/v1).
 * Сейчас реализован мок-клиентом (mock/mockApi.ts); в ЛР 9 подключается
 * HTTP-реализация с теми же сигнатурами — экраны при этом не меняются.
 */
export interface BonusApi {
  // ── Аутентификация (SEC-1) ──────────────────────────────────────────────
  /** `POST /api/v1/auth/login` — вход по email и паролю, возвращает JWT и профиль. */
  login(email: string, password: string): Promise<AuthSession>;
  /** `GET /api/v1/auth/me` — профиль по сохранённому токену. */
  restoreSession(token: string): Promise<AuthSession>;
  /** `POST /api/v1/auth/logout` — завершение сессии. */
  logout(): Promise<void>;

  // ── Бонусный счёт сотрудника (UC1, UC2) ─────────────────────────────────
  /** `GET /api/v1/accounts/me` — баланс, итоги месяца, последние операции, динамика за 6 мес. */
  getMyAccount(): Promise<AccountSummary>;
  /** `GET /api/v1/accounts/me/operations?from&to&type` — история операций своего счёта. */
  getMyOperations(filter?: Pick<OperationFilter, 'from' | 'to' | 'type'>): Promise<Operation[]>;

  // ── Программы мотивации (UC3) ───────────────────────────────────────────
  /** `GET /api/v1/programs` — каталог программ. */
  getPrograms(): Promise<Program[]>;
  /** `POST /api/v1/programs/{id}/redemptions` — списание баллов на программу. */
  redeemProgram(programId: string): Promise<Redemption>;

  // ── Сотрудники и подразделения ──────────────────────────────────────────
  /** `GET /api/v1/departments` */
  getDepartments(): Promise<Department[]>;
  /** `GET /api/v1/employees?scope=team|all` — для руководителя только подчинённые. */
  getEmployees(): Promise<Employee[]>;
  /** `GET /api/v1/employees/team` — подчинённые/все сотрудники с балансами и KPI. */
  getTeam(): Promise<TeamMember[]>;

  // ── Начисления (UC5, UC6) ───────────────────────────────────────────────
  /** `POST /api/v1/accruals/preview` — расчёт без сохранения: коэффициенты, лимиты. */
  previewAccrual(request: AccrualRequest): Promise<AccrualPreview>;
  /** `POST /api/v1/accruals` — ручное начисление одному или нескольким сотрудникам. */
  createAccrual(request: AccrualRequest): Promise<AccrualResult>;

  // ── Журнал операций (UC7) ───────────────────────────────────────────────
  /** `GET /api/v1/operations?employeeId&departmentId&type&status&from&to&q` */
  getOperations(filter?: OperationFilter): Promise<Operation[]>;
  /** `PUT /api/v1/operations/{id}` — корректировка суммы или отмена операции. */
  correctOperation(id: string, request: CorrectionRequest): Promise<Operation>;

  // ── Контроль финотдела ──────────────────────────────────────────────────
  /** `GET /api/v1/control/operations` — начисления на согласовании и спорные. */
  getControlQueue(): Promise<Operation[]>;
  /** `POST /api/v1/control/operations/{id}/approve` */
  approveOperation(id: string): Promise<Operation>;
  /** `POST /api/v1/control/operations/{id}/block` — блокировка с обязательной причиной. */
  blockOperation(id: string, reason: string): Promise<Operation>;

  // ── Правила начисления (UC8) ────────────────────────────────────────────
  /** `GET /api/v1/rules` — HR редактирует, руководитель читает (выбор правила в мастере начисления). */
  getRules(): Promise<Rule[]>;
  /** `POST /api/v1/rules` (без id) или `PUT /api/v1/rules/{id}` */
  saveRule(input: RuleInput, id?: string): Promise<RuleSaveResult>;

  // ── Бонусные кампании (UC9) ─────────────────────────────────────────────
  /** `GET /api/v1/campaigns` */
  getCampaigns(): Promise<Campaign[]>;
  /** `POST /api/v1/campaigns` — без бюджета получает статус «Требуется утверждение». */
  createCampaign(input: CampaignInput): Promise<Campaign>;

  // ── Отчёты и аналитика (UC10, UC11) ─────────────────────────────────────
  /** `GET /api/v1/reports/{type}?from&to` */
  buildReport(type: ReportType, from: string, to: string): Promise<Report>;
  /** `GET /api/v1/analytics/dashboard?period=month|quarter|halfyear` */
  getAnalytics(period: AnalyticsPeriod): Promise<AnalyticsDashboard>;

  // ── Пользователи (UC12) ─────────────────────────────────────────────────
  /** `GET /api/v1/users` */
  getUsers(): Promise<User[]>;
  /** `GET /api/v1/users?email=` — проверка дубля email перед созданием. */
  findUserByEmail(email: string): Promise<User | null>;
  /** `POST /api/v1/users` (без id) или `PUT /api/v1/users/{id}` */
  saveUser(input: UserInput, id?: string): Promise<User>;
  /** `PATCH /api/v1/users/{id}/status` — блокировка и разблокировка. */
  setUserStatus(id: string, status: User['status']): Promise<User>;

  // ── Интеграции (UC13) ───────────────────────────────────────────────────
  /** `GET /api/v1/integrations` */
  getIntegrations(): Promise<Integration[]>;
  /** `POST /api/v1/integrations/{id}/test` — проверка соединения с переданными параметрами. */
  testIntegration(id: string, params: Pick<Integration, 'url' | 'apiKey'>): Promise<IntegrationCheck>;
  /** `PUT /api/v1/integrations/{id}` */
  saveIntegration(id: string, patch: Partial<Pick<Integration, 'url' | 'apiKey' | 'enabled'>>): Promise<Integration>;

  // ── Аудит и уведомления (NFR-3, SEC-4) ──────────────────────────────────
  /** `GET /api/v1/audit?from&to&q` */
  getAudit(): Promise<AuditEntry[]>;
  /** `GET /api/v1/notifications` */
  getNotifications(): Promise<Notification[]>;
  /** `POST /api/v1/notifications/read` — отметить все прочитанными. */
  markNotificationsRead(): Promise<void>;
}
