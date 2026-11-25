# Генератор ER-диаграмм (draw.io) для ЛР7 «Организация системы хранения данных»
# Вариант 46, «TechnoSoft Bonus». Источник правды: backend/.../db/migration/V1__schema.sql
# Запуск: python3 er_diagrams.py er-model.drawio
import sys, html

ENT_F, ENT_S = '#DBE4FF', '#3B5BDB'   # сущности
REL_F, REL_S = '#FFF3BF', '#F08C00'   # связи
ATT_S = '#5C6370'                      # атрибуты
INK = '#1F2430'
FONT = 'fontFamily=Helvetica;'


def esc(t):
    return html.escape(t, quote=True).replace('\n', '&#xa;')


class Page:
    def __init__(s, name):
        s.name, s.cells, s.n = name, [], 0

    def nid(s, p='c'):
        s.n += 1
        return f'{p}{s.n}'

    def vertex(s, text, x, y, w, h, style, parent='1', vid=None):
        i = vid or s.nid()
        s.cells.append(f'<mxCell id="{i}" value="{esc(text)}" style="{style}" vertex="1" parent="{parent}">'
                       f'<mxGeometry x="{x:.0f}" y="{y:.0f}" width="{w:.0f}" height="{h:.0f}" as="geometry"/></mxCell>')
        return i

    def edge(s, a, b, style, pts=(), label='', parent='1'):
        i = s.nid('e')
        p = ''
        if pts:
            p = '<Array as="points">' + ''.join(f'<mxPoint x="{x:.0f}" y="{y:.0f}"/>' for x, y in pts) + '</Array>'
        s.cells.append(f'<mxCell id="{i}" value="{esc(label)}" style="{style}" edge="1" parent="{parent}" source="{a}" target="{b}">'
                       f'<mxGeometry relative="1" as="geometry">{p}</mxGeometry></mxCell>')
        return i

    def elabel(s, eid, text, pos, dx=0, dy=0, style=''):
        """Подпись на ребре: pos от -1 (источник) до 1 (цель)."""
        i = s.nid('l')
        st = ('edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];' + FONT +
              'fontSize=13;fontStyle=1;labelBackgroundColor=#FFFFFF;fontColor=' + INK + ';' + style)
        s.cells.append(f'<mxCell id="{i}" value="{esc(text)}" style="{st}" vertex="1" connectable="0" parent="{eid}">'
                       f'<mxGeometry x="{pos}" relative="1" as="geometry"><mxPoint x="{dx}" y="{dy}" as="offset"/></mxGeometry></mxCell>')

    def xml(s, k, w=1200, h=1700):
        return (f'<diagram id="p{k}" name="{esc(s.name)}"><mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" '
                f'tooltips="1" connect="1" arrows="1" fold="1" page="0" pageScale="1" pageWidth="{w}" pageHeight="{h}" '
                f'math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>' + ''.join(s.cells) +
                '</root></mxGraphModel></diagram>')


# =====================================================================
# 1. Концептуальная модель — нотация Чена
# =====================================================================
ST_ENT = (f'rounded=0;whiteSpace=wrap;html=1;fillColor={ENT_F};strokeColor={ENT_S};strokeWidth=2;'
          f'fontSize=14;fontStyle=1;fontColor={INK};' + FONT)
ST_REL = (f'rhombus;whiteSpace=wrap;html=1;fillColor={REL_F};strokeColor={REL_S};strokeWidth=1.5;'
          f'fontSize=12;fontColor={INK};' + FONT)
ST_ATT = (f'ellipse;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor={ATT_S};fontSize=12;fontColor={INK};'
          'spacing=0;' + FONT)
ST_LINE = f'endArrow=none;html=1;rounded=0;strokeColor={INK};strokeWidth=1.2;'
ST_ALINE = f'endArrow=none;html=1;rounded=0;strokeColor={ATT_S};strokeWidth=1;'

EW, EH = 156, 50       # сущность
RW, RH = 118, 62       # связь (ромб)
AH = 30                # атрибут (овал)


def att_w(t):
    ln = max(len(x) for x in t.split('\n'))
    return max(64, ln * 7.4 + 26)


class Chen(Page):
    def __init__(s, name):
        super().__init__(name)
        s.pos = {}

    def ent(s, key, text, cx, cy, w=EW):
        s.pos[key] = (cx, cy)
        return s.vertex(text, cx - w / 2, cy - EH / 2, w, EH, ST_ENT, vid=key)

    def rel(s, key, text, cx, cy, w=RW, h=RH):
        s.pos[key] = (cx, cy)
        return s.vertex(text, cx - w / 2, cy - h / 2, w, h, ST_REL, vid=key)

    def att(s, owner, text, cx, cy, kind='', pts=()):
        """kind: key — ключевой (подчёркнут), multi — многозначный, derived — производный."""
        st = ST_ATT
        h = AH if '\n' not in text else 44
        if kind == 'key':
            st += 'fontStyle=4;'
        if kind == 'multi':
            st = st.replace('ellipse;', 'shape=doubleEllipse;') + 'margin=3;'
            h += 8
        if kind == 'derived':
            st += 'dashed=1;dashPattern=6 4;'
        w = att_w(text) + (14 if kind == 'multi' else 0)
        a = s.vertex(text, cx - w / 2, cy - h / 2, w, h, st)
        s.edge(owner, a, ST_ALINE, pts)
        return a

    def link(s, r, e, card, pts=(), pos=0.6, dx=0, dy=0, style=''):
        """Линия от ромба r к сущности e с кардинальностью card у конца сущности."""
        eid = s.edge(r, e, ST_LINE + style, pts)
        if card:
            s.elabel(eid, card, pos, dx, dy)
        return eid


