# ЛР 9. Примеры запросов и ответов REST API TechnoSoft Bonus

Реальный прогон против сервера Spring Boot (`java -jar target/bonus-0.1.0-SNAPSHOT.jar`, БД `tpsis_bonus_app`
с демо-данными V2). Запросы выполнялись последовательно в указанном порядке — номера операций и балансы
в ответах согласованы между собой. Длинные массивы сокращены до 2–3 элементов (последний элемент
«… ещё N из M» — пометка сокращения), JWT сокращены.

Базовый адрес: `http://localhost:8080/api/v1`. Формат: JSON (UTF-8), даты ISO 8601, суммы — целые баллы,
идентификаторы — строки. Аутентификация: `Authorization: Bearer <JWT>` (HS256, срок 8 ч).
Ошибка любого вида: `{ "status", "code", "message", "path", "timestamp" }`.

Переменные окружения с токенами (`TOKEN_EMPLOYEE`, `TOKEN_MANAGER`, `TOKEN_HR`, `TOKEN_FINANCE`, `TOKEN_ADMIN`,
`TOKEN_BLOCKED`) получены запросами раздела 1:

```bash
TOKEN_HR=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"olga.melnik@technosoft.by","password":"demo"}' | jq -r .token)
```

## Содержание

1. Аутентификация (SEC-1)
2. UC1. Просмотр баланса
3. UC2. История операций
4. UC3. Использование бонусов на программы мотивации
5. UC5, UC6. Ручное начисление и расчёт
6. UC7. Корректировка начисления
7. Контроль финансового отдела (UC5, UC7)
8. UC8. Настройка правил начисления
9. UC9. Бонусные кампании
10. UC10. Отчёты
11. UC11. Анализ эффективности
12. UC12. Управление пользователями
13. UC13. Настройка интеграций
14. Журнал аудита и уведомления (NFR-3, SEC-4)
15. Общие ошибки: 401, 403, 404, 405, 415

## 1. Аутентификация (SEC-1)

Демо-пароль всех учётных записей — `demo` (BCrypt-хеш в `app_user.password_hash`).

### 1.1. Вход: employee

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "anna.kovaleva@technosoft.by", "password": "demo"}'
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiw…",
  "user": {
    "id": "1",
    "fullName": "Анна Ковалёва",
    "email": "anna.kovaleva@technosoft.by",
    "departmentId": "1",
    "position": "Frontend-разработчик",
    "role": "employee",
    "status": "active",
    "employeeId": "1",
    "lastLoginAt": "2026-11-25T10:36:59.18365Z"
  }
}
```

### 1.2. Вход: manager

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "dmitry.savitsky@technosoft.by", "password": "demo"}'
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyIiw…",
  "user": {
    "id": "2",
    "fullName": "Дмитрий Савицкий",
    "email": "dmitry.savitsky@technosoft.by",
    "departmentId": "1",
    "position": "Руководитель группы разработки",
    "role": "manager",
    "status": "active",
    "employeeId": "2",
    "lastLoginAt": "2026-11-25T10:36:59.313219Z"
  }
}
```

### 1.3. Вход: hr

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "olga.melnik@technosoft.by", "password": "demo"}'
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMCI…",
  "user": {
    "id": "20",
    "fullName": "Ольга Мельник",
    "email": "olga.melnik@technosoft.by",
    "departmentId": "5",
    "position": "HR-бизнес-партнёр",
    "role": "hr",
    "status": "active",
    "employeeId": "20",
    "lastLoginAt": "2026-11-25T10:36:59.380587Z"
  }
}
```

### 1.4. Вход: finance

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "igor.zhuk@technosoft.by", "password": "demo"}'
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMyI…",
  "user": {
    "id": "23",
    "fullName": "Игорь Жук",
    "email": "igor.zhuk@technosoft.by",
    "departmentId": "6",
    "position": "Финансовый контролёр",
    "role": "finance",
    "status": "active",
    "employeeId": "23",
    "lastLoginAt": "2026-11-25T10:36:59.446739Z"
  }
}
```

### 1.5. Вход: admin

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "sergey.litvinov@technosoft.by", "password": "demo"}'
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxNyI…",
  "user": {
    "id": "17",
    "fullName": "Сергей Литвинов",
    "email": "sergey.litvinov@technosoft.by",
    "departmentId": "4",
    "position": "Системный администратор",
    "role": "admin",
    "status": "active",
    "employeeId": "17",
    "lastLoginAt": "2026-11-25T10:36:59.510819Z"
  }
}
```

### 1.6. Вход: сотрудник с заблокированным бонусным счётом (вход разрешён, счёт недоступен)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "pavel.karpovich@technosoft.by", "password": "demo"}'
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5Iiw…",
  "user": {
    "id": "9",
    "fullName": "Павел Карпович",
    "email": "pavel.karpovich@technosoft.by",
    "departmentId": "2",
    "position": "QA-инженер",
    "role": "employee",
    "status": "active",
    "employeeId": "9",
    "lastLoginAt": "2026-11-25T10:36:59.575833Z"
  }
}
```

### 1.7. Профиль по токену (восстановление сессии)

```bash
curl -s -X GET 'http://localhost:8080/api/v1/auth/me' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **200 OK**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiw…",
  "user": {
    "id": "1",
    "fullName": "Анна Ковалёва",
    "email": "anna.kovaleva@technosoft.by",
    "departmentId": "1",
    "position": "Frontend-разработчик",
    "role": "employee",
    "status": "active",
    "employeeId": "1",
    "lastLoginAt": "2026-11-25T10:36:59.18365Z"
  }
}
```

### 1.8. Неверный пароль → 401

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "anna.kovaleva@technosoft.by", "password": "wrong"}'
```

Ответ: **401 Unauthorized**

```json
{
  "status": 401,
  "code": "BAD_CREDENTIALS",
  "message": "Неверный email или пароль. Проверьте раскладку и попробуйте ещё раз.",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-11-25T13:36:59.660175+03:00"
}
```

### 1.9. Учётная запись заблокирована → 403

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "gleb.sorokin@technosoft.by", "password": "demo"}'
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "USER_BLOCKED",
  "message": "Учётная запись заблокирована. Обратитесь к администратору системы.",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-11-25T13:36:59.722031+03:00"
}
```

### 1.10. Невалидный email → 422

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "anna", "password": ""}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Похоже, в адресе опечатка: нужен вид name@technosoft.by. Введите пароль.",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-11-25T13:36:59.726507+03:00"
}
```

## 2. UC1. Просмотр баланса

Роли: employee, manager, hr (владельцы бонусного счёта).

### 2.1. Баланс и сводка сотрудника

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **200 OK**

```json
{
  "account": {
    "employeeId": "1",
    "status": "active",
    "balance": 8900,
    "pending": 1500,
    "updatedAt": "2026-11-25T08:00:00Z"
  },
  "accruedThisMonth": 1700,
  "spentThisMonth": 0,
  "recent": [
    {
      "id": "146",
      "number": "ОП-000146",
      "employeeId": "1",
      "employeeName": "Анна Ковалёва",
      "departmentId": "1",
      "type": "accrual",
      "amount": 1500,
      "status": "pending",
      "basis": "achievement",
      "reason": "Доклад на внутреннем митапе фронтенда",
      "createdAt": "2026-11-23T09:47:00Z",
      "createdBy": "Дмитрий Савицкий",
      "comment": "Превышен лимит 1 000 Б на сотрудника",
      "disputed": false
    },
    {
      "id": "137",
      "number": "ОП-000137",
      "employeeId": "1",
      "employeeName": "Анна Ковалёва",
      "departmentId": "1",
      "type": "accrual",
      "amount": 1700,
      "status": "accrued",
      "basis": "kpi",
      "reason": "План спринта за ноябрь, KPI 97 %",
      "ruleId": "1",
      "campaignId": "1",
      "createdAt": "2026-11-20T08:02:00Z",
      "createdBy": "Система",
      "disputed": false
    },
    "… ещё 3 из 5"
  ],
  "history": [
    {
      "month": "2026-06",
      "balance": 1250
    },
    {
      "month": "2026-07",
      "balance": 2550
    },
    "… ещё 4 из 6"
  ]
}
```

### 2.2. Счёт заблокирован (альтернативный поток) → 423

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me' \
  -H "Authorization: Bearer $TOKEN_BLOCKED"
```

