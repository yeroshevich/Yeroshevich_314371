#!/usr/bin/env python3
"""Генерирует самодостаточные HTML-макеты (lo-fi) экранов TechnoSoft Bonus.

Каждый экран -> отдельный NN-name.html рядом со скриптом, общий стиль wf.css.
Запуск: python3 build.py  (затем ./render.sh для PNG).
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
NB = " "  # неразрывный пробел для сумм

# ---------------------------------------------------------------- роли и меню
MENUS = {
    "Сотрудник": ["Мой баланс", "История операций", "Программы мотивации"],
    "Руководитель": ["Мой баланс", "История операций", "Программы мотивации",
                     "Моя команда", "Начисление", "Аналитика"],
    "HR": ["Главная", "Сотрудники", "Начисление", "Операции", "Правила начисления",
           "Кампании", "Отчёты", "Аналитика"],
    "Финансовый отдел": ["Контроль начислений", "Операции", "Отчёты"],
    "Администратор": ["Пользователи", "Интеграции", "Журнал аудита"],
}
PEOPLE = {
    "Сотрудник": "Ковалёва А. С.",
    "Руководитель": "Орлов Д. В.",
    "HR": "Миронова Е. П.",
    "Финансовый отдел": "Лебедев К. И.",
    "Администратор": "Сидоров П. Н.",
}


# ---------------------------------------------------------------- примитивы
def b(n):
    """Сумма с неразрывными пробелами: 12450 -> '12 450 Б'."""
    s = f"{abs(n):,}".replace(",", NB)
    sign = "+" if n > 0 else ("−" if n < 0 else "")
    return f"{sign}{s}{NB}Б"


def bb(n):
    return f"{abs(n):,}".replace(",", NB) + f"{NB}Б"


def L(w, block=False):
    return f'<i class="lnb" style="width:{w}px"></i>' if block else f'<i class="ln" style="width:{w}px"></i>'


def btn(t, kind="", style=""):
    cls = "btn" + "".join(f" {k}" for k in kind.split())
    st = f' style="{style}"' if style else ""
    return f'<span class="{cls}"{st}>{t}</span>'


def inp(value, kind="", placeholder=False, style=""):
    cls = "inp" + (" sel" if "sel" in kind else "") + (" ta" if "ta" in kind else "") \
        + (" err" if "err" in kind else "") + (" lg" if "lg" in kind else "") + (" ph-t" if placeholder else "")
    pre = '<i class="ico srch"></i>' if "srch" in kind else ('<i class="ico cal"></i>' if "cal" in kind else "")
    st = f' style="{style}"' if style else ""
    return f'<div class="{cls}"{st}>{pre}<span class="v">{value}</span></div>'


def field(label, control, hint="", style=""):
    h = f'<span class="hint">{hint}</span>' if hint else ""
    st = f' style="{style}"' if style else ""
    return f'<div class="field"{st}><label>{label}</label>{control}{h}</div>'


def seg(items, on=0):
    return '<span class="seg">' + "".join(
        f'<span class="{"on" if i == on else ""}">{t}</span>' for i, t in enumerate(items)) + "</span>"


def tag(t, kind=""):
    return f'<span class="tag {kind}">{t}</span>'


STATUS = {
    "Начислено": "", "Списано": "wh", "Скорректировано": "wh", "На согласовании": "ds",
    "Заблокировано": "dk", "Отменено": "wh", "Активно": "", "Черновик": "ds", "Активен": "",
    "Заблокирован": "dk",
}


def st(t):
    return tag(t, STATUS.get(t, ""))


def table(head, rows, right=(), extra=""):
    th = "".join(f'<th class="{"r" if i in right else ""}">{h}</th>' for i, h in enumerate(head))
    trs = []
    for r in rows:
        cls = ""
        if isinstance(r, tuple) and len(r) == 2 and isinstance(r[1], str) and isinstance(r[0], list):
            r, cls = r
        tds = "".join(f'<td class="{"r" if i in right else ""}">{c}</td>' for i, c in enumerate(r))
        trs.append(f'<tr class="{cls}">{tds}</tr>')
    return f'<table class="t" {extra}><thead><tr>{th}</tr></thead><tbody>{"".join(trs)}</tbody></table>'


def pager(text="Всего 48 операций", pages=("1", "2", "3", "4", "5")):
    ps = "".join(f'<span class="{"on" if i == 0 else ""}">{p}</span>' for i, p in enumerate(pages))
    return (f'<div class="pager"><span class="nb mut">{text}</span><span class="nb sp"></span>'
            f'<span>‹</span>{ps}<span>›</span><span class="nb"></span>'
            f'<div class="inp sel" style="height:32px"><span class="v">10 / стр.</span></div></div>')


def line_chart(points, months, ylabels, height):
    pts = " ".join(f"{i * 100 / (len(points) - 1):.1f},{100 - p:.1f}" for i, p in enumerate(points))
    dots = "".join(
        f'<circle cx="{i * 100 / (len(points) - 1):.1f}" cy="{100 - p:.1f}" r="0.9" fill="#fff" stroke="#555" '
        f'stroke-width="1.5" vector-effect="non-scaling-stroke"/>' for i, p in enumerate(points))
    area = f"0,100 {pts} 100,100"
    ys = "".join(f"<span>{y}</span>" for y in ylabels)
    xs = "".join(f"<span>{m}</span>" for m in months)
    return (f'<div class="chart" style="height:{height}px"><div class="yax">{ys}</div>'
            f'<div class="plot"><svg viewBox="-2 -4 104 108" preserveAspectRatio="none">'
            f'<polygon points="{area}" fill="#e9e9e9"/>'
            f'<polyline points="{pts}" fill="none" stroke="#555" stroke-width="2" vector-effect="non-scaling-stroke"/>'
            f'{dots}</svg></div><div class="xax">{xs}</div></div>')


def bar_chart(groups, labels, ylabels, height):
    gs = "".join('<div class="grp">' + "".join(
        f'<b class="{"lt" if j == 1 else ""}" style="height:{v}%"></b>' for j, v in enumerate(g)) + "</div>"
        for g in groups)
    ys = "".join(f"<span>{y}</span>" for y in ylabels)
    xs = "".join(f"<span>{m}</span>" for m in labels)
    return (f'<div class="chart" style="height:{height}px"><div class="yax">{ys}</div>'
            f'<div class="plot"><div class="bars">{gs}</div></div><div class="xax">{xs}</div></div>')


# ---------------------------------------------------------------- каркас
def page(fname, title, body, mobile=False):
    html = f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width={'375' if mobile else '1440'}">
<title>{title} — макет TechnoSoft Bonus</title>
<link rel="stylesheet" href="wf.css">
</head>
<body{' class="m"' if mobile else ''}>
{body}
</body>
</html>
"""
    with open(os.path.join(HERE, fname), "w", encoding="utf-8") as f:
        f.write(html)


