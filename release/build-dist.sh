#!/usr/bin/env bash
# Сборка дистрибутива TechnoSoft Bonus (ЛР 11).
#
#   release/build-dist.sh            # клиент + сервер (с тестами) + резервная копия БД + папка и zip
#   SKIP_TESTS=1 release/build-dist.sh
#
# Результат: release/dist/TechnoSoftBonus-<VERSION>/ и release/dist/TechnoSoftBonus-<VERSION>.zip
#   app/technosoft-bonus.jar       — сервер Spring Boot со встроенным клиентом (classpath:/static/)
#   config/application.yml        — внешняя конфигурация (release/files/config/application.yml)
#   db/technosoft_bonus.backup     — резервная копия БД (pg_dump, custom format)
#   db/technosoft_bonus.sql        — та же копия в виде SQL
#   restore-db.sh / restore-db.bat — создание БД и восстановление из копии
#   start.sh / start.bat           — запуск сервера
#   README.txt                     — краткое руководство по установке
#   SHA256SUMS.txt                 — контрольные суммы файлов
#
# Нужны: Node.js 20+ и npm, JDK 21, Maven 3.9, PostgreSQL 14+ (сервер и клиентские программы), zip.
# Резервная копия БД делается из чистой временной БД: сервер из собранного архива один раз
# запускается на ней (Flyway создаёт схему и демонстрационные данные) и останавливается.
# Параметры временной БД: BUILD_DB_HOST, BUILD_DB_PORT, BUILD_DB_USER (по умолчанию — локальный
# сервер и текущий пользователь), BUILD_DB_NAME (technosoft_bonus_build; удаляется в конце).

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
VERSION="${VERSION:-1.0}"
NAME="TechnoSoftBonus-$VERSION"
OUT="$HERE/dist"
DIST="$OUT/$NAME"
FILES="$HERE/files"
JAR="$ROOT/backend/target/technosoft-bonus.jar"

BUILD_DB_HOST="${BUILD_DB_HOST:-localhost}"
BUILD_DB_PORT="${BUILD_DB_PORT:-5432}"
BUILD_DB_USER="${BUILD_DB_USER:-$(id -un)}"
BUILD_DB_NAME="${BUILD_DB_NAME:-technosoft_bonus_build}"
PGCONN=(-h "$BUILD_DB_HOST" -p "$BUILD_DB_PORT" -U "$BUILD_DB_USER")

step() { printf '\n== %s ==\n' "$*"; }
die() { echo "Ошибка: $*" >&2; exit 1; }

for tool in node npm java mvn psql createdb dropdb pg_dump zip unzip shasum; do
    command -v "$tool" >/dev/null 2>&1 || die "не найдена программа $tool"
done

step "1/5 Клиент: сборка (React + Vite, VITE_API_MODE=http)"
cd "$ROOT/frontend"
[ -d node_modules ] || npm ci
VITE_API_MODE=http npm run build
[ -f dist/index.html ] || die "клиент не собран: нет frontend/dist/index.html"

step "2/5 Сервер: сборка исполняемого архива со встроенным клиентом (mvn -Pdist)"
cd "$ROOT/backend"
MVN_ARGS=(-B -Pdist clean package)
[ "${SKIP_TESTS:-0}" = "1" ] && MVN_ARGS+=(-DskipTests)
mvn "${MVN_ARGS[@]}"
[ -f "$JAR" ] || die "нет $JAR"
unzip -l "$JAR" 'BOOT-INF/classes/static/index.html' >/dev/null 2>&1 || die "клиент не встроен в $JAR"

step "3/5 Резервная копия БД: чистая БД $BUILD_DB_NAME → миграции и демо-данные → pg_dump"
dropdb "${PGCONN[@]}" --if-exists "$BUILD_DB_NAME"
createdb "${PGCONN[@]}" -E UTF8 -T template0 "$BUILD_DB_NAME"
LOG="$(mktemp -t technosoft-bonus-build.XXXXXX)"
# Порт 0 — любой свободный; синхронизация статусов кампаний и обработка событий отключены, чтобы копия
# содержала ровно результат миграций (сервер выполнит их сам при первом запуске у пользователя).
SPRING_DATASOURCE_URL="jdbc:postgresql://$BUILD_DB_HOST:$BUILD_DB_PORT/$BUILD_DB_NAME" \
SPRING_DATASOURCE_USERNAME="$BUILD_DB_USER" SPRING_DATASOURCE_PASSWORD="${PGPASSWORD:-}" \
    java -jar "$JAR" --server.port=0 --bonus.campaign-lifecycle.enabled=false \
         --bonus.event-scheduler.enabled=false >"$LOG" 2>&1 &
APP_PID=$!
for _ in $(seq 1 120); do
    grep -q 'Started BonusApplication' "$LOG" && break
    kill -0 "$APP_PID" 2>/dev/null || { cat "$LOG"; die "сервер завершился при выполнении миграций"; }
    sleep 1
done
grep -q 'Started BonusApplication' "$LOG" || { kill "$APP_PID"; die "сервер не запустился за 120 с"; }
kill "$APP_PID"; wait "$APP_PID" 2>/dev/null || true
rm -f "$LOG"
psql "${PGCONN[@]}" -d "$BUILD_DB_NAME" -Atc "select 'миграции: ' || string_agg(version || ' ' || description, ', ' order by installed_rank) from flyway_schema_history where success"

rm -rf "$DIST" "$OUT/$NAME.zip"
mkdir -p "$DIST/app" "$DIST/config" "$DIST/db"
pg_dump "${PGCONN[@]}" -d "$BUILD_DB_NAME" -Fc -Z 9 --no-owner --no-privileges -f "$DIST/db/technosoft_bonus.backup"
pg_dump "${PGCONN[@]}" -d "$BUILD_DB_NAME" -Fp --no-owner --no-privileges -f "$DIST/db/technosoft_bonus.sql"
dropdb "${PGCONN[@]}" "$BUILD_DB_NAME"

step "4/5 Состав дистрибутива"
cp "$JAR" "$DIST/app/technosoft-bonus.jar"
cp "$FILES/config/application.yml" "$DIST/config/application.yml"
cp "$FILES/start.sh" "$FILES/restore-db.sh" "$DIST/"
chmod 755 "$DIST/start.sh" "$DIST/restore-db.sh"
# Windows: .bat — с переводом строки CRLF (cmd.exe), README.txt — UTF-8 с BOM и CRLF (Блокнот Windows 8).
for f in start.bat restore-db.bat; do
    sed 's/\r$//; s/$/\r/' "$FILES/$f" > "$DIST/$f"
done
{ printf '\xEF\xBB\xBF'; sed 's/\r$//; s/$/\r/' "$FILES/README.txt"; } > "$DIST/README.txt"
(cd "$DIST" && find . -type f ! -name SHA256SUMS.txt | sort | sed 's|^\./||' | xargs shasum -a 256 > SHA256SUMS.txt)

step "5/5 Архив zip"
(cd "$OUT" && zip -qr -X "$NAME.zip" "$NAME")

echo
echo "Готово:"
du -sh "$DIST" "$OUT/$NAME.zip"
(cd "$DIST" && find . -type f | sort | xargs ls -l | awk '{printf "  %10d  %s\n", $5, $9}')