def conceptual():
    d = Chen('Концептуальная модель (Чен)')
    E, A, R, L = d.ent, d.att, d.rel, d.link

    # ---------- Сотрудник
    E('emp', 'Сотрудник', 640, 400)
    A('emp', 'табельный №', 480, 300, 'key')
    A('emp', 'дата приёма', 520, 235)
    A('emp', 'ФИО', 600, 190)
    A('emp', 'email', 700, 190)
    A('emp', 'должность', 765, 250)
    R('r_boss', 'руководит', 470, 530)
    L('r_boss', 'emp', '1', style='exitX=0.5;exitY=0;entryX=0.05;entryY=1;', pos=0.6)
    L('r_boss', 'emp', 'N', style='exitX=1;exitY=0.5;entryX=0.3;entryY=1;', pos=0.6)

    # ---------- Учётная запись, роль, аудит, уведомления
    E('usr', 'Учётная запись', 250, 400)
    R('r_has', 'имеет', 440, 400, w=100)
    L('r_has', 'emp', '1'); L('r_has', 'usr', '1')
    A('usr', '№ уч. записи', 110, 300, 'key')
    A('usr', 'статус', 120, 350)
    A('usr', 'последний вход', 355, 300)
    E('role', 'Роль', 250, 110, w=130)
    A('role', 'код роли', 90, 80, 'key')
    A('role', 'название', 90, 140)
    R('r_role', 'назначена', 250, 230)
    L('r_role', 'role', '1'); L('r_role', 'usr', 'N')
    E('aud', 'Запись аудита', 85, 645, w=140)
    R('r_aud', 'выполняет', 85, 530, w=112)
    L('r_aud', 'usr', '1', style='entryX=0.1;entryY=1;'); L('r_aud', 'aud', 'N')
    A('aud', '№ записи', 42, 735, 'key')
    A('aud', 'действие', 85, 795)
    A('aud', 'время', 132, 735)
    E('ntf', 'Уведомление', 270, 720, w=140)
    R('r_ntf', 'получает', 230, 590, w=108)
    L('r_ntf', 'usr', '1', style='entryX=0.35;entryY=1;'); L('r_ntf', 'ntf', 'N')
    A('ntf', '№ уведомления', 200, 818, 'key')
    A('ntf', 'заголовок', 290, 865)
    A('ntf', 'прочитано', 368, 800)

    # ---------- Счёт и операция
    E('acc', 'Бонусный счёт', 640, 700)
    R('r_own', 'владеет', 640, 560, w=104)
    L('r_own', 'emp', '1'); L('r_own', 'acc', '1')
    A('acc', '№ счёта', 500, 640, 'key')
    A('acc', 'баланс', 480, 700, 'derived')
    A('acc', 'статус', 500, 760)
    E('op', 'Операция', 640, 1130)
    R('r_cont', 'содержит', 640, 920)
    L('r_cont', 'acc', '1'); L('r_cont', 'op', 'N')
    A('op', 'номер', 575, 1205, 'key')
    A('op', 'тип', 705, 1205)
    A('op', 'дата', 590, 1270)
    A('op', 'сумма', 692, 1270)
    A('op', 'статус', 641, 1330)
    R('r_rev', 'согласовывает', 490, 990, w=132)
    L('r_rev', 'usr', 'M', style='entryX=0.9;entryY=1;'); L('r_rev', 'op', 'N', style='entryX=0;entryY=0.2;')
    A('r_rev', 'решение', 340, 950)
    A('r_rev', 'комментарий', 360, 1035)
    A('r_rev', 'дата', 480, 1070)
    R('r_corr', 'корректирует', 640, 1410, w=132)
    L('r_corr', 'op', '1', pts=[(520, 1410), (520, 1172), (572, 1172)], style='exitX=0;exitY=0.5;entryX=0.06;entryY=1;', pos=0.42)
    L('r_corr', 'op', 'N', pts=[(760, 1410), (760, 1172), (708, 1172)], style='exitX=1;exitY=0.5;entryX=0.94;entryY=1;', pos=0.42)

    # ---------- Программа мотивации (снизу слева)
    E('prg', 'Программа мотивации', 200, 1170, w=190)
    R('r_pay', 'оплачивается', 420, 1130, w=130)
    L('r_pay', 'prg', '1'); L('r_pay', 'op', 'N', style='entryX=0;entryY=0.5;')
    A('prg', '№ программы', 100, 1260, 'key')
    A('prg', 'название', 220, 1270)
    A('prg', 'категория', 150, 1320)
    A('prg', 'стоимость', 290, 1320)

    # ---------- Подразделение и кампания
    E('dep', 'Подразделение', 850, 690)
    R('r_incl', 'включает', 750, 545, w=106)
    L('r_incl', 'dep', '1'); L('r_incl', 'emp', 'N')
    R('r_head', 'возглавляет', 880, 560, w=124)
    L('r_head', 'dep', '1'); L('r_head', 'emp', '1')
    A('dep', '№ подразделения', 780, 780, 'key')
    A('dep', 'название', 928, 778)
    E('cmp', 'Бонусная кампания', 880, 950, w=176)
    R('r_tgt', 'нацелена на', 858, 825, w=124)
    L('r_tgt', 'cmp', 'M'); L('r_tgt', 'dep', 'N')
    R('r_boost', 'повышает', 760, 1040, w=104)
    L('r_boost', 'cmp', '1'); L('r_boost', 'op', 'N')
    A('cmp', '№ кампании', 1010, 1030, 'key')
    A('cmp', 'коэффициент', 940, 1070)
    A('cmp', 'сроки', 770, 905)

    # ---------- KPI и правила
    E('kpi', 'Показатель KPI', 1070, 470)
    R('r_ach', 'достигает', 880, 440, w=110)
    L('r_ach', 'emp', 'M'); L('r_ach', 'kpi', 'N')
    A('r_ach', 'период', 900, 360)
    A('r_ach', 'значение, %', 1010, 370)
    A('kpi', 'код KPI', 1120, 380, 'key')
    A('kpi', 'название', 1140, 545)
    A('kpi', 'ед. изм.', 1020, 545)
    E('rule', 'Правило начисления', 1080, 740, w=180)
    R('r_meas', 'измеряет', 1080, 620, w=106)
    L('r_meas', 'kpi', '1'); L('r_meas', 'rule', 'N')
    A('rule', '№ правила', 978, 812, 'key')
    A('rule', 'вес', 1160, 830)
    A('rule', 'базовые баллы', 985, 887)
    R('r_basis', 'обосновывает', 1060, 1130, w=134)
    L('r_basis', 'rule', '1', style='entryX=0.55;entryY=1;'); L('r_basis', 'op', 'N', pts=[(840, 1130)], style='entryX=1;entryY=0.4;')

    # ---------- Бизнес-событие и интеграция
    R('r_part', 'участвует', 880, 260, w=108)
    L('r_part', 'emp', '1', style='entryX=1;entryY=0;')
    E('evt', 'Бизнес-событие', 1040, 1290, w=160)
    L('r_part', 'evt', 'N', pts=[(1220, 260), (1220, 1290)], style='exitX=1;exitY=0.5;entryX=1;entryY=0.5;', pos=0.95)
    R('r_gen', 'порождает', 880, 1250, w=110)
    L('r_gen', 'evt', '1'); L('r_gen', 'op', '1', style='entryX=1;entryY=0.75;')
    A('evt', '№ события', 905, 1345, 'key')
    A('evt', 'тип', 1040, 1205)
    A('evt', 'статус', 1150, 1205)
    A('evt', 'дата получения', 1150, 1355)
    E('int', 'Интеграция', 1040, 1510, w=150)
    R('r_sup', 'поставляет', 1040, 1395, w=110)
    L('r_sup', 'int', '1'); L('r_sup', 'evt', 'N')
    A('int', 'вид системы', 860, 1470, 'key')
    A('int', 'название', 870, 1550)
    A('int', 'URL', 1180, 1450)
    A('int', 'проверка\nсоединения', 1170, 1560, 'multi')

    # ---------- Легенда
    lx, ly = 975, 20
    d.vertex('Обозначения', lx, ly, 250, 22, f'text;html=1;fontSize=13;fontStyle=1;align=left;fontColor={INK};' + FONT)
    items = [('ключ', 'key', 'ключевой атрибут'), ('атрибут', '', 'атрибут'),
             ('атрибут', 'multi', 'многозначный атрибут'), ('атрибут', 'derived', 'производный атрибут')]
    for k, (t, kind, cap) in enumerate(items):
        st = ST_ATT + ('fontStyle=4;' if kind == 'key' else '') + ('dashed=1;dashPattern=6 4;' if kind == 'derived' else '')
        if kind == 'multi':
            st = st.replace('ellipse;', 'shape=doubleEllipse;') + 'margin=3;'
        d.vertex(t, lx, ly + 28 + k * 34, 80, 28, st)
        d.vertex(cap, lx + 90, ly + 28 + k * 34, 160, 28, f'text;html=1;fontSize=12;align=left;verticalAlign=middle;fontColor={INK};' + FONT)
    d.out = d
    return d