def shell(role, active, crumb, content, overlay=""):
    items = "".join(
        f'<div class="mi{" on" if m == active else ""}"><i class="ico"></i>{m}</div>' for m in MENUS[role])
    return f"""<div class="app">
  <aside class="sider">
    <div class="logo"><i class="x" style="width:28px;height:28px"></i>TechnoSoft Bonus</div>
    <nav class="menu">{items}</nav>
    <div class="sfoot"><span class="lbl">Роль: {role}</span><span class="fx"><i class="ico"></i>Выйти</span></div>
  </aside>
  <header class="hdr">
    <span class="crumb">{crumb}</span>
    <span class="sp"></span>
    <span class="bell" title="Уведомления"><i></i><b>3</b></span>
    <span class="prof"><i class="ava"></i><span>{PEOPLE[role]}<small>{role}</small></span><span class="mut">▾</span></span>
  </header>
  <main class="main">
{content}
  </main>
</div>
{overlay}"""


def crumb(*parts):
    return " / ".join(parts[:-1]) + (" / " if len(parts) > 1 else "") + f"<b>{parts[-1]}</b>"


# =============================================================== 01 Вход
def s01():
    roles = "".join(btn(r, "w") for r in MENUS)
    body = f"""<div style="display:grid;grid-template-columns:720px 720px;height:900px">
  <section style="background:#e9e9e9;border-right:1.5px solid #555;padding:64px 72px;display:flex;flex-direction:column;gap:24px">
    <div class="fx"><i class="x" style="width:44px;height:44px"></i><span style="font-size:22px;font-weight:700">TechnoSoft Bonus</span></div>
    <i class="x" style="width:576px;height:400px;border-radius:8px;display:block"></i>
    <h2 style="font-size:24px;line-height:32px;max-width:520px">Бонусы за результат — в одном месте</h2>
    <div style="width:520px">{L(520, True)}{L(470, True)}{L(300, True)}</div>
    <span class="lbl" style="margin-top:auto">МКПАО «TechnoSoft», 2026</span>
  </section>
  <section style="display:flex;align-items:center;justify-content:center">
    <div style="width:400px" class="fxc">
      <h1>Вход в систему</h1>
      <span class="mut" style="margin-bottom:8px">Используйте корпоративную почту и пароль</span>
      {field("Email", inp("a.kovaleva@technosoft.by", "lg"))}
      <div class="field"><label>Пароль</label><div class="inp lg"><span class="v">••••••••••</span><span class="lnk mut">Показать</span></div></div>
      <div class="fx" style="justify-content:space-between"><span class="fx" style="gap:8px"><i class="chk on"></i>Запомнить меня</span><span class="lnk">Забыли пароль?</span></div>
      {btn("Войти", "p lg w")}
      <div class="fx mt8" style="gap:10px"><i class="st-ln"></i><span class="mut">Быстрый вход для демонстрации</span><i class="st-ln"></i></div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">{roles}</div>
    </div>
  </section>
</div>"""
    page("01-login.html", "Вход", body)


# =============================================================== 02 Мой баланс
def ops_list(n=6):
    data = [(500, "24.11.2026"), (-3000, "20.11.2026"), (1200, "15.11.2026"), (300, "10.11.2026"),
            (-1500, "02.11.2026"), (800, "28.10.2026")][:n]
    out = ""
    for amt, d in data:
        out += (f'<div class="fx" style="height:62px;border-bottom:1px solid #bbb">'
                f'<i class="ico" style="width:28px;height:28px;border-radius:50%"></i>'
                f'<div style="flex:1">{L(150, True)}<span class="lbl">{d}</span></div>'
                f'<b style="font-variant-numeric:tabular-nums">{b(amt)}</b></div>')
    return out


def s02():
    kpis = [("Начислено за ноябрь", b(2000), "+18 % к октябрю"), ("Потрачено за ноябрь", b(-3000), "1 программа"),
            ("Ожидает согласования", bb(300), "1 начисление")]
    kc = "".join(f'<div class="box" style="display:flex;flex-direction:column;gap:6px">'
                 f'<span class="fx"><i class="ico"></i><span class="lbl" style="font-size:13px">{t}</span></span>'
                 f'<span class="kpi mt8">{v}</span><span class="lbl">{s}</span></div>' for t, v, s in kpis)
    content = f"""<div class="ph"><h1>Мой баланс</h1></div>
<div class="row" style="height:180px">
  <div class="box f c5" style="display:flex;flex-direction:column;gap:6px">
    <span class="fx"><i class="ico" style="border-radius:50%"></i><b>Бонусный счёт</b><span class="sp"></span>{tag("Активен", "wh")}</span>
    <span class="big mt8">12{NB}450{NB}Б</span>
    <span class="mut">Доступно для программ мотивации</span>
    <span class="fx" style="margin-top:auto">{btn("Выбрать программу", "p")}{btn("История операций")}</span>
  </div>
  <div class="c7" style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px">{kc}</div>
</div>
<div class="row mt24">
  <div class="box c8" style="height:530px">
    <div class="bh"><h2>Динамика баланса</h2><span class="sp"></span>{seg(["6 месяцев", "12 месяцев"], 0)}</div>
    {line_chart([22, 35, 30, 52, 60, 78], ["Июн", "Июл", "Авг", "Сен", "Окт", "Ноя"], ["15 000", "10 000", "5 000", "0"], 420)}
  </div>
  <div class="box c4" style="height:530px">
    <div class="bh"><h2>Последние операции</h2><span class="sp"></span><span class="lnk">Вся история</span></div>
    {ops_list(6)}
    <div class="mt12">{btn("Показать все операции", "w")}</div>
  </div>
</div>"""
    page("02-my-balance.html", "Мой баланс", shell("Сотрудник", "Мой баланс", crumb("Главная", "Мой баланс"), content))


# =============================================================== 03 История операций
HIST = [("24.11.2026", "Начисление", 500, "Начислено"), ("20.11.2026", "Списание", -3000, "Списано"),
        ("18.11.2026", "Начисление", 300, "На согласовании"), ("15.11.2026", "Начисление", 1200, "Начислено"),
        ("12.11.2026", "Корректировка", -200, "Скорректировано"), ("10.11.2026", "Начисление", 300, "Начислено"),
        ("02.11.2026", "Списание", -1500, "Списано"), ("28.10.2026", "Начисление", 800, "Начислено"),
        ("21.10.2026", "Начисление", 400, "Отменено"), ("15.10.2026", "Начисление", 1000, "Начислено")]


