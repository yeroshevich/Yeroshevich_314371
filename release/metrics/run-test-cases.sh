#!/usr/bin/env bash
# ЛР 11, правильность: повторный прогон 30 тест-кейсов ЛР 10 (frontend/scripts/test-cases.mjs, Chrome) против
# УСТАНОВЛЕННОГО дистрибутива: клиент раздаёт сам сервер (BASE_URL=http://localhost:8080), БД — установочная.
# Сценарий ЛР 10 не меняется: его копия получает другие пути (playwright-core из frontend/, отчёт — в out/test-cases/),
# чтобы не перезаписать документы ЛР 10.
# Сервер должен быть запущен с обработкой очереди событий каждые 5 с (нужно для TC-10):
#   BONUS_EVENT_SCHEDULER_DELAY=PT5S ./start.sh
#   PGDATABASE=technosoft_bonus release/metrics/run-test-cases.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="$HERE/out/test-cases"
mkdir -p "$OUT/screens"
COPY="$OUT/test-cases-lr11.mjs"

sed -e "s|^import { chromium } from 'playwright-core';|import { createRequire } from 'node:module';\nconst { chromium } = createRequire(process.env.FRONTEND_DIR + '/package.json')('playwright-core');|" \
    -e "s|^const frontend = resolve(here, '..');|const frontend = process.env.FRONTEND_DIR;|" \
    -e "s|^const docs = resolve(frontend, '..', 'docs', 'lr10');|const docs = process.env.OUT_DIR;|" \
    "$ROOT/frontend/scripts/test-cases.mjs" > "$COPY"

FRONTEND_DIR="$ROOT/frontend" OUT_DIR="$OUT" BASE_URL="${BASE_URL:-http://localhost:8080}" \
    PGDATABASE="${PGDATABASE:-technosoft_bonus}" node "$COPY"