Ответ: **423 Locked**

```json
{
  "status": 423,
  "code": "ACCOUNT_BLOCKED",
  "message": "Баланс недоступен, обратитесь к HR.",
  "path": "/api/v1/accounts/me",
  "timestamp": "2026-11-25T13:36:59.75637+03:00"
}
```

## 3. UC2. История операций

Фильтры: `from`, `to` (YYYY-MM-DD, включительно), `type` (accrual | writeoff | correction).

### 3.1. Начисления за ноябрь

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me/operations?type=accrual&from=2026-11-01&to=2026-11-30' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **200 OK**

```json
[
  {
    "id": "146",
    "number": "ОП-000146",
    "employeeId": "1",
    "employeeName": "Анна Ковалёва",
    "departmentId": "1",
    "type": "accrual",
    "amount": 1500,
    "status": "pending",
    "basis": "achievement",
    "reason": "Доклад на внутреннем митапе фронтенда",
    "createdAt": "2026-11-23T09:47:00Z",
    "createdBy": "Дмитрий Савицкий",
    "comment": "Превышен лимит 1 000 Б на сотрудника",
    "disputed": false
  },
  {
    "id": "137",
    "number": "ОП-000137",
    "employeeId": "1",
    "employeeName": "Анна Ковалёва",
    "departmentId": "1",
    "type": "accrual",
    "amount": 1700,
    "status": "accrued",
    "basis": "kpi",
    "reason": "План спринта за ноябрь, KPI 97 %",
    "ruleId": "1",
    "campaignId": "1",
    "createdAt": "2026-11-20T08:02:00Z",
    "createdBy": "Система",
    "disputed": false
  }
]
```

### 3.2. Неизвестный тип → 422

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me/operations?type=bonus' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Тип: accrual, writeoff или correction.",
  "path": "/api/v1/accounts/me/operations",
  "timestamp": "2026-11-25T13:36:59.779631+03:00"
}
```

### 3.3. Период задан наоборот → 422

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me/operations?from=2026-11-30&to=2026-11-01' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Дата начала периода позже даты окончания.",
  "path": "/api/v1/accounts/me/operations",
  "timestamp": "2026-11-25T13:36:59.784671+03:00"
}
```

## 4. UC3. Использование бонусов на программы мотивации

### 4.1. Каталог программ

```bash
curl -s -X GET 'http://localhost:8080/api/v1/programs' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **200 OK**

```json
[
  {
    "id": "1",
    "name": "Сертификат в книжный магазин",
    "category": "gift",
    "description": "Электронный сертификат номиналом 100 BYN.",
    "cost": 1500,
    "active": true
  },
  {
    "id": "2",
    "name": "Худи и термокружка TechnoSoft",
    "category": "merch",
    "description": "Комплект фирменного мерча.",
    "cost": 900,
    "active": true
  },
  {
    "id": "3",
    "name": "Абонемент в бассейн",
    "category": "health",
    "description": "Месяц безлимитного посещения бассейна в клубе-партнёре.",
    "cost": 2500,
    "active": true
  },
  "… ещё 5 из 8"
]
```

### 4.2. Списание 6 000 Б на курс английского (успех)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/programs/5/redemptions' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **201 Created**

```json
{
  "operation": {
    "id": "287",
    "number": "ОП-000287",
    "employeeId": "1",
    "employeeName": "Анна Ковалёва",
    "departmentId": "1",
    "type": "writeoff",
    "amount": -6000,
    "status": "written_off",
    "basis": "program",
    "reason": "Программа «Курс английского языка»",
    "programId": "5",
    "createdAt": "2026-11-25T10:36:59.802056Z",
    "createdBy": "Анна Ковалёва",
    "disputed": false
  },
  "account": {
    "employeeId": "1",
    "status": "active",
    "balance": 2900,
    "pending": 1500,
    "updatedAt": "2026-11-25T10:36:59.802056Z"
  }
}
```

### 4.3. Не хватает баллов → 409 INSUFFICIENT_FUNDS

```bash
curl -s -X POST 'http://localhost:8080/api/v1/programs/6/redemptions' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Не хватает 9 100 Б: программа стоит 12 000 Б, на счёте 2 900 Б.",
  "path": "/api/v1/programs/6/redemptions",
  "timestamp": "2026-11-25T13:36:59.83446+03:00"
}
```

### 4.4. Программа неактивна → 409 PROGRAM_INACTIVE

```bash
curl -s -X POST 'http://localhost:8080/api/v1/programs/8/redemptions' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "PROGRAM_INACTIVE",
  "message": "Программа «Корпоративный сплав по Неману» сейчас недоступна. Сезон завершён. Запись откроется в мае.",
  "path": "/api/v1/programs/8/redemptions",
  "timestamp": "2026-11-25T13:36:59.839273+03:00"
}
```

### 4.5. Счёт заблокирован → 423

```bash
curl -s -X POST 'http://localhost:8080/api/v1/programs/1/redemptions' \
  -H "Authorization: Bearer $TOKEN_BLOCKED"
```

Ответ: **423 Locked**

```json
{
  "status": 423,
  "code": "ACCOUNT_BLOCKED",
  "message": "Баланс недоступен, обратитесь к HR.",
  "path": "/api/v1/programs/1/redemptions",
  "timestamp": "2026-11-25T13:36:59.844852+03:00"
}
```

### 4.6. Программа не найдена → 404

```bash
curl -s -X POST 'http://localhost:8080/api/v1/programs/999/redemptions' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **404 Not Found**

```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Программа не найдена. Обновите каталог.",
  "path": "/api/v1/programs/999/redemptions",
  "timestamp": "2026-11-25T13:36:59.849312+03:00"
}
```

## 5. UC5, UC6. Ручное начисление и расчёт

Роли: manager (только своя команда), hr (любой сотрудник). Лимит без согласования — 1 000 Б на сотрудника.

### 5.1. Подразделения

```bash
curl -s -X GET 'http://localhost:8080/api/v1/departments' \
  -H "Authorization: Bearer $TOKEN_MANAGER"
```

Ответ: **200 OK**

```json
[
  {
    "id": "1",
    "name": "Разработка ПО",
    "headId": "2"
  },
  {
    "id": "2",
    "name": "Обеспечение качества",
    "headId": "8"
  },
  {
    "id": "3",
    "name": "Бизнес-анализ",
    "headId": "12"
  },
  "… ещё 3 из 6"
]
```

### 5.2. Команда руководителя с балансами и KPI

```bash
curl -s -X GET 'http://localhost:8080/api/v1/employees/team' \
  -H "Authorization: Bearer $TOKEN_MANAGER"
```

Ответ: **200 OK**

```json
[
  {
    "id": "1",
    "fullName": "Анна Ковалёва",
    "email": "anna.kovaleva@technosoft.by",
    "position": "Frontend-разработчик",
    "departmentId": "1",
    "managerId": "2",
    "hiredAt": "2021-02-22",
    "kpiCompletion": 107,
    "departmentName": "Разработка ПО",
    "balance": 2900,
    "accountStatus": "active",
    "accruedThisMonth": 1700
  },
  {
    "id": "5",
    "fullName": "Артём Василевич",
    "email": "artem.vasilevich@technosoft.by",
    "position": "Ведущий разработчик",
    "departmentId": "1",
    "managerId": "2",
    "hiredAt": "2017-09-11",
    "kpiCompletion": 99,
    "departmentName": "Разработка ПО",
    "balance": 4800,
    "accountStatus": "active",
    "accruedThisMonth": 1900
  },
  "… ещё 5 из 7"
]
```

### 5.3. Сотрудники (руководитель видит только подчинённых)

```bash
curl -s -X GET 'http://localhost:8080/api/v1/employees' \
  -H "Authorization: Bearer $TOKEN_MANAGER"
```

Ответ: **200 OK**