def s03():
    rows = [[d, t, f"<b>{b(a)}</b>", L(260 - (i % 3) * 50), st(s)] for i, (d, t, a, s) in enumerate(HIST)]
    content = f"""<div class="ph"><h1>История операций</h1><span class="sp"></span>{btn("Скачать выписку")}</div>
<div class="box f fx" style="align-items:flex-end;gap:16px">
  {field("Период", inp("01.10.2026 – 30.11.2026", "cal"), style="width:300px")}
  {field("Тип операции", inp("Все типы", "sel"), style="width:220px")}
  {field("Статус", inp("Все статусы", "sel"), style="width:220px")}
  {btn("Показать")}{btn("Сбросить фильтры", "d")}
</div>
<div class="fx mt16 mut" style="margin-bottom:12px">Найдено 48 операций · начислено {b(9300)} · списано {b(-4500)}</div>
{table(["Дата", "Тип", "Сумма", "Причина", "Статус"], rows, right=(2,))}
{pager()}"""
    page("03-history.html", "История операций",
         shell("Сотрудник", "История операций", crumb("Главная", "История операций"), content))


# =============================================================== 04 Программы мотивации
PROGS = [("Курс английского языка", "Обучение", 3000), ("Абонемент в фитнес-клуб", "Здоровье", 4500),
         ("Конференция TechConf 2026", "Обучение", 8000), ("Сертификат в книжный магазин", "Подарки", 1500),
         ("Дополнительный выходной", "Отдых", 5000), ("Курс по Kubernetes", "Обучение", 6000),
         ("Набор фирменного мерча", "Подарки", 1000), ("Поездка на корпоративный слёт", "Отдых", 15000)]


def programs_content():
    cards = ""
    for name, cat, cost in PROGS:
        can = cost <= 12450
        action = btn("Использовать", "p w") if can else btn("Не хватает " + bb(cost - 12450), "off w")
        cards += (f'<div class="box" style="padding:12px;display:flex;flex-direction:column;gap:8px;height:322px">'
                  f'<i class="x" style="height:104px;border-radius:6px;display:block"></i>'
                  f'<span>{tag(cat)}</span><h3>{name}</h3>{L(200, True)}'
                  f'<span class="kpi" style="font-size:20px;margin-top:auto">{bb(cost)}</span>{action}</div>')
    return f"""<div class="ph"><h1>Программы мотивации</h1><span class="sp"></span>
  <span class="box f" style="padding:6px 14px">Ваш баланс: <b>12{NB}450{NB}Б</b></span></div>
<div class="fx" style="margin-bottom:20px">{seg(["Все", "Обучение", "Здоровье", "Подарки", "Отдых"], 0)}<span class="sp"></span>
  {inp("Поиск программы", "srch", placeholder=True, style="width:300px")}{inp("Сначала дешевле", "sel", style="width:200px")}</div>
<div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:24px">{cards}</div>"""


def s04():
    page("04-programs.html", "Программы мотивации",
         shell("Сотрудник", "Программы мотивации", crumb("Главная", "Программы мотивации"), programs_content()))
    modal = f"""<div class="dim"></div>
<div class="modal" role="dialog">
  <div class="mhd"><h2>Использовать баллы на программу?</h2><span class="close">×</span></div>
  <div class="mbd fxc">
    <div class="fx" style="gap:16px"><i class="x" style="width:96px;height:72px;border-radius:6px"></i>
      <div><h3>Курс английского языка</h3><span class="mut">Обучение · 3 месяца, онлайн</span></div></div>
    <dl class="kv mt8"><dt>Стоимость</dt><dd>{bb(3000)}</dd><dt>Баланс сейчас</dt><dd>{bb(12450)}</dd>
      <dt>Баланс после</dt><dd>{bb(9450)}</dd></dl>
    <div class="alert"><i class="ico"></i><span>Баллы спишутся сразу. Отменить заявку можно в течение 24 часов в «Истории операций».</span></div>
  </div>
  <div class="mft">{btn("Отмена")}{btn("Использовать " + bb(3000), "p")}</div>
</div>"""
    page("04b-programs-confirm.html", "Программы мотивации — подтверждение",
         shell("Сотрудник", "Программы мотивации", crumb("Главная", "Программы мотивации"), programs_content(), modal))


# =============================================================== 05 Моя команда
def s05():
    pos = ["Ведущий разработчик", "Разработчик", "Разработчик", "Тестировщик", "Аналитик", "Разработчик",
           "Дизайнер", "Стажёр"]
    data = [(18200, 4200, 96), (12450, 3000, 88), (9800, 2600, 81), (7600, 1800, 74), (11300, 2900, 90),
            (6400, 1200, 62), (8900, 2100, 79), (1500, 600, 55)]
    rows = []
    for i, (p, (bal, q, k)) in enumerate(zip(pos, data)):
        kp = f'<span class="fx" style="gap:8px"><span class="bar" style="width:90px"><i style="width:{k}%"></i></span>{k}{NB}%</span>'
        rows.append([f'<i class="chk{" on" if i in (1, 3, 5) else ""}"></i>',
                     f'<span class="fx" style="gap:8px"><i class="ava s"></i>{L(130)}</span>', p,
                     f"<b>{bb(bal)}</b>", b(q), kp, f"{12 + i}.11.2026",
                     btn("Начислить", "s") + " " + btn("История", "s")])
    rows = [(r, "sel") if i in (1, 3, 5) else r for i, r in enumerate(rows)]
    content = f"""<div class="ph"><div><h1>Моя команда</h1><span class="mut">Отдел разработки · 8 сотрудников</span></div><span class="sp"></span>{btn("Начислить бонус выбранным (3)", "p")}</div>
<div class="row" style="margin-bottom:20px">
  <div class="box f c3"><span class="lbl">Баланс команды</span><div class="kpi">{bb(76150)}</div></div>
  <div class="box f c3"><span class="lbl">Начислено за квартал</span><div class="kpi">{b(18400)}</div></div>
  <div class="box f c3"><span class="lbl">Средний KPI</span><div class="kpi">78{NB}%</div></div>
  <div class="box f c3"><span class="lbl">Ждут согласования</span><div class="kpi">2</div></div>
</div>
<div class="fx" style="margin-bottom:12px">{inp("Поиск по ФИО", "srch", placeholder=True, style="width:320px")}{inp("Все должности", "sel", style="width:220px")}</div>
{table(["", "Сотрудник", "Должность", "Баланс", "За квартал", "Выполнение KPI", "Последнее начисление", "Действия"], rows, right=(3, 4))}"""
    page("05-team.html", "Моя команда", shell("Руководитель", "Моя команда", crumb("Главная", "Моя команда"), content))


