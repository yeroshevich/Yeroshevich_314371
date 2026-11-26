# Проверка установки дистрибутива «с нуля» (ЛР 11)

Дата итогового прогона: **25.11.2026, 19:28–19:32 (MSK)**. Дистрибутив: `release/dist/TechnoSoftBonus-1.0.zip`
(собран `release/build-dist.sh`). Шаги выполнялись в той последовательности, которая описана в `README.txt`
дистрибутива, сценарием `release/metrics/install-check.sh` (каждая команда, её вывод, код завершения и длительность
записаны в журнал). Полный журнал — `release/metrics/out/install/install-log.md`, вывод консоли сервера —
`release/metrics/out/install/console.txt`, результат проверки в браузере — `release/metrics/out/install-browser-check.json`.
Установка выполнялась в пустую временную папку, БД — отдельная `technosoft_bonus` (рабочая БД разработки не использовалась);
после всех измерений сервер остановлен, БД удалена (`release/metrics/stop-all.sh`).

Первый прогон (25.11.2026, 16:02, тот же порядок шагов, предыдущая сборка дистрибутива с пулом соединений 10)
дал те же результаты; его времена приведены в столбце «1-й прогон» для сравнения.

## Окружение

| Компонент | Фактически | Требование дистрибутива |
|---|---|---|
| ОС | macOS 26.5.2 (Apple Silicon, 24 ГБ ОЗУ) | Windows 8+, macOS, Linux (проверена только macOS) |
| Java | OpenJDK 21 (Temurin-21+35) | Java 21 или новее (start.sh/start.bat проверяют версию) |
| PostgreSQL | 14.18 (сервер и клиентские программы psql, pg_restore) | 14 или новее (проверена 14.18) |
| Браузер | Google Chrome (headless, playwright-core) | Chrome, Edge, Firefox, Safari актуальных версий |
| Порт | 8080 свободен | 8080 или `BONUS_PORT` |

## Последовательность установки и время шагов

| № | Шаг (как в README.txt) | Команда | Результат | Время, с (итог / 1-й прогон) |
|---|---|---|---|---|
| 0 | Проверка окружения | `java -version`, `psql --version`, `lsof -iTCP:8080` | Java 21, PostgreSQL 14.18, порт свободен, БД `technosoft_bonus` нет | 0,15 / 0,16 |
| 1.1 | Распаковка | `unzip TechnoSoftBonus-1.0.zip -d <папка>` | 10 файлов, 56 МБ | 0,25 / 0,27 |
| 1.2 | Контрольные суммы | `shasum -a 256 -c SHA256SUMS.txt` | все 9 файлов `OK` | 0,11 / 0,12 |
| 2.1 | Восстановление БД | `./restore-db.sh localhost 5432 <пользователь> technosoft_bonus` | БД создана (UTF8), восстановлена из `db/technosoft_bonus.backup`; версия схемы 2, сотрудников 26, учётных записей 26, операций 153 | 0,61 / 0,65 |
| 2.2 | Повторный запуск восстановления | та же команда | отказ (код 1): «БД не создана. Если она уже существует, удалите её или запустите: FORCE=1 …»; данные не перезаписаны | 0,03 / 0,03 |
| 3.1 | Запуск без секретов (проверка защиты) | `./start.sh` без `BONUS_JWT_SECRET`, `BONUS_SECRET_KEY` | отказ (код 1) с подсказкой, как сгенерировать секреты; Java не запускалась | 0,30 / 0,20 |
| 3.2 | Настройка | `export BONUS_DB_USER=…`; `BONUS_JWT_SECRET=$(openssl rand -base64 48)`; `BONUS_SECRET_KEY=$(openssl rand -base64 32)` | секреты 64 и 44 символа | 0,01 / 0,02 |
| 4 | Запуск сервера | `nohup ./start.sh > console.txt &` | «Started BonusApplication», порт 8080, Flyway: схема версии 2 актуальна, миграции не нужны | 7,28 / 3,49 |
| 5.1 | Главная страница | `curl http://localhost:8080/` | HTTP 200, `text/html`, 1 506 байт | 0,05 / 0,06 |
| 5.2 | Глубокая ссылка (обновление страницы) | `curl http://localhost:8080/admin/users` | HTTP 200, `index.html` клиента | 0,01 / 0,03 |
| 5.3 | API без токена | `curl /api/v1/accounts/me` | HTTP 401 `{"code":"UNAUTHORIZED",…}` | 0,02 / 0,02 |
| 5.4 | Вход и баланс через API | `POST /api/v1/auth/login`, `GET /api/v1/accounts/me` | токен 183 символа; `"balance":8900` | 0,23 / 0,28 |
| 5.5 | Документация API | `curl -L /swagger-ui.html` | HTTP 200 после 1 перенаправления на `/swagger-ui/index.html` | 0,02 / 0,03 |
| 5.6 | Спецификация OpenAPI | `curl /v3/api-docs` | JSON «TechnoSoft Bonus REST API» | 0,24 / 0,36 |
| 5.7 | Память после запуска | `ps -o rss -p <pid>` | RSS 505 472 КБ (≈ 494 МБ) при `-Xmx512m` | 0,01 / 0,01 |
| 6.1 | Браузер: открыть http://localhost:8080 | Chrome | HTTP 200, переход на `/login`, заголовок «Вход» (снимок `screens/install-login.png`) | 0,73 / 2,33 |
| 6.2 | Браузер: «Войти как сотрудник» | Chrome | `/me`, «Здравствуйте, Анна», баланс **8 900 Б** (снимок `screens/install-main.png`) | 1,78 / 1,77 |
| 6.3 | Браузер: открыть `/history` и обновить страницу | Chrome | HTTP 200 и 200 после обновления, заголовок «История операций» | 2,44 / 1,24 |
| 6.4 | Браузер: документация API | Chrome | `/swagger-ui/index.html`, «TechnoSoft Bonus REST API», 34 операции (снимок `screens/install-swagger.png`) | 0,80 / 0,83 |