# =====================================================================
# Схема БД (по V1__schema.sql): таблица -> (русское имя, [(колонка, тип, ключи, NOT NULL, русское имя)])
# =====================================================================
S = {
 'role': ('Роль', [
    ('code', 'VARCHAR(20)', 'PK', 1, 'Код роли'),
    ('name', 'VARCHAR(50)', 'UK', 1, 'Название')]),
 'department': ('Подразделение', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код подразделения'),
    ('name', 'VARCHAR(100)', 'UK', 1, 'Название'),
    ('head_employee_id', 'BIGINT', 'FK, UK', 0, 'Руководитель')]),
 'kpi': ('Показатель KPI', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код показателя'),
    ('code', 'VARCHAR(30)', 'UK', 1, 'Обозначение'),
    ('name', 'VARCHAR(150)', '', 1, 'Наименование'),
    ('unit', 'VARCHAR(20)', '', 1, 'Единица измерения')]),
 'employee': ('Сотрудник', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код сотрудника'),
    ('full_name', 'VARCHAR(150)', '', 1, 'ФИО'),
    ('email', 'VARCHAR(150)', 'UK', 1, 'Email'),
    ('position', 'VARCHAR(100)', '', 1, 'Должность'),
    ('department_id', 'BIGINT', 'FK', 1, 'Подразделение'),
    ('manager_id', 'BIGINT', 'FK', 0, 'Руководитель'),
    ('hired_at', 'DATE', '', 1, 'Дата приёма'),
    ('external_id', 'VARCHAR(50)', 'UK', 0, 'Идентификатор в HRIS')]),
 'app_user': ('Учётная запись', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код учётной записи'),
    ('employee_id', 'BIGINT', 'FK, UK', 1, 'Сотрудник'),
    ('role_code', 'VARCHAR(20)', 'FK', 1, 'Роль'),
    ('password_hash', 'VARCHAR(100)', '', 1, 'Хеш пароля'),
    ('status', 'VARCHAR(10)', '', 1, 'Статус'),
    ('created_at', 'TIMESTAMPTZ', '', 1, 'Дата создания'),
    ('last_login_at', 'TIMESTAMPTZ', '', 0, 'Последний вход')]),
 'bonus_account': ('Бонусный счёт', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код счёта'),
    ('employee_id', 'BIGINT', 'FK, UK', 1, 'Сотрудник'),
    ('balance', 'INTEGER', '', 1, 'Баланс'),
    ('status', 'VARCHAR(10)', '', 1, 'Статус'),
    ('updated_at', 'TIMESTAMPTZ', '', 1, 'Дата изменения'),
    ('version', 'INTEGER', '', 1, 'Версия записи')]),
 'kpi_result': ('Результат KPI', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код результата'),
    ('employee_id', 'BIGINT', 'FK, UK1', 1, 'Сотрудник'),
    ('kpi_id', 'BIGINT', 'FK, UK1', 1, 'Показатель'),
    ('period_start', 'DATE', 'UK1', 1, 'Начало периода'),
    ('period_end', 'DATE', '', 1, 'Конец периода'),
    ('value_pct', 'NUMERIC(5,2)', '', 1, 'Значение, %'),
    ('recorded_at', 'TIMESTAMPTZ', '', 1, 'Дата записи')]),
 'accrual_rule': ('Правило начисления', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код правила'),
    ('name', 'VARCHAR(150)', '', 1, 'Название'),
    ('kpi_id', 'BIGINT', 'FK', 1, 'Показатель'),
    ('weight', 'NUMERIC(3,2)', '', 1, 'Вес'),
    ('base_points', 'INTEGER', '', 1, 'Базовые баллы'),
    ('periodicity', 'VARCHAR(10)', '', 1, 'Периодичность'),
    ('threshold', 'NUMERIC(5,2)', '', 1, 'Порог'),
    ('params', 'JSONB', '', 1, 'Параметры'),
    ('active', 'BOOLEAN', '', 1, 'Активно'),
    ('effective_from', 'DATE', '', 1, 'Действует с'),
    ('updated_at', 'TIMESTAMPTZ', '', 1, 'Дата изменения')]),
 'campaign': ('Бонусная кампания', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код кампании'),
    ('name', 'VARCHAR(150)', '', 1, 'Название'),
    ('description', 'TEXT', '', 1, 'Описание'),
    ('start_date', 'DATE', '', 1, 'Дата начала'),
    ('end_date', 'DATE', '', 1, 'Дата окончания'),
    ('coefficient', 'NUMERIC(3,2)', '', 1, 'Коэффициент'),
    ('budget', 'INTEGER', '', 0, 'Бюджет'),
    ('status', 'VARCHAR(15)', '', 1, 'Статус'),
    ('created_at', 'TIMESTAMPTZ', '', 1, 'Дата создания')]),
 'campaign_department': ('Целевое подразделение', [
    ('campaign_id', 'BIGINT', 'PK, FK', 1, 'Кампания'),
    ('department_id', 'BIGINT', 'PK, FK', 1, 'Подразделение')]),
 'program': ('Программа мотивации', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код программы'),
    ('name', 'VARCHAR(150)', 'UK', 1, 'Название'),
    ('category', 'VARCHAR(15)', '', 1, 'Категория'),
    ('description', 'TEXT', '', 1, 'Описание'),
    ('cost', 'INTEGER', '', 1, 'Стоимость, баллы'),
    ('active', 'BOOLEAN', '', 1, 'Активна'),
    ('inactive_reason', 'VARCHAR(200)', '', 0, 'Причина неактивности')]),
 'integration': ('Интеграция', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код интеграции'),
    ('kind', 'VARCHAR(15)', 'UK', 1, 'Вид системы'),
    ('name', 'VARCHAR(100)', '', 1, 'Название'),
    ('url', 'VARCHAR(255)', '', 1, 'URL'),
    ('api_key_encrypted', 'VARCHAR(512)', '', 1, 'Ключ API (шифр.)'),
    ('enabled', 'BOOLEAN', '', 1, 'Включена'),
    ('last_sync_at', 'TIMESTAMPTZ', '', 0, 'Последняя синхронизация')]),
 'integration_check': ('Проверка интеграции', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код проверки'),
    ('integration_id', 'BIGINT', 'FK', 1, 'Интеграция'),
    ('ok', 'BOOLEAN', '', 1, 'Успешно'),
    ('message', 'VARCHAR(255)', '', 1, 'Сообщение'),
    ('latency_ms', 'INTEGER', '', 0, 'Задержка, мс'),
    ('checked_at', 'TIMESTAMPTZ', '', 1, 'Время проверки')]),
 'business_event': ('Бизнес-событие', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код события'),
    ('integration_id', 'BIGINT', 'FK, UK1', 0, 'Интеграция-источник'),
    ('external_id', 'VARCHAR(100)', 'UK1', 1, 'Внешний идентификатор'),
    ('event_type', 'VARCHAR(50)', '', 1, 'Тип события'),
    ('employee_id', 'BIGINT', 'FK', 1, 'Сотрудник'),
    ('payload', 'JSONB', '', 1, 'Данные события'),
    ('status', 'VARCHAR(10)', '', 1, 'Статус обработки'),
    ('error_message', 'VARCHAR(255)', '', 0, 'Текст ошибки'),
    ('received_at', 'TIMESTAMPTZ', '', 1, 'Дата получения'),
    ('processed_at', 'TIMESTAMPTZ', '', 0, 'Дата обработки')]),
 'operation': ('Операция', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код операции'),
    ('number', 'VARCHAR(12)', 'UK', 1, 'Номер'),
    ('account_id', 'BIGINT', 'FK', 1, 'Бонусный счёт'),
    ('type', 'VARCHAR(10)', '', 1, 'Тип'),
    ('status', 'VARCHAR(12)', '', 1, 'Статус'),
    ('basis', 'VARCHAR(12)', '', 1, 'Основание'),
    ('amount', 'INTEGER', '', 1, 'Сумма, баллы'),
    ('original_amount', 'INTEGER', '', 0, 'Сумма до корректировки'),
    ('reason', 'VARCHAR(300)', '', 1, 'Причина'),
    ('rule_id', 'BIGINT', 'FK', 0, 'Правило начисления'),
    ('campaign_id', 'BIGINT', 'FK', 0, 'Кампания'),
    ('program_id', 'BIGINT', 'FK', 0, 'Программа'),
    ('event_id', 'BIGINT', 'FK, UK', 0, 'Бизнес-событие'),
    ('corrects_id', 'BIGINT', 'FK', 0, 'Корректируемая операция'),
    ('created_by', 'BIGINT', 'FK', 0, 'Автор (уч. запись)'),
    ('created_at', 'TIMESTAMPTZ', '', 1, 'Дата создания')]),
 'operation_review': ('Согласование операции', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код согласования'),
    ('operation_id', 'BIGINT', 'FK', 1, 'Операция'),
    ('reviewer_id', 'BIGINT', 'FK', 1, 'Согласующий'),
    ('decision', 'VARCHAR(10)', '', 1, 'Решение'),
    ('comment', 'VARCHAR(300)', '', 0, 'Комментарий'),
    ('decided_at', 'TIMESTAMPTZ', '', 1, 'Дата решения')]),
 'audit_log': ('Запись аудита', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код записи'),
    ('occurred_at', 'TIMESTAMPTZ', '', 1, 'Время события'),
    ('user_id', 'BIGINT', 'FK', 0, 'Пользователь'),
    ('action', 'VARCHAR(100)', '', 1, 'Действие'),
    ('object_type', 'VARCHAR(50)', '', 1, 'Тип объекта'),
    ('object_id', 'BIGINT', '', 0, 'Код объекта'),
    ('details', 'JSONB', '', 1, 'Детали'),
    ('ip', 'INET', '', 0, 'IP-адрес')]),
 'notification': ('Уведомление', [
    ('id', 'BIGSERIAL', 'PK', 1, 'Код уведомления'),
    ('user_id', 'BIGINT', 'FK', 1, 'Получатель'),
    ('title', 'VARCHAR(150)', '', 1, 'Заголовок'),
    ('text', 'VARCHAR(500)', '', 1, 'Текст'),
    ('created_at', 'TIMESTAMPTZ', '', 1, 'Дата создания'),
    ('read_at', 'TIMESTAMPTZ', '', 0, 'Дата прочтения')]),
}