```json
[
  {
    "id": "1",
    "fullName": "Анна Ковалёва",
    "email": "anna.kovaleva@technosoft.by",
    "position": "Frontend-разработчик",
    "departmentId": "1",
    "managerId": "2",
    "hiredAt": "2021-02-22",
    "kpiCompletion": 107
  },
  {
    "id": "5",
    "fullName": "Артём Василевич",
    "email": "artem.vasilevich@technosoft.by",
    "position": "Ведущий разработчик",
    "departmentId": "1",
    "managerId": "2",
    "hiredAt": "2017-09-11",
    "kpiCompletion": 99
  },
  "… ещё 5 из 7"
]
```

### 5.4. Предварительный расчёт с коэффициентом кампании «Осенний релиз 2.0» (×1,5)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals/preview' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": ["1", "3"], "amount": 800, "basis": "project", "reason": "Срочный релиз платформы 2.0 в выходные", "campaignId": "1"}'
```

Ответ: **200 OK**

```json
{
  "lines": [
    {
      "employeeId": "1",
      "employeeName": "Анна Ковалёва",
      "baseAmount": 800,
      "coefficient": 1.5,
      "total": 1200,
      "needsApproval": true
    },
    {
      "employeeId": "3",
      "employeeName": "Максим Новик",
      "baseAmount": 800,
      "coefficient": 1.5,
      "total": 1200,
      "needsApproval": true
    }
  ],
  "total": 2400,
  "limit": 1000,
  "approvalCount": 2
}
```

### 5.5. Начисление в пределах лимита → accrued

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": ["3"], "amount": 600, "basis": "project", "reason": "Срочный релиз платформы 2.0 в выходные"}'
```

Ответ: **201 Created**

```json
{
  "operations": [
    {
      "id": "288",
      "number": "ОП-000288",
      "employeeId": "3",
      "employeeName": "Максим Новик",
      "departmentId": "1",
      "type": "accrual",
      "amount": 600,
      "status": "accrued",
      "basis": "project",
      "reason": "Срочный релиз платформы 2.0 в выходные",
      "createdAt": "2026-11-25T10:36:59.930677Z",
      "createdBy": "Дмитрий Савицкий",
      "disputed": false
    }
  ],
  "accruedCount": 1,
  "pendingCount": 0
}
```

### 5.6. Начисление выше лимита → pending (на согласование финотдела)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": ["4"], "amount": 1200, "basis": "achievement", "reason": "Доклад на конференции JPoint"}'
```

Ответ: **201 Created**

```json
{
  "operations": [
    {
      "id": "289",
      "number": "ОП-000289",
      "employeeId": "4",
      "employeeName": "Екатерина Шевчук",
      "departmentId": "1",
      "type": "accrual",
      "amount": 1200,
      "status": "pending",
      "basis": "achievement",
      "reason": "Доклад на конференции JPoint",
      "createdAt": "2026-11-25T10:36:59.946118Z",
      "createdBy": "Дмитрий Савицкий",
      "comment": "Превышен лимит 1 000 Б на сотрудника",
      "disputed": false
    }
  ],
  "accruedCount": 0,
  "pendingCount": 1
}
```

### 5.7. Сотрудник не из команды руководителя → 403

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": ["13"], "amount": 500, "basis": "project", "reason": "Помощь соседнему отделу"}'
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Руководитель может управлять баллами только своей команды.",
  "path": "/api/v1/accruals",
  "timestamp": "2026-11-25T13:36:59.959074+03:00"
}
```

### 5.8. Счёт получателя заблокирован → 423 (начисление не проводится)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": ["9"], "amount": 300, "basis": "kpi", "reason": "Квартальный KPI"}'
```

Ответ: **423 Locked**

```json
{
  "status": 423,
  "code": "ACCOUNT_BLOCKED",
  "message": "Счёт сотрудника «Павел Карпович» заблокирован: начисление невозможно",
  "path": "/api/v1/accruals",
  "timestamp": "2026-11-25T13:36:59.964612+03:00"
}
```

### 5.9. Ошибки заполнения → 422

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": [], "amount": 0, "basis": "bonus", "reason": ""}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Укажите сумму больше нуля. Основание: kpi, project или achievement. Выберите хотя бы одного сотрудника. Укажите причину начисления.",
  "path": "/api/v1/accruals",
  "timestamp": "2026-11-25T13:36:59.967713+03:00"
}
```

### 5.10. Тело не разобрано → 400

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds":["1"],"amount":"сто"}'
```

Ответ: **400 Bad Request**

```json
{
  "status": 400,
  "code": "MALFORMED_REQUEST",
  "message": "Не удалось разобрать тело запроса: проверьте формат JSON и типы полей.",
  "path": "/api/v1/accruals",
  "timestamp": "2026-11-25T13:36:59.971436+03:00"
}
```

### 5.11. Роль employee → 403

```bash
curl -s -X POST 'http://localhost:8080/api/v1/accruals' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE" \
  -H 'Content-Type: application/json' \
  -d '{"employeeIds": ["1"], "amount": 100, "basis": "kpi", "reason": "Себе"}'
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Для этого действия у вашей роли нет прав.",
  "path": "/api/v1/accruals",
  "timestamp": "2026-11-25T13:36:59.974668+03:00"
}
```

## 6. UC7. Корректировка начисления

Роли: hr (любые операции), manager (своя команда). История не переписывается: исходная операция → cancelled, новая сумма — операция типа correction.

### 6.1. Журнал операций с фильтрами (HR)

```bash
curl -s -X GET 'http://localhost:8080/api/v1/operations?status=pending&from=2026-11-01&to=2026-11-30' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
[
  {
    "id": "289",
    "number": "ОП-000289",
    "employeeId": "4",
    "employeeName": "Екатерина Шевчук",
    "departmentId": "1",
    "type": "accrual",
    "amount": 1200,
    "status": "pending",
    "basis": "achievement",
    "reason": "Доклад на конференции JPoint",
    "createdAt": "2026-11-25T10:36:59.946118Z",
    "createdBy": "Дмитрий Савицкий",
    "comment": "Превышен лимит 1 000 Б на сотрудника",
    "disputed": false
  },
  {
    "id": "146",
    "number": "ОП-000146",
    "employeeId": "1",
    "employeeName": "Анна Ковалёва",
    "departmentId": "1",
    "type": "accrual",
    "amount": 1500,
    "status": "pending",
    "basis": "achievement",
    "reason": "Доклад на внутреннем митапе фронтенда",
    "createdAt": "2026-11-23T09:47:00Z",
    "createdBy": "Дмитрий Савицкий",
    "comment": "Превышен лимит 1 000 Б на сотрудника",
    "disputed": false
  },
  "… ещё 2 из 4"
]
```

### 6.2. Поиск по ФИО без учёта регистра

```bash
curl -s -X GET 'http://localhost:8080/api/v1/operations?q=%D0%BD%D0%BE%D0%B2%D0%B8%D0%BA&type=accrual&from=2026-11-01' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
[
  {
    "id": "288",
    "number": "ОП-000288",
    "employeeId": "3",
    "employeeName": "Максим Новик",
    "departmentId": "1",
    "type": "accrual",
    "amount": 600,
    "status": "accrued",
    "basis": "project",
    "reason": "Срочный релиз платформы 2.0 в выходные",
    "createdAt": "2026-11-25T10:36:59.930677Z",
    "createdBy": "Дмитрий Савицкий",
    "disputed": false
  },
  {
    "id": "139",
    "number": "ОП-000139",
    "employeeId": "3",
    "employeeName": "Максим Новик",
    "departmentId": "1",
    "type": "accrual",
    "amount": 1900,
    "status": "accrued",
    "basis": "kpi",
    "reason": "План спринта за ноябрь, KPI 103 %",
    "ruleId": "1",
    "campaignId": "1",
    "createdAt": "2026-11-20T10:06:00Z",
    "createdBy": "Система",
    "disputed": false
  },
  "… ещё 1 из 3"
]
```

### 6.3. Уменьшить сумму операции ОП-000288 600 → 400

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/288' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"action": "adjust", "newAmount": 400, "reason": "Часть работ перенесена на следующий спринт"}'
```

Ответ: **200 OK**

