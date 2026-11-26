#!/usr/bin/env bash
# ЛР 11. Проверка установки дистрибутива «с нуля» строго по шагам README.txt (macOS: start.sh, restore-db.sh).
# Каждый шаг: команда, вывод, код завершения и длительность — в журнал $WORK/install-log.md.
# Сервер остаётся запущенным (PID в $WORK/server.pid) — на нём выполняются измерения метрик.
#
#   WORK=/путь/к/пустой/папке DB_USER=<пользователь PostgreSQL> release/metrics/install-check.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ZIP="${ZIP:-$ROOT/release/dist/TechnoSoftBonus-1.0.zip}"
WORK="${WORK:?укажите WORK — пустую папку для установки}"
DB_USER="${DB_USER:-postgres}"
DB_NAME="${DB_NAME:-technosoft_bonus}"
LOG="$WORK/install-log.md"
APP="$WORK/TechnoSoftBonus-1.0"

mkdir -p "$WORK"
: > "$LOG"
now() { perl -MTime::HiRes=time -e 'printf "%.2f", time'; }
# run "описание" команда... — выполняет в $PWD, пишет команду, вывод, код и время
run() {
    local title="$1"; shift
    local t0 t1 code out
    t0=$(now)
    out="$("$@" 2>&1)"; code=$?
    t1=$(now)
    {
        printf '\n### %s\n\n```\n$ %s\n%s\n```\n' "$title" "$*" "$out"
        printf 'Код завершения: %s; время: %s с\n' "$code" "$(perl -e "printf '%.2f', $t1-$t0")"
    } >> "$LOG"
    printf '%-70s код %s, %s с\n' "$title" "$code" "$(perl -e "printf '%.2f', $t1-$t0")"
    return $code
}

echo "# Журнал проверки установки ($(date '+%d.%m.%Y %H:%M:%S %Z'))" >> "$LOG"

# ---- Шаг 0. Окружение ------------------------------------------------------------------------
run "0.1 ОС" sw_vers
run "0.2 Java" java -version
run "0.3 PostgreSQL (клиент и сервер)" bash -c 'psql --version; pg_restore --version; psql -d postgres -Atc "select version()"'
run "0.4 Порт 8080 свободен (lsof не находит слушающий процесс)" bash -c '! lsof -nP -iTCP:8080 -sTCP:LISTEN'
run "0.5 БД $DB_NAME отсутствует" bash -c "! psql -d postgres -Atc \"select 1 from pg_database where datname='$DB_NAME'\" | grep -q 1"

# ---- Шаг 1. Распаковка -----------------------------------------------------------------------
cd "$WORK" || exit 1
run "1.1 Распаковка архива" unzip -q "$ZIP" -d "$WORK"
cd "$APP" || exit 1
run "1.2 Состав дистрибутива" bash -c 'find . -type f | sort | xargs ls -l | awk "{print \$1, \$5, \$9}"; du -sh .'
run "1.3 Проверка контрольных сумм" shasum -a 256 -c SHA256SUMS.txt

# ---- Шаг 2. Восстановление БД ----------------------------------------------------------------
run "2.1 Восстановление БД из резервной копии" ./restore-db.sh localhost 5432 "$DB_USER" "$DB_NAME"
run "2.2 Повторный запуск (БД уже есть — ожидается отказ без перезаписи)" ./restore-db.sh localhost 5432 "$DB_USER" "$DB_NAME"

# ---- Шаг 3. Настройка ------------------------------------------------------------------------
export BONUS_DB_USER="$DB_USER" BONUS_DB_NAME="$DB_NAME"
run "3.1 Запуск без секретов (ожидается понятный отказ)" perl -e "alarm 30; exec @ARGV" env -u BONUS_JWT_SECRET -u BONUS_SECRET_KEY ./start.sh
BONUS_JWT_SECRET="$(openssl rand -base64 48)"
BONUS_SECRET_KEY="$(openssl rand -base64 32)"
export BONUS_JWT_SECRET BONUS_SECRET_KEY
run "3.2 Секреты сгенерированы (длина, символов)" bash -c 'echo "BONUS_JWT_SECRET: ${#BONUS_JWT_SECRET}, BONUS_SECRET_KEY: ${#BONUS_SECRET_KEY}"'

# ---- Шаг 4. Запуск ---------------------------------------------------------------------------
t0=$(now)
nohup ./start.sh > "$WORK/console.txt" 2>&1 &
echo $! > "$WORK/server.pid"
for _ in $(seq 1 600); do
    grep -q 'Started BonusApplication' "$WORK/console.txt" && break
    kill -0 "$(cat "$WORK/server.pid")" 2>/dev/null || break
    sleep 0.1
done
t1=$(now)
{
    printf '\n### 4.1 Запуск сервера: nohup ./start.sh > console.txt &\n\n```\n'
    cat "$WORK/console.txt"
    printf '```\nPID %s; до строки «Started BonusApplication»: %s с\n' "$(cat "$WORK/server.pid")" "$(perl -e "printf '%.2f', $t1-$t0")"
} >> "$LOG"
printf '%-70s %s с\n' "4.1 Запуск сервера" "$(perl -e "printf '%.2f', $t1-$t0")"
grep -q 'Started BonusApplication' "$WORK/console.txt" || { echo "Сервер не запустился"; exit 1; }

# ---- Шаг 5. Проверка работоспособности (HTTP) ------------------------------------------------
B=http://localhost:8080
run "5.1 Главная страница /" curl -s -o /dev/null -w 'HTTP %{http_code}, %{content_type}, %{size_download} байт, %{time_total} с\n' $B/
run "5.2 Глубокая ссылка /admin/users (обновление страницы)" curl -s -w '\nHTTP %{http_code}, %{content_type}\n' $B/admin/users
run "5.3 API без токена → 401" curl -s -w '\nHTTP %{http_code}\n' $B/api/v1/accounts/me
run "5.4 Вход сотрудника и запрос баланса" bash -c "
    T=\$(curl -s -H 'Content-Type: application/json' -d '{\"email\":\"anna.kovaleva@technosoft.by\",\"password\":\"demo\"}' $B/api/v1/auth/login | sed -E 's/.*\"token\":\"([^\"]+)\".*/\\1/')
    echo \"токен: \${#T} символов\"
    curl -s -H \"Authorization: Bearer \$T\" $B/api/v1/accounts/me | head -c 400; echo"
run "5.5 Документация API" curl -s -L -o /dev/null -w 'HTTP %{http_code} после %{num_redirects} перенаправления, итог %{url_effective}\n' $B/swagger-ui.html
run "5.6 Спецификация OpenAPI" bash -c "curl -s $B/v3/api-docs | head -c 200; echo"
run "5.7 Память процесса сервера (RSS, КБ) после запуска" ps -o pid,rss,vsz,command -p "$(cat "$WORK/server.pid")"

# ---- Шаг 6. Проверка в браузере (Google Chrome) ----------------------------------------------
run "6.1 Браузер: вход, баланс, глубокая ссылка, документация API" node "$HERE/install-browser-check.mjs"

echo "Журнал: $LOG"
