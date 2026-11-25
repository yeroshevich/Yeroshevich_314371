"""Генератор демонстрационных данных (DML) для схемы V1__schema.sql.

Данные совпадают с мок-данными клиента (frontend/src/api/mock/db.ts):
те же подразделения, сотрудники, роли, правила, кампании, программы, интеграции.
Операции генерируются детерминированно (seed 46); баланс каждого счёта равен
сумме проведённых операций (accrued, written_off, corrected).

Запуск: python generate_seed.py > ../src/main/resources/db/migration/V2__seed_data.sql
Требуется пакет bcrypt (хэш пароля демо-учёток «demo»).
"""
import random
from datetime import date, datetime, timedelta

import bcrypt

rnd = random.Random(46)
NOW = datetime(2026, 11, 25, 12, 0)
PWD = bcrypt.hashpw(b'demo', bcrypt.gensalt(10)).decode()


def q(v):
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'TRUE' if v else 'FALSE'
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, datetime):
        return f"'{v:%Y-%m-%d %H:%M:%S}+03'"
    if isinstance(v, date):
        return f"'{v:%Y-%m-%d}'"
    return "'" + str(v).replace("'", "''") + "'"


def insert(table, cols, rows):
    out = [f'INSERT INTO {table} ({", ".join(cols)}) VALUES']
    out.append(',\n'.join('    (' + ', '.join(q(v) for v in r) + ')' for r in rows) + ';')
    return '\n'.join(out) + '\n'


sql = ['-- TechnoSoft Bonus — демонстрационные данные (миграция Flyway V2). Сгенерировано tools/generate_seed.py\n']

# ---- справочники
roles = [('employee', 'Сотрудник'), ('manager', 'Руководитель'), ('hr', 'HR'), ('finance', 'Финансовый отдел'), ('admin', 'Администратор')]
sql.append(insert('role', ['code', 'name'], roles))

depts = ['Разработка ПО', 'Обеспечение качества', 'Бизнес-анализ', 'DevOps и инфраструктура', 'Управление персоналом', 'Финансы']
sql.append(insert('department', ['id', 'name'], [(i + 1, n) for i, n in enumerate(depts)]))

E = [  # ФИО, логин, должность, отдел, руководитель, год приёма
    ('Анна Ковалёва', 'anna.kovaleva', 'Frontend-разработчик', 1, 2, 2021),
    ('Дмитрий Савицкий', 'dmitry.savitsky', 'Руководитель группы разработки', 1, None, 2015),
    ('Максим Новик', 'maxim.novik', 'Backend-разработчик (Java)', 1, 2, 2020),
    ('Екатерина Шевчук', 'ekaterina.shevchuk', 'Backend-разработчик (Java)', 1, 2, 2022),
    ('Артём Василевич', 'artem.vasilevich', 'Ведущий разработчик', 1, 2, 2017),
    ('Юлия Романович', 'yulia.romanovich', 'Frontend-разработчик', 1, 2, 2023),
    ('Никита Гончаров', 'nikita.goncharov', 'Mobile-разработчик', 1, 2, 2024),
    ('Наталья Кравченко', 'natalia.kravchenko', 'Руководитель отдела QA', 2, None, 2016),
    ('Павел Карпович', 'pavel.karpovich', 'QA-инженер', 2, 8, 2022),
    ('Виктория Лис', 'victoria.lis', 'Инженер по автоматизации тестирования', 2, 8, 2021),
    ('Андрей Козлов', 'andrey.kozlov', 'QA-инженер', 2, 8, 2025),
    ('Ирина Соколова', 'irina.sokolova', 'Руководитель отдела аналитики', 3, None, 2014),
    ('Алексей Бондаренко', 'alexey.bondarenko', 'Бизнес-аналитик', 3, 12, 2019),
    ('Мария Тарасевич', 'maria.tarasevich', 'Системный аналитик', 3, 12, 2020),
    ('Кирилл Лукашевич', 'kirill.lukashevich', 'Аналитик данных', 3, 12, 2023),
    ('Владимир Ермаков', 'vladimir.ermakov', 'Руководитель DevOps', 4, None, 2013),
    ('Сергей Литвинов', 'sergey.litvinov', 'Системный администратор', 4, 16, 2018),
    ('Денис Пашкевич', 'denis.pashkevich', 'DevOps-инженер', 4, 16, 2021),
    ('Евгений Михалевич', 'evgeny.mikhalevich', 'SRE-инженер', 4, 16, 2022),
    ('Ольга Мельник', 'olga.melnik', 'HR-бизнес-партнёр', 5, 21, 2019),
    ('Елена Шидловская', 'elena.shidlovskaya', 'Директор по персоналу', 5, None, 2012),
    ('Татьяна Климович', 'tatiana.klimovich', 'Специалист по обучению', 5, 21, 2024),
    ('Игорь Жук', 'igor.zhuk', 'Финансовый контролёр', 6, 24, 2018),
    ('Людмила Сидоренко', 'lyudmila.sidorenko', 'Финансовый директор', 6, None, 2011),
    ('Ангелина Островская', 'angelina.ostrovskaya', 'Экономист', 6, 24, 2023),
    ('Глеб Сорокин', 'gleb.sorokin', 'Стажёр-разработчик', 1, 2, 2026),
]
emp_rows = []
for i, (fio, login, pos, d, m, y) in enumerate(E, 1):
    emp_rows.append((i, fio, f'{login}@technosoft.by', pos, d, None, date(y, rnd.randint(1, 9), rnd.randint(10, 28)), f'HR-{1000 + i}'))