```json
{
  "id": "290",
  "number": "ОП-000290",
  "employeeId": "3",
  "employeeName": "Максим Новик",
  "departmentId": "1",
  "type": "correction",
  "amount": 400,
  "originalAmount": 600,
  "status": "corrected",
  "basis": "correction",
  "reason": "Часть работ перенесена на следующий спринт",
  "createdAt": "2026-11-25T10:37:00.009299Z",
  "createdBy": "Ольга Мельник",
  "disputed": false
}
```

### 6.4. Повторная корректировка уже заменённой операции → 409

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/288' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"action": "adjust", "newAmount": 300, "reason": "Повтор"}'
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "OPERATION_STATE_CONFLICT",
  "message": "Операцию в статусе «cancelled» корректировать нельзя.",
  "path": "/api/v1/operations/288",
  "timestamp": "2026-11-25T13:37:00.025126+03:00"
}
```

### 6.5. Сотрудник тратит ещё 1 500 Б (баланс 2 900 → 1 400)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/programs/1/redemptions' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **201 Created**

```json
{
  "operation": {
    "id": "291",
    "number": "ОП-000291",
    "employeeId": "1",
    "employeeName": "Анна Ковалёва",
    "departmentId": "1",
    "type": "writeoff",
    "amount": -1500,
    "status": "written_off",
    "basis": "program",
    "reason": "Программа «Сертификат в книжный магазин»",
    "programId": "1",
    "createdAt": "2026-11-25T10:37:00.02918Z",
    "createdBy": "Анна Ковалёва",
    "disputed": false
  },
  "account": {
    "employeeId": "1",
    "status": "active",
    "balance": 1400,
    "pending": 1500,
    "updatedAt": "2026-11-25T10:37:00.02918Z"
  }
}
```

### 6.6. Отмена начисления ОП-000084 (1 900 Б): баланс стал бы −500 → pending

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/84' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"action": "cancel", "reason": "Начисление задвоено с ОП-000117"}'
```

Ответ: **200 OK**

```json
{
  "id": "292",
  "number": "ОП-000292",
  "employeeId": "1",
  "employeeName": "Анна Ковалёва",
  "departmentId": "1",
  "type": "correction",
  "amount": -1900,
  "originalAmount": 1900,
  "status": "pending",
  "basis": "correction",
  "reason": "Начисление задвоено с ОП-000117",
  "createdAt": "2026-11-25T10:37:00.047201Z",
  "createdBy": "Ольга Мельник",
  "comment": "Баланс сотрудника стал бы отрицательным: нужно согласование финотдела",
  "disputed": false
}
```

### 6.7. Корректировка списания ОП-000287 → 409

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/287' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"action": "cancel", "reason": "Ошибка"}'
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "OPERATION_STATE_CONFLICT",
  "message": "Операцию в статусе «written_off» корректировать нельзя.",
  "path": "/api/v1/operations/287",
  "timestamp": "2026-11-25T13:37:00.058811+03:00"
}
```

### 6.8. Нет причины → 422

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/84' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"action": "adjust", "newAmount": 100, "reason": ""}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Укажите причину корректировки.",
  "path": "/api/v1/operations/84",
  "timestamp": "2026-11-25T13:37:00.062214+03:00"
}
```

### 6.9. Роль finance → 403

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/84' \
  -H "Authorization: Bearer $TOKEN_FINANCE" \
  -H 'Content-Type: application/json' \
  -d '{"action": "cancel", "reason": "x"}'
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Для этого действия у вашей роли нет прав.",
  "path": "/api/v1/operations/84",
  "timestamp": "2026-11-25T13:37:00.06509+03:00"
}
```

## 7. Контроль финансового отдела (UC5, UC7)

Роль: finance.

### 7.1. Очередь контроля

```bash
curl -s -X GET 'http://localhost:8080/api/v1/control/operations' \
  -H "Authorization: Bearer $TOKEN_FINANCE"
```

Ответ: **200 OK**

```json
[
  {
    "id": "131",
    "number": "ОП-000131",
    "employeeId": "5",
    "employeeName": "Артём Василевич",
    "departmentId": "1",
    "type": "accrual",
    "amount": 7500,
    "status": "pending",
    "basis": "project",
    "reason": "Сдача архитектуры платформы 2.0 заказчику",
    "ruleId": "3",
    "createdAt": "2026-11-16T08:05:00Z",
    "createdBy": "Дмитрий Савицкий",
    "comment": "Превышен лимит 1 000 Б на сотрудника",
    "disputed": false
  },
  {
    "id": "134",
    "number": "ОП-000134",
    "employeeId": "18",
    "employeeName": "Денис Пашкевич",
    "departmentId": "4",
    "type": "accrual",
    "amount": 6000,
    "status": "pending",
    "basis": "achievement",
    "reason": "Восстановление кластера после аварии в выходные",
    "createdAt": "2026-11-19T15:30:00Z",
    "createdBy": "Владимир Ермаков",
    "comment": "Превышен лимит 1 000 Б на сотрудника",
    "disputed": false
  },
  {
    "id": "146",
    "number": "ОП-000146",
    "employeeId": "1",
    "employeeName": "Анна Ковалёва",
    "departmentId": "1",
    "type": "accrual",
    "amount": 1500,
    "status": "pending",
    "basis": "achievement",
    "reason": "Доклад на внутреннем митапе фронтенда",
    "createdAt": "2026-11-23T09:47:00Z",
    "createdBy": "Дмитрий Савицкий",
    "comment": "Превышен лимит 1 000 Б на сотрудника",
    "disputed": false
  },
  "… ещё 2 из 5"
]
```

### 7.2. Подтвердить начисление ОП-000289 → accrued

```bash
curl -s -X POST 'http://localhost:8080/api/v1/control/operations/289/approve' \
  -H "Authorization: Bearer $TOKEN_FINANCE"
```

Ответ: **200 OK**

```json
{
  "id": "289",
  "number": "ОП-000289",
  "employeeId": "4",
  "employeeName": "Екатерина Шевчук",
  "departmentId": "1",
  "type": "accrual",
  "amount": 1200,
  "status": "accrued",
  "basis": "achievement",
  "reason": "Доклад на конференции JPoint",
  "createdAt": "2026-11-25T10:36:59.946118Z",
  "createdBy": "Дмитрий Савицкий",
  "disputed": false
}
```

### 7.3. Повторное подтверждение → 409

```bash
curl -s -X POST 'http://localhost:8080/api/v1/control/operations/289/approve' \
  -H "Authorization: Bearer $TOKEN_FINANCE"
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "OPERATION_STATE_CONFLICT",
  "message": "Подтвердить можно только операцию на согласовании; статус ОП-000289 — «accrued».",
  "path": "/api/v1/control/operations/289/approve",
  "timestamp": "2026-11-25T13:37:00.090182+03:00"
}
```

### 7.4. Подтвердить корректировку ОП-000292 при нехватке баллов → 409

```bash
curl -s -X POST 'http://localhost:8080/api/v1/control/operations/292/approve' \
  -H "Authorization: Bearer $TOKEN_FINANCE"
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Подтверждение невозможно: баланс станет −500 Б. Заблокируйте корректировку или дождитесь пополнения счёта.",
  "path": "/api/v1/control/operations/292/approve",
  "timestamp": "2026-11-25T13:37:00.09549+03:00"
}
```

### 7.5. Заблокировать без причины → 422

```bash
curl -s -X POST 'http://localhost:8080/api/v1/control/operations/292/block' \
  -H "Authorization: Bearer $TOKEN_FINANCE" \
  -H 'Content-Type: application/json' \
  -d '{"reason": ""}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Укажите причину блокировки — её увидит сотрудник.",
  "path": "/api/v1/control/operations/292/block",
  "timestamp": "2026-11-25T13:37:00.09949+03:00"
}
```

### 7.6. Заблокировать ОП-000292 с причиной → blocked

```bash
curl -s -X POST 'http://localhost:8080/api/v1/control/operations/292/block' \
  -H "Authorization: Bearer $TOKEN_FINANCE" \
  -H 'Content-Type: application/json' \
  -d '{"reason": "Отмена уводит баланс в минус: сотрудник уже потратил баллы"}'