# =============================================================== 06 Ручное начисление (шаг 2)
def s06():
    prev = [("×1,0", 500, 500), ("×1,2", 500, 600), ("×1,0", 500, 500)]
    prow = [[f'<span class="fx" style="gap:8px"><i class="ava s"></i>{L(90)}</span>', bb(base), k, f"<b>{bb(tot)}</b>"]
            for k, base, tot in prev]
    prow.append(([f"Итого (3 сотрудника)", "", "", f"<b>{bb(1600)}</b>"], "tot"))
    chips = "".join(f'<span class="tag wh fx" style="gap:6px;display:inline-flex;align-items:center"><i class="ava s" style="width:16px;height:16px"></i>{L(70)}×</span>' for _ in range(3))
    content = f"""<div class="ph"><h1>Ручное начисление</h1><span class="sp"></span><span class="lnk">Отменить начисление</span></div>
<div class="box">
  <div class="steps">
    <span class="st done"><i>✓</i>Сотрудники</span><span class="st-ln"></span>
    <span class="st cur"><i>2</i>Сумма и причина</span><span class="st-ln dash"></span>
    <span class="st"><i>3</i>Подтверждение</span>
  </div>
</div>
<div class="row mt24">
  <div class="box c7 fxc" style="gap:18px;height:598px">
    <div class="fx"><h2>Сумма и причина</h2></div>
    <div class="field"><label>Получатели</label><div class="fx" style="gap:8px;flex-wrap:wrap">{chips}<span class="lnk">Изменить список</span></div></div>
    <div class="fx" style="gap:24px;align-items:flex-start">
      {field("Сумма на одного сотрудника", '<div class="inp" style="width:220px"><span class="v"><b>500</b></span><span class="mut">Б</span></div>', "Без согласования — до 1 000 Б на сотрудника", style="width:300px")}
      {field("Причина", inp("Выполнение KPI проекта", "sel"), style="flex:1")}
    </div>
    {field("Основание", '<div class="inp ta" style="min-height:96px"><span class="v">Релиз версии 3.2 закрыт в срок, задача PRJ-1482. ' + L(160) + '</span></div>', "Комментарий увидит сотрудник и финансовый отдел")}
    <div class="field"><label>Документ-основание <span class="mut" style="font-weight:400">(необязательно)</span></label>
      <div class="fx">{btn("Прикрепить файл", "d")}<span class="fx" style="gap:6px"><i class="ico"></i>Протокол_спринта_47.pdf · 240 КБ</span></div></div>
    <div class="fx" style="margin-top:auto;justify-content:space-between">{btn("Назад")}{btn("Далее: подтверждение", "p")}</div>
  </div>
  <div class="box f c5 fxc" style="height:598px">
    <div class="fx"><h2>Предпросмотр расчёта</h2></div>
    <span class="mut">Сумма × коэффициент активной кампании</span>
    {table(["Сотрудник", "База", "Коэф.", "Итого"], prow, right=(1, 2, 3))}
    <div class="alert"><i class="ico"></i><span>Кампания «Лучший проект квартала» даёт ×1,2 одному сотруднику.</span></div>
    <div class="alert"><i class="ico"></i><span>Все суммы в пределах лимита — начисление пройдёт без согласования.</span></div>
  </div>
</div>"""
    page("06-accrual-step2.html", "Ручное начисление — шаг 2",
         shell("Руководитель", "Начисление", crumb("Главная", "Начисление", "Новое начисление"), content))


# =============================================================== 07 Операции + корректировка
OPS = [("10482", "25.11.2026", "Начисление", 1200, "Начислено", "Орлов Д. В."),
       ("10481", "25.11.2026", "Начисление", 500, "Начислено", "Система"),
       ("10479", "24.11.2026", "Списание", -3000, "Списано", "Сотрудник"),
       ("10476", "24.11.2026", "Начисление", 2500, "На согласовании", "Миронова Е. П."),
       ("10471", "23.11.2026", "Корректировка", -200, "Скорректировано", "Миронова Е. П."),
       ("10468", "22.11.2026", "Начисление", 800, "Начислено", "Система"),
       ("10460", "21.11.2026", "Начисление", 400, "Отменено", "Орлов Д. В."),
       ("10455", "20.11.2026", "Начисление", 5000, "Заблокировано", "Миронова Е. П."),
       ("10451", "20.11.2026", "Начисление", 300, "Начислено", "Система"),
       ("10447", "19.11.2026", "Списание", -1500, "Списано", "Сотрудник")]


def s07():
    rows = []
    for i, (n, d, t, a, s, who) in enumerate(OPS):
        r = [n, d, L(120), t, f"<b>{b(a)}</b>", L(150 - (i % 3) * 30), who, st(s), btn("Корректировать", "s")]
        rows.append((r, "sel") if i == 0 else r)
    content = f"""<div class="ph"><h1>Операции</h1><span class="sp"></span>{btn("Новое начисление", "p")}</div>
<div class="box f fx" style="align-items:flex-end;gap:16px;margin-bottom:16px">
  {field("Сотрудник", inp("ФИО или табельный номер", "srch", placeholder=True), style="width:260px")}
  {field("Период", inp("01.11.2026 – 25.11.2026", "cal"), style="width:240px")}
  {field("Тип", inp("Все типы", "sel"), style="width:170px")}
  {field("Статус", inp("Все статусы", "sel"), style="width:170px")}
  {btn("Показать")}
</div>
{table(["№", "Дата", "Сотрудник", "Тип", "Сумма", "Причина", "Инициатор", "Статус", ""], rows, right=(4,))}
{pager("Всего 1 284 операции")}"""
    drawer = f"""<div class="dim"></div>
<aside class="drawer">
  <div class="mhd"><h2>Корректировка операции №10482</h2><span class="close">×</span></div>
  <div class="mbd">
    <div class="box f"><dl class="kv"><dt>Сотрудник</dt><dd>{L(140)}</dd><dt>Операция</dt><dd>Начисление, 25.11.2026</dd>
      <dt>Сумма</dt><dd>{b(1200)}</dd><dt>Баланс сотрудника</dt><dd>{bb(9650)}</dd></dl></div>
    <div class="field"><label>Что сделать</label>
      <div class="fxc" style="gap:10px">
        <span class="fx" style="gap:10px"><i class="rad"></i>Увеличить сумму</span>
        <span class="fx" style="gap:10px"><i class="rad on"></i><b>Уменьшить сумму</b></span>
        <span class="fx" style="gap:10px"><i class="rad"></i>Отменить операцию целиком</span>
      </div></div>
    {field("На сколько уменьшить", '<div class="inp" style="width:220px"><span class="v"><b>400</b></span><span class="mut">Б</span></div>', "Новая сумма операции: 800 Б")}
    {field("Причина корректировки", '<div class="inp ta" style="min-height:96px"><span class="v">KPI пересчитан после проверки отчёта: уровень достижения 67 % вместо 100 %.</span></div>', "Обязательно — попадёт в журнал аудита")}
    <div class="alert"><i class="ico"></i><span>Баланс после корректировки: <b>{bb(9250)}</b>. Сотрудник получит уведомление.</span></div>
  </div>
  <div class="mft">{btn("Отмена")}{btn("Сохранить корректировку", "p")}</div>
</aside>"""
    page("07-operations-adjust.html", "Операции — корректировка",
         shell("HR", "Операции", crumb("Главная", "Операции"), content, drawer))