Итог: от распаковки до готового к работе сервера — около 9 с машинного времени (без учёта ввода команд);
все проверки работоспособности пройдены. Шаг 6 в журнале итогового прогона занял 172 с, из них сами действия
в браузере — 5,8 с (таблица); остальное — запуск и закрытие Chrome вне измеряемых шагов. В момент итогового прогона
у компьютера не было доступа в интернет: браузер дважды сообщил `ERR_NAME_NOT_RESOLVED` — это внешний шрифт Inter
с fonts.googleapis.com, который подключает `index.html` клиента; интерфейс при этом работал на системном шрифте.
В первом прогоне (с сетью) весь шаг 6 занял 5,9 с, ошибок в консоли браузера не было.

Время запуска сервера в итоговом прогоне (7,3 с) больше, чем в первом (3,5 с): по журналу консоли инициализация JPA
заняла 2,5 с против 0,9 с; другие шаги не замедлились. Причина не устанавливалась (предположительно фоновая нагрузка
на компьютер); оба значения приведены как измеренные.

## Дополнительные проверки сценариев дистрибутива

| Проверка | Команда | Результат |
|---|---|---|
| Восстановление из SQL-копии (если нет `pg_restore` или файла `.backup`) | `restore-db.sh` в папке только с `db/technosoft_bonus.sql` | БД восстановлена через `psql`: версия схемы 2, 26 / 26 / 153 (`release/metrics/out/install/restore-from-sql.txt`) |
| Пересоздание существующей БД | `FORCE=1 ./restore-db.sh … technosoft_bonus_replay` | БД удалена и восстановлена заново (использовалось для второго экземпляра) |
| Недопустимое имя БД | `./restore-db.sh localhost 5432 <пользователь> bad-name` | отказ: «допустимы латинские буквы, цифры и «_»» |
| Второй экземпляр без пересборки | `BONUS_PORT=8081 BONUS_DB_NAME=technosoft_bonus_replay BONUS_DB_POOL_SIZE=20 BONUS_HTTP_THREADS=15 ./start.sh` | сервер на порту 8081 с другой БД; токен, выданный экземпляром 8080, принят экземпляром 8081 (HTTP 200) |
| Изменение интервала обработки событий | `BONUS_EVENT_SCHEDULER_DELAY=PT5S ./start.sh` | очередь событий обрабатывается каждые 5 с (нужно для тест-кейса TC-10) |
| Повторный прогон 30 тест-кейсов ЛР 10 против установленного дистрибутива | `release/metrics/run-test-cases.sh` | **30 из 30** пройдено (клиент раздаётся сервером, БД установочная) |

## Вывод консоли при запуске (итоговый прогон)

Столбцы PID и имени приложения сокращены; полный вывод — `release/metrics/out/install/console.txt`.