```

Ответ: **200 OK**

```json
{
  "id": "292",
  "number": "ОП-000292",
  "employeeId": "1",
  "employeeName": "Анна Ковалёва",
  "departmentId": "1",
  "type": "correction",
  "amount": -1900,
  "originalAmount": 1900,
  "status": "blocked",
  "basis": "correction",
  "reason": "Начисление задвоено с ОП-000117",
  "createdAt": "2026-11-25T10:37:00.047201Z",
  "createdBy": "Ольга Мельник",
  "comment": "Отмена уводит баланс в минус: сотрудник уже потратил баллы",
  "disputed": false
}
```

### 7.7. Роль manager → 403

```bash
curl -s -X POST 'http://localhost:8080/api/v1/control/operations/131/approve' \
  -H "Authorization: Bearer $TOKEN_MANAGER"
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Для этого действия у вашей роли нет прав.",
  "path": "/api/v1/control/operations/131/approve",
  "timestamp": "2026-11-25T13:37:00.114453+03:00"
}
```

## 8. UC8. Настройка правил начисления

Чтение — hr, manager; изменение — hr.

### 8.1. Правила (последние версии)

```bash
curl -s -X GET 'http://localhost:8080/api/v1/rules' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
[
  {
    "id": "1",
    "name": "Выполнение плана спринта",
    "kpi": "Закрытые задачи спринта",
    "weight": 0.4,
    "basePoints": 3000,
    "formula": "3 000 × 0,4 × уровень достижения",
    "periodicity": "month",
    "threshold": 80,
    "active": true,
    "usedInCurrentPeriod": true,
    "updatedAt": "2026-11-25T08:20:40.099646Z"
  },
  {
    "id": "2",
    "name": "Качество релиза",
    "kpi": "Релиз без критических дефектов",
    "weight": 0.3,
    "basePoints": 3000,
    "formula": "3 000 × 0,3 × уровень достижения",
    "periodicity": "month",
    "threshold": 90,
    "active": true,
    "usedInCurrentPeriod": false,
    "updatedAt": "2026-11-25T08:20:40.099646Z"
  },
  "… ещё 4 из 6"
]
```

### 8.2. Создать правило (действует сразу)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/rules' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Отзывы клиентов", "kpi": "Индекс удовлетворённости клиентов", "weight": 0.25, "basePoints": 4000, "periodicity": "quarter", "threshold": 85, "active": true}'
```

Ответ: **201 Created**

```json
{
  "rule": {
    "id": "7",
    "name": "Отзывы клиентов",
    "kpi": "Индекс удовлетворённости клиентов",
    "weight": 0.25,
    "basePoints": 4000,
    "formula": "4 000 × 0,25 × уровень достижения",
    "periodicity": "quarter",
    "threshold": 85,
    "active": true,
    "usedInCurrentPeriod": false,
    "updatedAt": "2026-11-25T10:37:00.14329Z"
  },
  "effectiveFrom": "2026-11-25",
  "deferred": false
}
```

### 8.3. Второе правило для того же показателя → 409 RULE_EXISTS

```bash
curl -s -X POST 'http://localhost:8080/api/v1/rules' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Дубль", "kpi": "Индекс удовлетворённости клиентов", "weight": 0.3, "basePoints": 4000, "periodicity": "quarter", "threshold": 85, "active": true}'
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "RULE_EXISTS",
  "message": "Для показателя «Индекс удовлетворённости клиентов» уже есть правило «Отзывы клиентов». Измените его вместо создания нового.",
  "path": "/api/v1/rules",
  "timestamp": "2026-11-25T13:37:00.154954+03:00"
}
```

### 8.4. Изменить правило, применённое в текущем месяце → новая версия с 1-го числа следующего (deferred)

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/rules/1' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Выполнение плана спринта", "kpi": "Закрытые задачи спринта", "weight": 0.45, "basePoints": 3000, "periodicity": "month", "threshold": 80, "active": true}'
```

Ответ: **200 OK**

```json
{
  "rule": {
    "id": "8",
    "name": "Выполнение плана спринта",
    "kpi": "Закрытые задачи спринта",
    "weight": 0.45,
    "basePoints": 3000,
    "formula": "3 000 × 0,45 × уровень достижения",
    "periodicity": "month",
    "threshold": 80,
    "active": true,
    "usedInCurrentPeriod": true,
    "updatedAt": "2026-11-25T10:37:00.161602Z"
  },
  "effectiveFrom": "2026-12-01",
  "deferred": true
}
```

### 8.5. Изменить правило, не применявшееся в текущем квартале → сразу

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/rules/4' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Наставничество", "kpi": "Наставничество", "weight": 0.2, "basePoints": 5500, "periodicity": "quarter", "threshold": 100, "active": true}'
```

Ответ: **200 OK**

```json
{
  "rule": {
    "id": "4",
    "name": "Наставничество",
    "kpi": "Наставничество",
    "weight": 0.2,
    "basePoints": 5500,
    "formula": "5 500 × 0,2 × уровень достижения",
    "periodicity": "quarter",
    "threshold": 100,
    "active": true,
    "usedInCurrentPeriod": false,
    "updatedAt": "2026-11-25T10:37:00.174884Z"
  },
  "effectiveFrom": "2026-11-25",
  "deferred": false
}
```

### 8.6. Ошибки заполнения → 422

```bash
curl -s -X POST 'http://localhost:8080/api/v1/rules' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "", "kpi": "Скорость", "weight": 1.5, "basePoints": 0, "periodicity": "day", "threshold": 200, "active": true}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Базовая сумма должна быть больше нуля. Укажите название правила. Периодичность: month, quarter, year или event. Порог — от 0 до 150 %. Вес показателя — от 0,05 до 1.",
  "path": "/api/v1/rules",
  "timestamp": "2026-11-25T13:37:00.181376+03:00"
}
```

