# Журнал проверки установки (25.11.2026 19:28:53 MSK)

### 0.1 ОС

```
$ sw_vers
ProductName:		macOS
ProductVersion:		26.5.2
BuildVersion:		25F84
```
Код завершения: 0; время: 0.01 с

### 0.2 Java

```
$ java -version
openjdk version "21" 2023-09-19 LTS
OpenJDK Runtime Environment Temurin-21+35 (build 21+35-LTS)
OpenJDK 64-Bit Server VM Temurin-21+35 (build 21+35-LTS, mixed mode)
```
Код завершения: 0; время: 0.04 с

### 0.3 PostgreSQL (клиент и сервер)

```
$ bash -c psql --version; pg_restore --version; psql -d postgres -Atc "select version()"
psql (PostgreSQL) 14.18 (Homebrew)
pg_restore (PostgreSQL) 14.18 (Homebrew)
PostgreSQL 14.18 (Homebrew) on aarch64-apple-darwin24.4.0, compiled by Apple clang version 17.0.0 (clang-1700.0.13.3), 64-bit
```
Код завершения: 0; время: 0.04 с

### 0.4 Порт 8080 свободен (lsof не находит слушающий процесс)

```
$ bash -c ! lsof -nP -iTCP:8080 -sTCP:LISTEN

```
Код завершения: 0; время: 0.03 с

### 0.5 БД technosoft_bonus отсутствует

```
$ bash -c ! psql -d postgres -Atc "select 1 from pg_database where datname='technosoft_bonus'" | grep -q 1

```
Код завершения: 0; время: 0.03 с

### 1.1 Распаковка архива

```
$ unzip -q /Users/valeriyyerashevich/SITES/snow2027/ТПСИС/ТПСИС 2/вариант 46(1-12)/Yeroshevich_314371/release/dist/TechnoSoftBonus-1.0.zip -d /private/tmp/claude-501/-Users-valeriyyerashevich-SITES-snow2027-------------2---------46-1-12-/039bccbc-1edb-42f5-980e-d76e69a6938f/scratchpad/install-final

```
Код завершения: 0; время: 0.25 с

### 1.2 Состав дистрибутива

```
$ bash -c find . -type f | sort | xargs ls -l | awk "{print \$1, \$5, \$9}"; du -sh .
-rw-r--r--@ 7167 ./README.txt
-rw-r--r--@ 752 ./SHA256SUMS.txt
-rw-r--r--@ 58907190 ./app/technosoft-bonus.jar
-rw-r--r--@ 5059 ./config/application.yml
-rw-r--r--@ 73784 ./db/technosoft_bonus.backup
-rw-r--r--@ 95052 ./db/technosoft_bonus.sql
-rw-r--r--@ 4315 ./restore-db.bat
-rwxr-xr-x@ 4310 ./restore-db.sh
-rw-r--r--@ 3201 ./start.bat
-rwxr-xr-x@ 3098 ./start.sh
 56M	.
```
Код завершения: 0; время: 0.02 с

### 1.3 Проверка контрольных сумм

```
$ shasum -a 256 -c SHA256SUMS.txt
README.txt: OK
app/technosoft-bonus.jar: OK
config/application.yml: OK
db/technosoft_bonus.backup: OK
db/technosoft_bonus.sql: OK
restore-db.bat: OK
restore-db.sh: OK
start.bat: OK
start.sh: OK
```
Код завершения: 0; время: 0.11 с

### 2.1 Восстановление БД из резервной копии

```
$ ./restore-db.sh localhost 5432 valeriyyerashevich technosoft_bonus
Сервер PostgreSQL: localhost:5432, пользователь valeriyyerashevich, база данных technosoft_bonus
1/3 Создание БД technosoft_bonus (UTF8)
2/3 Восстановление из db/technosoft_bonus.backup (pg_restore)
3/3 Проверка
версия схемы 2
сотрудников 26
учётных записей 26
операций 153
Готово. БД technosoft_bonus восстановлена; запуск сервера — start.sh.
```
Код завершения: 0; время: 0.61 с

### 2.2 Повторный запуск (БД уже есть — ожидается отказ без перезаписи)

```
$ ./restore-db.sh localhost 5432 valeriyyerashevich technosoft_bonus
Сервер PostgreSQL: localhost:5432, пользователь valeriyyerashevich, база данных technosoft_bonus
1/3 Создание БД technosoft_bonus (UTF8)
createdb: error: database creation failed: ERROR:  database "technosoft_bonus" already exists
Ошибка: БД не создана. Если она уже существует, удалите её или запустите: FORCE=1 ./restore-db.sh localhost 5432 valeriyyerashevich technosoft_bonus
```
Код завершения: 1; время: 0.03 с

### 3.1 Запуск без секретов (ожидается понятный отказ)

```
$ perl -e alarm 30; exec @ARGV env -u BONUS_JWT_SECRET -u BONUS_SECRET_KEY ./start.sh
Ошибка: не заданы секреты BONUS_JWT_SECRET и/или BONUS_SECRET_KEY.
Сгенерируйте их (openssl rand -base64 48 и openssl rand -base64 32) и задайте
переменными окружения или в начале start.sh. См. README.txt, шаг 4.
```
Код завершения: 1; время: 0.30 с

