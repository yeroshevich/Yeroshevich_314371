/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Базовый URL REST API; по умолчанию `/api/v1` (в разработке проксируется Vite на :8080). */
  readonly VITE_API_URL?: string;
  /** `http` (по умолчанию) — сервер Spring Boot; `mock` — in-memory мок без сервера. */
  readonly VITE_API_MODE?: 'http' | 'mock';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