```
TechnoSoft Bonus: http://localhost:8080  (БД localhost:5432/technosoft_bonus, пользователь valeriyyerashevich)

 :: Spring Boot ::               (v3.3.13)

19:28:55.158 INFO [main] BonusApplication: Starting BonusApplication v0.1.0-SNAPSHOT using Java 21 with PID 73466
19:28:55.159 INFO [main] BonusApplication: No active profile set, falling back to 1 default profile: "default"
19:28:55.814 INFO [main] RepositoryConfigurationDelegate: Finished Spring Data repository scanning in 63 ms. Found 17 JPA repository interfaces.
19:28:56.486 INFO [main] TomcatWebServer: Tomcat initialized with port 8080 (http)
19:28:56.507 INFO [main] StandardEngine: Starting Servlet engine: [Apache Tomcat/10.1.42]
19:28:56.944 INFO [main] HikariDataSource: HikariPool-1 - Starting...
19:28:57.135 INFO [main] HikariDataSource: HikariPool-1 - Start completed.
19:28:57.186 INFO [main] FlywayExecutor: Database: jdbc:postgresql://localhost:5432/technosoft_bonus (PostgreSQL 14.18)
19:28:57.267 INFO [main] DbValidate: Successfully validated 2 migrations (execution time 00:00.050s)
19:28:57.290 INFO [main] DbMigrate: Current version of schema "public": 2
19:28:57.296 INFO [main] DbMigrate: Schema "public" is up to date. No migration necessary.
19:28:57.543 INFO [main] Version: HHH000412: Hibernate ORM core version 6.5.3.Final
19:28:59.953 INFO [main] LocalContainerEntityManagerFactoryBean: Initialized JPA EntityManagerFactory for persistence unit 'default'
19:29:01.751 INFO [main] WelcomePageHandlerMapping: Adding welcome page: class path resource [static/index.html]
19:29:01.919 INFO [main] TomcatWebServer: Tomcat started on port 8080 (http) with context path '/'
19:29:01.925 INFO [main] BonusApplication: Started BonusApplication in 6.992 seconds (process running for 7.192)
19:29:01.979 INFO [main] CampaignLifecycleSchedulingConfig: Синхронизация статусов кампаний при старте приложения: изменено 0
19:29:02.002 INFO [nio-8080-exec-1] DispatcherServlet: Completed initialization in 0 ms
19:29:02.588 INFO [nio-8080-exec-3] AbstractOpenApiResource: Init duration for springdoc-openapi is: 211 ms
```

## Снимки экрана

- `screens/install-login.png` — страница «Вход» сразу после запуска (http://localhost:8080 → /login);
- `screens/install-main.png` — главный экран сотрудника «Мой баланс» после входа (баланс 8 900 Б);
- `screens/install-swagger.png` — документация REST API (Swagger UI, 34 операции).

## Требования к окружению (измерено)

| Параметр | Значение |
|---|---|
| Архив дистрибутива | 53 167 298 байт (≈ 51 МБ) |
| Распакованная папка | 56 МБ (из них сервер со встроенным клиентом 58 907 190 байт; копия БД 73 784 байт, SQL-копия 95 052 байт) |
| Java | 21 (сборка под Java 21; более старая версия — понятный отказ start.sh) |
| PostgreSQL | 14.18 проверена; пул сервера — 45 соединений (`BONUS_DB_POOL_SIZE`), второй экземпляр запускался с 20 |
| Память процесса сервера | после запуска 494 МБ RSS (куча занята 85 МБ из 512); под нагрузкой 500 клиентов — до 540 МБ RSS, куча до 146 МБ (итоговый прогон) |
| Время запуска | 3,5–7,3 с до «Started BonusApplication» |
| Время восстановления БД | 0,6 с (`pg_restore`), SQL-вариант — сопоставимо |

## Что не проверено

- **Windows.** `start.bat` и `restore-db.bat` не исполнялись (компьютера с Windows нет). Они проверены чтением:
  только ASCII-символы (нет проблем с кодовой страницей cmd), переводы строк CRLF (подтверждено `file`: «DOS batch file
  text, ASCII text, with CRLF line terminators»), переменные внутри блоков `( … )` не используются — ветвления сделаны
  через `goto`, поэтому не нужна отложенная подстановка; вывод `psql` в цикл `for /f` не передаётся (обход проблемы
  кавычек и знака `=`); поиск PostgreSQL — `PATH`, затем `PG_BIN`, затем `%ProgramFiles%\PostgreSQL\*\bin`; версия Java
  разбирается из первой строки `java -version`; при ошибке восстановления частично созданная БД удаляется.
  Остаётся риск, что какая-то из этих конструкций поведёт себя иначе на конкретной версии Windows: проверить
  установку на Windows 8/10/11 до сдачи.
- Мобильные платформы (Android 10+, iOS 13+) — только эмуляция ширины экрана 375 пикселей в Chrome для компьютера,
  на реальных устройствах клиент не открывался.
- Linux, другие браузеры (Firefox, Safari, Edge) — не проверялись.
- PostgreSQL новее 14 — не проверялся (формат копии custom версии 1.14 читается `pg_restore` 14 и новее).
