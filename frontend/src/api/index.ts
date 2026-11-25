import type { BonusApi } from './client';
import { httpApi } from './http/httpApi';
import { mockApi } from './mock/mockApi';

/**
 * Точка подключения слоя данных. Экраны импортируют только `api`.
 * VITE_API_MODE=http (по умолчанию) — REST API Spring Boot (ЛР 9), VITE_API_MODE=mock —
 * мок-сервер в памяти браузера (ЛР 6). Сигнатуры одинаковы: экраны не знают, какой клиент работает.
 */
export const api: BonusApi = import.meta.env.VITE_API_MODE === 'mock' ? mockApi : httpApi;

export { ApiError } from './client';
export type { BonusApi } from './client';
export { SESSION_TOKEN_KEY } from './session';
export * from './types';