# =============================================================== 08 Правила начисления + форма
def s08():
    rules = [("Выполнение KPI спринта", "Закрытые задачи, %", "0,4", "Месяц", "80 %", "Активно"),
             ("Сдача проекта в срок", "Дата релиза", "0,3", "Квартал", "100 %", "Активно"),
             ("Наставничество", "Часы с новичками", "0,1", "Месяц", "10 ч", "Активно"),
             ("Профессиональная сертификация", "Сертификат", "0,2", "Разово", "—", "Активно"),
             ("Отзывы клиентов", "NPS проекта", "0,15", "Квартал", "50", "Черновик"),
             ("Рационализаторское предложение", "Принятые идеи", "0,1", "Месяц", "1", "Активно"),
             ("Снижение числа дефектов", "Баги в проде", "0,25", "Квартал", "−20 %", "Отменено"),
             ("Обучение коллег", "Проведённые семинары", "0,1", "Квартал", "2", "Активно")]
    rows = [[n, k, w, L(110), p, t, st(s), btn("Изменить", "s")] for n, k, w, p, t, s in rules]
    content = f"""<div class="ph"><h1>Правила начисления</h1><span class="sp"></span>{btn("Добавить правило", "p")}</div>
<div class="fx" style="margin-bottom:16px">{inp("Поиск по названию", "srch", placeholder=True, style="width:300px")}{seg(["Все", "Активные", "Черновики", "Отключённые"], 0)}</div>
{table(["Название", "Показатель", "Вес", "Формула", "Период", "Порог", "Статус", ""], rows, right=(2,))}"""
    drawer = f"""<div class="dim"></div>
<aside class="drawer">
  <div class="mhd"><h2>Новое правило</h2><span class="close">×</span></div>
  <div class="mbd">
    {field("Название", inp("Выполнение KPI спринта"))}
    <div class="fx" style="gap:16px;align-items:flex-start">
      {field("Показатель", inp("Закрытые задачи, %", "sel"), style="flex:1")}
      {field("Вес (0–1)", inp("0,4"), style="width:120px")}
    </div>
    {field("Формула", '<div class="inp ta" style="min-height:64px;font-family:Menlo,Consolas,monospace;font-size:13px"><span class="v">вес × уровень_достижения × 1000</span></div>', "Пример: при KPI 90 % — 0,4 × 0,9 × 1000 = 360 Б")}
    <div class="field"><label>Периодичность</label>{seg(["Месяц", "Квартал", "Год", "Разово"], 0)}</div>
    <div class="fx" style="gap:16px;align-items:flex-start">
      {field("Порог достижения", '<div class="inp"><span class="v">80</span><span class="mut">%</span></div>', "Ниже порога бонус не начисляется", style="flex:1")}
      {field("Действует с", inp("01.12.2026", "cal"), style="flex:1")}
    </div>
    <div class="alert"><i class="ico"></i><span>Правило уже применялось в ноябре — изменения вступят в силу с 1 декабря.</span></div>
  </div>
  <div class="mft"><span class="fx" style="gap:8px;margin-right:auto"><i class="sw on"></i>Активно</span>{btn("Отмена")}{btn("Сохранить правило", "p")}</div>
</aside>"""
    page("08-rules-form.html", "Правила начисления — форма",
         shell("HR", "Правила начисления", crumb("Главная", "Правила начисления"), content, drawer))


# =============================================================== 09 Кампании
def s09():
    camps = [("Лучший проект квартала", "Активна", "01.10 – 31.12.2026", "×1,2", 120000, 200000),
             ("Наставничество 2026", "Активна", "01.09 – 31.12.2026", "×1,5", 45000, 80000),
             ("Сертификация облачных технологий", "Активна", "15.10 – 15.12.2026", "×2,0", 64000, 100000),
             ("Запуск новой ERP", "Требуется утверждение", "01.12 – 28.02.2027", "×1,3", 0, 0),
             ("Новогодний бонус", "Запланирована", "20.12 – 31.12.2026", "×1,1", 0, 150000),
             ("Здоровая команда", "Активна", "01.11 – 30.11.2026", "×1,0", 38000, 40000)]
    cards = ""
    for name, s, dates, k, used, budget in camps:
        stt = {"Активна": "", "Требуется утверждение": "dk", "Запланирована": "ds"}[s]
        if budget:
            pct = round(used * 100 / budget)
            bud = (f'<div class="fx" style="justify-content:space-between"><span class="lbl">Бюджет</span>'
                   f'<span class="lbl">{bb(used)} из {bb(budget)}</span></div><div class="bar"><i style="width:{pct}%"></i></div>')
        else:
            bud = '<div class="alert" style="padding:6px 10px"><i class="ico"></i><span>Бюджет не задан — кампания не стартует без утверждения.</span></div>'
        act = btn("Отправить на утверждение", "p") if not budget else btn("Изменить")
        cards += (f'<div class="box fxc" style="height:322px;gap:10px"><div class="fx"><i class="x" style="width:40px;height:40px;border-radius:6px"></i>'
                  f'<span>{tag(s, stt)}</span><span class="sp"></span><span class="kpi" style="font-size:22px">{k}</span></div>'
                  f'<h3>{name}</h3><span class="fx" style="gap:8px"><i class="ico cal"></i>{dates}</span>'
                  f'<span class="fx" style="gap:8px"><i class="ico"></i>Отделы: {L(130)}</span>{L(260, True)}'
                  f'<div style="margin-top:auto" class="fxc">{bud}<div class="fx">{act}{btn("Подробнее")}</div></div></div>')
    content = f"""<div class="ph"><h1>Бонусные кампании</h1><span class="sp"></span>{btn("Создать кампанию", "p")}</div>
<div class="fx" style="margin-bottom:20px">{seg(["Все (6)", "Активные (4)", "Запланированные (1)", "Требуют утверждения (1)", "Завершённые"], 0)}<span class="sp"></span>{inp("Поиск кампании", "srch", placeholder=True, style="width:260px")}</div>
<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px">{cards}</div>"""
    page("09-campaigns.html", "Бонусные кампании",
         shell("HR", "Кампании", crumb("Главная", "Кампании"), content))