sql.append(insert('employee', ['id', 'full_name', 'email', 'position', 'department_id', 'manager_id', 'hired_at', 'external_id'], emp_rows))
sql.append(''.join(f'UPDATE employee SET manager_id = {m} WHERE id = {i};\n' for i, (_, _, _, _, m, _) in enumerate(E, 1) if m))
heads = {1: 2, 2: 8, 3: 12, 4: 16, 5: 21, 6: 24}
sql.append(''.join(f'UPDATE department SET head_employee_id = {e} WHERE id = {d};\n' for d, e in heads.items()) + '\n')

role_by = {2: 'manager', 8: 'manager', 12: 'manager', 16: 'manager', 20: 'hr', 21: 'hr', 22: 'hr', 23: 'finance', 24: 'finance', 17: 'admin'}
users = [(i, i, role_by.get(i, 'employee'), PWD, 'blocked' if i == 26 else 'active', NOW - timedelta(days=i % 7, hours=rnd.randint(1, 9)))
         for i in range(1, len(E) + 1)]
sql.append(insert('app_user', ['id', 'employee_id', 'role_code', 'password_hash', 'status', 'last_login_at'], users))

kpis = [('SPRINT', 'Закрытые задачи спринта', '%'), ('RELEASE', 'Релиз без критических дефектов', '%'), ('STAGE', 'Этап принят заказчиком в срок', '%'),
        ('MENTOR', 'Новички, прошедшие адаптацию', '%'), ('TENURE', 'Полный год стажа', '%'), ('CERT', 'Полученный сертификат', '%')]
sql.append(insert('kpi', ['id', 'code', 'name', 'unit'], [(i + 1, *k) for i, k in enumerate(kpis)]))

rules = [  # название, kpi, вес, база, периодичность, порог, активно
    ('Выполнение плана спринта', 1, 0.4, 3000, 'month', 80, True),
    ('Качество релиза', 2, 0.3, 3000, 'month', 90, True),
    ('Сдача этапа проекта', 3, 0.5, 6000, 'event', 100, True),
    ('Наставничество', 4, 0.2, 5000, 'quarter', 100, True),
    ('Годовщина работы в компании', 5, 1.0, 1000, 'year', 100, True),
    ('Профессиональная сертификация', 6, 0.25, 8000, 'event', 100, False),
]
sql.append(insert('accrual_rule', ['id', 'name', 'kpi_id', 'weight', 'base_points', 'periodicity', 'threshold', 'params', 'active', 'effective_from'],
                  [(i + 1, n, k, w, b, p, t, '{"roundTo": 50}', a, date(2026, 6, 1)) for i, (n, k, w, b, p, t, a) in enumerate(rules)]))