# Внешние ключи: (дочерняя таблица, колонка, родительская таблица, колонка родителя)
FK = [
 ('department', 'head_employee_id', 'employee', 'id'),
 ('employee', 'department_id', 'department', 'id'),
 ('employee', 'manager_id', 'employee', 'id'),
 ('app_user', 'employee_id', 'employee', 'id'),
 ('app_user', 'role_code', 'role', 'code'),
 ('bonus_account', 'employee_id', 'employee', 'id'),
 ('kpi_result', 'employee_id', 'employee', 'id'),
 ('kpi_result', 'kpi_id', 'kpi', 'id'),
 ('accrual_rule', 'kpi_id', 'kpi', 'id'),
 ('campaign_department', 'campaign_id', 'campaign', 'id'),
 ('campaign_department', 'department_id', 'department', 'id'),
 ('integration_check', 'integration_id', 'integration', 'id'),
 ('business_event', 'integration_id', 'integration', 'id'),
 ('business_event', 'employee_id', 'employee', 'id'),
 ('operation', 'account_id', 'bonus_account', 'id'),
 ('operation', 'rule_id', 'accrual_rule', 'id'),
 ('operation', 'campaign_id', 'campaign', 'id'),
 ('operation', 'program_id', 'program', 'id'),
 ('operation', 'event_id', 'business_event', 'id'),
 ('operation', 'corrects_id', 'operation', 'id'),
 ('operation', 'created_by', 'app_user', 'id'),
 ('operation_review', 'operation_id', 'operation', 'id'),
 ('operation_review', 'reviewer_id', 'app_user', 'id'),
 ('audit_log', 'user_id', 'app_user', 'id'),
 ('notification', 'user_id', 'app_user', 'id'),
]

