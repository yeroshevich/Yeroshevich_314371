#!/bin/sh
# TechnoSoft Bonus 1.0 — запуск сервера (macOS, Linux).
# Веб-интерфейс и REST API: http://localhost:8080 (порт — BONUS_PORT). Остановка — Ctrl+C.
#
# Параметры можно задать переменными окружения до запуска или исправить значения ниже.
# Подробнее о каждом параметре — config/application.yml.

cd "$(dirname "$0")" || exit 1

# ---- Параметры подключения к БД (по умолчанию) ----------------------------------------------
: "${BONUS_DB_HOST:=localhost}"
: "${BONUS_DB_PORT:=5432}"
: "${BONUS_DB_NAME:=technosoft_bonus}"
: "${BONUS_DB_USER:=postgres}"
: "${BONUS_DB_PASSWORD:=}"
: "${BONUS_PORT:=8080}"

# ---- Секреты (обязательно) -----------------------------------------------------------------
# Сгенерировать значения:  openssl rand -base64 48   (BONUS_JWT_SECRET)
#                          openssl rand -base64 32   (BONUS_SECRET_KEY)
# и раскомментировать строки ниже (или задать переменные окружения):
# BONUS_JWT_SECRET='...'
# BONUS_SECRET_KEY='...'

# ---- Память Java: предел кучи 512 МБ ------------------------------------------------------
: "${JAVA_OPTS:=-Xms128m -Xmx512m}"

export BONUS_DB_HOST BONUS_DB_PORT BONUS_DB_NAME BONUS_DB_USER BONUS_DB_PASSWORD BONUS_PORT
export BONUS_JWT_SECRET BONUS_SECRET_KEY

if [ -z "${BONUS_JWT_SECRET:-}" ] || [ -z "${BONUS_SECRET_KEY:-}" ]; then
    echo "Ошибка: не заданы секреты BONUS_JWT_SECRET и/или BONUS_SECRET_KEY." >&2
    echo "Сгенерируйте их (openssl rand -base64 48 и openssl rand -base64 32) и задайте" >&2
    echo "переменными окружения или в начале start.sh. См. README.txt, шаг 4." >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi
if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "Ошибка: не найдена Java. Установите Java 21 (JDK или JRE) и повторите запуск." >&2
    exit 1
fi
# Первая строка java -version: openjdk version "21.0.2" ... → 21; Java 8: "1.8.0_x" → 1.
JAVA_MAJOR=$("$JAVA" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)
if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -lt 21 ]; then
    echo "Ошибка: нужна Java 21 или новее, найдена версия $JAVA_MAJOR ($JAVA)." >&2
    exit 1
fi

mkdir -p logs
echo "TechnoSoft Bonus: http://localhost:$BONUS_PORT  (БД $BONUS_DB_HOST:$BONUS_DB_PORT/$BONUS_DB_NAME, пользователь $BONUS_DB_USER)"
# shellcheck disable=SC2086 # JAVA_OPTS намеренно разбивается на отдельные параметры
exec "$JAVA" $JAVA_OPTS -Dfile.encoding=UTF-8 -jar app/technosoft-bonus.jar \
    --spring.config.additional-location=file:./config/application.yml "$@"