# =============================================================== 10 Отчёты
def s10():
    deps = [("Разработка", 412, 486200, 210400, 1180, "39 %"), ("Тестирование", 168, 172300, 80100, 1026, "14 %"),
            ("Продажи", 305, 298500, 154800, 979, "24 %"), ("Поддержка", 240, 141600, 71200, 590, "11 %"),
            ("HR и администрация", 96, 62800, 30900, 654, "5 %"), ("Финансы", 74, 51400, 22300, 695, "4 %"),
            ("Маркетинг", 88, 37700, 19600, 428, "3 %")]
    rows = [[n, str(c), bb(a), bb(s), bb(m), p] for n, c, a, s, m, p in deps]
    rows.append((["Итого", "1 383", bb(1250500), bb(589300), bb(904), "100 %"], "tot"))
    content = f"""<div class="ph"><h1>Отчёты</h1></div>
<div class="box f">
  <div class="fx" style="align-items:flex-end;gap:16px">
    <div class="field"><label>Тип отчёта</label>{seg(["По сотрудникам", "По подразделениям", "По кампаниям"], 1)}</div>
    {field("Период", inp("01.07.2026 – 30.09.2026", "cal"), style="width:250px")}
    {field("Подразделения", inp("Все подразделения", "sel"), style="width:220px")}
    {btn("Сформировать отчёт", "p")}
  </div>
</div>
<div class="fx mt24" style="margin-bottom:12px"><div><h2>Начисления по подразделениям</h2><span class="mut">III квартал 2026 · сформирован 25.11.2026 в 10:14</span></div>
  <span class="sp"></span>{btn('<i class="ico"></i>Скачать Excel')}{btn('<i class="ico"></i>Скачать PDF')}</div>
{table(["Подразделение", "Сотрудников", "Начислено", "Списано", "Средний бонус", "Доля фонда"], rows, right=(1, 2, 3, 4, 5))}"""
    page("10-reports.html", "Отчёты", shell("HR", "Отчёты", crumb("Главная", "Отчёты"), content))


# =============================================================== 11 Аналитика
def s11():
    kpis = [("Начислено за квартал", bb(1250500), "+12 % к II кварталу"),
            ("Средний бонус на сотрудника", bb(904), "+6 % к II кварталу"),
            ("Конверсия в программы", f"38{NB}%", "−3 п. п. к II кварталу"),
            ("Активных кампаний", "4", "1 ждёт утверждения")]
    kc = "".join(f'<div class="box c3" style="height:128px"><span class="fx"><i class="ico"></i><span class="lbl" style="font-size:13px">{t}</span></span>'
                 f'<div class="kpi mt12">{v}</div><span class="lbl">{s}</span></div>' for t, v, s in kpis)
    hb = [("Разработка", 100, "1 180"), ("Тестирование", 87, "1 026"), ("Продажи", 83, "979"), ("Финансы", 59, "695"),
          ("HR", 55, "654"), ("Поддержка", 50, "590"), ("Маркетинг", 36, "428")]
    hbars = "".join(f'<div class="hb"><span>{n}</span><span class="bar"><i style="width:{w}%"></i></span><b class="r" style="text-align:right">{v}</b></div>' for n, w, v in hb)
    content = f"""<div class="ph"><h1>Аналитика</h1><span class="sp"></span>{seg(["Месяц", "Квартал", "Год"], 1)}
  {inp("Все подразделения", "sel", style="width:210px")}<span class="fx" style="gap:8px"><i class="sw on"></i>Сравнить с прошлым периодом</span></div>
<div class="row">{kc}</div>
<div class="row mt24">
  <div class="box c7" style="height:536px">
    <div class="bh"><h2>Начисления по месяцам</h2><span class="sp"></span><span class="leg"><span><i style="background:#d4d4d4"></i>2026</span><span><i></i>2025</span></span></div>
    {bar_chart([(55, 48), (62, 50), (58, 57), (70, 61), (76, 64), (88, 70)], ["Апр", "Май", "Июн", "Июл", "Авг", "Сен"], ["500 тыс.", "375 тыс.", "250 тыс.", "125 тыс.", "0"], 430)}
  </div>
  <div class="box c5" style="height:536px">
    <div class="bh"><h2>Средний бонус по подразделениям</h2></div>
    <span class="lbl">Баллов на сотрудника за квартал</span>
    <div class="mt16 fxc" style="gap:14px">{hbars}</div>
  </div>
</div>"""
    page("11-analytics.html", "Аналитика", shell("HR", "Аналитика", crumb("Главная", "Аналитика"), content))


# =============================================================== 12 Контроль начислений
def s12():
    data = [("25.11.2026", 5000, "Орлов Д. В.", "Превышен лимит", True),
            ("25.11.2026", 1200, "Система", "Повторное начисление", True),
            ("24.11.2026", 2500, "Миронова Е. П.", "Превышен лимит", False),
            ("24.11.2026", 800, "Орлов Д. В.", "Нет основания", False),
            ("23.11.2026", 3000, "Миронова Е. П.", "Превышен лимит", False),
            ("22.11.2026", 600, "Система", "Повторное начисление", False),
            ("21.11.2026", 1500, "Орлов Д. В.", "Нет основания", False)]
    rows = []
    for d, a, who, why, sel in data:
        r = [f'<i class="chk{" on" if sel else ""}"></i>', d, L(120), f"<b>{b(a)}</b>", who, tag(why, "wh"),
             L(90), st("На согласовании"), btn("Подтвердить", "s") + " " + btn("Заблокировать", "s d")]
        rows.append((r, "sel") if sel else r)
    content = f"""<div class="ph"><h1>Контроль начислений</h1><span class="sp"></span>{btn("Отчёт для бухгалтерии")}</div>
<div class="alert" style="margin-bottom:16px"><i class="ico"></i><span><b>7 начислений ждут проверки</b> на сумму {bb(14600)}. Пока вы их не подтвердите, баллы не попадут на счёт сотрудников.</span></div>
<div class="fx" style="margin-bottom:16px">{field("Причина пометки", inp("Все причины", "sel"), style="width:240px")}{field("Период", inp("01.11.2026 – 25.11.2026", "cal"), style="width:250px")}{field("Инициатор", inp("Все инициаторы", "sel"), style="width:220px")}</div>
<div class="box f fx" style="padding:10px 16px;margin-bottom:12px"><b>Выбрано: 2 на {bb(6200)}</b><span class="sp"></span>{btn("Подтвердить выбранные", "p")}{btn("Заблокировать выбранные", "d")}{btn("Снять выбор")}</div>
{table(["", "Дата", "Сотрудник", "Сумма", "Инициатор", "Почему проверка", "Основание", "Статус", "Решение"], rows, right=(3,))}"""
    page("12-control.html", "Контроль начислений",
         shell("Финансовый отдел", "Контроль начислений", crumb("Главная", "Контроль начислений"), content))