### 8.7. Роль manager изменяет правило → 403

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/rules/2' \
  -H "Authorization: Bearer $TOKEN_MANAGER" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Качество релиза", "kpi": "Релиз без критических дефектов", "weight": 0.3, "basePoints": 3000, "periodicity": "month", "threshold": 90, "active": true}'
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Для этого действия у вашей роли нет прав.",
  "path": "/api/v1/rules/2",
  "timestamp": "2026-11-25T13:37:00.184657+03:00"
}
```

## 9. UC9. Бонусные кампании

Чтение — hr, manager; создание — hr.

### 9.1. Кампании

```bash
curl -s -X GET 'http://localhost:8080/api/v1/campaigns' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
[
  {
    "id": "3",
    "name": "Квартал без инцидентов",
    "description": "Бонус дежурным инженерам за квартал без инцидентов первой категории.",
    "startDate": "2026-12-01",
    "endDate": "2027-02-28",
    "departmentIds": [
      "4"
    ],
    "coefficient": 2,
    "budget": null,
    "spent": 0,
    "status": "needs_approval"
  },
  {
    "id": "2",
    "name": "Неделя наставничества",
    "description": "Двойные баллы за наставничество новых сотрудников в первую неделю месяца.",
    "startDate": "2026-12-01",
    "endDate": "2026-12-07",
    "departmentIds": [
      "1",
      "2",
      "… ещё 4 из 6"
    ],
    "coefficient": 2,
    "budget": 15000,
    "spent": 0,
    "status": "planned"
  },
  "… ещё 2 из 4"
]
```

### 9.2. Кампания без бюджета → needs_approval (даты из браузера в формате ISO-момента)

```bash
curl -s -X POST 'http://localhost:8080/api/v1/campaigns' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Зимний спринт качества", "description": "Двойные баллы инженерам QA за релизы без дефектов", "startDate": "2026-12-31T21:00:00.000Z", "endDate": "2027-01-31T20:59:59.999Z", "departmentIds": ["2"], "coefficient": 2, "budget": null}'
```

Ответ: **201 Created**

```json
{
  "id": "5",
  "name": "Зимний спринт качества",
  "description": "Двойные баллы инженерам QA за релизы без дефектов",
  "startDate": "2027-01-01",
  "endDate": "2027-01-31",
  "departmentIds": [
    "2"
  ],
  "coefficient": 2,
  "budget": null,
  "spent": 0,
  "status": "needs_approval"
}
```

### 9.3. Кампания с бюджетом, стартует позже → planned

```bash
curl -s -X POST 'http://localhost:8080/api/v1/campaigns' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Предновогодний релиз", "description": "", "startDate": "2026-12-01", "endDate": "2026-12-25", "departmentIds": ["1", "4"], "coefficient": 1.5, "budget": 20000}'
```

Ответ: **201 Created**

```json
{
  "id": "6",
  "name": "Предновогодний релиз",
  "description": "",
  "startDate": "2026-12-01",
  "endDate": "2026-12-25",
  "departmentIds": [
    "1",
    "4"
  ],
  "coefficient": 1.5,
  "budget": 20000,
  "spent": 0,
  "status": "planned"
}
```

### 9.4. Окончание раньше начала → 422

```bash
curl -s -X POST 'http://localhost:8080/api/v1/campaigns' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"name": "Ошибка сроков", "startDate": "2026-12-10", "endDate": "2026-12-01", "departmentIds": ["1"], "coefficient": 1.5, "budget": 1000}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Дата окончания раньше даты начала. Проверьте сроки кампании.",
  "path": "/api/v1/campaigns",
  "timestamp": "2026-11-25T13:37:00.232925+03:00"
}
```

## 10. UC10. Отчёты

Роли: hr, finance. Вид: employees | departments | campaigns; период включительно.

### 10.1. По подразделениям за ноябрь

```bash
curl -s -X GET 'http://localhost:8080/api/v1/reports/departments?from=2026-11-01&to=2026-11-30' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
{
  "type": "departments",
  "from": "2026-11-01",
  "to": "2026-11-30",
  "rows": [
    {
      "key": "1",
      "name": "Разработка ПО",
      "group": "8 сотр.",
      "accrued": 11750,
      "spent": 10000,
      "pending": 9000,
      "balance": 38900,
      "operations": 16
    },
    {
      "key": "2",
      "name": "Обеспечение качества",
      "group": "4 сотр.",
      "accrued": 5250,
      "spent": 1500,
      "pending": 0,
      "balance": 20900,
      "operations": 5
    },
    {
      "key": "3",
      "name": "Бизнес-анализ",
      "group": "4 сотр.",
      "accrued": 3700,
      "spent": 1500,
      "pending": 0,
      "balance": 23600,
      "operations": 4
    },
    "… ещё 3 из 6"
  ],
  "generatedAt": "2026-11-25T13:37:00.246755+03:00"
}
```

### 10.2. По сотрудникам за ноябрь

```bash
curl -s -X GET 'http://localhost:8080/api/v1/reports/employees?from=2026-11-01&to=2026-11-30' \
  -H "Authorization: Bearer $TOKEN_FINANCE"
```

Ответ: **200 OK**

```json
{
  "type": "employees",
  "from": "2026-11-01",
  "to": "2026-11-30",
  "rows": [
    {
      "key": "11",
      "name": "Андрей Козлов",
      "group": "Обеспечение качества",
      "accrued": 1950,
      "spent": 1500,
      "pending": 0,
      "balance": 7950,
      "operations": 2
    },
    {
      "key": "1",
      "name": "Анна Ковалёва",
      "group": "Разработка ПО",
      "accrued": 1700,
      "spent": 7500,
      "pending": 1500,
      "balance": 1400,
      "operations": 5
    },
    "… ещё 17 из 19"
  ],
  "generatedAt": "2026-11-25T13:37:00.257246+03:00"
}
```

### 10.3. По кампаниям за осень

```bash
curl -s -X GET 'http://localhost:8080/api/v1/reports/campaigns?from=2026-09-01&to=2026-11-30' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
{
  "type": "campaigns",
  "from": "2026-09-01",
  "to": "2026-11-30",
  "rows": [
    {
      "key": "1",
      "name": "Осенний релиз 2.0",
      "group": "Бюджет 60 000 Б",
      "accrued": 45250,
      "spent": 0,
      "pending": 0,
      "balance": 14750,
      "operations": 25
    }
  ],
  "generatedAt": "2026-11-25T13:37:00.269622+03:00"
}
```

### 10.4. Нет параметра to → 400

```bash
curl -s -X GET 'http://localhost:8080/api/v1/reports/employees?from=2026-11-01' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **400 Bad Request**

```json
{
  "status": 400,
  "code": "MALFORMED_REQUEST",
  "message": "Не указан обязательный параметр «to».",
  "path": "/api/v1/reports/employees",
  "timestamp": "2026-11-25T13:37:00.273226+03:00"
}
```

### 10.5. Неизвестный вид отчёта → 422

```bash
curl -s -X GET 'http://localhost:8080/api/v1/reports/bonuses?from=2026-11-01&to=2026-11-30' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Вид отчёта: employees, departments или campaigns.",
  "path": "/api/v1/reports/bonuses",
  "timestamp": "2026-11-25T13:37:00.276165+03:00"
}
```

## 11. UC11. Анализ эффективности

Роли: hr (вся компания), manager (своё подразделение). period: month | quarter | halfyear.

### 11.1. Дашборд HR за квартал

```bash
curl -s -X GET 'http://localhost:8080/api/v1/analytics/dashboard?period=quarter' \
  -H "Authorization: Bearer $TOKEN_HR"
```

Ответ: **200 OK**

```json
{
  "period": "quarter",
  "currentLabel": "сен – ноя 2026",
  "previousLabel": "июн – авг 2026",
  "averageBonus": {
    "current": 3665,
    "previous": 2900
  },
  "programConversion": {
    "current": 54,
    "previous": 27
  },
  "totalAccrued": {
    "current": 95300,
    "previous": 75400
  },
  "totalSpent": {
    "current": 31600,
    "previous": 10900
  },
  "byDepartment": [
    {
      "department": "Разработка ПО",
      "current": 34400,
      "previous": 18400
    },
    {
      "department": "Обеспечение качества",
      "current": 14650,
      "previous": 11150
    },
    {
      "department": "Бизнес-анализ",
      "current": 15550,
      "previous": 14450
    },
    "… ещё 3 из 6"
  ],
  "monthly": [
    {
      "month": "июн",
      "accrued": 21750,
      "spent": 0
    },
    {
      "month": "июл",
      "accrued": 27750,
      "spent": 7900
    },
    {
      "month": "авг",
      "accrued": 25900,
      "spent": 3000
    },
    "… ещё 3 из 6"
  ],
  "partial": false
}
```

### 11.2. Дашборд руководителя за месяц

```bash
curl -s -X GET 'http://localhost:8080/api/v1/analytics/dashboard?period=month' \
  -H "Authorization: Bearer $TOKEN_MANAGER"
```

Ответ: **200 OK**

```json
{
  "period": "month",
  "currentLabel": "ноябрь 2026",
  "previousLabel": "октябрь 2026",
  "averageBonus": {
    "current": 1469,
    "previous": 1256
  },
  "programConversion": {
    "current": 25,
    "previous": 0
  },
  "totalAccrued": {
    "current": 11750,
    "previous": 10050
  },
  "totalSpent": {
    "current": 10000,
    "previous": 0
  },
  "byDepartment": [
    {
      "department": "Разработка ПО",
      "current": 11750,
      "previous": 10050
    }
  ],
  "monthly": [
    {
      "month": "июн",
      "accrued": 5750,
      "spent": 0
    },
    {
      "month": "июл",
      "accrued": 6250,
      "spent": 1500
    },
    {
      "month": "авг",
      "accrued": 6400,
      "spent": 1500
    },
    "… ещё 3 из 6"
  ],
  "partial": false
}
```

## 12. UC12. Управление пользователями

Роль: admin. Новый пользователь получает пустой бонусный счёт и временный пароль `demo`.

### 12.1. Пользователи