IX = {
 'employee': ['ix_employee_department (department_id)', 'ix_employee_manager (manager_id)'],
 'operation': ['ix_operation_account_dt (account_id, created_at DESC)',
               "ix_operation_status (status) WHERE status = 'pending'",
               'ix_operation_campaign (campaign_id)'],
 'kpi_result': ['ix_kpi_result_employee (employee_id, period_start)'],
 'business_event': ["ix_event_status (status) WHERE status <> 'processed'"],
 'audit_log': ['ix_audit_occurred (occurred_at DESC)'],
 'notification': ['ix_notification_user (user_id, read_at)'],
}

DOMAIN = {'BIGSERIAL': 'целое', 'BIGINT': 'целое', 'INTEGER': 'целое', 'TEXT': 'строка', 'INET': 'строка',
          'DATE': 'дата', 'TIMESTAMPTZ': 'дата-время', 'BOOLEAN': 'логический', 'JSONB': 'JSON'}


def domain(t):
    if t.startswith('VARCHAR'):
        return 'строка'
    if t.startswith('NUMERIC'):
        return 'десятичное'
    return DOMAIN[t]


# =====================================================================
# Таблицы (логическая и физическая модели) — вороньи лапки
# =====================================================================
HH, RHT = 30, 22                       # высота заголовка и строки
TXT = f'text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;spacingLeft=0;fontSize=12;fontColor={INK};' + FONT
GRAY = '#868E96'