camps = [
    ('Осенний релиз 2.0', 'Полуторный коэффициент к KPI-начислениям команд, которые выпускают релиз платформы 2.0.', date(2026, 9, 1), date(2026, 12, 15), 1.5, 60000, 'active', [1, 2]),
    ('Неделя наставничества', 'Двойные баллы за наставничество новых сотрудников в первую неделю месяца.', date(2026, 12, 1), date(2026, 12, 7), 2.0, 15000, 'planned', [1, 2, 3, 4, 5, 6]),
    ('Квартал без инцидентов', 'Бонус дежурным инженерам за квартал без инцидентов первой категории.', date(2026, 12, 1), date(2027, 2, 28), 2.0, None, 'needs_approval', [4]),
    ('Летний хакатон', 'Призовые баллы участникам и победителям внутреннего хакатона.', date(2026, 7, 1), date(2026, 7, 31), 1.2, 40000, 'finished', [1, 3]),
]
sql.append(insert('campaign', ['id', 'name', 'description', 'start_date', 'end_date', 'coefficient', 'budget', 'status'],
                  [(i + 1, *c[:7]) for i, c in enumerate(camps)]))
sql.append(insert('campaign_department', ['campaign_id', 'department_id'], [(i + 1, d) for i, c in enumerate(camps) for d in c[7]]))

progs = [
    ('Сертификат в книжный магазин', 'gift', 'Электронный сертификат номиналом 100 BYN.', 1500, True, None),
    ('Худи и термокружка TechnoSoft', 'merch', 'Комплект фирменного мерча.', 900, True, None),
    ('Абонемент в бассейн', 'health', 'Месяц безлимитного посещения бассейна в клубе-партнёре.', 2500, True, None),
    ('Дополнительный выходной', 'dayoff', 'Оплачиваемый день отдыха по согласованию с руководителем.', 4000, True, None),
    ('Курс английского языка', 'education', 'Три месяца занятий в группе, два раза в неделю.', 6000, True, None),
    ('Участие в профильной конференции', 'education', 'Билет и командировочные на профильную конференцию.', 12000, True, None),
    ('Наушники с шумоподавлением', 'gift', 'Беспроводные наушники для работы в open space.', 9500, True, None),
    ('Корпоративный сплав по Неману', 'event', 'Двухдневный выезд с командой.', 3500, False, 'Сезон завершён. Запись откроется в мае.'),
]
sql.append(insert('program', ['id', 'name', 'category', 'description', 'cost', 'active', 'inactive_reason'], [(i + 1, *p) for i, p in enumerate(progs)]))

integ = [('hris', 'HRIS', 'https://hris.technosoft.by/api/v2', True, NOW - timedelta(hours=2)),
         ('erp', 'ERP', 'http://erp.technosoft.local/api', False, None),
         ('ad', 'Active Directory', 'https://ad.technosoft.by/ldap', True, NOW - timedelta(minutes=25)),
         ('notifications', 'Сервис уведомлений', 'https://notify.technosoft.by/v1', True, NOW - timedelta(minutes=5))]
sql.append(insert('integration', ['id', 'kind', 'name', 'url', 'api_key_encrypted', 'enabled', 'last_sync_at'],
                  [(i + 1, k, n, u, 'enc:v1:' + format(rnd.getrandbits(64), 'x'), en, ls) for i, (k, n, u, en, ls) in enumerate(integ)]))
sql.append(insert('integration_check', ['integration_id', 'ok', 'message', 'latency_ms', 'checked_at'], [
    (1, True, 'Соединение установлено', 182, NOW - timedelta(hours=2)),
    (2, False, 'Сервер отклонил подключение: нужен адрес HTTPS', None, NOW - timedelta(days=1)),
    (3, True, 'Соединение установлено', 64, NOW - timedelta(minutes=25)),
    (4, True, 'Соединение установлено', 97, NOW - timedelta(hours=3))]))

# ---- KPI-результаты текущего месяца
kres = []
for e in range(1, len(E) + 1):
    for k in (1, 2):
        kres.append((e, k, date(2026, 11, 1), date(2026, 11, 30), round(rnd.uniform(68, 128), 2)))
sql.append(insert('kpi_result', ['employee_id', 'kpi_id', 'period_start', 'period_end', 'value_pct'], kres))

# ---- операции, события, согласования
balance = {e: 0 for e in range(1, len(E) + 1)}
ops, events, reviews, audit, notes = [], [], [], [], []
seq = 0


def moment(month, d1=1, d2=28):
    d = datetime(2026, month, rnd.randint(d1, d2), rnd.randint(9, 18), rnd.randint(0, 59))
    while d.weekday() > 4:
        d -= timedelta(days=1)
    return min(d, NOW - timedelta(hours=rnd.randint(1, 5)))


