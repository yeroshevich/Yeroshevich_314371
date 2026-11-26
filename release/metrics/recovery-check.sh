#!/usr/bin/env bash
# ЛР 11, восстанавливаемость (NFR-2):
#  1) во время непрерывной записи (recovery-writer.mjs) процесс сервера аварийно завершается kill -9;
#  2) сервер запускается снова (start.sh) — измеряется время до готовности; проверяется, что все операции,
#     подтверждённые сервером до сбоя, есть в БД, и что баланс каждого счёта равен сумме его проведённых операций;
#  3) резервное копирование (pg_dump -Fc) и восстановление в новую БД скриптом дистрибутива restore-db.sh —
#     сравнивается число строк во всех таблицах, измеряется время восстановления.
# Работает с отдельным экземпляром дистрибутива: APP (папка), PORT, DB, DB_USER, SECRETS (файл export ...).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
APP="${APP:?папка установленного дистрибутива}"
PORT="${PORT:-8081}"; DB="${DB:-technosoft_bonus_replay}"; DB_USER="${DB_USER:-postgres}"
RESTORED="${DB}_restored"
OUT="$HERE/out"; mkdir -p "$OUT"
LOG="$OUT/recovery-log.md"; : > "$LOG"
now() { perl -MTime::HiRes=time -e 'printf "%.3f", time'; }
q() { psql -U "$DB_USER" -d "$1" -Atc "$2"; }
PIDFILE="$APP/../recovery-server.pid"

start_server() {
    # Вывод группы целиком — в файл: иначе фоновая оболочка удерживает stdout вызывающего конвейера
    (cd "$APP" && . "$SECRETS" && BONUS_PORT=$PORT BONUS_DB_NAME=$DB BONUS_DB_POOL_SIZE=20 BONUS_HTTP_THREADS=15 BONUS_LOG_FILE=logs/recovery.log \
        exec nohup ./start.sh) > "$1" 2>&1 < /dev/null &
    echo $! > "$PIDFILE"
    for _ in $(seq 1 600); do
        grep -q 'Started BonusApplication' "$1" && return 0
        sleep 0.1
    done
    return 1
}

# Текущий процесс сервера на порту
SERVER_PID=$(lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN)
[ -n "$SERVER_PID" ] || { echo "сервер на порту $PORT не запущен"; exit 1; }
OPS_BEFORE=$(q "$DB" "select count(*) from operation")

# 1. Запись + kill -9
BASE_URL="http://localhost:$PORT" node "$HERE/recovery-writer.mjs" > "$OUT/recovery-writer.txt" 2>&1 &
WRITER=$!
for _ in $(seq 1 100); do grep -q first-ack "$OUT/recovery-writer.txt" && break; sleep 0.1; done
sleep 3
T_KILL=$(now)
kill -9 "$SERVER_PID"
wait "$WRITER"
ACKED=$(node -e "console.log(require('$OUT/recovery-acked.json').acked)")
echo "kill -9 $SERVER_PID; подтверждено сервером до сбоя: $ACKED операций" | tee -a "$LOG"

# 2. Перезапуск
T0=$(now)
start_server "$OUT/recovery-console.txt" || { echo "сервер не перезапустился"; exit 1; }
T1=$(now)
RESTART=$(perl -e "printf '%.2f', $T1-$T0")
IDS=$(node -e "console.log(require('$OUT/recovery-acked.json').operations.map(o=>o.id).join(','))")
FOUND=$(q "$DB" "select count(*) from operation where id in ($IDS) and status = 'accrued'")
OPS_AFTER=$(q "$DB" "select count(*) from operation")
MISMATCH=$(q "$DB" "select count(*) from bonus_account a left join (select account_id, sum(amount) total from operation where status in ('accrued','written_off','corrected') group by 1) s on s.account_id = a.id where a.balance <> coalesce(s.total, 0)")
{
    echo "Перезапуск до «Started BonusApplication»: $RESTART с"
    echo "Операций в БД: до нагрузки $OPS_BEFORE, после перезапуска $OPS_AFTER"
    echo "Подтверждённые до сбоя операции найдены после перезапуска (status=accrued): $FOUND из $ACKED"
    echo "Счета, у которых баланс не равен сумме проведённых операций (проверочный запрос): $MISMATCH"
} | tee -a "$LOG"

# 3. Резервная копия и восстановление в новую БД скриптом дистрибутива
TABLES=$(q "$DB" "select string_agg(table_name, ' ' order by table_name) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'")
count_rows() { for t in $TABLES; do printf '%s=%s\n' "$t" "$(q "$1" "select count(*) from $t")"; done; }
count_rows "$DB" > "$OUT/recovery-rows-source.txt"
TB0=$(now)
pg_dump -U "$DB_USER" -d "$DB" -Fc --no-owner --no-privileges -f "$OUT/recovery.backup"
TB1=$(now)
WORKDIR=$(mktemp -d)
mkdir -p "$WORKDIR/db"; cp "$APP/restore-db.sh" "$WORKDIR/"; cp "$OUT/recovery.backup" "$WORKDIR/db/technosoft_bonus.backup"
TR0=$(now)
(cd "$WORKDIR" && FORCE=1 ./restore-db.sh localhost 5432 "$DB_USER" "$RESTORED") >> "$LOG" 2>&1
TR1=$(now)
rm -rf "$WORKDIR"
count_rows "$RESTORED" > "$OUT/recovery-rows-restored.txt"
SRC_ROWS=$(awk -F= '{s+=$2} END {print s}' "$OUT/recovery-rows-source.txt")
RES_ROWS=$(awk -F= '{s+=$2} END {print s}' "$OUT/recovery-rows-restored.txt")
SAME_TABLES=$(diff "$OUT/recovery-rows-source.txt" "$OUT/recovery-rows-restored.txt" >/dev/null && echo "все таблицы совпадают" || echo "ЕСТЬ РАСХОЖДЕНИЯ")
{
    echo "Резервная копия pg_dump -Fc: $(perl -e "printf '%.2f', $TB1-$TB0") с, $(stat -f %z "$OUT/recovery.backup") байт"
    echo "Восстановление restore-db.sh в $RESTORED: $(perl -e "printf '%.2f', $TR1-$TR0") с"
    echo "Строк во всех таблицах: исходная БД $SRC_ROWS, восстановленная $RES_ROWS ($SAME_TABLES)"
} | tee -a "$LOG"
rm -f "$OUT/recovery.backup"
dropdb -U "$DB_USER" "$RESTORED"
node -e "
const fs=require('fs');
fs.writeFileSync('$OUT/recovery.json', JSON.stringify({
  date: new Date().toISOString(), killedPid: $SERVER_PID, acked: $ACKED, foundAfterRestart: $FOUND,
  balanceMismatches: $MISMATCH, restartSec: $RESTART, opsBefore: $OPS_BEFORE, opsAfter: $OPS_AFTER,
  backupRestore: { sourceRows: $SRC_ROWS, restoredRows: $RES_ROWS, restoreSec: $(perl -e "printf '%.2f', $TR1-$TR0") },
  X_crash: +($FOUND/$ACKED).toFixed(4), X_backup: +($RES_ROWS/$SRC_ROWS).toFixed(4) }, null, 2));"
cat "$OUT/recovery.json"