class Tables(Page):
    def __init__(s, name):
        super().__init__(name)
        s.rows = {}        # (таблица, колонка) -> (id, y центра, x слева, x справа)
        s.box = {}         # таблица -> (x, y, w, h)

    def table(s, t, x, y, title, rows, cols, stub=False, footer=()):
        """rows: [(колонка, [текст по графам], ключ_PK)]; cols: ширины граф."""
        w = sum(cols)
        n = len(rows)
        fh = 8 + 17 * len(footer) if footer else 0
        h = HH + RHT * n + fh
        dash = 'dashed=1;dashPattern=6 3;' if stub else ''
        s.vertex('', x, y, w, h, f'rounded=0;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor={ENT_S};strokeWidth=1.5;' + dash, vid=t)
        s.vertex(title, x, y, w, HH, f'rounded=0;whiteSpace=wrap;html=1;fillColor={"#EDF0F7" if stub else ENT_F};'
                 f'strokeColor={ENT_S};strokeWidth=1.5;fontSize=13;fontStyle=1;fontColor={INK};' + dash + FONT)
        npk = sum(1 for r in rows if r[2])
        for i, (col, cells, pk) in enumerate(rows):
            ry = y + HH + i * RHT
            rid = s.vertex('', x, ry, w, RHT, 'rounded=0;html=1;strokeColor=none;fillColor=none;', vid=f'{t}.{col}')
            s.rows[(t, col)] = (rid, ry + RHT / 2, x, x + w)
            cx = x
            for j, (txt, cw) in enumerate(zip(cells, cols)):
                st = TXT
                if j == 0:
                    st += f'fontStyle=1;fontSize=11;fontColor={ENT_S};align=center;'
                elif pk and j == 1:
                    st += 'fontStyle=4;'
                if j >= 2:
                    st += f'fontColor={"#495057"};'
                if txt:
                    s.vertex(txt, cx + (0 if j == 0 else 2), ry, cw - 2, RHT, st)
                cx += cw
        if npk and npk < n:
            s.vertex('', x, y + HH + npk * RHT, w, 1, f'rounded=0;html=1;strokeColor={ENT_S};fillColor={ENT_S};strokeWidth=0.5;')
        if footer:
            fy = y + HH + n * RHT
            s.vertex('', x, fy, w, 1, f'rounded=0;html=1;strokeColor={GRAY};fillColor={GRAY};strokeWidth=0.5;dashed=1;')
            s.vertex('<br>'.join(html.escape(f) for f in footer), x + 6, fy + 3, w - 8, fh - 4,
                     TXT + f'fontSize=11;fontColor=#495057;verticalAlign=top;spacingTop=0;')
        s.box[t] = (x, y, w, h)

    def rel(s, child, ccol, parent, pcol, cside, pside, ch=None, c_end='ERzeroToMany', p_end='ERmandOne', via=None):
        """Связь FK -> PK. cside/pside: 'L'/'R' (бок строки) или 'T'/'B' (верх/низ таблицы)."""
        st = (f'html=1;rounded=0;edgeStyle=none;strokeColor={INK};strokeWidth=1.2;startArrow={c_end};endArrow={p_end};'
              'startFill=0;endFill=0;startSize=11;endSize=11;')
        if cside in 'TB':
            a = child; b = parent
            st += f'exitX=0.5;exitY={0 if cside == "T" else 1};exitDx={via or 0};entryX=0.5;entryY={0 if pside == "T" else 1};entryDx={via or 0};'
            return s.edge(a, b, st)
        a, ay, al, ar = s.rows[(child, ccol)]
        b, by, bl, br = s.rows[(parent, pcol)]
        st += f'exitX={0 if cside == "L" else 1};exitY=0.5;entryX={0 if pside == "L" else 1};entryY=0.5;'
        pts = [] if ch is None else [(ch, ay), (ch, by)]
        return s.edge(a, b, st, pts)

    def loop_top(s, child, ccol, parent, cside, ch, ytop, xin, c_end='ERzeroToMany', p_end='ERzeroToOne'):
        """Связь, входящая в таблицу сверху (рекурсивные FK)."""
        a, ay, al, ar = s.rows[(child, ccol)]
        px, py, pw, ph = s.box[parent]
        st = (f'html=1;rounded=0;edgeStyle=none;strokeColor={INK};strokeWidth=1.2;startArrow={c_end};endArrow={p_end};'
              f'startFill=0;endFill=0;startSize=11;endSize=11;exitX={0 if cside == "L" else 1};exitY=0.5;'
              f'entryX={(xin - px) / pw:.4f};entryY=0;')
        return s.edge(a, parent, st, [(ch, ay), (ch, ytop), (xin, ytop)])