# =============================================================== 13 Пользователи
def s13():
    data = [("Разработка", "Сотрудник", "Активен"), ("Разработка", "Руководитель", "Активен"),
            ("HR", "HR", "Активен"), ("Финансы", "Финансовый отдел", "Активен"), ("ИТ", "Администратор", "Активен"),
            ("Продажи", "Сотрудник", "Заблокирован"), ("Поддержка", "Сотрудник", "Активен"),
            ("Тестирование", "Руководитель", "Активен"), ("Маркетинг", "Сотрудник", "Активен"),
            ("Разработка", "Сотрудник", "Активен")]
    rows = [[f'<span class="fx" style="gap:8px"><i class="ava s"></i>{L(80)}</span>', L(100), dep, role, st(s),
             btn("Изменить", "s")] for dep, role, s in data]
    content = f"""<div class="ph"><h1>Пользователи</h1><span class="sp"></span>{btn("Импорт из HRIS")}</div>
<div class="row">
  <div class="c8">
    <div class="fx" style="margin-bottom:12px">{inp("Поиск по ФИО или email", "srch", placeholder=True, style="flex:1")}{inp("Все роли", "sel", style="width:180px")}{inp("Все отделы", "sel", style="width:170px")}</div>
    {table(["Сотрудник", "Email", "Отдел", "Роль", "Статус", ""], rows)}
    {pager("Всего 2 514 пользователей", ("1", "2", "3", "…", "252"))}
  </div>
  <div class="c4 box f fxc" style="gap:12px;height:732px">
    <h2>Новый пользователь</h2>
    {field("ФИО", inp("Петрова Мария Игоревна"))}
    <div class="field"><label>Email</label>{inp("a.kovaleva@technosoft.by", "err")}
      <span class="err-t">Этот email уже занят (Ковалёва А. С.). Укажите другой или откройте её карточку.</span></div>
    {field("Отдел", inp("Тестирование", "sel"))}
    {field("Должность", inp("Инженер по тестированию"))}
    {field("Роль", inp("Сотрудник", "sel"), "Роль определяет пункты меню и права")}
    {field("Руководитель <span class='mut' style='font-weight:400'>(необязательно)</span>", inp("Выберите руководителя", "sel", placeholder=True))}
    <span class="fx" style="gap:8px"><i class="sw on"></i>Учётная запись активна</span>
    <div class="fx" style="margin-top:auto;justify-content:flex-end">{btn("Отмена")}{btn("Создать пользователя", "p")}</div>
  </div>
</div>"""
    page("13-users.html", "Пользователи",
         shell("Администратор", "Пользователи", crumb("Администрирование", "Пользователи"), content))


# =============================================================== 14 Интеграции
def s14():
    ints = [("HRIS", "Кадровые данные: сотрудники, отделы, должности", "URL API", "https://hris.technosoft.local/api/v1",
             "API-ключ", "••••••••••••3f9a", "Подключено", "Синхронизация: сегодня в 09:15", True, "",
             "Расписание синхронизации", "Каждые 15 минут"),
            ("ERP", "Бизнес-события: закрытые задачи и проекты", "URL API", "https://erp.technosoft.local/rest",
             "API-ключ", "••••••••••••a71c", "Ошибка соединения", "Последняя попытка: сегодня в 10:02", True,
             "Сервер не ответил за 10 с. Проверьте URL и доступность сервера ERP.",
             "Расписание синхронизации", "Каждые 5 минут"),
            ("Active Directory", "Вход по корпоративной учётной записи", "Адрес LDAP", "ldaps://ad.technosoft.local:636",
             "Сервисная учётная запись", "svc-bonus@technosoft.local", "Подключено", "Проверено: 24.11.2026", True, "",
             "Группа пользователей", "TS-Bonus-Users"),
            ("Сервис уведомлений", "Email и push о начислениях и списаниях", "URL сервиса", "https://notify.technosoft.local",
             "API-ключ", "Не задан", "Не настроено", "Ещё не проверялось", False, "",
             "Каналы", "Email и push")]
    cards = ""
    for name, desc, l1, v1, l2, v2, s, sync, on, err, l3, v3 in ints:
        stt = {"Подключено": "", "Ошибка соединения": "dk", "Не настроено": "ds"}[s]
        e = f'<div class="alert" style="padding:6px 10px"><i class="ico"></i><span>{err}</span></div>' if err else ""
        cards += (f'<div class="box fxc c6" style="height:354px;gap:12px">'
                  f'<div class="fx"><i class="x" style="width:44px;height:44px;border-radius:6px"></i><div><h2>{name}</h2>'
                  f'<span class="mut">{desc}</span></div><span class="sp"></span><span class="fx" style="gap:8px">'
                  f'<i class="sw{" on" if on else ""}"></i>{"Включено" if on else "Выключено"}</span></div>'
                  f'<div class="fx">{tag(s, stt)}<span class="lbl">{sync}</span></div>'
                  f'<div class="fx" style="gap:16px;align-items:flex-start">{field(l1, inp(v1), style="flex:1.3")}'
                  f'{field(l2, inp(v2, placeholder=(v2 == "Не задан")), style="flex:1")}</div>'
                  f'{field(l3, inp(v3, "sel" if l3 != "Группа пользователей" else ""), style="width:50%")}{e}'
                  f'<div class="fx" style="margin-top:auto">{btn("Проверить соединение")}{btn("Сохранить", "p")}</div></div>')
    content = f"""<div class="ph"><h1>Интеграции</h1><span class="sp"></span><span class="mut">Обмен данными по HTTPS, формат JSON</span></div>
<div class="row">{cards}</div>"""
    page("14-integrations.html", "Интеграции",
         shell("Администратор", "Интеграции", crumb("Администрирование", "Интеграции"), content))