```bash
curl -s -X GET 'http://localhost:8080/api/v1/users' \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Ответ: **200 OK**

```json
[
  {
    "id": "13",
    "fullName": "Алексей Бондаренко",
    "email": "alexey.bondarenko@technosoft.by",
    "departmentId": "3",
    "position": "Бизнес-аналитик",
    "role": "employee",
    "status": "active",
    "employeeId": "13",
    "lastLoginAt": "2026-11-19T02:00:00Z"
  },
  {
    "id": "25",
    "fullName": "Ангелина Островская",
    "email": "angelina.ostrovskaya@technosoft.by",
    "departmentId": "6",
    "position": "Экономист",
    "role": "employee",
    "status": "active",
    "employeeId": "25",
    "lastLoginAt": "2026-11-21T05:00:00Z"
  },
  "… ещё 24 из 26"
]
```

### 12.2. Проверка email перед созданием: свободен

```bash
curl -s -X GET 'http://localhost:8080/api/v1/users?email=marina.zakharova%40technosoft.by' \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Ответ: **200 OK**

```json
[]
```

### 12.3. Создать пользователя

```bash
curl -s -X POST 'http://localhost:8080/api/v1/users' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"fullName": "Марина Захарова", "email": "marina.zakharova@technosoft.by", "departmentId": "2", "position": "QA-инженер", "role": "employee"}'
```

Ответ: **201 Created**

```json
{
  "id": "27",
  "fullName": "Марина Захарова",
  "email": "marina.zakharova@technosoft.by",
  "departmentId": "2",
  "position": "QA-инженер",
  "role": "employee",
  "status": "active",
  "employeeId": "27"
}
```

### 12.4. Повторное создание с тем же email → 409 EMAIL_EXISTS

```bash
curl -s -X POST 'http://localhost:8080/api/v1/users' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"fullName": "Марина Захарова", "email": "Marina.Zakharova@technosoft.by", "departmentId": "2", "position": "QA-инженер", "role": "employee"}'
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "EMAIL_EXISTS",
  "message": "Пользователь с email marina.zakharova@technosoft.by уже есть.",
  "path": "/api/v1/users",
  "timestamp": "2026-11-25T13:37:00.403624+03:00"
}
```

### 12.5. Изменить должность и роль

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/users/27' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"fullName": "Марина Захарова", "email": "marina.zakharova@technosoft.by", "departmentId": "2", "position": "Ведущий QA-инженер", "role": "manager"}'
```

Ответ: **200 OK**

```json
{
  "id": "27",
  "fullName": "Марина Захарова",
  "email": "marina.zakharova@technosoft.by",
  "departmentId": "2",
  "position": "Ведущий QA-инженер",
  "role": "manager",
  "status": "active",
  "employeeId": "27"
}
```

### 12.6. Заблокировать

```bash
curl -s -X PATCH 'http://localhost:8080/api/v1/users/27/status' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"status": "blocked"}'
```

Ответ: **200 OK**

```json
{
  "id": "27",
  "fullName": "Марина Захарова",
  "email": "marina.zakharova@technosoft.by",
  "departmentId": "2",
  "position": "Ведущий QA-инженер",
  "role": "manager",
  "status": "blocked",
  "employeeId": "27"
}
```

### 12.7. Вход заблокированного → 403

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email": "marina.zakharova@technosoft.by", "password": "demo"}'
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "USER_BLOCKED",
  "message": "Учётная запись заблокирована. Обратитесь к администратору системы.",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-11-25T13:37:00.480112+03:00"
}
```

### 12.8. Разблокировать

```bash
curl -s -X PATCH 'http://localhost:8080/api/v1/users/27/status' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"status": "active"}'
```

Ответ: **200 OK**

```json
{
  "id": "27",
  "fullName": "Марина Захарова",
  "email": "marina.zakharova@technosoft.by",
  "departmentId": "2",
  "position": "Ведущий QA-инженер",
  "role": "manager",
  "status": "active",
  "employeeId": "27"
}
```

### 12.9. Заблокировать самого себя → 409

```bash
curl -s -X PATCH 'http://localhost:8080/api/v1/users/17/status' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"status": "blocked"}'
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "CONFLICT",
  "message": "Нельзя заблокировать собственную учётную запись.",
  "path": "/api/v1/users/17/status",
  "timestamp": "2026-11-25T13:37:00.489768+03:00"
}
```

### 12.10. Ошибки заполнения → 422

```bash
curl -s -X POST 'http://localhost:8080/api/v1/users' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"fullName": "", "email": "marina", "departmentId": null, "position": "", "role": "boss"}'
```

Ответ: **422 Unprocessable Entity**

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "Выберите подразделение. Похоже, в адресе опечатка: нужен вид name@technosoft.by. Укажите ФИО. Укажите должность. Роль: employee, manager, hr, finance или admin.",
  "path": "/api/v1/users",
  "timestamp": "2026-11-25T13:37:00.49225+03:00"
}
```

## 13. UC13. Настройка интеграций

Роль: admin. Ключ API хранится зашифрованным (AES-256-GCM) и возвращается маской `••••••••`; маска в запросе означает «использовать сохранённый ключ».

### 13.1. Подключения

```bash
curl -s -X GET 'http://localhost:8080/api/v1/integrations' \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Ответ: **200 OK**

```json
[
  {
    "id": "1",
    "kind": "hris",
    "name": "HRIS",
    "description": "Кадровые данные: сотрудники, подразделения, должности (DATA-2).",
    "url": "https://hris.technosoft.by/api/v2",
    "apiKey": "••••••••",
    "enabled": true,
    "lastSyncAt": "2026-11-25T07:00:00Z",
    "lastCheck": {
      "ok": true,
      "message": "Соединение установлено",
      "checkedAt": "2026-11-25T07:00:00Z",
      "latencyMs": 182
    }
  },
  {
    "id": "2",
    "kind": "erp",
    "name": "ERP",
    "description": "Проекты и этапы для начислений по проектным задачам.",
    "url": "http://erp.technosoft.local/api",
    "apiKey": "••••••••",
    "enabled": false,
    "lastCheck": {
      "ok": false,
      "message": "Сервер отклонил подключение: нужен адрес HTTPS",
      "checkedAt": "2026-11-24T09:00:00Z"
    }
  },
  "… ещё 2 из 4"
]
```

### 13.2. Проверка соединения: адрес не HTTPS → ok = false

```bash
curl -s -X POST 'http://localhost:8080/api/v1/integrations/2/test' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"url": "http://erp.technosoft.local/api", "apiKey": "••••••••"}'
```

Ответ: **200 OK**

```json
{
  "ok": false,
  "message": "Сервер отклонил подключение: нужен адрес HTTPS",
  "checkedAt": "2026-11-25T10:37:00.505272Z"
}
```

### 13.3. Проверка соединения: короткий ключ → ok = false

```bash
curl -s -X POST 'http://localhost:8080/api/v1/integrations/2/test' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://erp.technosoft.by/api", "apiKey": "erp-1"}'
```

Ответ: **200 OK**

```json
{
  "ok": false,
  "message": "Ключ не принят: ERP ожидает ключ не короче 8 символов",
  "checkedAt": "2026-11-25T10:37:00.513167Z"
}
```

### 13.4. Проверка соединения: успех

```bash
curl -s -X POST 'http://localhost:8080/api/v1/integrations/2/test' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://erp.technosoft.by/api", "apiKey": "erp-51c0aa-2026"}'
```

Ответ: **200 OK**

```json
{
  "ok": true,
  "message": "Соединение установлено",
  "checkedAt": "2026-11-25T10:37:00.518837Z",
  "latencyMs": 93
}
```

### 13.5. Сохранить адрес и ключ

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/integrations/2' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://erp.technosoft.by/api", "apiKey": "erp-51c0aa-2026"}'
```

Ответ: **200 OK**

```json
{
  "id": "2",
  "kind": "erp",
  "name": "ERP",
  "description": "Проекты и этапы для начислений по проектным задачам.",
  "url": "https://erp.technosoft.by/api",
  "apiKey": "••••••••",
  "enabled": false,
  "lastCheck": {
    "ok": true,
    "message": "Соединение установлено",
    "checkedAt": "2026-11-25T10:37:00.526124Z",
    "latencyMs": 93
  }
}
```

### 13.6. Включить синхронизацию

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/integrations/2' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"enabled": true}'
```

Ответ: **200 OK**