def ends(child, ccol, parent):
    """Вид концов по физическим ограничениям: (конец у FK, конец у PK)."""
    k, nn = next((c[2], c[3]) for c in S[child][1] if c[0] == ccol)
    uniq = 'UK' in k.replace('UK1', '')
    return ('ERzeroToOne' if uniq else 'ERzeroToMany'), ('ERmandOne' if nn else 'ERzeroToOne')


def legend_tables(d, x, y, phys, w=560):
    d.vertex('Обозначения', x, y, 200, 20, f'text;html=1;fontSize=13;fontStyle=1;align=left;fontColor={INK};' + FONT)
    items = [('ERmandOne', 'ровно один'), ('ERzeroToOne', 'ноль или один'), ('ERzeroToMany', 'ноль или много')]
    for k, (m, cap) in enumerate(items):
        yy = y + 32 + k * 26
        a = d.vertex('', x, yy - 1, 1, 2, 'rounded=0;html=1;strokeColor=none;fillColor=none;')
        b = d.vertex('', x + 56, yy - 1, 1, 2, 'rounded=0;html=1;strokeColor=none;fillColor=none;')
        d.edge(a, b, f'html=1;edgeStyle=none;endArrow={m};startArrow=none;endFill=0;endSize=11;strokeColor={INK};strokeWidth=1.2;')
        d.vertex(cap, x + 66, yy - 11, 150, 22, TXT)
    keys = 'PK — первичный ключ;  FK — внешний ключ'
    if phys:
        keys += ';  UK — уникальный ключ (UK1 — составной);  NN — NOT NULL;  ix_… — индексы'
    d.vertex(keys, x, y + 110, w, 60, TXT + 'whiteSpace=wrap;fontSize=12;verticalAlign=top;')


# ---------------------------------------------------------------------
# 2. Логическая модель
# ---------------------------------------------------------------------
LCOLS = [50, 168, 88]


def logical():
    d = Tables('Логическая модель')

    def T(t, x, y):
        rn, cols = S[t]
        rows = [(c, [k.replace('UK1', '').replace('UK', '').strip(', ') if 'FK' in k or 'PK' in k else '',
                     r, domain(ty)], 'PK' in k) for c, ty, k, nn, r in cols]
        d.table(t, x, y, f'{rn}', rows, LCOLS)

    XA, XB, XC = 80, 500, 920
    T('role', XA, 40); T('app_user', XA, 164); T('integration', XA, 400); T('integration_check', XA, 630)
    T('notification', XA, 840); T('audit_log', XA, 1050); T('operation_review', XA, 1300)
    T('department', XB, 40); T('employee', XB, 186); T('business_event', XB, 440)
    T('bonus_account', XB, 740); T('operation', XB, 950)
    T('campaign_department', XC, 40); T('campaign', XC, 160); T('kpi_result', XC, 440)
    T('kpi', XC, 670); T('accrual_rule', XC, 836); T('program', XC, 1160)

    def F(c, cc, p, pc, cs, ps, ch=None, **kw):
        ce, pe = ends(c, cc, p)
        kw.setdefault('c_end', ce); kw.setdefault('p_end', pe)
        d.rel(c, cc, p, pc, cs, ps, ch, **kw)

    # вертикальные связи соседних таблиц
    F('app_user', 'role_code', 'role', 'code', 'T', 'B')
    F('integration_check', 'integration_id', 'integration', 'id', 'T', 'B')
    F('business_event', 'employee_id', 'employee', 'id', 'T', 'B')
    F('operation', 'account_id', 'bonus_account', 'id', 'T', 'B')
    F('campaign_department', 'campaign_id', 'campaign', 'id', 'B', 'T')
    F('kpi_result', 'kpi_id', 'kpi', 'id', 'B', 'T')
    F('accrual_rule', 'kpi_id', 'kpi', 'id', 'T', 'B')
    # левый край колонки A
    F('notification', 'user_id', 'app_user', 'id', 'L', 'L', 58)
    F('audit_log', 'user_id', 'app_user', 'id', 'R', 'R', 418)
    F('operation_review', 'reviewer_id', 'app_user', 'id', 'L', 'L', 34)
    # промежуток A–B
    F('app_user', 'employee_id', 'employee', 'id', 'R', 'L', 440)
    F('business_event', 'integration_id', 'integration', 'id', 'L', 'R', 440)
    F('operation_review', 'operation_id', 'operation', 'id', 'R', 'L', 420)
    F('operation', 'created_by', 'app_user', 'id', 'L', 'R', 405)
    F('department', 'head_employee_id', 'employee', 'id', 'B', 'T', c_end='ERzeroToOne')
    F('employee', 'department_id', 'department', 'id', 'L', 'L', 470)
    d.loop_top('operation', 'corrects_id', 'operation', 'L', 478, 928, 560)
    F('operation', 'event_id', 'business_event', 'id', 'L', 'L', 455)
    # промежуток B–C
    d.loop_top('employee', 'manager_id', 'employee', 'R', 830, 164, 760)
    F('bonus_account', 'employee_id', 'employee', 'id', 'R', 'R', 850)
    F('kpi_result', 'employee_id', 'employee', 'id', 'L', 'R', 870)
    F('campaign_department', 'department_id', 'department', 'id', 'L', 'R', 870)
    F('operation', 'rule_id', 'accrual_rule', 'id', 'R', 'L', 860)
    F('operation', 'program_id', 'program', 'id', 'R', 'L', 880)
    F('operation', 'campaign_id', 'campaign', 'id', 'R', 'L', 895)
    legend_tables(d, XA, 1490, False)
    return d


