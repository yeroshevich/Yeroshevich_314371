#!/usr/bin/env bash
# ЛР 11: полный прогон проверки установки и измерений метрик качества на установленном дистрибутиве.
#   WORK=/пустая/папка DB_USER=<пользователь PostgreSQL> release/metrics/run-all.sh
# Порядок: установка «с нуля» (install-check.sh) → 30 тест-кейсов (сервер перезапущен с обработкой очереди каждые 5 с)
# → второй экземпляр на чистой БД (порт 8081): 96 запросов ЛР 9, аварийная остановка и восстановление
# → матрица прав и соглашения REST → устойчивость к ошибкам → время отклика → 500 клиентов и память
# → массовые начисления → обход экранов → анализ исходного кода.
# В конце оба сервера остаются запущенными (остановка и удаление БД — stop-all.sh).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?}"; DB_USER="${DB_USER:-postgres}"
APP="$WORK/TechnoSoftBonus-1.0"
export PGDATABASE=technosoft_bonus

wait_started() { for _ in $(seq 1 300); do grep -q 'Started BonusApplication' "$1" && return 0; sleep 0.2; done; return 1; }
restart_main() {   # $1 — дополнительные переменные окружения
    kill "$(cat "$WORK/server.pid")"; while lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null; do sleep 0.3; done
    (cd "$APP" && . "$WORK/secrets.env" && exec env $1 nohup ./start.sh) > "$WORK/console-$2.txt" 2>&1 < /dev/null &
    echo $! > "$WORK/server.pid"
    wait_started "$WORK/console-$2.txt"
}

echo "== 1. Установка =="
WORK="$WORK" DB_USER="$DB_USER" "$HERE/install-check.sh"
cp "$WORK/install-log.md" "$WORK/console.txt" "$HERE/out/install/"
# Секреты установки сохраняются для перезапусков (вне дистрибутива и репозитория)
printf "export BONUS_JWT_SECRET='%s'\nexport BONUS_SECRET_KEY='%s'\nexport BONUS_DB_USER=%s\n" \
    "$(openssl rand -base64 48)" "$(openssl rand -base64 32)" "$DB_USER" > "$WORK/secrets.env"

echo "== 2. 30 тест-кейсов (очередь событий каждые 5 с: BONUS_EVENT_SCHEDULER_DELAY=PT5S) =="
restart_main "BONUS_EVENT_SCHEDULER_DELAY=PT5S" tc
"$HERE/run-test-cases.sh" | tail -2

echo "== 3. Второй экземпляр: порт 8081, БД technosoft_bonus_replay =="
(cd "$APP" && ./restore-db.sh localhost 5432 "$DB_USER" technosoft_bonus_replay | tail -1)
(cd "$APP" && . "$WORK/secrets.env" && BONUS_PORT=8081 BONUS_DB_NAME=technosoft_bonus_replay BONUS_DB_POOL_SIZE=20 BONUS_HTTP_THREADS=15 \
    BONUS_LOG_FILE=logs/replay.log exec nohup ./start.sh) > "$WORK/console-replay.txt" 2>&1 < /dev/null &
wait_started "$WORK/console-replay.txt"
BASE_URL=http://localhost:8081 node "$HERE/replay-api-examples.mjs" | grep -E '"(requests|failures5xx|statusMatches|X_completeness)"'
echo "-- Масштабирование: токен, выданный экземпляром 8080, на экземпляре 8081 (другая БД, тот же секрет) --"
TOKEN=$(curl -s -H 'Content-Type: application/json' -d '{"email":"anna.kovaleva@technosoft.by","password":"demo"}' http://localhost:8080/api/v1/auth/login | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -o /dev/null -w 'GET :8081/api/v1/accounts/me с токеном :8080 → HTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/accounts/me | tee "$HERE/out/scale-token.txt"
curl -s http://localhost:8081/v3/api-docs | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>console.log('servers в OpenAPI экземпляра 8081:', JSON.stringify(JSON.parse(s).servers)))" | tee -a "$HERE/out/scale-token.txt"
APP="$APP" PORT=8081 DB=technosoft_bonus_replay DB_USER="$DB_USER" SECRETS="$WORK/secrets.env" "$HERE/recovery-check.sh" | tail -4

# FROM=4 — продолжить с шага 4 (серверы уже запущены)
echo "== 4. Матрица прав и соглашения REST =="
node "$HERE/api-matrix.mjs" | grep -E '"(checks|unauthorizedAccess|falseDenials|X_security|restConforming|X_consistency)"'
echo "== 5. Устойчивость к ошибкам =="
node "$HERE/robustness.mjs" | grep -E '"(cases|correctlyRejected|serverErrors5xx|dataUnchanged|X_faultTolerance)"'
echo "== 6. Время отклика =="
node "$HERE/perf.mjs" | tail -14
echo "== 7. 500 одновременных клиентов, память =="
SERVER_PID=$(cat "$WORK/server.pid") node "$HERE/load500.mjs" | grep -E '"(successTotal|clientsTotal|worstBalanceP95Ms|worstTotalP95Ms|peakHeapUsedMb|peakRssMb|X_resources)"'
echo "== 8. Массовые начисления =="
CONCURRENCY=1 OUT_NAME=mass-accrual-c1.json node "$HERE/mass-accrual.mjs" | grep -E '"(operationsCreated|massWallSec|over2s|perf3Met)"'
CONCURRENCY=20 OUT_NAME=mass-accrual-c20.json node "$HERE/mass-accrual.mjs" | grep -E '"(operationsCreated|massWallSec|over2s|perf3Met)"'
echo "== 9. Обход экранов =="
node "$HERE/ui-audit.mjs" | grep -E '"(controls|withVisibleLabel|withAccessibleNameOnly|X_understandability|overflowPairs|overflowOk|X_adaptability)"'
echo "== 9a. NFR-3, NFR-4 по данным БД =="
node "$HERE/nfr-check.mjs" | grep -E '"(operationsAfterInstall|audited|eventsWithSeveralOperations|duplicateEventRejected)"'
echo "== 10. Исходный код =="
node "$HERE/static-analysis.mjs" > /dev/null && echo "out/static-analysis.json"
ps -o pid,rss,command -p "$(cat "$WORK/server.pid")" | tee "$HERE/out/rss-final.txt"