def op(emp, at, typ, amount, status, basis, reason, rule=None, camp=None, prog=None, event=None, by=None, corr=None, orig=None):
    global seq
    seq += 1
    ops.append((seq, f'ОП-{seq:06d}', emp, typ, status, basis, amount, orig, reason, rule, camp, prog, event, corr, by, at))
    if status in ('accrued', 'written_off', 'corrected'):
        balance[emp] += amount
    return seq


staff = [e for e in range(1, 26)]
for month in range(6, 12):
    for emp in staff:
        if emp == 9:  # счёт заблокирован HR (альт. поток UC1)
            continue
        kpi_val = rnd.uniform(70, 125)
        if kpi_val >= 80:  # BR-5: без подтверждённого результата бонус не начисляется
            pts = round(3000 * 0.4 * kpi_val / 100 / 50) * 50
            camp = 1 if month >= 9 and E[emp - 1][3] in (1, 2) else None
            if camp:
                pts = round(pts * 1.5 / 50) * 50
            ev = len(events) + 1
            at = moment(month, 25, 28) if month < 11 else moment(11, 20, 24)
            events.append((ev, 1, f'SPRINT-{month:02d}-{emp:02d}', 'kpi_closed', emp, '{"kpi": "SPRINT", "value": %.1f}' % kpi_val, 'processed', at, at + timedelta(seconds=3)))
            op(emp, at, 'accrual', pts, 'accrued', 'kpi', f'План спринта за {["июнь","июль","август","сентябрь","октябрь","ноябрь"][month-6]}, KPI {kpi_val:.0f} %', rule=1, camp=camp, event=ev)
        if rnd.random() < 0.18:
            prog = rnd.choice([1, 2, 3])
            cost = progs[prog - 1][3]
            if balance[emp] >= cost:  # UC3: баланс ≥ стоимости
                op(emp, moment(month), 'writeoff', -cost, 'written_off', 'program', f'Программа «{progs[prog - 1][0]}»', prog=prog, by=emp)

# ручные начисления, лимит 1 000 Б (UC5)
op(1, datetime(2026, 11, 23, 12, 47), 'accrual', 1500, 'pending', 'achievement', 'Доклад на внутреннем митапе фронтенда', by=2)
op(3, datetime(2026, 10, 14, 15, 20), 'accrual', 800, 'accrued', 'project', 'Миграция сервиса начислений на Java 21', by=2)
op(5, datetime(2026, 11, 16, 11, 5), 'accrual', 7500, 'pending', 'project', 'Сдача архитектуры платформы 2.0 заказчику', rule=3, by=2)
op(18, datetime(2026, 11, 19, 18, 30), 'accrual', 6000, 'pending', 'achievement', 'Восстановление кластера после аварии в выходные', by=16)
blk = op(10, datetime(2026, 11, 10, 10, 15), 'accrual', 2000, 'blocked', 'achievement', 'Автотесты платёжного модуля', by=8)
reviews.append((blk, 23, 'blocked', 'Дублирует начисление по правилу «Качество релиза» за октябрь', datetime(2026, 11, 11, 9, 40)))
ap = op(13, datetime(2026, 10, 20, 16, 0), 'accrual', 1800, 'accrued', 'project', 'Требования к модулю аналитики приняты заказчиком', by=12)
reviews.append((ap, 23, 'approved', 'Подтверждено протоколом приёмки', datetime(2026, 10, 21, 10, 0)))
# корректировка (UC7)
base = op(3, datetime(2026, 11, 3, 14, 0), 'accrual', 1100, 'cancelled', 'kpi', 'План спринта: ошибка в сумме', rule=1, by=20)
op(3, datetime(2026, 11, 4, 9, 30), 'correction', 1400, 'corrected', 'correction', 'Исправлена сумма: учтён коэффициент кампании', corr=base, orig=1100, by=20)
# автоначисление в годовщину (UC4)
ev = len(events) + 1
events.append((ev, None, 'TENURE-2026-17', 'anniversary', 17, '{"years": 8}', 'processed', datetime(2026, 9, 15, 0, 1), datetime(2026, 9, 15, 0, 1, 2)))
op(17, datetime(2026, 9, 15, 0, 1, 2), 'accrual', 1000, 'accrued', 'kpi', 'Годовщина работы в компании: 8 лет', rule=5, event=ev)
# событие с ошибкой (альт. поток UC4)
events.append((len(events) + 1, 1, 'SPRINT-11-99', 'kpi_closed', 11, '{"kpi": "SPRINT", "value": null}', 'failed', datetime(2026, 11, 24, 18, 0), None))