# ---------------------------------------------------------------------
# 3. Физическая модель (PostgreSQL) — два листа по подсистемам
# ---------------------------------------------------------------------
PCOLS = [58, 140, 104, 34]


def phys_table(d, t, x, y, stub_of=None):
    rn, cols = S[t]
    if stub_of:
        rows = [(c, [k, c, ty, 'NN' if nn else ''], True) for c, ty, k, nn, r in cols if 'PK' in k]
        rows.append(('_more', ['', '…', '', ''], False))
        d.table(t, x, y, f'{t}  (см. {stub_of})', rows, PCOLS, stub=True)
        return
    rows = [(c, [k, c, ty, 'NN' if nn else ''], 'PK' in k) for c, ty, k, nn, r in cols]
    d.table(t, x, y, f'{t} ({rn[0].lower() + rn[1:]})', rows, PCOLS, footer=IX.get(t, ()))


def physical1():
    d = Tables('Физическая модель, лист 1')
    T = lambda t, x, y, st=None: phys_table(d, t, x, y, st)
    XA, XB, XC = 50, 474, 898
    T('role', XA, 40); T('app_user', XA, 164); T('operation_review', XA, 700)
    T('department', XB, 40); T('employee', XB, 186); T('bonus_account', XB, 480); T('operation', XB, 690)
    T('accrual_rule', XC, 700, 'лист 2'); T('campaign', XC, 800, 'лист 2')
    T('program', XC, 900, 'лист 2'); T('business_event', XC, 1000, 'лист 2')

    def F(c, cc, p, pc, cs, ps, ch=None, **kw):
        ce, pe = ends(c, cc, p)
        kw.setdefault('c_end', ce); kw.setdefault('p_end', pe)
        d.rel(c, cc, p, pc, cs, ps, ch, **kw)

    F('app_user', 'role_code', 'role', 'code', 'T', 'B')
    F('department', 'head_employee_id', 'employee', 'id', 'B', 'T')
    F('bonus_account', 'employee_id', 'employee', 'id', 'T', 'B')
    F('operation', 'account_id', 'bonus_account', 'id', 'T', 'B')
    F('app_user', 'employee_id', 'employee', 'id', 'R', 'L', 430)
    F('employee', 'department_id', 'department', 'id', 'L', 'L', 450)
    d.loop_top('employee', 'manager_id', 'employee', 'R', 830, 164, 740)
    F('operation_review', 'operation_id', 'operation', 'id', 'R', 'L', 430)
    F('operation_review', 'reviewer_id', 'app_user', 'id', 'L', 'L', 24)
    F('operation', 'created_by', 'app_user', 'id', 'L', 'R', 410)
    d.loop_top('operation', 'corrects_id', 'operation', 'L', 450, 668, 540)
    F('operation', 'rule_id', 'accrual_rule', 'id', 'R', 'L', 824)
    F('operation', 'campaign_id', 'campaign', 'id', 'R', 'L', 840)
    F('operation', 'program_id', 'program', 'id', 'R', 'L', 856)
    F('operation', 'event_id', 'business_event', 'id', 'R', 'L', 866)
    legend_tables(d, XA, 900, True, 330)
    return d


def physical2():
    d = Tables('Физическая модель, лист 2')
    T = lambda t, x, y, st=None: phys_table(d, t, x, y, st)
    XA, XB, XC = 50, 474, 898
    T('employee', XA, 40, 'лист 1'); T('kpi_result', XA, 170); T('kpi', XA, 435); T('accrual_rule', XA, 609)
    T('program', XA, 935)
    T('department', XB, 40, 'лист 1'); T('campaign_department', XB, 170); T('campaign', XB, 300)
    T('business_event', XB, 580)
    T('integration', XC, 40); T('integration_check', XC, 280); T('app_user', XC, 494, 'лист 1')
    T('audit_log', XC, 624); T('notification', XC, 910)

    def F(c, cc, p, pc, cs, ps, ch=None, **kw):
        ce, pe = ends(c, cc, p)
        kw.setdefault('c_end', ce); kw.setdefault('p_end', pe)
        d.rel(c, cc, p, pc, cs, ps, ch, **kw)

    F('kpi_result', 'employee_id', 'employee', 'id', 'T', 'B')
    F('kpi_result', 'kpi_id', 'kpi', 'id', 'B', 'T')
    F('accrual_rule', 'kpi_id', 'kpi', 'id', 'T', 'B')
    F('campaign_department', 'department_id', 'department', 'id', 'T', 'B')
    F('campaign_department', 'campaign_id', 'campaign', 'id', 'B', 'T')
    F('integration_check', 'integration_id', 'integration', 'id', 'T', 'B')
    F('audit_log', 'user_id', 'app_user', 'id', 'T', 'B')
    F('business_event', 'employee_id', 'employee', 'id', 'L', 'R', 430)
    F('business_event', 'integration_id', 'integration', 'id', 'R', 'L', 845)
    F('notification', 'user_id', 'app_user', 'id', 'L', 'L', 868)
    legend_tables(d, XB, 910, True, 330)
    return d


pages = [conceptual(), logical(), physical1(), physical2()]

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'er-model.drawio'
    open(out, 'w', encoding='utf8').write('<mxfile host="Electron">' + ''.join(p.xml(k) for k, p in enumerate(pages)) + '</mxfile>')
    print('ok', len(pages))
