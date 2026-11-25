import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Сборка клиента TechnoSoft Bonus. Запросы /api проксируются на сервер Spring Boot (ЛР 9);
// адрес сервера можно переопределить переменной окружения API_TARGET.
const apiProxy = {
  '/api': { target: process.env.API_TARGET ?? 'http://localhost:8080', changeOrigin: true },
};

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: apiProxy },
  preview: { port: 4173, proxy: apiProxy },
  build: {
    chunkSizeWarningLimit: 1800,
  },
});