sql.append(insert('business_event', ['id', 'integration_id', 'external_id', 'event_type', 'employee_id', 'payload', 'status', 'received_at', 'processed_at'], events))
sql.append("UPDATE business_event SET error_message = 'Некорректные входные данные: значение KPI отсутствует' WHERE status = 'failed';\n\n")

accounts = [(e, e, balance[e], 'blocked' if e == 9 else 'active', NOW - timedelta(hours=e)) for e in range(1, len(E) + 1)]
sql.append(insert('bonus_account', ['id', 'employee_id', 'balance', 'status', 'updated_at'], accounts))
ops.sort(key=lambda o: o[15])
renum = {o[0]: i + 1 for i, o in enumerate(ops)}
ops = [(renum[o[0]], f'ОП-{renum[o[0]]:06d}', *o[2:13], renum.get(o[13]) if o[13] else None, *o[14:]) for o in ops]
reviews = [(renum[r[0]], *r[1:]) for r in reviews]
sql.append(insert('operation', ['id', 'number', 'account_id', 'type', 'status', 'basis', 'amount', 'original_amount', 'reason', 'rule_id', 'campaign_id', 'program_id', 'event_id', 'corrects_id', 'created_by', 'created_at'], ops))
sql.append(insert('operation_review', ['operation_id', 'reviewer_id', 'decision', 'comment', 'decided_at'], reviews))

audit = [
    (datetime(2026, 11, 25, 9, 58, 30), 23, 'Блокировка начисления', 'operation', None, '{"reason": "дубль"}', '10.10.4.23'),
    (datetime(2026, 11, 24, 17, 12), 20, 'Изменение правила', 'accrual_rule', 2, '{"threshold": [85, 90]}', '10.10.4.20'),
    (datetime(2026, 11, 24, 0, 1), None, 'Автоначисление по правилу', 'accrual_rule', 1, '{"processed": 22}', None),
    (datetime(2026, 11, 23, 12, 47), 2, 'Ручное начисление', 'operation', None, '{"employee": "Анна Ковалёва", "amount": 1500}', '10.10.2.2'),
    (datetime(2026, 11, 22, 10, 5), 17, 'Проверка соединения', 'integration', 2, '{"ok": false}', '10.10.1.17'),
    (datetime(2026, 11, 20, 15, 40), 21, 'Создание кампании', 'campaign', 3, '{"budget": null}', '10.10.4.21'),
    (datetime(2026, 11, 18, 11, 0), 17, 'Блокировка пользователя', 'app_user', 26, '{}', '10.10.1.17'),
    (datetime(2026, 11, 4, 9, 30), 20, 'Корректировка', 'operation', None, '{"from": 1100, "to": 1400}', '10.10.4.20'),
]
sql.append(insert('audit_log', ['occurred_at', 'user_id', 'action', 'object_type', 'object_id', 'details', 'ip'], audit))
sql.append(insert('notification', ['user_id', 'title', 'text', 'created_at', 'read_at'], [
    (1, 'Начисление на согласовании', 'Доклад на внутреннем митапе фронтенда: 1 500 Б ждут подтверждения финотдела.', datetime(2026, 11, 23, 12, 47), None),
    (1, 'Начислено 1 800 Б', 'План спринта за ноябрь.', datetime(2026, 11, 22, 18, 0), None),
    (10, 'Начисление заблокировано', 'Автотесты платёжного модуля: дублирует начисление по правилу.', datetime(2026, 11, 11, 9, 40), datetime(2026, 11, 11, 10, 2)),
]))

# выравнивание последовательностей после явных id
for t in ['department', 'kpi', 'employee', 'app_user', 'bonus_account', 'accrual_rule', 'campaign', 'program', 'integration', 'business_event', 'operation']:
    sql.append(f"SELECT setval(pg_get_serial_sequence('{t}', 'id'), (SELECT max(id) FROM {t}));\n")

print('\n'.join(sql))