### 3.2 Секреты сгенерированы (длина, символов)

```
$ bash -c echo "BONUS_JWT_SECRET: ${#BONUS_JWT_SECRET}, BONUS_SECRET_KEY: ${#BONUS_SECRET_KEY}"
BONUS_JWT_SECRET: 64, BONUS_SECRET_KEY: 44
```
Код завершения: 0; время: 0.01 с

### 4.1 Запуск сервера: nohup ./start.sh > console.txt &

```
TechnoSoft Bonus: http://localhost:8080  (БД localhost:5432/technosoft_bonus, пользователь valeriyyerashevich)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::               (v3.3.13)

2026-11-25T19:28:55.158+03:00  INFO 73466 --- [technosoft-bonus] [           main] by.technosoft.bonus.BonusApplication     : Starting BonusApplication v0.1.0-SNAPSHOT using Java 21 with PID 73466 (/private/tmp/claude-501/-Users-valeriyyerashevich-SITES-snow2027-------------2---------46-1-12-/039bccbc-1edb-42f5-980e-d76e69a6938f/scratchpad/install-final/TechnoSoftBonus-1.0/app/technosoft-bonus.jar started by valeriyyerashevich in /private/tmp/claude-501/-Users-valeriyyerashevich-SITES-snow2027-------------2---------46-1-12-/039bccbc-1edb-42f5-980e-d76e69a6938f/scratchpad/install-final/TechnoSoftBonus-1.0)
2026-11-25T19:28:55.159+03:00  INFO 73466 --- [technosoft-bonus] [           main] by.technosoft.bonus.BonusApplication     : No active profile set, falling back to 1 default profile: "default"
2026-11-25T19:28:55.745+03:00  INFO 73466 --- [technosoft-bonus] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-11-25T19:28:55.814+03:00  INFO 73466 --- [technosoft-bonus] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 63 ms. Found 17 JPA repository interfaces.
2026-11-25T19:28:56.486+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8080 (http)
2026-11-25T19:28:56.507+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-11-25T19:28:56.507+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.42]
2026-11-25T19:28:56.546+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2026-11-25T19:28:56.547+03:00  INFO 73466 --- [technosoft-bonus] [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 1368 ms
2026-11-25T19:28:56.944+03:00  INFO 73466 --- [technosoft-bonus] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-11-25T19:28:57.132+03:00  INFO 73466 --- [technosoft-bonus] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@43b6cb1c
2026-11-25T19:28:57.135+03:00  INFO 73466 --- [technosoft-bonus] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-11-25T19:28:57.186+03:00  INFO 73466 --- [technosoft-bonus] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/technosoft_bonus (PostgreSQL 14.18)
2026-11-25T19:28:57.267+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 2 migrations (execution time 00:00.050s)
2026-11-25T19:28:57.290+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": 2
2026-11-25T19:28:57.296+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
2026-11-25T19:28:57.451+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.hibernate.jpa.internal.util.LogHelper  : HHH000204: Processing PersistenceUnitInfo [name: default]
2026-11-25T19:28:57.543+03:00  INFO 73466 --- [technosoft-bonus] [           main] org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.5.3.Final
2026-11-25T19:28:57.597+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.h.c.internal.RegionFactoryInitiator    : HHH000026: Second-level cache disabled
2026-11-25T19:28:58.155+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-11-25T19:28:59.906+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.h.e.t.j.p.i.JtaPlatformInitiator       : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-11-25T19:28:59.953+03:00  INFO 73466 --- [technosoft-bonus] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-25T19:29:00.201+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.s.d.j.r.query.QueryEnhancerFactory     : Hibernate is in classpath; If applicable, HQL parser will be used.
2026-11-25T19:29:01.751+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.s.b.a.w.s.WelcomePageHandlerMapping    : Adding welcome page: class path resource [static/index.html]
2026-11-25T19:29:01.919+03:00  INFO 73466 --- [technosoft-bonus] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
2026-11-25T19:29:01.925+03:00  INFO 73466 --- [technosoft-bonus] [           main] by.technosoft.bonus.BonusApplication     : Started BonusApplication in 6.992 seconds (process running for 7.192)
```
PID 73466; до строки «Started BonusApplication»: 7.28 с

### 5.1 Главная страница /

```
$ curl -s -o /dev/null -w HTTP %{http_code}, %{content_type}, %{size_download} байт, %{time_total} с\n http://localhost:8080/
HTTP 200, text/html;charset=UTF-8, 1506 байт, 0.034591 с
```
Код завершения: 0; время: 0.05 с

### 5.2 Глубокая ссылка /admin/users (обновление страницы)

