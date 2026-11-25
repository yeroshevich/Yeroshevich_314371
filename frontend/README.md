# TechnoSoft Bonus — клиент (ЛР 6, ЛР 9)

React 18 + TypeScript + Vite + Ant Design 5. Данные — через интерфейс `BonusApi` (`src/api/client.ts`):

- `VITE_API_MODE=http` (по умолчанию, ЛР 9) — REST API сервера Spring Boot (`src/api/http/httpApi.ts`, fetch + JWT);
  запросы `/api` Vite проксирует на `http://localhost:8080` (переопределяется `API_TARGET`), адрес API — `VITE_API_URL` (по умолчанию `/api/v1`);
- `VITE_API_MODE=mock` (ЛР 6) — мок в памяти браузера (`src/api/mock`), задержка 200–400 мс.

```bash
npm install
npm run dev          # http://localhost:5173
npm run build        # tsc --noEmit + сборка в dist/
npm run screenshots  # после build, при запущенном сервере: PNG экранов в ../docs/lr9/screens (нужен Google Chrome)
VITE_API_MODE=mock npm run dev   # без сервера, на мок-данных
```

Демо-вход: кнопки «Войти как …» на /login или email из `src/api/mock/db.ts` (`DEMO_ACCOUNTS`; те же учётные записи есть в БД сервера), пароль `demo`.
Витрина системы дизайна без входа: `/design-system`.
