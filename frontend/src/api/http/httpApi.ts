import { ApiError, type BonusApi } from '../client';
import { SESSION_TOKEN_KEY } from '../session';
import type {
  AccountSummary,
  AccrualPreview,
  AccrualResult,
  AnalyticsDashboard,
  AuditEntry,
  AuthSession,
  Campaign,
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
  Rule,
  RuleSaveResult,
  TeamMember,
  User,
} from '../types';

/**
 * HTTP-реализация BonusApi поверх fetch: REST API Spring Boot (CON-3), JSON, JWT в заголовке
 * Authorization. Ошибки сервера ({ status, code, message, path, timestamp }) превращаются
 * в ApiError — экраны обрабатывают их так же, как ошибки мок-клиента.
 */

const BASE_URL = (import.meta.env.VITE_API_URL ?? '/api/v1').replace(/\/$/, '');

/** Токен текущей сессии: после входа — в памяти, после перезагрузки вкладки — из sessionStorage. */
let token: string | null = null;

const currentToken = () => token ?? sessionStorage.getItem(SESSION_TOKEN_KEY);

type Query = Record<string, string | string[] | undefined>;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Query;
  /** Токен для этого запроса вместо текущего (восстановление сессии). */
  bearer?: string | null;
}

function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === undefined || value === '') return;
    (Array.isArray(value) ? value : [value]).forEach((v) => params.append(key, v));
  });
  const qs = params.toString();
  return `${BASE_URL}${path}${qs ? `?${qs}` : ''}`;
}

const id = (value: string) => encodeURIComponent(value);

async function request<T>(path: string, { method = 'GET', body, query, bearer }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const auth = bearer === undefined ? currentToken() : bearer;
  if (auth) headers.Authorization = `Bearer ${auth}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Нет связи с сервером. Проверьте подключение и повторите попытку.', 'NETWORK_ERROR');
  }

  if (response.status === 204) return undefined as T;
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) throw toApiError(response.status, payload);
  return payload as T;
}

function toApiError(status: number, payload: unknown): ApiError {
  if (payload && typeof payload === 'object' && 'message' in payload) {
    const p = payload as { message?: string; code?: string };
    if (p.message) return new ApiError(status, p.message, p.code);
  }
  const fallback =
    status === 401 ? 'Сессия закончилась. Войдите снова.'
      : status === 403 ? 'Для этого действия у вашей роли нет прав.'
        : status >= 500 ? 'Сервер временно недоступен. Повторите попытку позже.'
          : `Запрос не выполнен (код ${status}).`;
  return new ApiError(status, fallback);
}

export const httpApi: BonusApi = {
  // ── Аутентификация ───────────────────────────────────────────────────────
  async login(email, password) {
    const session = await request<AuthSession>('/auth/login', { method: 'POST', body: { email, password }, bearer: null });
    token = session.token;
    return session;
  },

  async restoreSession(saved) {
    const session = await request<AuthSession>('/auth/me', { bearer: saved });
    token = session.token;
    return session;
  },

  async logout() {
    try {
      await request<void>('/auth/logout', { method: 'POST' });
    } catch (e) {
      // Истёкший токен — выход всё равно выполняется на клиенте.
      if (!(e instanceof ApiError && e.status === 401)) throw e;
    } finally {
      token = null;
    }
  },

  // ── Счёт сотрудника ──────────────────────────────────────────────────────
  getMyAccount: () => request<AccountSummary>('/accounts/me'),
  getMyOperations: (filter) =>
    request<Operation[]>('/accounts/me/operations', { query: { from: filter?.from, to: filter?.to, type: filter?.type } }),

  // ── Программы мотивации ──────────────────────────────────────────────────
  getPrograms: () => request<Program[]>('/programs'),
  redeemProgram: (programId) => request<Redemption>(`/programs/${id(programId)}/redemptions`, { method: 'POST' }),

  // ── Сотрудники и подразделения ───────────────────────────────────────────
  getDepartments: () => request<Department[]>('/departments'),
  getEmployees: () => request<Employee[]>('/employees'),
  getTeam: () => request<TeamMember[]>('/employees/team'),

  // ── Начисления ───────────────────────────────────────────────────────────
  previewAccrual: (req) => request<AccrualPreview>('/accruals/preview', { method: 'POST', body: req }),
  createAccrual: (req) => request<AccrualResult>('/accruals', { method: 'POST', body: req }),

  // ── Журнал операций ──────────────────────────────────────────────────────
  getOperations: (filter: OperationFilter = {}) =>
    request<Operation[]>('/operations', {
      query: {
        employeeId: filter.employeeId,
        departmentId: filter.departmentId,
        type: filter.type,
        status: filter.status,
        from: filter.from,
        to: filter.to,
        q: filter.query?.trim(),
      },
    }),
  correctOperation: (opId, req) => request<Operation>(`/operations/${id(opId)}`, { method: 'PUT', body: req }),

  // ── Контроль финотдела ───────────────────────────────────────────────────
  getControlQueue: () => request<Operation[]>('/control/operations'),
  approveOperation: (opId) => request<Operation>(`/control/operations/${id(opId)}/approve`, { method: 'POST' }),
  blockOperation: (opId, reason) =>
    request<Operation>(`/control/operations/${id(opId)}/block`, { method: 'POST', body: { reason } }),

  // ── Правила и кампании ───────────────────────────────────────────────────
  getRules: () => request<Rule[]>('/rules'),
  saveRule: (input, ruleId) =>
    ruleId
      ? request<RuleSaveResult>(`/rules/${id(ruleId)}`, { method: 'PUT', body: input })
      : request<RuleSaveResult>('/rules', { method: 'POST', body: input }),
  getCampaigns: () => request<Campaign[]>('/campaigns'),
  createCampaign: (input) => request<Campaign>('/campaigns', { method: 'POST', body: input }),

  // ── Отчёты и аналитика ───────────────────────────────────────────────────
  buildReport: (type, from, to) => request<Report>(`/reports/${id(type)}`, { query: { from, to } }),
  getAnalytics: (period) => request<AnalyticsDashboard>('/analytics/dashboard', { query: { period } }),

  // ── Пользователи ─────────────────────────────────────────────────────────
  getUsers: () => request<User[]>('/users'),
  async findUserByEmail(email) {
    const found = await request<User[]>('/users', { query: { email: email.trim() } });
    return found[0] ?? null;
  },
  saveUser: (input, userId) =>
    userId
      ? request<User>(`/users/${id(userId)}`, { method: 'PUT', body: input })
      : request<User>('/users', { method: 'POST', body: input }),
  setUserStatus: (userId, status) => request<User>(`/users/${id(userId)}/status`, { method: 'PATCH', body: { status } }),

  // ── Интеграции ───────────────────────────────────────────────────────────
  getIntegrations: () => request<Integration[]>('/integrations'),
  testIntegration: (integrationId, params) =>
    request<IntegrationCheck>(`/integrations/${id(integrationId)}/test`, { method: 'POST', body: params }),
  saveIntegration: (integrationId, patch) =>
    request<Integration>(`/integrations/${id(integrationId)}`, { method: 'PUT', body: patch }),

  // ── Аудит и уведомления ──────────────────────────────────────────────────
  getAudit: () => request<AuditEntry[]>('/audit'),
  getNotifications: () => request<Notification[]>('/notifications'),
  markNotificationsRead: () => request<void>('/notifications/read', { method: 'POST' }),
};
