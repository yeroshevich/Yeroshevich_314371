#!/bin/sh
# TechnoSoft Bonus 1.0 — создание базы данных и восстановление её из резервной копии (macOS, Linux).
#
#   ./restore-db.sh [хост] [порт] [пользователь] [имя_БД]
#   по умолчанию:    localhost 5432  postgres       technosoft_bonus
#
# Пользователь PostgreSQL должен иметь право создавать базы данных (CREATEDB) — например, postgres.
# Пароль: переменная PGPASSWORD или файл ~/.pgpass; иначе скрипт спросит его один раз.
# Копия db/technosoft_bonus.backup восстанавливается программой pg_restore; если её нет —
# SQL-копия db/technosoft_bonus.sql выполняется через psql.
# Существующая БД с тем же именем не перезаписывается; пересоздать её: FORCE=1 ./restore-db.sh ...

set -eu
cd "$(dirname "$0")"

HOST=${1:-${BONUS_DB_HOST:-localhost}}
PORT=${2:-${BONUS_DB_PORT:-5432}}
DB_USER=${3:-${BONUS_DB_USER:-postgres}}
DB_NAME=${4:-${BONUS_DB_NAME:-technosoft_bonus}}
BACKUP=db/technosoft_bonus.backup
SQL=db/technosoft_bonus.sql

case "$DB_NAME" in
    ''|[0-9]*|*[!A-Za-z0-9_]*)
        echo "Ошибка: имя БД «$DB_NAME» — допустимы латинские буквы, цифры и «_», первая — не цифра." >&2
        exit 1 ;;
esac

for tool in psql createdb dropdb; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Ошибка: не найдена программа $tool (клиент PostgreSQL). Добавьте каталог bin PostgreSQL в PATH." >&2
        exit 1
    fi
done
if command -v pg_restore >/dev/null 2>&1 && [ -f "$BACKUP" ]; then
    MODE=backup
elif [ -f "$SQL" ]; then
    MODE=sql
else
    echo "Ошибка: нет файла резервной копии ($BACKUP или $SQL)." >&2
    exit 1
fi

if [ -z "${PGPASSWORD:-}" ] && [ ! -f "${PGPASSFILE:-$HOME/.pgpass}" ] && [ -t 0 ]; then
    printf 'Пароль пользователя %s (Enter — без пароля): ' "$DB_USER"
    stty -echo 2>/dev/null || true
    read -r PASS || PASS=
    stty echo 2>/dev/null || true
    echo
    if [ -n "$PASS" ]; then PGPASSWORD=$PASS; export PGPASSWORD; fi
fi

CONN="-h $HOST -p $PORT -U $DB_USER"
echo "Сервер PostgreSQL: $HOST:$PORT, пользователь $DB_USER, база данных $DB_NAME"

# shellcheck disable=SC2086 # CONN намеренно разбивается на параметры
if [ "${FORCE:-0}" = "1" ]; then
    echo "FORCE=1: удаление существующей БД $DB_NAME"
    dropdb $CONN --if-exists "$DB_NAME"
fi

echo "1/3 Создание БД $DB_NAME (UTF8)"
# shellcheck disable=SC2086
if ! createdb $CONN -E UTF8 -T template0 "$DB_NAME"; then
    echo "Ошибка: БД не создана. Если она уже существует, удалите её или запустите: FORCE=1 $0 $HOST $PORT $DB_USER $DB_NAME" >&2
    exit 1
fi

echo "2/3 Восстановление из $( [ "$MODE" = backup ] && echo "$BACKUP (pg_restore)" || echo "$SQL (psql)" )"
# shellcheck disable=SC2086
if [ "$MODE" = backup ]; then
    pg_restore $CONN -d "$DB_NAME" --no-owner --no-privileges --exit-on-error "$BACKUP" && OK=1 || OK=0
else
    psql $CONN -d "$DB_NAME" -q -v ON_ERROR_STOP=1 -f "$SQL" >/dev/null && OK=1 || OK=0
fi
if [ "$OK" != 1 ]; then
    echo "Ошибка восстановления. Частично созданная БД $DB_NAME удаляется." >&2
    # shellcheck disable=SC2086
    dropdb $CONN --if-exists "$DB_NAME" || true
    exit 1
fi

echo "3/3 Проверка"
# shellcheck disable=SC2086
psql $CONN -d "$DB_NAME" -At -F ' ' -c "
    select 'версия схемы', (select version from flyway_schema_history where success order by installed_rank desc limit 1)
    union all select 'сотрудников', count(*)::text from employee
    union all select 'учётных записей', count(*)::text from app_user
    union all select 'операций', count(*)::text from operation"
echo "Готово. БД $DB_NAME восстановлена; запуск сервера — start.sh."
