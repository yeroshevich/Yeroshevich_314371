#!/usr/bin/env node
/**
 * ЛР 11, метрики по исходному коду (без запуска сервера):
 *  - анализируемость: классы основного кода сервера с документирующим комментарием /** … *\/ перед объявлением;
 *  - соответствие сопровождаемости: компоненты Spring без внедрения через поля (@Autowired/@Inject/@Resource/@Value
 *    на поле) и с внедрением через конструктор;
 *  - изменяемость: существующие классы, изменённые при исправлении дефекта D-1 (git diff относительно коммита до D-1);
 *  - взаимозаменяемость: порты (интерфейсы внешних зависимостей) — реализация не упоминается по имени в других
 *    классах основного кода (подключается только через внедрение зависимостей); клиент — экраны не импортируют
 *    конкретную реализацию BonusApi (mock/http);
 *  - привлекательность: «сырые» цвета (#hex, rgb(), hsl()) в исходниках клиента вне системы дизайна (src/theme);
 *  - обучаемость (знаменатель): функции ролей — пункты меню по ролям (frontend/src/routes.tsx) и общие функции;
 *  - простота использования: тест-кейсы ЛР 11 (out/test-cases/test-cases.md), выполняемые не более чем за 5 шагов
 *    пользователя (шаги «[инструмент]» — не действия пользователя).
 *   node release/metrics/static-analysis.mjs
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { ROOT, round2, save, nowIso } from './lib.mjs';

const walk = (dir, ext) => readdirSync(dir).flatMap((f) => {
  const p = join(dir, f);
  return statSync(p).isDirectory() ? walk(p, ext) : ext.some((e) => p.endsWith(e)) ? [p] : [];
});
const MAIN = resolve(ROOT, 'backend', 'src', 'main', 'java');
const javaFiles = walk(MAIN, ['.java']);
const rel = (p) => relative(MAIN, p).replace(/^by\/technosoft\/bonus\//, '');

// ── Анализируемость ──────────────────────────────────────────────────────────────────────────────
const DECL = /^(public\s+|final\s+|abstract\s+|sealed\s+)*(class|interface|record|enum|@interface)\s+\w+/m;
const javadoc = javaFiles.map((f) => {
  const src = readFileSync(f, 'utf8');
  const m = DECL.exec(src);
  if (!m) return { file: rel(f), documented: false, note: 'объявление не найдено' };
  const before = src.slice(0, m.index).split('\n');
  while (before.length && (/^\s*@/.test(before.at(-1)) || /^\s*$/.test(before.at(-1)) || /^\s+[\w"=,(){}. ]*\)?\s*$/.test(before.at(-1)) && !/\*\//.test(before.at(-1)))) before.pop();
  const tail = before.join('\n');
  const documented = /\*\/\s*$/.test(tail) && tail.lastIndexOf('/**') > tail.lastIndexOf('*/', tail.length - 3) - 100000 && /\/\*\*[\s\S]*\*\/\s*$/.test(tail.slice(tail.lastIndexOf('/**')));
  return { file: rel(f), documented };
});
const undocumented = javadoc.filter((j) => !j.documented).map((j) => j.file);

// ── Внедрение зависимостей ─────────────────────────────────────────────────────────────────────
const COMPONENT = /@(Component|Service|Repository|RestController|Controller|Configuration|RestControllerAdvice|ControllerAdvice)\b/;
const FIELD_INJECTION = /@(Autowired|Inject|Resource|Value)(\([^)]*\))?\s+(private|protected|public)?\s*(static\s+)?[\w<>[\], ?.]+\s+\w+\s*(=[^;]*)?;/g;
const components = javaFiles.filter((f) => COMPONENT.test(readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, ''))).map((f) => {
  const src = readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  const cls = rel(f).split('/').pop().replace('.java', '');
  const fieldInjection = [...src.matchAll(FIELD_INJECTION)].map((m) => m[0].replace(/\s+/g, ' '));
  const ctor = new RegExp(`(public|protected)?\\s*${cls}\\s*\\(([^)]*)\\)\\s*\\{`).exec(src);
  const finalFields = [...src.matchAll(/^\s*private\s+final\s+[\w<>[\], ?.]+\s+\w+\s*;/gm)].length;
  return { file: rel(f), fieldInjection, constructorInjection: Boolean(ctor && ctor[2].trim()), finalFields, ok: fieldInjection.length === 0 };
});

// ── Изменяемость (D-1) ─────────────────────────────────────────────────────────────────────────
const git = (...a) => execFileSync('git', ['-C', ROOT, ...a], { encoding: 'utf8' }).trim();
const headClasses = git('ls-tree', '-r', '--name-only', 'HEAD', '--', 'backend/src/main/java').split('\n').filter((f) => f.endsWith('.java'));
const changedExisting = git('diff', '--name-only', 'HEAD', '--', 'backend/src/main/java').split('\n').filter((f) => f.endsWith('.java') && headClasses.includes(f));
const newSinceHead = javaFiles.map((f) => relative(ROOT, f)).filter((f) => !headClasses.includes(f));

// ── Взаимозаменяемость ─────────────────────────────────────────────────────────────────────────
const PORTS = [
  { port: 'TokenService', impl: 'JwtTokenService', what: 'выпуск и проверка токенов входа' },
  { port: 'SecretCipher', impl: 'AesGcmSecretCipher', what: 'шифрование ключей интеграций' },
  { port: 'IntegrationProbe', impl: 'SimulatedIntegrationProbe', what: 'проверка соединения с внешней системой' },
  { port: 'ClientAddressProvider', impl: 'RequestClientAddressProvider', what: 'адрес клиента для журнала аудита' },
  { port: 'AccrualStrategy', impl: 'KpiAccrualStrategy|FixedEventAccrualStrategy', what: 'алгоритм начисления по правилу' },
  { port: 'PasswordEncoder', impl: 'BCryptPasswordEncoder', what: 'хеширование паролей' },
  { port: 'Clock', impl: 'Clock\\.system', what: 'системные часы' },
];
const mainSrc = javaFiles.map((f) => ({ f: rel(f), src: readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^import .*$/gm, '') }));
const ports = PORTS.map((p) => {
  const re = new RegExp(`\\b(${p.impl})\\b`);
  const users = mainSrc.filter((x) => re.test(x.src) && !new RegExp(`/(${p.impl})\\.java$`).test(x.f)).map((x) => x.f);
  return { ...p, referencedIn: users };
});
// В конфигурационных классах (@Bean) упоминание реализации допустимо — это и есть точка подключения.
for (const p of ports) {
  p.outsideConfig = p.referencedIn.filter((f) => !/config\/|Config\.java$/.test(f));
  p.replaceable = p.outsideConfig.length === 0;
}
const FRONT = resolve(ROOT, 'frontend', 'src');
const screenFiles = [...walk(resolve(FRONT, 'pages'), ['.tsx']), ...walk(resolve(FRONT, 'components'), ['.tsx']), ...walk(resolve(FRONT, 'layout'), ['.tsx']), ...walk(resolve(FRONT, 'auth'), ['.tsx'])];
const directApi = screenFiles.filter((f) => /from ['"][./]*api\/(mock|http)/.test(readFileSync(f, 'utf8'))).map((f) => relative(FRONT, f));
ports.push({ port: 'BonusApi (клиент)', impl: 'mockApi | httpApi', what: 'слой данных клиента (VITE_API_MODE)', referencedIn: directApi, outsideConfig: directApi, replaceable: directApi.length === 0 });

// ── Цвета клиента ──────────────────────────────────────────────────────────────────────────────
const COLOR = /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/g;
const TOKEN = /\bcolors\.\w+|\btoken\.color\w*|\bvar\(--[\w-]+\)|\bON_DARK_\w+|\btype=["'](secondary|success|warning|danger)["']|\bcolor=["'](success|processing|error|warning|default)["']/g;
const allFront = walk(FRONT, ['.ts', '.tsx', '.css']);
const colorFiles = allFront.map((f) => {
  const src = readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
  return { file: relative(FRONT, f), raw: (src.match(COLOR) ?? []).length, tokens: (src.match(TOKEN) ?? []).length,
    rawSamples: [...new Set(src.match(/#[0-9a-fA-F]{3,8}\b|\brgba?\([^)]*\)|\bhsla?\([^)]*\)/g) ?? [])].slice(0, 5) };
});
const isTheme = (f) => f.file.startsWith('theme/');
const outsideTheme = colorFiles.filter((f) => !isTheme(f));
const rawOutside = outsideTheme.reduce((s, f) => s + f.raw, 0);
const tokenUses = outsideTheme.reduce((s, f) => s + f.tokens, 0);
const SCREEN_FILES = walk(resolve(FRONT, 'pages'), ['.tsx']).map((f) => relative(FRONT, f)).filter((f) => !/DesignSystemPage/.test(f));
// Экран считается без нарушений, если файл экрана не содержит сырых цветов; общие компоненты (layout, components) с сырыми цветами перечислены отдельно (sharedRaw).
const sharedRaw = colorFiles.filter((f) => /^(layout|components)\//.test(f.file) && f.raw > 0).map((f) => f.file);
const screens = SCREEN_FILES.map((f) => {
  const own = colorFiles.find((c) => c.file === f);
  return { screen: f, rawOwn: own.raw, samples: own.rawSamples, ok: own.raw === 0 };
});

// ── Функции ролей (для руководства пользователя) ───────────────────────────────────────────────
const routes = readFileSync(resolve(FRONT, 'routes.tsx'), 'utf8');
const roleNames = { employee: 'Сотрудник', manager: 'Руководитель', hr: 'HR', finance: 'Финансовый отдел', admin: 'Администратор' };
const functions = [];
for (const m of routes.matchAll(/path: '([^']+)', title: '([^']+)'[\s\S]*?menu: \{([^}]*)\}/g)) {
  for (const mm of m[3].matchAll(/(\w+): '([^']+)'/g)) functions.push({ role: roleNames[mm[1]], menu: mm[2], screen: m[2], path: m[1] });
}
const common = ['Вход в систему (email и пароль или демо-вход)', 'Просмотр и отметка уведомлений', 'Выход из системы'];
for (const r of Object.values(roleNames)) for (const c of common) functions.push({ role: r, menu: c, screen: '—', path: '—' });

// ── Простота использования ─────────────────────────────────────────────────────────────────────
const tcFile = resolve(ROOT, 'release', 'metrics', 'out', 'test-cases', 'test-cases.md');
let ease = null;
if (existsSync(tcFile)) {
  const rows = readFileSync(tcFile, 'utf8').split('\n').filter((l) => /^\| TC-\d+ \| .* \| .*<br>|^\| TC-\d+ \| [^|]+ \| (Предусловие|1\.)/.test(l));
  const cases = rows.map((l) => {
    const cells = l.split(/(?<!\\)\|/).map((c) => c.trim());
    const steps = cells[3].split('<br>').filter((s) => /^\d+\. /.test(s));
    const userSteps = steps.filter((s) => !s.includes('[инструмент]'));
    return { id: cells[1], title: cells[2], steps: steps.length, userSteps: userSteps.length, ok: userSteps.length <= 5 };
  }).filter((c) => c.userSteps > 0);
  ease = { threshold: 5, operations: cases.length, within: cases.filter((c) => c.ok).length, X: round2(cases.filter((c) => c.ok).length / cases.length), cases };
}

const summary = {
  date: nowIso(),
  analysability: { classes: javadoc.length, documented: javadoc.length - undocumented.length, X: round2((javadoc.length - undocumented.length) / javadoc.length), undocumented },
  injection: { components: components.length, withoutFieldInjection: components.filter((c) => c.ok).length,
    constructorInjection: components.filter((c) => c.constructorInjection).length,
    X: round2(components.filter((c) => c.ok).length / components.length), violations: components.filter((c) => !c.ok) },
  changeability: { headClasses: headClasses.length, changedExistingForD1: changedExisting, X: round2(1 - changedExisting.length / headClasses.length), newSinceHead },
  replaceability: { ports: ports.length, replaceable: ports.filter((p) => p.replaceable).length, X: round2(ports.filter((p) => p.replaceable).length / ports.length),
    details: ports.map(({ port, impl, what, outsideConfig, replaceable }) => ({ port, impl, what, outsideConfig, replaceable })) },
  colors: { rawOutsideTheme: rawOutside, tokenUsesOutsideTheme: tokenUses, X_usage: round2(1 - rawOutside / (rawOutside + tokenUses)),
    filesWithRaw: outsideTheme.filter((f) => f.raw > 0).map((f) => ({ file: f.file, raw: f.raw, samples: f.rawSamples })),
    screens: screens.length, screensOk: screens.filter((s) => s.ok).length, X_screens: round2(screens.filter((s) => s.ok).length / screens.length), sharedRaw },
  functions: { total: functions.length, byRole: Object.fromEntries(Object.values(roleNames).map((r) => [r, functions.filter((f) => f.role === r).length])) },
  ease,
};
save('static-analysis.json', { summary, javadoc, components, colorFiles, functions, screens });
console.log(JSON.stringify(summary, (k, v) => (k === 'cases' || k === 'newSinceHead' ? undefined : v), 2));
