#!/usr/bin/env bash
# Рендерит все lo-fi макеты (*.html) в PNG через установленный Google Chrome (headless).
# Десктоп — 1440×900, мобильные (*mobile*) — 375×812. Результат: ./png/<имя>.png
# Использование: ./render.sh            — сгенерировать HTML (build.py) и отрендерить все
#                SCALE=2 ./render.sh    — PNG в двойной плотности (для печати)
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
SCALE="${SCALE:-1}"
OUT="$DIR/png"
mkdir -p "$OUT"

if command -v python3 >/dev/null 2>&1; then
  python3 "$DIR/build.py"
fi

PROFILE="$(mktemp -d)"
trap 'rm -rf "$PROFILE"' EXIT

for html in "$DIR"/[0-9]*.html; do
  name="$(basename "$html" .html)"
  size="1440,900"
  [[ "$name" == *mobile* ]] && size="375,812"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --no-first-run \
    --no-default-browser-check --disable-extensions --user-data-dir="$PROFILE" \
    --force-device-scale-factor="$SCALE" --window-size="$size" \
    --screenshot="$OUT/$name.png" "file://$html" >/dev/null 2>&1
  echo "png/$name.png  ($size, x$SCALE)"
done
