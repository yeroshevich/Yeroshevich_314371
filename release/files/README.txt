TechnoSoft Bonus 1.0 — программное средство управления бонусной системой сотрудников
==================================================================================

Сервер (Java, Spring Boot) раздаёт веб-интерфейс и REST API на одном порту.
Пользователи работают в браузере: Windows 8 и новее, macOS, Linux, Android 10+, iOS 13+.

1. Необходимые компоненты
-------------------------
- Java 21 (JDK или JRE, например Eclipse Temurin 21): https://adoptium.net
  Проверка: java -version  -> версия 21 или новее.
- PostgreSQL 14 или новее (сервер и клиентские программы psql, pg_restore):
  https://www.postgresql.org/download/  (под Windows — установщик EDB, вместе с Command Line Tools).
- Браузер: Google Chrome, Microsoft Edge, Mozilla Firefox или Safari актуальной версии.
- Свободный порт 8080 (меняется переменной BONUS_PORT) и порт PostgreSQL (обычно 5432).
- Память: от 1 ГБ свободной оперативной памяти для сервера (предел кучи Java — 512 МБ, -Xmx512m;
  под нагрузкой 500 клиентов процесс занимал до 0,6 ГБ); диск: около 150 МБ (дистрибутив, БД, журналы).
- PostgreSQL: не менее 50 свободных соединений на экземпляр сервера (пул — 45, параметр BONUS_DB_POOL_SIZE).

2. Состав дистрибутива
----------------------
  app/technosoft-bonus.jar       сервер со встроенным веб-интерфейсом
  config/application.yml         настройки: БД, порт, секреты (через переменные окружения)
  db/technosoft_bonus.backup     резервная копия БД (формат pg_dump custom) с демонстрационными данными
  db/technosoft_bonus.sql        та же копия в виде SQL-скрипта
  restore-db.bat, restore-db.sh  создание БД и восстановление из резервной копии
  start.bat, start.sh            запуск сервера
  SHA256SUMS.txt                 контрольные суммы файлов

3. Восстановление базы данных
-----------------------------
Windows (командная строка в папке дистрибутива):
    set PGPASSWORD=пароль_пользователя_postgres
    restore-db.bat localhost 5432 postgres technosoft_bonus
macOS / Linux:
    PGPASSWORD=пароль ./restore-db.sh localhost 5432 postgres technosoft_bonus
Параметры по порядку: хост, порт, пользователь PostgreSQL (с правом создания БД), имя БД.
Без параметров используются значения, показанные выше. Скрипт создаёт БД в кодировке UTF8,
восстанавливает её из копии и выводит число сотрудников, учётных записей и операций (26, 26, 153).
Существующая БД с тем же именем не перезаписывается; пересоздать: set FORCE=1 (Windows)
или FORCE=1 ./restore-db.sh ... (macOS, Linux).

4. Настройка
------------
Обязательны два секрета (своё значение для каждой установки):
  BONUS_JWT_SECRET  — подпись токенов входа, не короче 32 символов;
  BONUS_SECRET_KEY  — ключ шифрования API-ключей интеграций: 32 байта в base64 (44 символа).
Сгенерировать:
  macOS / Linux:  openssl rand -base64 48    и    openssl rand -base64 32
  Windows (PowerShell), N = 48 и N = 32:
    $b = New-Object byte[] N; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
Задайте их переменными окружения или впишите в блок «Secrets» в начале start.bat / start.sh.
Подключение к БД: BONUS_DB_HOST, BONUS_DB_PORT, BONUS_DB_NAME, BONUS_DB_USER, BONUS_DB_PASSWORD
(по умолчанию localhost, 5432, technosoft_bonus, postgres, без пароля); порт сервера — BONUS_PORT.
Остальные параметры описаны в config/application.yml. Пересборка не требуется.
В пароле БД в start.bat символ % нужно удваивать (%%).

5. Запуск и проверка работоспособности
--------------------------------------
Windows: start.bat        macOS / Linux: ./start.sh
Сервер готов, когда в консоли появится строка «Started BonusApplication» (обычно 5–10 с).
Журнал также пишется в logs/technosoft-bonus.log. Остановка — Ctrl+C.
Проверка:
  1) откройте в браузере http://localhost:8080 — появится страница «Вход»;
  2) нажмите «Войти как сотрудник» (или email anna.kovaleva@technosoft.by, пароль demo) —
     откроется «Мой баланс» с текущим балансом;
  3) документация REST API: http://localhost:8080/swagger-ui.html
Демонстрационные учётные записи (пароль у всех demo): сотрудник anna.kovaleva@technosoft.by,
руководитель dmitry.savitsky@technosoft.by, HR olga.melnik@technosoft.by,
финансовый отдел igor.zhuk@technosoft.by, администратор sergey.litvinov@technosoft.by.
После проверки смените пароли или заблокируйте демонстрационные учётные записи.

6. Типичные проблемы
--------------------
- «Port 8080 was already in use» — порт занят: задайте другой BONUS_PORT.
- «Connection to localhost:5432 refused» / «password authentication failed» — проверьте, что
  PostgreSQL запущен, и параметры BONUS_DB_*.
- «нужна Java 21» / UnsupportedClassVersionError — установите Java 21 и/или задайте JAVA_HOME.
- Ошибка про BONUS_JWT_SECRET / BONUS_SECRET_KEY — не заданы или слишком короткие секреты (п. 4).
- «too many clients already» — у PostgreSQL не хватает соединений (max_connections) для пула сервера:
  уменьшите BONUS_DB_POOL_SIZE (по умолчанию 45) вместе с BONUS_HTTP_THREADS (по умолчанию 40);
  пул должен оставаться больше BONUS_HTTP_THREADS + 2.
