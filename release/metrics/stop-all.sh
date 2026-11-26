#!/usr/bin/env bash
# Уборка после run-all.sh: остановить серверы (8080, 8081) и удалить БД проверки установки.
set -u
for port in 8080 8081 4173; do
    pid=$(lsof -nP -t -iTCP:$port -sTCP:LISTEN)
    [ -n "$pid" ] && kill $pid && echo "остановлен процесс $pid (порт $port)"
done
for _ in $(seq 1 30); do lsof -nP -iTCP:8080 -iTCP:8081 -sTCP:LISTEN >/dev/null || break; sleep 0.5; done
for db in technosoft_bonus technosoft_bonus_replay technosoft_bonus_replay_restored technosoft_bonus_build; do
    dropdb --if-exists "$db" && echo "БД $db удалена (если была)"
done