```json
{
  "id": "2",
  "kind": "erp",
  "name": "ERP",
  "description": "Проекты и этапы для начислений по проектным задачам.",
  "url": "https://erp.technosoft.by/api",
  "apiKey": "••••••••",
  "enabled": true,
  "lastSyncAt": "2026-11-25T10:37:00.534862Z",
  "lastCheck": {
    "ok": true,
    "message": "Соединение установлено",
    "checkedAt": "2026-11-25T10:37:00.526124Z",
    "latencyMs": 93
  }
}
```

### 13.7. Сохранить неработающий адрес → 409

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/integrations/2' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"url": "http://erp.local", "apiKey": "••••••••"}'
```

Ответ: **409 Conflict**

```json
{
  "status": 409,
  "code": "CONFLICT",
  "message": "Параметры не сохранены — проверка соединения не прошла: «Сервер отклонил подключение: нужен адрес HTTPS». Исправьте адрес или ключ и проверьте снова.",
  "path": "/api/v1/integrations/2",
  "timestamp": "2026-11-25T13:37:00.54343+03:00"
}
```

### 13.8. Интеграция не найдена → 404

```bash
curl -s -X POST 'http://localhost:8080/api/v1/integrations/99/test' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://x.technosoft.by", "apiKey": "12345678"}'
```

Ответ: **404 Not Found**

```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Интеграция не найдена.",
  "path": "/api/v1/integrations/99/test",
  "timestamp": "2026-11-25T13:37:00.547488+03:00"
}
```

## 14. Журнал аудита и уведомления (NFR-3, SEC-4)

### 14.1. Журнал аудита (admin), поиск «Ковалёва»

```bash
curl -s -X GET 'http://localhost:8080/api/v1/audit?q=%D0%9A%D0%BE%D0%B2%D0%B0%D0%BB%D1%91%D0%B2%D0%B0' \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Ответ: **200 OK**

```json
[
  {
    "id": "194",
    "timestamp": "2026-11-25T10:37:00.029562Z",
    "userName": "Анна Ковалёва",
    "role": "employee",
    "action": "Использование баллов",
    "objectType": "Операция",
    "objectLabel": "ОП-000291, −1 500 Б",
    "ip": "—"
  },
  {
    "id": "190",
    "timestamp": "2026-11-25T10:36:59.805421Z",
    "userName": "Анна Ковалёва",
    "role": "employee",
    "action": "Использование баллов",
    "objectType": "Операция",
    "objectLabel": "ОП-000287, −6 000 Б",
    "ip": "—"
  },
  {
    "id": "184",
    "timestamp": "2026-11-25T10:36:59.199685Z",
    "userName": "Анна Ковалёва",
    "role": "employee",
    "action": "Вход в систему",
    "objectType": "Сессия",
    "objectLabel": "anna.kovaleva@technosoft.by",
    "ip": "0:0:0:0:0:0:0:1"
  },
  "… ещё 1 из 4"
]
```

### 14.2. Журнал аудита за сегодня

```bash
curl -s -X GET 'http://localhost:8080/api/v1/audit?from=2026-11-25&to=2026-11-25' \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Ответ: **200 OK**

```json
[
  {
    "id": "211",
    "timestamp": "2026-11-25T10:37:00.536247Z",
    "userName": "Сергей Литвинов",
    "role": "admin",
    "action": "Включение интеграции",
    "objectType": "Интеграция",
    "objectLabel": "ERP",
    "ip": "0:0:0:0:0:0:0:1"
  },
  {
    "id": "210",
    "timestamp": "2026-11-25T10:37:00.529012Z",
    "userName": "Сергей Литвинов",
    "role": "admin",
    "action": "Изменение параметров",
    "objectType": "Интеграция",
    "objectLabel": "ERP",
    "ip": "0:0:0:0:0:0:0:1"
  },
  {
    "id": "209",
    "timestamp": "2026-11-25T10:37:00.519459Z",
    "userName": "Сергей Литвинов",
    "role": "admin",
    "action": "Проверка соединения",
    "objectType": "Интеграция",
    "objectLabel": "ERP — успех",
    "ip": "0:0:0:0:0:0:0:1"
  },
  "… ещё 26 из 29"
]
```

### 14.3. Уведомления сотрудника

```bash
curl -s -X GET 'http://localhost:8080/api/v1/notifications' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **200 OK**

```json
[
  {
    "id": "11",
    "title": "Начисление заблокировано",
    "text": "ОП-000292: Отмена уводит баланс в минус: сотрудник уже потратил баллы",
    "createdAt": "2026-11-25T10:37:00.107842Z",
    "read": false
  },
  {
    "id": "9",
    "title": "Корректировка на согласовании",
    "text": "ОП-000292: 1 900 Б → 0 Б. Начисление задвоено с ОП-000117",
    "createdAt": "2026-11-25T10:37:00.050243Z",
    "read": false
  },
  {
    "id": "8",
    "title": "Списано 1 500 Б",
    "text": "Вы участвуете: Программа «Сертификат в книжный магазин». Детали придут на почту.",
    "createdAt": "2026-11-25T10:37:00.032678Z",
    "read": false
  },
  "… ещё 3 из 6"
]
```

### 14.4. Отметить все прочитанными

```bash
curl -s -X POST 'http://localhost:8080/api/v1/notifications/read' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **204 No Content**

## 15. Общие ошибки: 401, 403, 404, 405, 415

### 15.1. Без токена → 401

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me'
```

Ответ: **401 Unauthorized**

```json
{
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Сессия закончилась. Войдите снова.",
  "path": "/api/v1/accounts/me",
  "timestamp": "2026-11-25T13:37:00.569773+03:00"
}
```

### 15.2. Поддельный токен → 401

```bash
curl -s -X GET 'http://localhost:8080/api/v1/accounts/me' \
  -H 'Authorization: Bearer garbage.token.value'
```

Ответ: **401 Unauthorized**

```json
{
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Сессия закончилась. Войдите снова.",
  "path": "/api/v1/accounts/me",
  "timestamp": "2026-11-25T13:37:00.570853+03:00"
}
```

### 15.3. Чужая роль: сотрудник открывает журнал аудита → 403

```bash
curl -s -X GET 'http://localhost:8080/api/v1/audit' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **403 Forbidden**

```json
{
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Для этого действия у вашей роли нет прав.",
  "path": "/api/v1/audit",
  "timestamp": "2026-11-25T13:37:00.57289+03:00"
}
```

### 15.4. Неизвестный адрес → 404

```bash
curl -s -X GET 'http://localhost:8080/api/v1/bonuses' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **404 Not Found**

```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Адрес /api/v1/bonuses не найден.",
  "path": "/api/v1/bonuses",
  "timestamp": "2026-11-25T13:37:00.576918+03:00"
}
```

### 15.5. Операция не найдена → 404

```bash
curl -s -X PUT 'http://localhost:8080/api/v1/operations/999999' \
  -H "Authorization: Bearer $TOKEN_HR" \
  -H 'Content-Type: application/json' \
  -d '{"action": "cancel", "reason": "Ошибка"}'
```

Ответ: **404 Not Found**

```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Операция не найдена. Обновите журнал.",
  "path": "/api/v1/operations/999999",
  "timestamp": "2026-11-25T13:37:00.580964+03:00"
}
```

### 15.6. Метод не поддерживается → 405

```bash
curl -s -X DELETE 'http://localhost:8080/api/v1/programs' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **405 Method Not Allowed**

```json
{
  "status": 405,
  "code": "METHOD_NOT_ALLOWED",
  "message": "Метод DELETE не поддерживается для /api/v1/programs.",
  "path": "/api/v1/programs",
  "timestamp": "2026-11-25T13:37:00.583654+03:00"
}
```

### 15.7. Тело не JSON → 415

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: text/plain' \
  -d 'email=anna'
```

Ответ: **415 Unsupported Media Type**

```json
{
  "status": 415,
  "code": "UNSUPPORTED_MEDIA_TYPE",
  "message": "Передайте тело запроса в формате application/json.",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-11-25T13:37:00.585074+03:00"
}
```

### 15.8. Выход → 204

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/logout' \
  -H "Authorization: Bearer $TOKEN_EMPLOYEE"
```

Ответ: **204 No Content**
