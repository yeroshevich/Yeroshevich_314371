/**
 * Общие функции скриптов измерений ЛР 11 (Node 20+, без зависимостей проекта, кроме playwright-core клиента).
 * Сервер — установленный дистрибутив (по умолчанию http://localhost:8080, переменная BASE_URL).
 */
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const HERE = dirname(fileURLToPath(import.meta.url));
export const ROOT = resolve(HERE, '..', '..');
export const OUT = resolve(HERE, 'out');
export const BASE = (process.env.BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
export const API = `${BASE}/api/v1`;
export const PASSWORD = 'demo';

/** playwright-core из node_modules клиента (frontend/), браузер — установленный Google Chrome. */
export function chromium() {
  return createRequire(resolve(ROOT, 'frontend', 'package.json'))('playwright-core').chromium;
}

export const ACCOUNTS = {
  employee: 'anna.kovaleva@technosoft.by',
  manager: 'dmitry.savitsky@technosoft.by',
  hr: 'olga.melnik@technosoft.by',
  finance: 'igor.zhuk@technosoft.by',
  admin: 'sergey.litvinov@technosoft.by',
};
export const ROLES = Object.keys(ACCOUNTS);

/** HTTP-запрос к серверу: статус, заголовки, тело (JSON, если разбирается), время ответа, мс. */
export async function http(method, path, { token, body, raw, headers = {}, timeoutMs } = {}) {
  const url = path.startsWith('http') ? path : `${API}${path}`;
  const h = { ...headers };
  if (token) h.Authorization = `Bearer ${token}`;
  let payload;
  if (raw !== undefined) payload = raw;
  else if (body !== undefined) {
    payload = JSON.stringify(body);
    h['Content-Type'] ??= 'application/json';
  }
  const t0 = performance.now();
  let res;
  try {
    res = await fetch(url, { method, headers: h, body: payload, redirect: 'manual',
      signal: timeoutMs ? AbortSignal.timeout(timeoutMs) : undefined });
  } catch (e) {
    return { status: 0, error: String(e.cause?.code ?? e.name ?? e.message), ms: performance.now() - t0 };
  }
  const text = await res.text();
  const ms = performance.now() - t0;
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* не JSON */
  }
  return { status: res.status, headers: res.headers, text, json, ms };
}

export async function login(role) {
  const email = ACCOUNTS[role] ?? role;
  const r = await http('POST', '/auth/login', { body: { email, password: PASSWORD } });
  if (r.status !== 200) throw new Error(`Вход ${email}: ${r.status} ${r.text}`);
  return r.json.token;
}

export async function tokens() {
  const out = {};
  for (const role of ROLES) out[role] = await login(role);
  return out;
}

/** Единый формат ошибки сервера {status, code, message, path, timestamp}. */
export function isApiError(r) {
  const j = r.json;
  return Boolean(j && typeof j === 'object' && j.status === r.status && typeof j.code === 'string'
    && typeof j.message === 'string' && typeof j.path === 'string' && typeof j.timestamp === 'string');
}

export function percentile(values, p) {
  const s = [...values].sort((a, b) => a - b);
  if (!s.length) return NaN;
  const idx = Math.min(s.length - 1, Math.max(0, Math.ceil((p / 100) * s.length) - 1));
  return s[idx];
}

export const round2 = (x) => Math.round(x * 100) / 100;

export function save(name, data) {
  mkdirSync(OUT, { recursive: true });
  const file = resolve(OUT, name);
  writeFileSync(file, typeof data === 'string' ? data : `${JSON.stringify(data, null, 2)}\n`);
  return file;
}

export const nowIso = () => new Date().toLocaleString('ru-RU', { timeZone: 'Europe/Minsk' });
