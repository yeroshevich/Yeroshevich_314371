#!/usr/bin/env bash
# ЛР 11, стабильность: выборочная мутационная проверка. В КОПИИ серверной части (исходный код проекта не меняется)
# по очереди вносятся изменения кода, имитирующие ошибки; после каждого выполняется полный набор тестов (mvn test).
# Изменение «обнаружено», если хотя бы один тест упал. Результат — out/mutation.json и out/mutation-log.txt.
#   WORK=/пустая/папка release/metrics/mutation.sh
# Интеграционные тесты используют рабочую БД (tpsis_bonus_app) в транзакции с откатом.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:?папка для копии}"
OUT="$HERE/out"; mkdir -p "$OUT"
LOG="$OUT/mutation-log.txt"; [ -z "${ONLY:-}" ] && : > "$LOG"
rm -rf "$WORK/backend"; mkdir -p "$WORK"
rsync -a --exclude target "$ROOT/backend/" "$WORK/backend/"
J="$WORK/backend/src/main/java/by/technosoft/bonus"

# id | файл | perl-подстановка | описание (символ «|» внутри подстановки недопустим — это разделитель)
# ONLY=M14 — выполнить только перечисленные изменения (журнал дополняется, итог пересчитывается по журналу).
MUTANTS=(
"M01|service/RedemptionService.java|s/account\.getBalance\(\) < cost/account.getBalance() <= cost/|Списание: «баланс < стоимости» заменено на «≤» (равный стоимости баланс не хватает)"
"M02|service/CorrectionService.java|s/boolean needsApproval = account\.getBalance\(\) \+ delta < 0;/boolean needsApproval = false;/|Корректировка: отключено согласование при уходе баланса в минус"
"M03|service/strategy/KpiAccrualStrategy.java|s/kpiValue\.compareTo\(rule\.getThreshold\(\)\) < 0/kpiValue.compareTo(rule.getThreshold()) <= 0/|Начисление по KPI: порог «<» заменён на «≤»"
"M04|service/campaign/CampaignLifecycleService.java|s/status == CampaignStatus\.PLANNED && !today\.isBefore\(startDate\)/false/|Кампании (D-1): отключён переход «Запланирована → Активна»"
"M05|service/campaign/CampaignLifecycleService.java|s/today\.isAfter\(endDate\)/!today.isBefore(endDate)/|Кампании (D-1): завершение в сам день окончания"
"M06|service/query/CampaignQueryService.java|s/^(\s*)campaignLifecycleService\.synchronizeToday\(\);/\$1\/\/ mutant/ if \$. < 55|Кампании (D-1): список кампаний без синхронизации статусов"
"M07|service/AccrualService.java|s/calc\.total\(\) > limit/calc.total() >= limit/|Ручное начисление: сумма, равная лимиту, уходит на согласование"
"M08|service/AccessPolicy.java|s/employee\.isManagedBy\(actor\.getEmployee\(\)\.getId\(\)\)/true/|Права: руководитель управляет любым сотрудником"
"M09|service/BonusCalculationService.java|s/base\.value\(\)\.multiply\(campaign\.getCoefficient\(\)\)/base.value()/|Расчёт: коэффициент кампании не применяется"
"M10|service/ReviewService.java|s/^(\s*)account\.applyDelta\(op\.getAmount\(\), now\);/\$1\/\/ mutant/|Контроль финотдела: подтверждение не меняет баланс"
"M11|service/listener/AuditListener.java|s/(public void onOperationRegistered\(OperationRegisteredEvent event\) \{)/\$1 if (event != null) return;/|Аудит: операции не записываются в журнал"
"M12|web/controller/Roles.java|s/ACCOUNT_HOLDER = \"hasAnyRole\('employee', 'manager', 'hr'\)\"/ACCOUNT_HOLDER = \"hasAnyRole('employee', 'manager', 'hr', 'finance')\"/|Ролевая модель: финотделу открыт «Мой баланс»"
"M13|service/admin/UserAdminService.java|s/if \(employeeRepository\.existsByEmailIgnoreCase\(email\)\)/if (false)/|Пользователи: не проверяется дубль email"
"M14|service/integration/SimulatedIntegrationProbe.java|s/!\"https\"\.equals\(uri\.getScheme/\"never\".equals(uri.getScheme/|Интеграции: разрешён адрес HTTP"
)

cd "$WORK/backend"
echo "Исходная копия: mvn test" | tee -a "$LOG"
mvn -o -q test > "$WORK/baseline.log" 2>&1 || { echo "исходная копия не проходит тесты" | tee -a "$LOG"; exit 1; }
RESULTS=()
for m in "${MUTANTS[@]}"; do
    IFS='|' read -r id file expr desc <<< "$m"
    if [ -n "${ONLY:-}" ] && [[ ",$ONLY," != *",$id,"* ]]; then continue; fi
    f="$J/$file"
    cp "$f" "$f.orig"
    perl -pi -e "$expr" "$f"
    if cmp -s "$f" "$f.orig"; then
        echo "$id: подстановка не применилась — пропуск" | tee -a "$LOG"
        RESULTS+=("{\"id\":\"$id\",\"file\":\"$file\",\"desc\":\"$desc\",\"applied\":false}")
        mv "$f.orig" "$f"; continue
    fi
    diff "$f.orig" "$f" | sed 's/^/    /' >> "$LOG"
    t0=$(date +%s)
    mvn -o -q test > "$WORK/$id.log" 2>&1; code=$?
    t1=$(date +%s)
    failed=$(grep -hE "^\[ERROR\]   [A-Za-z]+.*:[0-9]+" "$WORK/$id.log" | sed -E 's/^\[ERROR\]   //; s/:[0-9]+.*//' | sort -u | head -8 | paste -sd ';' -)
    [ -z "$failed" ] && failed=$(grep -hE "COMPILATION ERROR" "$WORK/$id.log" | head -1)
    killed=$([ $code -ne 0 ] && echo true || echo false)
    echo "$id $([ "$killed" = true ] && echo ОБНАРУЖЕНО || echo НЕ ОБНАРУЖЕНО) ($((t1 - t0)) с): $desc — $failed" | tee -a "$LOG"
    RESULTS+=("{\"id\":\"$id\",\"file\":\"$file\",\"desc\":\"$desc\",\"applied\":true,\"killed\":$killed,\"failedTests\":\"${failed//\"/\'}\"}")
    mv "$f.orig" "$f"
done
# Итог — по журналу (строки «Mxx ОБНАРУЖЕНО / НЕ ОБНАРУЖЕНО (N с): описание — упавшие тесты»; последняя запись по id)
node -e "
const lines=require('fs').readFileSync('$LOG','utf8').split('\\n');
const r={};
for (const l of lines) { const m=/^(M\\d+) (ОБНАРУЖЕНО|НЕ ОБНАРУЖЕНО) \\((\\d+) с\\): (.*?) — (.*)$/.exec(l); if (m) r[m[1]]={id:m[1],killed:m[2]==='ОБНАРУЖЕНО',sec:+m[3],desc:m[4],failedTests:m[5].split(';').filter(Boolean)}; }
const a=Object.values(r).sort((x,y)=>x.id.localeCompare(y.id)); const k=a.filter(x=>x.killed);
require('fs').writeFileSync('$OUT/mutation.json', JSON.stringify({date:new Date().toISOString(),mutants:a.length,killed:k.length,X:+(k.length/a.length).toFixed(2),results:a},null,2));
console.log('обнаружено', k.length, 'из', a.length);"