```
$ curl -s -w \nHTTP %{http_code}, %{content_type}\n http://localhost:8080/admin/users
<!doctype html>
<html lang="ru">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="theme-color" content="#14213D" />
    <title>TechnoSoft Bonus</title>
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
      rel="stylesheet"
    />
    <script type="module" crossorigin src="/assets/index-DKBofB4Q.js"></script>
    <link rel="modulepreload" crossorigin href="/assets/rolldown-runtime-XKyaHtl9.js">
    <link rel="modulepreload" crossorigin href="/assets/format-CMSRE4HL.js">
    <link rel="modulepreload" crossorigin href="/assets/config-provider-Bs7KHx00.js">
    <link rel="modulepreload" crossorigin href="/assets/extendsObject-CtkcbUcF.js">
    <link rel="modulepreload" crossorigin href="/assets/useClosable-Dt8wisA2.js">
    <link rel="modulepreload" crossorigin href="/assets/row-CcBUoZ7R.js">
    <link rel="modulepreload" crossorigin href="/assets/app-9-9i_4As.js">
    <link rel="modulepreload" crossorigin href="/assets/dist-BYncPSVe.js">
    <link rel="modulepreload" crossorigin href="/assets/Input-3jkNUsLI.js">
    <link rel="stylesheet" crossorigin href="/assets/index-ZQdnlNEK.css">
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>

HTTP 200, text/html
```
Код завершения: 0; время: 0.01 с

### 5.3 API без токена → 401

```
$ curl -s -w \nHTTP %{http_code}\n http://localhost:8080/api/v1/accounts/me
{"status":401,"code":"UNAUTHORIZED","message":"Сессия закончилась. Войдите снова.","path":"/api/v1/accounts/me","timestamp":"2026-11-25T19:29:02.067918+03:00"}
HTTP 401
```
Код завершения: 0; время: 0.02 с

### 5.4 Вход сотрудника и запрос баланса

```
$ bash -c 
    T=$(curl -s -H 'Content-Type: application/json' -d '{"email":"anna.kovaleva@technosoft.by","password":"demo"}' http://localhost:8080/api/v1/auth/login | sed -E 's/.*"token":"([^"]+)".*/\1/')
    echo "токен: ${#T} символов"
    curl -s -H "Authorization: Bearer $T" http://localhost:8080/api/v1/accounts/me | head -c 400; echo
токен: 183 символов
{"account":{"employeeId":"1","status":"active","balance":8900,"pending":1500,"updatedAt":"2026-11-25T08:00:00Z"},"accruedThisMonth":1700,"spentThisMonth":0,"recent":[{"id":"146","number":"ОП-000146","employeeId":"1","employeeName":"Анна Ковалёва","departmentId":"1","type":"accrual","amount":1500,"status":"pending","basis":"achievement","reason":"Доклад на внутреннем
```
Код завершения: 0; время: 0.23 с

### 5.5 Документация API

```
$ curl -s -L -o /dev/null -w HTTP %{http_code} после %{num_redirects} перенаправления, итог %{url_effective}\n http://localhost:8080/swagger-ui.html
HTTP 200 после 1 перенаправления, итог http://localhost:8080/swagger-ui/index.html
```
Код завершения: 0; время: 0.02 с

### 5.6 Спецификация OpenAPI

```
$ bash -c curl -s http://localhost:8080/v3/api-docs | head -c 200; echo
{"openapi":"3.0.1","info":{"title":"TechnoSoft Bonus REST API","description":"Серверная часть ПС управления бонусной системой сотрудников (Spring B
```
Код завершения: 0; время: 0.24 с

### 5.7 Память процесса сервера (RSS, КБ) после запуска

```
$ ps -o pid,rss,vsz,command -p 73466
  PID    RSS      VSZ COMMAND
73466 505472 413907456 /Users/valeriyyerashevich/.sdkman/candidates/java/current/bin/java -Xms128m -Xmx512m -Dfile.encoding=UTF-8 -jar app/technosoft-bonus.jar --spring.config.additional-location=file:./config/application.yml
```
Код завершения: 0; время: 0.01 с

### 6.1 Браузер: вход, баланс, глубокая ссылка, документация API

```
$ node /Users/valeriyyerashevich/SITES/snow2027/ТПСИС/ТПСИС 2/вариант 46(1-12)/Yeroshevich_314371/release/metrics/install-browser-check.mjs
✓ Открыть http://localhost:8080 — 0.73 с: HTTP 200, адрес /login, заголовок «Вход»
✓ Войти как сотрудник — 1.78 с: адрес /me, «Здравствуйте, Анна», баланс 8 900 Б
✓ Глубокая ссылка: открыть /history и обновить страницу — 2.44 с: HTTP 200 и 200 (после обновления), заголовок «История операций»
✓ Открыть документацию API /swagger-ui.html — 0.8 с: HTTP 200, итоговый адрес /swagger-ui/index.html, «TechnoSoft Bonus REST API», операций 34
Ошибок в консоли браузера: 2. Результат: /Users/valeriyyerashevich/SITES/snow2027/ТПСИС/ТПСИС 2/вариант 46(1-12)/Yeroshevich_314371/release/metrics/out/install-browser-check.json
```
Код завершения: 0; время: 172.19 с