# =============================================================== 15 Журнал аудита
def s15():
    data = [("25.11.2026 10:42:15", "HR", "Изменение правила", "Правило №12", "вес 0,3 → 0,4"),
            ("25.11.2026 10:31:02", "Руководитель", "Ручное начисление", "Операция №10482", f"+1 200 Б, 1 сотрудник"),
            ("25.11.2026 10:02:47", "Система", "Ошибка интеграции", "ERP", "таймаут 10 с, отложено"),
            ("25.11.2026 09:58:30", "Финансовый отдел", "Блокировка начисления", "Операция №10455", "превышен лимит"),
            ("25.11.2026 09:15:00", "Система", "Синхронизация", "HRIS", "обновлено 14 записей"),
            ("24.11.2026 18:20:11", "Администратор", "Смена роли", "Пользователь", "Сотрудник → Руководитель"),
            ("24.11.2026 17:44:09", "HR", "Корректировка", "Операция №10471", "−200 Б"),
            ("24.11.2026 16:03:51", "Сотрудник", "Списание", "Операция №10479", "−3 000 Б, программа"),
            ("24.11.2026 15:12:38", "Администратор", "Вход в систему", "Сессия", "успешно"),
            ("24.11.2026 14:55:20", "HR", "Создание кампании", "Кампания №9", "черновик")]
    rows = []
    for i, (dt, role, act, obj, det) in enumerate(data):
        user = '<span class="mut">—</span>' if role == "Система" else L(110)
        rows.append([dt, user, role, act, obj, det, f"10.0.12.{40 + i}" if role != "Система" else "—"])
    content = f"""<div class="ph"><h1>Журнал аудита</h1><span class="sp"></span>{btn("Выгрузить в CSV")}</div>
<div class="box f fx" style="align-items:flex-end;gap:16px;margin-bottom:16px">
  {field("Период", inp("24.11.2026 – 25.11.2026", "cal"), style="width:250px")}
  {field("Пользователь", inp("ФИО или email", "srch", placeholder=True), style="width:230px")}
  {field("Действие", inp("Все действия", "sel"), style="width:200px")}
  {field("Объект", inp("Все объекты", "sel"), style="width:180px")}
  {btn("Показать")}{btn("Сбросить", "d")}
</div>
{table(["Дата и время", "Пользователь", "Роль", "Действие", "Объект", "Что изменилось", "IP-адрес"], rows)}
{pager("Всего 18 406 записей · хранятся 3 года", ("1", "2", "3", "…", "1841"))}"""
    page("15-audit.html", "Журнал аудита",
         shell("Администратор", "Журнал аудита", crumb("Администрирование", "Журнал аудита"), content))


# =============================================================== 16 Мобильные
def mhdr(title):
    return (f'<header class="mhdr"><span class="burger" title="Меню"><i></i><i></i><i></i></span>'
            f'<span class="t">{title}</span><span class="bell" title="Уведомления"><i></i><b>3</b></span>'
            f'<span style="width:44px;height:44px;display:flex;align-items:center;justify-content:center"><i class="ava"></i></span></header>')


def s16a():
    kp = "".join(f'<div class="mcard fx" style="padding:10px 14px"><i class="ico"></i><span class="lbl" style="flex:1;font-size:13px">{t}</span><b class="kpi" style="font-size:18px;line-height:22px">{v}</b></div>'
                 for t, v in [("Начислено за ноябрь", b(2000)), ("Потрачено за ноябрь", b(-3000)), ("Ожидает согласования", bb(300))])
    body = f"""{mhdr("Мой баланс")}
<main class="mmain">
  <div class="mcard" style="background:#e9e9e9">
    <span class="fx"><b>Бонусный счёт</b><span class="sp"></span>{tag("Активен", "wh")}</span>
    <div class="big" style="font-size:38px;line-height:46px;margin-top:6px">12{NB}450{NB}Б</div>
    <span class="mut">Доступно для программ мотивации</span>
    <div class="mt12">{btn("Выбрать программу", "p w")}</div>
  </div>
  {kp}
  <div class="mcard">
    <div class="fx" style="margin-bottom:8px"><h3>Динамика баланса</h3><span class="sp"></span><span class="lbl">6 мес.</span></div>
    {line_chart([22, 35, 30, 52, 60, 78], ["Июн", "Июл", "Авг", "Сен", "Окт", "Ноя"], ["15 т.", "10 т.", "5 т.", "0"], 112)}
  </div>
  <div class="mcard" style="padding-bottom:4px">
    <div class="fx"><h3>Последние операции</h3><span class="sp"></span><span class="lnk">Все</span></div>
    {ops_list(2)}
  </div>
</main>"""
    page("16a-mobile-balance.html", "Мой баланс (мобильная версия)", body, mobile=True)


def s16b():
    cards = ""
    for d, t, a, s in HIST[:6]:
        cards += (f'<div class="mcard" style="padding:10px 14px"><div class="fx"><span class="lbl">{d} · {t}</span><span class="sp"></span>{st(s)}</div>'
                  f'<div class="fx" style="margin-top:6px">{L(170)}<span class="sp"></span><b style="font-size:16px">{b(a)}</b></div></div>')
    body = f"""{mhdr("История операций")}
<main class="mmain" style="gap:10px">
  <div class="fx" style="gap:8px">{btn('<i class="ico cal"></i>Ноябрь 2026', "", "flex:1")}{btn('<i class="ico"></i>Фильтры (1)', "", "flex:1")}</div>
  {seg(["Все", "Начисления", "Списания"], 0).replace('class="seg"', 'class="seg" style="display:flex"').replace('<span class="', '<span style="flex:1;text-align:center" class="')}
  <span class="lbl">Найдено 12 операций</span>
  {cards}
  {btn("Показать ещё", "w")}
</main>"""
    page("16b-mobile-history.html", "История операций (мобильная версия)", body, mobile=True)


def s16c():
    items = "".join(f'<div class="mi{" on" if m == "Мой баланс" else ""}" style="height:48px"><i class="ico"></i>{m}</div>'
                    for m in MENUS["Сотрудник"])
    body = f"""{mhdr("Мой баланс")}
<main class="mmain"><div class="mcard" style="height:180px;background:#e9e9e9"></div><div class="mcard" style="height:60px"></div><div class="mcard" style="height:60px"></div></main>
<div class="dim"></div>
<aside style="position:fixed;top:0;left:0;width:290px;height:812px;background:#e9e9e9;border-right:1.5px solid #555;display:flex;flex-direction:column">
  <div class="logo" style="justify-content:space-between;padding-right:8px"><span class="fx" style="gap:10px"><i class="x" style="width:28px;height:28px"></i>TechnoSoft Bonus</span><span class="close" style="margin:0">×</span></div>
  <div class="fx" style="padding:16px 20px;border-bottom:1.5px solid #555"><i class="ava"></i><span>{PEOPLE["Сотрудник"]}<small class="lbl" style="display:block">Сотрудник</small></span></div>
  <nav class="menu">{items}</nav>
  <div class="sfoot"><span class="fx"><i class="ico"></i>Выйти</span></div>
</aside>"""
    page("16c-mobile-menu.html", "Меню (мобильная версия)", body, mobile=True)


if __name__ == "__main__":
    for fn in (s01, s02, s03, s04, s05, s06, s07, s08, s09, s10, s11, s12, s13, s14, s15, s16a, s16b, s16c):
        fn()
    print("HTML-макеты сгенерированы в", HERE)
