# Yeroshevich_314371

Программное средство управления бонусной системой организации (TechnoSoft Bonus).
Лабораторные работы по дисциплине «Технологии проектирования сложных информационных систем», вариант 46.

- `frontend/` — клиентская часть (React + TypeScript + Vite + Ant Design)
- `backend/` — серверная часть (Java 21 + Spring Boot 3 + PostgreSQL): REST API `/api/v1`, JWT, документация OpenAPI
  (`/v3/api-docs`, `/swagger-ui.html`); запуск: `mvn -DskipTests package && java -jar target/bonus-0.1.0-SNAPSHOT.jar`
- `docs/lr9/` — примеры запросов и ответов API (`api-examples.md`), спецификация `openapi.json`, скриншоты клиента с сервером
