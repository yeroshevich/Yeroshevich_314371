# Генератор UML-диаграмм (draw.io) для ЛР9 «Реализация программного решения» (REST API серверной части)
# Вариант 46, ПС «TechnoSoft Bonus». Источник правды — код backend/src/main/java/by/technosoft/bonus/ (web/**, service/**)
# Запуск: python3 uml_lr9.py uml-lr9.drawio
# Экспорт: draw.io -x -f png -s 2 -b 10 -p <N с 1> -o out.png uml-lr9.drawio
import sys, html
from PIL import ImageFont

INK = '#1F2430'
CF, CS = '#DBE4FF', '#3B5BDB'      # классы / компоненты
NF, NS = '#FFF3BF', '#F08C00'      # примечания
GR = '#5C6370'                     # рамки паттернов, линии жизни
FONT = 'fontFamily=Helvetica;'

_fonts = {}


def tw(s, fs=12, bold=False):
    k = (fs, bold)
    if k not in _fonts:
        _fonts[k] = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', fs, index=1 if bold else 0)
    return _fonts[k].getlength(s)


def esc(t):
    return html.escape(t, quote=True).replace('\n', '&#xa;')


def h(t):
    n = len(t) - len(t.lstrip(' '))
    return '\u00a0' * n + html.escape(t[n:], quote=False)


def hl(lines):
    return '<br>'.join(h(x) for x in lines)


def wrap(text, width, fs=11):
    out = []
    for para in text.split('\n'):
        cur = ''
        for w in para.split(' '):
            t = (cur + ' ' + w).strip()
            if tw(t, fs) <= width or not cur:
                cur = t
            else:
                out.append(cur)
                cur = w
        out.append(cur)
    return out


class Page:
    def __init__(s, name):
        s.name, s.cells, s.n, s.g, s.back = name, [], 0, {}, []

    def nid(s, p='c'):
        s.n += 1
        return f'{p}{s.n}'

    def v(s, value, x, y, w, hh, style, parent='1', back=False, rel=False):
        i = s.nid()
        c = (f'<mxCell id="{i}" value="{esc(value)}" style="{style}" vertex="1" parent="{parent}">'
             f'<mxGeometry x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{hh:.1f}" as="geometry"/></mxCell>')
        (s.back if back else s.cells).append(c)
        if not rel:
            s.g[i] = (x, y, w, hh)
        return i

    def e(s, style, a=None, b=None, sp=None, tp=None, pts=(), back=False):
        """Ребро; sp/tp — абсолютные точки на границах a/b (или свободные концы)."""
        i = s.nid('e')
        st = style
        if a is not None and sp is not None:
            x, y, w, hh = s.g[a]
            st += f'exitX={(sp[0]-x)/w:.4f};exitY={(sp[1]-y)/hh:.4f};exitDx=0;exitDy=0;exitPerimeter=0;'
        if b is not None and tp is not None:
            x, y, w, hh = s.g[b]
            st += f'entryX={(tp[0]-x)/w:.4f};entryY={(tp[1]-y)/hh:.4f};entryDx=0;entryDy=0;entryPerimeter=0;'
        geo = ''
        if a is None and sp is not None:
            geo += f'<mxPoint x="{sp[0]:.1f}" y="{sp[1]:.1f}" as="sourcePoint"/>'
        if b is None and tp is not None:
            geo += f'<mxPoint x="{tp[0]:.1f}" y="{tp[1]:.1f}" as="targetPoint"/>'
        if pts:
            geo += '<Array as="points">' + ''.join(f'<mxPoint x="{x:.1f}" y="{y:.1f}"/>' for x, y in pts) + '</Array>'
        src = f' source="{a}"' if a else ''
        tgt = f' target="{b}"' if b else ''
        c = (f'<mxCell id="{i}" value="" style="{st}" edge="1" parent="1"{src}{tgt}>'
             f'<mxGeometry relative="1" as="geometry">{geo}</mxGeometry></mxCell>')
        (s.back if back else s.cells).append(c)
        return i

    def t(s, lines, x, y, fs=11, align='left', bold=False, bg='#FFFFFF', anchor='tl', italic=False):
        """Текстовая подпись; lines — строка или список строк. anchor: tl, tc, tr, bl, bc, br, cl, cc, cr."""
        if isinstance(lines, str):
            lines = lines.split('\n')
        w = max(tw(l, fs, bold) for l in lines) + 6
        hh = len(lines) * fs * 1.25 + 4
        ax = {'l': 0, 'c': 0.5, 'r': 1}[anchor[1]]
        ay = {'t': 0, 'c': 0.5, 'b': 1}[anchor[0]]
        x0, y0 = x - w * ax, y - hh * ay
        style = (f'text;html=1;align={align};verticalAlign=middle;whiteSpace=nowrap;spacing=0;spacingLeft=3;spacingRight=3;'
                 f'fontSize={fs};fontColor={INK};{FONT}' + (f'labelBackgroundColor={bg};' if bg else '') +
                 ('fontStyle=1;' if bold else '') + ('fontStyle=2;' if italic else ''))
        s.v('<br>'.join(h(l) for l in lines), x0, y0, w, hh, style)
        return (x0, y0, w, hh)

    def note(s, text, x, y, w, fs=11, lines=None):
        lines = lines or wrap(text, w - 26, fs)
        hh = len(lines) * fs * 1.25 + 14
        st = (f'shape=note;size=12;html=1;whiteSpace=nowrap;fillColor={NF};strokeColor={NS};fontSize={fs};align=left;'
              f'verticalAlign=top;spacingLeft=7;spacingTop=4;spacingRight=12;fontColor={INK};{FONT}')
        return s.v(hl(lines), x, y, w, hh, st)

    def frame(s, label, x, y, w, hh, fs=13):
        tabw = tw(label, fs, True) + 30
        st = (f'shape=umlFrame;html=1;width={tabw:.0f};height=26;verticalAlign=top;align=left;spacingLeft=8;spacingTop=3;'
              f'fontSize={fs};fontStyle=1;fontColor={INK};strokeColor={GR};dashed=1;dashPattern=8 4;fillColor=none;'
              f'swimlaneFillColor=none;{FONT}')
        return s.v(h(label), x, y, w, hh, st, back=True)

    def xml(s, k):
        return (f'<diagram id="p{k}" name="{esc(s.name)}"><mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" '
                f'tooltips="1" connect="1" arrows="1" fold="1" page="0" pageScale="1" pageWidth="1200" pageHeight="1700" '
                f'math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>' + ''.join(s.back) + ''.join(s.cells) +
                '</root></mxGraphModel></diagram>')

    # точки на сторонах
    def R(s, i, f=0.5): x, y, w, hh = s.g[i]; return (x + w, y + hh * f)
    def L(s, i, f=0.5): x, y, w, hh = s.g[i]; return (x, y + hh * f)
    def T(s, i, f=0.5): x, y, w, hh = s.g[i]; return (x + w * f, y)
    def B(s, i, f=0.5): x, y, w, hh = s.g[i]; return (x + w * f, y + hh)
    def bottom(s, i): x, y, w, hh = s.g[i]; return y + hh
    def right(s, i): x, y, w, hh = s.g[i]; return x + w


# ---------- стили связей ----------
EB = f'html=1;rounded=0;edgeStyle=none;strokeColor={INK};strokeWidth=1.2;endSize=10;startSize=10;'
ASSOC = EB + 'endArrow=open;endFill=0;'
DEP = EB + 'endArrow=open;endFill=0;dashed=1;dashPattern=6 4;'
REAL = EB + 'endArrow=block;endFill=0;endSize=14;dashed=1;dashPattern=6 4;'
GEN = EB + 'endArrow=block;endFill=0;endSize=14;'
AGGR = EB + 'startArrow=diamondThin;startFill=0;startSize=18;endArrow=open;endFill=0;'
COMP = EB + 'startArrow=diamondThin;startFill=1;startSize=18;endArrow=open;endFill=0;'
LINE = EB + 'endArrow=none;'

# ---------- класс UML ----------
LH = 15


def cls(p, x, y, name, stereo=None, attrs=None, ops=None, w=None, dashed=False, minw=0, fs=12):
    """attrs/ops: список строк; строка, начинающаяся с '_', — статический член (подчёркнут)."""
    secs = [sec for sec in (attrs, ops) if sec is not None]
    head = ([f'«{stereo}»'] if stereo else []) + [name]
    hh = 10 + 15 * len(head)
    widths = [tw(name, fs, True)] + [tw(f'«{stereo}»', fs)] * bool(stereo)
    widths += [tw(it.lstrip('_'), fs) for sec in secs for it in sec]
    W = w or max(minw, int(max(widths) + 22))
    H = hh + sum(LH * len(sec) + 8 for sec in secs)
    st = (f'swimlane;html=1;fontStyle=0;horizontal=1;startSize={hh};collapsible=0;fillColor={CF};strokeColor={CS};'
          f'strokeWidth=1.5;swimlaneFillColor=#FFFFFF;fontSize={fs};fontColor={INK};verticalAlign=middle;{FONT}' +
          ('dashed=1;dashPattern=8 4;' if dashed else ''))
    val = (f'«{h(stereo)}»<br>' if stereo else '') + f'<b>{h(name)}</b>'
    cid = p.v(val, x, y, W, H, st)
    cy = hh
    for k, sec in enumerate(secs):
        sh = LH * len(sec) + 8
        if k > 0:
            p.v('', 0, cy - 1, W, 2, f'line;strokeWidth=1.5;fillColor=none;strokeColor={CS};', parent=cid, rel=True)
        txt = '<br>'.join((f'<u>{h(it[1:])}</u>' if it.startswith('_') else h(it)) for it in sec)
        p.v(txt, 0, cy, W, sh, f'text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=top;spacingLeft=8;'
            f'spacingTop=1;whiteSpace=nowrap;overflow=visible;fontSize={fs};fontColor={INK};{FONT}', parent=cid, rel=True)
        cy += sh
    return cid


def hv(p, style, a, b, ya=None, yb=None, cx=None):
    """Связь из правой/левой стороны a в противоположную сторону b с вертикальным участком в канале cx."""
    ax, ay, aw, ah = p.g[a]
    bx, by, bw, bh = p.g[b]
    lo, hi = max(ay, by) + 12, min(ay + ah, by + bh) - 12
    if ya is None and yb is None and lo <= hi:
        ya = yb = (lo + hi) / 2
    ya = ay + ah / 2 if ya is None else ya
    yb = by + bh / 2 if yb is None else yb
    if ax + aw <= bx:
        sp, tp = (ax + aw, ya), (bx, yb)
    else:
        sp, tp = (ax, ya), (bx + bw, yb)
    if abs(ya - yb) < 0.5:
        return p.e(style, a, b, sp, tp)
    cx = cx if cx is not None else (sp[0] + tp[0]) / 2
    return p.e(style, a, b, sp, tp, [(cx, ya), (cx, yb)])


def fix_geometry(p):
    """Переписать x/y/width верхнеуровневых вершин по p.g (после выравнивания колонок)."""
    import re
    out = []
    for c in p.cells:
        m = re.match(r'<mxCell id="(c\d+)"[^>]*parent="1"', c)
        if m and m.group(1) in p.g and 'vertex="1"' in c:
            x, y, w, hh = p.g[m.group(1)]
            c = re.sub(r'<mxGeometry x="[^"]*" y="[^"]*" width="[^"]*" height="[^"]*"',
                       f'<mxGeometry x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{hh:.1f}"', c, count=1)
            # ширина дочерних строк класса
            cid = m.group(1)
        out.append(c)
    # дочерние ячейки (строки класса) — подогнать ширину под контейнер
    res = []
    for c in out:
        m = re.search(r'parent="(c\d+)"', c)
        if m and m.group(1) in p.g and 'vertex="1"' in c and m.group(1) != '1':
            w = p.g[m.group(1)][2]
            c = re.sub(r'(<mxGeometry x="[^"]*" y="[^"]*" )width="[^"]*"', rf'\g<1>width="{w:.1f}"', c, count=1)
        res.append(c)
    p.cells = res

def node(p, label, x, y, w, hh, stereo='device', fill='#FFFFFF', depth=14):
    st = (f'shape=cube;html=1;whiteSpace=nowrap;boundedLbl=1;size={depth};direction=south;fillColor={fill};'
          f'strokeColor={INK};strokeWidth=1.5;verticalAlign=top;align=left;spacingLeft=8;spacingTop=4;fontSize=12;'
          f'fontColor={INK};{FONT}')
    return p.v(f'«{h(stereo)}»<br><b>{h(label)}</b>', x, y, w, hh, st)


def artifact(p, name, x, y, w, hh, sub=None):
    st = (f'rounded=0;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize=12;'
          f'fontColor={INK};align=left;verticalAlign=middle;spacingLeft=10;{FONT}')
    i = p.v(f'«artifact» <b>{h(name)}</b>' + (f'<br>{hl(sub)}' if sub else ''), x, y, w, hh, st)
    p.v('', x + w - 22, y + 7, 13, 17, f'shape=note;size=5;html=1;fillColor=#FFFFFF;strokeColor={CS};')
    return i




def ell(p, text, cx, cy, w=210, hh=56, fs=12):
    st = (f'ellipse;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize={fs};'
          f'fontColor={INK};align=center;verticalAlign=middle;perimeter=ellipsePerimeter;{FONT}')
    return p.v(hl(text.split('\n')), cx - w / 2, cy - hh / 2, w, hh, st)


def actor(p, label, cx, cy, fs=12):
    """Фигура актора 30×60 с центром (cx, cy); подпись — отдельной надписью под фигурой."""
    st = f'shape=umlActor;html=1;outlineConnect=0;fillColor=#FFFFFF;strokeColor={INK};strokeWidth=1.5;'
    i = p.v('', cx - 15, cy - 30, 30, 60, st)
    lines = label.split('\n')
    p.t(lines, cx, cy + 34, fs=fs, bold=True, anchor='tc', align='center', bg=None)
    return i


# =====================================================================
# 1. Диаграмма вариантов использования серверной части
# =====================================================================
UC_TEXT = {
    'login': 'Войти в систему',
    'uc1': 'Просмотреть баланс\n(UC1)',
    'uc2': 'Просмотреть историю\nопераций (UC2)',
    'uc3': 'Использовать бонусы\n(UC3)',
    'uc4': 'Начислить\nавтоматически (UC4)',
    'uc5': 'Начислить вручную\n(UC5)',
    'uc6': 'Рассчитать бонус\n(UC6)',
    'uc7': 'Скорректировать\nначисление (UC7)',
    'uc8': 'Настроить правила\n(UC8)',
    'uc9': 'Управлять кампаниями\n(UC9)',
    'uc10': 'Сформировать отчёт\n(UC10)',
    'uc11': 'Анализировать\nэффективность (UC11)',
    'uc12': 'Управлять\nпользователями (UC12)',
    'uc13': 'Настроить\nинтеграции (UC13)',
    'approve': 'Согласовать или\nзаблокировать начисление',
    'audit': 'Просмотреть\nжурнал аудита',
    'journal': 'Просмотреть\nжурнал операций',
}
ACTOR_TEXT = {'E': 'Сотрудник', 'M': 'Руководитель', 'H': 'HR-специалист', 'F': 'Финансовый\nотдел',
              'A': 'Администратор', 'S': 'Планировщик\n(по расписанию)'}
UC_ASSOC = {
    'E': ['login', 'uc1', 'uc2', 'uc3'],
    'M': ['uc5', 'uc6', 'uc7', 'uc11'],
    'H': ['uc5', 'uc6', 'uc7', 'uc8', 'uc9', 'uc10', 'uc11', 'journal'],
    'F': ['login', 'approve', 'uc10', 'journal'],
    'A': ['login', 'uc12', 'uc13', 'audit'],
    'S': ['uc4'],
}


def usecase_diagram(layout):
    p = Page('Варианты использования (REST API)')
    P, Y0 = UC_P, UC_Y0
    colx = UC_COLX
    uc_pos, ac_row = layout
    top = 20
    bx0, bx1 = 175, 1025
    ys = [Y0 + P * r for (_, r) in uc_pos.values()]
    bottom = max(ys) + 60
    p.v('', bx0, top, bx1 - bx0, bottom - top,
        f'rounded=0;html=1;fillColor=none;strokeColor={INK};strokeWidth=1.5;', back=True)
    p.t('Серверная часть TechnoSoft Bonus (REST API)', (bx0 + bx1) / 2, top + 6, fs=14, bold=True, anchor='tc',
        align='center', bg=None)
    E = {}
    for k, (c, r) in uc_pos.items():
        E[k] = ell(p, UC_TEXT[k], colx[c], Y0 + P * r)
    A = {}
    for a, r in ac_row.items():
        x = UC_LX if a in UC_LEFT else UC_RX
        A[a] = actor(p, ACTOR_TEXT[a], x, Y0 + P * r)
    # ассоциации
    for a, lst in UC_ASSOC.items():
        for u in lst:
            p.e(LINE, A[a], E[u])
    # «include»
    for u, v in (('uc5', 'uc6'), ('uc4', 'uc6')):
        p.e(DEP, E[u], E[v])
        x1, y1 = colx[uc_pos[u][0]], Y0 + P * uc_pos[u][1]
        x2, y2 = colx[uc_pos[v][0]], Y0 + P * uc_pos[v][1]
        if abs(y1 - y2) < 1:
            p.t('«include»', (x1 + x2) / 2, y1 - 4, fs=12, anchor='bc', align='center', bg=None)
        else:
            p.t('«include»', x1 - 8, (y1 + y2) / 2, fs=12, anchor='cr', align='right', bg=None)
    # обобщения: Руководитель и HR-специалист → Сотрудник (ортогональные линии вне границы системы)
    ex, ey = UC_LX, Y0 + P * ac_row['E']
    for a in ('M', 'H'):
        ay_ = Y0 + P * ac_row[a]
        if a in UC_LEFT:
            p.e(GEN, A[a], A['E'], (UC_LX - 15, ay_), (UC_LX - 15, ey), [(UC_LX - 60, ay_), (UC_LX - 60, ey)])
        else:
            p.e(GEN, A[a], A['E'], (UC_RX + 15, ay_), (ex, ey - 30),
                [(UC_RX + 62, ay_), (UC_RX + 62, 4), (ex, 4)])
    # примечания
    ny = bottom + 16
    p.note('Все варианты использования, кроме «Войти в систему», вызываются с токеном JWT '
           '(заголовок Authorization: Bearer); роль проверяет @PreAuthorize (Roles.*): без токена — 401, '
           'без нужной роли — 403.', 10, ny, 590, fs=12)
    p.note('Руководитель и HR-специалист также просматривают правила и кампании (GET /rules, /campaigns), '
           'изменяет их только HR-специалист; руководитель начисляет и корректирует только своей команде. '
           'Начисление выше лимита получает статус pending и попадает на согласование финансовому отделу.',
           610, ny, 580, fs=12)
    return p, (bx0, bx1, bottom)


UC_INCL_LABELS = []
UC_INCL_OFFS = [(0, 0), (0, 0)]
UC_P, UC_Y0 = 84, 95
UC_COLX = [335, 600, 865]
UC_LX, UC_RX = 85, 1110


# =====================================================================
# 2. Диаграммы классов API-слоя
# =====================================================================
def cls_size(name, stereo=None, attrs=None, ops=None, fs=12):
    secs = [sec for sec in (attrs, ops) if sec is not None]
    head = ([f'«{stereo}»'] if stereo else []) + [name]
    hh = 10 + 15 * len(head)
    widths = [tw(name, fs, True)] + [tw(f'«{stereo}»', fs)] * bool(stereo)
    widths += [tw(it.lstrip('_'), fs) for sec in secs for it in sec]
    return int(max(widths) + 22), hh + sum(LH * len(sec) + 8 for sec in secs)


C = '        '   # отступ продолжения сигнатуры

SPEC = {
    # --- контроллеры
    'AccountController': ('controller', None, ['+ summary(me: AuthenticatedUser)', C + ': AccountSummaryDto',
                                               '+ history(me, from, to, type)', C + ': List<OperationDto>']),
    'AccrualController': ('controller', None, ['+ preview(me, request:', C + 'AccrualPreviewRequestDto)',
                                               C + ': AccrualPreviewDto',
                                               '+ create(me, request: AccrualRequestDto)', C + ': AccrualResultDto']),
    'RuleController': ('controller', None, ['+ list(): List<RuleDto>', '+ create(me, request: RuleRequest)',
                                            C + ': RuleSaveResultDto', '+ update(me, id: Long,',
                                            C + 'request: RuleRequest): RuleSaveResultDto']),
    'UserController': ('controller', None, ['+ list(email: String): List<UserDto>',
                                            '+ create(me, request: UserRequest): UserDto',
                                            '+ update(me, id: Long, request: UserRequest)', C + ': UserDto',
                                            '+ setStatus(me, id: Long,', C + 'request: UserStatusRequest): UserDto']),
    # --- сервисы
    'AccountQueryService': ('service', None, ['+ overview(employeeId: Long): AccountOverview',
                                              '+ state(employeeId: Long): AccountState',
                                              '+ history(employeeId, from, to, operationType)',
                                              C + ': List<OperationEntry>']),
    'OperationQueryService': ('service', None, ['+ search(filter: OperationFilter)', C + ': List<OperationEntry>',
                                                '+ getAll(ids: Collection<Long>)', C + ': List<OperationEntry>',
                                                '+ controlQueue(): List<OperationEntry>']),
    'AccrualService': ('service', None, ['+ preview(actorUserId: Long,', C + 'request: AccrualRequest): AccrualPreview',
                                         '+ create(actorUserId: Long,', C + 'request: AccrualRequest): AccrualResult']),
    'RuleQueryService': ('service', None, ['+ list(): List<RuleEntry>', '+ get(ruleId: Long): RuleEntry',
                                           '+ usedInCurrentPeriod(rule: AccrualRule)', C + ': boolean']),
    'RuleAdminService': ('service', None, ['+ create(actorUserId, command: RuleCommand)', C + ': RuleSaveOutcome',
                                           '+ update(actorUserId, ruleId, command)', C + ': RuleSaveOutcome']),
    'UserQueryService': ('service', None, ['+ list(email: String): List<AppUser>', '+ get(id: Long): AppUser']),
    'UserAdminService': ('service', None, ['+ create(actorUserId, command: UserCommand)', C + ': AppUser',
                                           '+ update(actorUserId, userId, command): AppUser',
                                           '+ setStatus(actorUserId, userId, status)', C + ': AppUser']),
    # --- мапперы
    'AccountMapper': ('component', None, ['+ toDto(overview: AccountOverview)', C + ': AccountSummaryDto',
                                          '+ toDto(state: AccountState)', C + ': BonusAccountDto']),
    'OperationMapper': ('component', None, ['+ toDto(entry: OperationEntry)', C + ': OperationDto',
                                            '+ toDtos(entries): List<OperationDto>',
                                            '+ toCommand(dto: CorrectionRequestDto)', C + ': CorrectionRequest',
                                            '+ toFilter(employeeId, departmentId,', C + 'type, statuses, from, to, q)',
                                            C + ': OperationFilter']),
    'AccrualMapper': ('component', None, ['+ toCommand(dto: AccrualRequestDto)', C + ': AccrualRequest',
                                          '+ toDto(preview: AccrualPreview)', C + ': AccrualPreviewDto',
                                          '+ toDto(result: AccrualResult,', C + 'operations): AccrualResultDto']),
    'RuleMapper': ('component', None, ['+ toDto(entry: RuleEntry): RuleDto', '+ toDto(outcome: RuleSaveOutcome)',
                                       C + ': RuleSaveResultDto', '+ toCommand(dto: RuleRequest)', C + ': RuleCommand']),
    'UserMapper': ('component', None, ['+ toDto(user: AppUser): UserDto', '+ toCommand(dto: UserRequest)',
                                       C + ': UserCommand']),
    'ApiValues': ('utility', None, ['_+ id(id: Long): String', '_+ code(value: PersistentEnum): String',
                                    '_+ parse(type: Class<E>, code: String): E',
                                    '_+ number(value: BigDecimal): BigDecimal']),
    # --- DTO
    'AccountSummaryDto': ('dto', ['+ account: BonusAccountDto', '+ accruedThisMonth: int', '+ spentThisMonth: int',
                                  '+ recent: List<OperationDto>', '+ history: List<MonthBalanceDto>'], None),
    'OperationDto': ('dto', ['+ id: String', '+ number: String', '+ employeeId: String', '+ type: String',
                             '+ amount: int', '+ status: String', '+ basis: String', '+ createdAt: OffsetDateTime',
                             '+ disputed: boolean'], None),
    'AccrualRequestDto': ('dto', ['+ employeeIds: List<Long>', '+ amount: int', '+ basis: String', '+ reason: String',
                                  '+ ruleId: Long', '+ campaignId: Long'], None),
    'AccrualResultDto': ('dto', ['+ operations: List<OperationDto>', '+ accruedCount: int', '+ pendingCount: int'],
                         None),
    'RuleRequest': ('dto', ['+ name: String', '+ kpi: String', '+ weight: BigDecimal', '+ basePoints: int',
                            '+ periodicity: String', '+ threshold: BigDecimal', '+ active: boolean'], None),
    'RuleSaveResultDto': ('dto', ['+ rule: RuleDto', '+ effectiveFrom: LocalDate', '+ deferred: boolean'], None),
    'UserRequest': ('dto', ['+ fullName: String', '+ email: String', '+ departmentId: Long', '+ position: String',
                            '+ role: String'], None),
    'UserDto': ('dto', ['+ id: String', '+ fullName: String', '+ email: String', '+ role: String',
                        '+ status: String', '+ lastLoginAt: OffsetDateTime'], None),
    # --- лист 2: ошибки
    'GlobalExceptionHandler': ('component', None, [
        '+ domain(e: BonusDomainException, request)', C + ': ResponseEntity<ApiErrorResponse>',
        '+ invalidBody(e: MethodArgumentNotValidException,', C + 'request): ResponseEntity<ApiErrorResponse>',
        '+ accessDenied(e: AccessDeniedException, request)', C + ': ResponseEntity<ApiErrorResponse>',
        '+ concurrent(e, request)', '+ unexpected(e: Exception, request)',
        C + ': ResponseEntity<ApiErrorResponse>', '(всего 15 обработчиков @ExceptionHandler)']),
    'ErrorResponseFactory': ('component', ['- objectMapper: ObjectMapper', '- clock: Clock'], [
        '+ entity(code: ErrorCode, message: String,', C + 'request): ResponseEntity<ApiErrorResponse>',
        '+ entity(status: HttpStatus, code: String,', C + 'message, request)',
        '+ write(response, code: ErrorCode, message,', C + 'request)']),
    'DomainErrorStatus': ('component', ['- statuses: Map<Class<? extends', C + 'BonusDomainException>, HttpStatus>'],
                          ['+ of(exception: BonusDomainException)', C + ': HttpStatus']),
    'ApiErrorResponse': ('dto', ['+ status: int', '+ code: String', '+ message: String', '+ path: String',
                                 '+ timestamp: OffsetDateTime'], None),
    'ErrorCode': ('enumeration', ['MALFORMED_REQUEST', 'UNAUTHORIZED', 'BAD_CREDENTIALS', 'ACCESS_DENIED',
                                  'USER_BLOCKED', 'NOT_FOUND', 'METHOD_NOT_ALLOWED', 'CONFLICT', 'EMAIL_EXISTS',
                                  'RULE_EXISTS', 'INSUFFICIENT_FUNDS', 'PROGRAM_INACTIVE',
                                  'OPERATION_STATE_CONFLICT', 'CONCURRENT_MODIFICATION', 'UNSUPPORTED_MEDIA_TYPE',
                                  'VALIDATION_FAILED', 'INVALID_CALCULATION_INPUT', 'ACCOUNT_BLOCKED',
                                  'INTERNAL_ERROR'],
                  ['+ status(): HttpStatus', '+ description(): String']),
    # --- лист 2: безопасность
    'SecurityConfig': ('configuration', None, ['+ apiSecurity(http, tokenService,', C + 'authenticationService,',
                                               C + 'entryPoint, accessDeniedHandler)', C + ': SecurityFilterChain',
                                               '+ passwordEncoder(): PasswordEncoder',
                                               '+ corsConfigurationSource(properties)',
                                               C + ': CorsConfigurationSource']),
    'JwtAuthenticationFilter': (None, ['- tokenService: TokenService',
                                       '- authenticationService: AuthenticationService'],
                                ['# doFilterInternal(request, response, chain)',
                                 '_+ bearerToken(request): Optional<String>']),
    'TokenService': ('interface', None, ['+ issue(user: AppUser): IssuedToken',
                                         '+ verify(token: String): Optional<Long>']),
    'JwtTokenService': ('component', ['- key: SecretKey', '- settings: SecurityProperties.Jwt'],
                        ['+ issue(user: AppUser): IssuedToken', '+ verify(token: String): Optional<Long>']),
    'AuthenticationService': ('service', None, ['+ login(email: String, password: String)', C + ': AppUser',
                                                '+ activeUser(userId: Long): AppUser', '+ logout(userId: Long)']),
    'AuthController': ('controller', None, ['+ login(request: LoginRequest)', C + ': AuthSessionDto',
                                            '+ me(me: AuthenticatedUser, request)', C + ': AuthSessionDto',
                                            '+ logout(me: AuthenticatedUser)']),
    'RestAuthenticationEntryPoint': ('component', ['- errors: ErrorResponseFactory'],
                                     ['+ commence(request, response, e)']),
    'RestAccessDeniedHandler': ('component', ['- errors: ErrorResponseFactory'], ['+ handle(request, response, e)']),
    # --- лист 2: порты
    'IntegrationController': ('controller', None, ['+ list(): List<IntegrationDto>',
                                                   '+ test(me, id: Long, request: IntegrationTestRequest)',
                                                   C + ': IntegrationCheckDto',
                                                   '+ update(me, id: Long, request:',
                                                   C + 'IntegrationUpdateRequest): IntegrationDto']),
    'IntegrationAdminService': ('service', ['- probe: IntegrationProbe', '- cipher: SecretCipher',
                                            '- auditTrail: AuditTrail'],
                                ['+ test(actorUserId, integrationId, url, apiKey)', C + ': IntegrationCheck',
                                 '+ update(actorUserId, integrationId,', C + 'patch: IntegrationPatch)',
                                 C + ': IntegrationState']),
    'IntegrationProbe': ('interface', None, ['+ check(integration: Integration,', C + 'url: String, apiKey: String)',
                                             C + ': ProbeResult']),
    'SimulatedIntegrationProbe': ('component', None, ['+ check(integration, url, apiKey)', C + ': ProbeResult']),
    'SecretCipher': ('interface', None, ['+ encrypt(plain: String): String',
                                         '+ decrypt(stored: String): Optional<String>']),
    'AesGcmSecretCipher': ('component', ['- key: SecretKey', '- random: SecureRandom'],
                           ['+ encrypt(plain: String): String', '+ decrypt(stored: String): Optional<String>']),
    'AuditTrail': ('component', ['- clientAddress: ClientAddressProvider'],
                   ['+ record(actorUserId: Long, action: String,', C + 'objectType: String, objectId: Long,',
                    C + 'details: Map<String, ?>)']),
    'ClientAddressProvider': ('interface', None, ['+ currentAddress(): Optional<InetAddress>']),
    'RequestClientAddressProvider': ('component', None, ['+ currentAddress(): Optional<InetAddress>']),
}


def size(name):
    st, at, op = SPEC[name]
    return cls_size(name, st, at, op)


def box(p, name, x, y, w=None):
    st, at, op = SPEC[name]
    return cls(p, x, y, name, st, at, op, w=w)


def seg_edge(p, style, a, b, pts):
    """Ломаная из абсолютных точек: первая — на границе a, последняя — на границе b."""
    return p.e(style, a, b, pts[0], pts[-1], pts[1:-1])


def class_sheet1():
    p = Page('Классы API-1 (контроллеры, сервисы, мапперы, DTO)')
    cols = [['AccountSummaryDto', 'OperationDto', 'AccrualRequestDto', 'AccrualResultDto', 'RuleRequest',
             'RuleSaveResultDto', 'UserRequest', 'UserDto'],
            ['AccountMapper', 'OperationMapper', 'AccrualMapper', 'RuleMapper', 'UserMapper'],
            ['AccountController', 'ApiValues', 'AccrualController', 'RuleController', 'UserController'],
            ['AccountQueryService', 'OperationQueryService', 'AccrualService', 'RuleQueryService', 'RuleAdminService',
             'UserQueryService', 'UserAdminService']]
    W = [max(size(n)[0] for n in c) for c in cols]
    X0, X1 = 10, 1190
    G = (X1 - X0 - sum(W)) / 3
    X = [X0]
    for k in range(3):
        X.append(X[-1] + W[k] + G)
    print('  class1 gap', round(G), W)
    bands = [
        [['AccountSummaryDto'], ['AccountMapper'], ['AccountController'], ['AccountQueryService']],
        [['OperationDto'], ['OperationMapper'], ['ApiValues'], ['OperationQueryService']],
        [['AccrualRequestDto', 'AccrualResultDto'], ['AccrualMapper'], ['AccrualController'], ['AccrualService']],
        [['RuleRequest', 'RuleSaveResultDto'], ['RuleMapper'], ['RuleController'],
         ['RuleQueryService', 'RuleAdminService']],
        [['UserRequest', 'UserDto'], ['UserMapper'], ['UserController'], ['UserQueryService', 'UserAdminService']],
    ]
    SG, BG = 16, 42
    y = 12
    B = {}
    for band in bands:
        hs = [sum(size(n)[1] for n in st) + SG * (len(st) - 1) for st in band]
        bh = max(hs)
        for k, st in enumerate(band):
            yy = y + (bh - hs[k]) / 2
            for n in st:
                w = size(n)[0] if k == 2 and n == 'ApiValues' else W[k]
                B[n] = box(p, n, X[k], yy, w=W[k])
                yy += size(n)[1] + SG
        y += bh + BG
    g = {n: p.g[i] for n, i in B.items()}
    pe = p.e
    p.e = lambda style, a=None, b=None, sp=None, tp=None, pts=(), back=False: pe(style, B.get(a, a), B.get(b, b), sp, tp, pts, back)
    p.right = lambda n: g[n][0] + g[n][2]
    p.bottom = lambda n: g[n][1] + g[n][3]
    cx = [(X[k] + W[k] + X[k + 1]) / 2 for k in range(3)]   # каналы между колонками

    def mid_overlap(a, b, f=0.5):
        ay, ah = g[a][1], g[a][3]
        by, bh = g[b][1], g[b][3]
        lo, hi = max(ay, by) + 10, min(ay + ah, by + bh) - 10
        return lo + (hi - lo) * f

    def hor(style, a, b, f=0.5):
        yy = mid_overlap(a, b, f)
        if g[a][0] < g[b][0]:
            return p.e(style, a, b, (p.right(a), yy), (g[b][0], yy))
        return p.e(style, a, b, (g[a][0], yy), (p.right(b), yy))

    # контроллер → маппер, контроллер → сервисы
    for c, m, ss in (('AccountController', 'AccountMapper', ['AccountQueryService']),
                     ('AccrualController', 'AccrualMapper', ['AccrualService']),
                     ('RuleController', 'RuleMapper', ['RuleQueryService', 'RuleAdminService']),
                     ('UserController', 'UserMapper', ['UserQueryService', 'UserAdminService'])):
        hor(ASSOC, c, m, 0.3 if c == 'AccountController' else 0.5)
        for s in ss:
            hor(ASSOC, c, s)
    # маппер → DTO
    for m, ds in (('AccountMapper', ['AccountSummaryDto']), ('OperationMapper', ['OperationDto']),
                  ('AccrualMapper', ['AccrualRequestDto', 'AccrualResultDto']),
                  ('RuleMapper', ['RuleRequest', 'RuleSaveResultDto']), ('UserMapper', ['UserRequest', 'UserDto'])):
        for d in ds:
            hor(DEP, m, d)
    # вертикальные связи
    def ver(style, a, b, fx=0.5):
        xa = g[a][0] + g[a][2] * fx
        if g[a][1] < g[b][1]:
            return p.e(style, a, b, (xa, p.bottom(a)), (xa, g[b][1]))
        return p.e(style, a, b, (xa, g[a][1]), (xa, p.bottom(b)))
    ver(ASSOC, 'AccountMapper', 'OperationMapper')
    ver(ASSOC, 'AccrualMapper', 'OperationMapper')
    ver(ASSOC, 'AccountQueryService', 'OperationQueryService')
    ver(DEP, 'AccountController', 'ApiValues')
    # OperationMapper ..> ApiValues
    om, av = 'OperationMapper', 'ApiValues'
    yv = g[av][1] + g[av][3] * 0.6
    p.e(DEP, om, av, (p.right(om), yv), (g[av][0], yv))
    # AccountController → OperationMapper (через канал)
    ac = 'AccountController'
    ya = g[ac][1] + g[ac][3] * 0.78
    yb = g[om][1] + 22
    seg_edge(p, ASSOC, ac, om, [(g[ac][0], ya), (cx[1], ya), (cx[1], yb), (p.right(om), yb)])
    # AccrualController → OperationQueryService (через канал)
    rc, oq = 'AccrualController', 'OperationQueryService'
    ya = g[rc][1] + 16
    yb = g[oq][1] + g[oq][3] * 0.6
    seg_edge(p, ASSOC, rc, oq, [(p.right(rc), ya), (cx[2], ya), (cx[2], yb), (g[oq][0], yb)])
    # примечание
    note_y = y - BG + 18
    p.note('Остальные контроллеры устроены так же (контроллер → сервис запросов или команд → маппер → DTO): '
           'AuthController, ProgramController, DirectoryController, OperationController, ControlController, '
           'CampaignController, ReportController, AnalyticsController, IntegrationController, AuditController, '
           'NotificationController — всего 15 контроллеров, 34 метода. Параметр me — текущий пользователь '
           '(@AuthenticationPrincipal AuthenticatedUser), доступ задаёт @PreAuthorize(Roles.*). '
           'Сплошная стрелка — зависимость через поле (внедрение через конструктор), пунктирная — использование '
           '(маппер читает или создаёт DTO, вызов статических методов ApiValues; ApiValues используют все мапперы).',
           X0, note_y, X1 - X0, fs=12)
    del p.e, p.right, p.bottom
    return p


def class_sheet2():
    p = Page('Классы API-2 (ошибки, безопасность, порты)')
    g = p.g
    R, B_, T, L = p.right, p.bottom, (lambda i: g[i][1]), (lambda i: g[i][0])

    def vx(a, b):
        lo, hi = max(g[a][0], g[b][0]), min(R(a), R(b))
        return (lo + hi) / 2

    def ver(style, a, b, x=None):
        x = vx(a, b) if x is None else x
        if T(a) < T(b):
            return p.e(style, a, b, (x, B_(a)), (x, T(b)))
        return p.e(style, a, b, (x, T(a)), (x, B_(b)))

    def hy(a, b, f=0.5):
        lo, hi = max(g[a][1], g[b][1]) + 10, min(B_(a), B_(b)) - 10
        return lo + (hi - lo) * f

    def hor(style, a, b, y=None):
        y = hy(a, b) if y is None else y
        if L(a) < L(b):
            return p.e(style, a, b, (R(a), y), (L(b), y))
        return p.e(style, a, b, (L(a), y), (R(b), y))

    # ---------- безопасность
    fy = 10
    yA = fy + 42
    sc = box(p, 'SecurityConfig', 20, yA)
    jf = box(p, 'JwtAuthenticationFilter', 330, yA)
    ts = box(p, 'TokenService', 650, yA)
    jt = box(p, 'JwtTokenService', 940, yA)
    yB = max(B_(i) for i in (sc, jf, ts, jt)) + 46
    ep = box(p, 'RestAuthenticationEntryPoint', 20, yB)
    ad = box(p, 'RestAccessDeniedHandler', 252, yB)
    au = box(p, 'AuthenticationService', 470, yB)
    ac = box(p, 'AuthController', 940, yB)
    hor(DEP, sc, jf, yA + 40)
    p.t('«create»', (R(sc) + L(jf)) / 2, yA + 36, fs=12, anchor='bc', align='center', bg=None)
    hor(ASSOC, jf, ts)
    hor(REAL, jt, ts)
    ver(ASSOC, jf, au)
    ver(ASSOC, sc, ep, 200)
    ver(ASSOC, sc, ad, (L(ad) + R(sc)) / 2)
    # AuthController → TokenService, → AuthenticationService
    xt = (L(ts) + R(ts)) / 2
    y1 = g[ac][1] + 20
    p.e(ASSOC, ac, ts, (L(ac), y1), (xt, B_(ts)), [(xt, y1)])
    hor(ASSOC, ac, au, B_(au) - 22)
    nsec = p.note('JwtAuthenticationFilter наследует OncePerRequestFilter; создаётся в SecurityConfig.apiSecurity() '
                  'и ставится перед UsernamePasswordAuthenticationFilter. Токен — JWT HS256 на 8 ч; учётная запись '
                  'перечитывается на каждом запросе (заблокированный со старым токеном получает 401). '
                  'Сессий нет (STATELESS), CSRF выключен.', 470, B_(au) + 24, 450, fs=12)
    sec_bottom = max(B_(i) for i in (ep, ad, au, ac, nsec)) + 22
    p.frame('Безопасность (web.security)', 8, fy, 1184, sec_bottom - fy)
    # ---------- ошибки
    ey = sec_bottom + 22
    yD = ey + 42
    erf = box(p, 'ErrorResponseFactory', 20, yD)
    geh = box(p, 'GlobalExceptionHandler', 352, yD)
    des = box(p, 'DomainErrorStatus', 697, yD)
    ec = box(p, 'ErrorCode', 973, yD)
    ae = box(p, 'ApiErrorResponse', 20, B_(erf) + 46)
    ver(ASSOC, ep, erf, 200)
    ver(ASSOC, ad, erf, (L(ad) + R(erf)) / 2)
    hor(ASSOC, geh, erf, yD + 70)
    hor(ASSOC, geh, des, yD + 70)
    ver(DEP, erf, ae, 100)
    # GEH ..> ErrorCode (под DomainErrorStatus), ERF ..> ErrorCode (через канал)
    yg = B_(geh) + 24
    xg = R(geh) - 40
    p.e(DEP, geh, ec, (xg, B_(geh)), (L(ec), yg), [(xg, yg)])
    ye = yg + 40
    xc = (R(erf) + L(geh)) / 2
    yr = B_(erf) - 20
    p.e(DEP, erf, ec, (R(erf), yr), (L(ec), ye), [(xc, yr), (xc, ye)])
    nerr = p.note('Единый ответ об ошибке {status, code, message, path, timestamp}. Статус доменного исключения '
                  'DomainErrorStatus выбирает по иерархии BonusDomainException (ближайший предок); фильтры '
                  'безопасности пишут ответ через ErrorResponseFactory.write().', 352, ye + 30, 560, fs=12)
    err_bottom = max(B_(i) for i in (ae, ec, nerr)) + 22
    p.frame('Ошибки (web.error)', 8, ey, 1184, err_bottom - ey)
    # ---------- порты
    py = err_bottom + 22
    yF = py + 42
    ic = box(p, 'IntegrationController', 20, yF)
    ia = box(p, 'IntegrationAdminService', 370, yF)
    ip = box(p, 'IntegrationProbe', 690, yF)
    sp = box(p, 'SimulatedIntegrationProbe', 960, yF)
    yG = max(B_(i) for i in (ic, ia, ip, sp)) + 46
    at = box(p, 'AuditTrail', 370, yG)
    s2 = box(p, 'SecretCipher', 655, yG)
    ag = box(p, 'AesGcmSecretCipher', 935, yG)
    yH = B_(at) + 46
    cap = box(p, 'ClientAddressProvider', 370, yH)
    rcap = box(p, 'RequestClientAddressProvider', 690, yH)
    hor(ASSOC, ic, ia, yF + 60)
    hor(ASSOC, ia, ip, yF + 60)
    hor(REAL, sp, ip)
    hor(REAL, ag, s2)
    ver(ASSOC, ia, at)
    ver(ASSOC, at, cap)
    hor(REAL, rcap, cap)
    xc = (R(ia) + L(s2)) / 2
    yi = B_(ia) - 24
    ys = hy(s2, s2)
    p.e(ASSOC, ia, s2, (R(ia), yi), (L(s2), ys), [(xc, yi), (xc, ys)])
    npt = p.note('Порты объявлены в слое сервисов, реализации внедряются Spring по интерфейсу (DIP). '
                 'Ключ интеграции хранится зашифрованным AES-256-GCM; проверка соединения в текущей версии '
                 'имитируется (адрес HTTPS и ключ не короче 8 символов).', 20, yG, 330, fs=12)
    port_bottom = max(B_(i) for i in (cap, rcap, npt)) + 22
    p.frame('Порты и их реализации (service.integration, service.audit, web.security)', 8, py, 1184,
            port_bottom - py)
    return p


# =====================================================================
# 3. Диаграмма развёртывания серверной части
# =====================================================================
def comp(p, name, x, y, w, hh, sub=None):
    lines = f'«component»<br><b>{h(name)}</b>' + (f'<br>{hl(sub)}' if sub else '')
    st = (f'rounded=0;html=1;whiteSpace=nowrap;fillColor=#FFFFFF;strokeColor={CS};strokeWidth=1.5;fontSize=12;'
          f'fontColor={INK};align=center;verticalAlign=middle;{FONT}')
    i = p.v(lines, x, y, w, hh, st)
    p.v('', x + w - 24, y + 7, 16, 18, f'shape=module;jettyWidth=7;jettyHeight=3;html=1;fillColor=#FFFFFF;strokeColor={CS};')
    return i


def artifact_box(p, name, x, y, w, hh, sub=None, valign='middle'):
    st = (f'rounded=0;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize=12;'
          f'fontColor={INK};align=left;verticalAlign={valign};spacingLeft=10;spacingTop=6;{FONT}')
    i = p.v(f'«artifact» <b>{h(name)}</b>' + (f'<br>{hl(sub)}' if sub else ''), x, y, w, hh, st)
    p.v('', x + w - 22, y + 7, 13, 17, f'shape=note;size=5;html=1;fillColor=#FFFFFF;strokeColor={CS};')
    return i


def deployment_diagram():
    p = Page('Развёртывание (REST API)')
    W = '#F8F9FF'
    # --- сервер приложений
    ax, ay, aw = 490, 10, 700
    app = node(p, 'Сервер приложений', ax, ay, aw, 600)
    jvm = node(p, 'JVM 21', ax + 20, ay + 62, aw - 54, 520, 'executionEnvironment', fill=W, depth=10)
    tom = node(p, 'Встроенный контейнер сервлетов Tomcat (Spring Boot 3.3)', ax + 40, ay + 124, aw - 94, 330,
               'executionEnvironment', fill='#FFFFFF', depth=10)
    arx, ary, arw, arh = ax + 60, ay + 186, aw - 134, 252
    jar = artifact_box(p, 'исполняемый архив серверной части', arx, ary, arw, arh, valign='top')
    cw, ch = 230, 74
    c1x, c2x = arx + 20, arx + arw - 20 - cw
    r1, r2 = ary + 44, ary + 44 + ch + 46
    f = comp(p, 'Фильтр JWT', c1x, r1, cw, ch, ['проверка токена, роль'])
    c = comp(p, 'Контроллеры REST', c2x, r1, cw, ch, ['15 контроллеров, /api/v1'])
    s = comp(p, 'Сервисы', c2x, r2, cw, ch, ['бизнес-логика и запросы'])
    r = comp(p, 'Репозитории', c1x, r2, cw, ch, ['Spring Data JPA'])
    for a, b in ((f, c), (c, s), (s, r)):
        xa, ya_, wa, ha = p.g[a]
        xb, yb, wb, hb = p.g[b]
        if abs(ya_ - yb) < 1:
            if xa < xb:
                p.e(DEP, a, b, (xa + wa, ya_ + ha / 2), (xb, yb + hb / 2))
            else:
                p.e(DEP, a, b, (xa, ya_ + ha / 2), (xb + wb, yb + hb / 2))
        else:
            p.e(DEP, a, b, (xa + wa / 2, ya_ + ha), (xb + wb / 2, yb))
    cfg = artifact_box(p, 'файл конфигурации', ax + 40, ay + 472, aw - 94, 92,
                       ['подключение к БД, ключ подписи JWT и срок токена (8 ч),',
                        'лимит начисления, интервал обработки событий, адреса клиента (CORS)'])
    # --- рабочая станция
    wx, wy, ww, wh = 10, 10, 340, 300
    ws = node(p, 'Рабочая станция пользователя', wx, wy, ww, wh)
    br = node(p, 'Веб-браузер', wx + 20, wy + 62, ww - 54, 220, 'executionEnvironment', fill=W, depth=10)
    artifact_box(p, 'клиентское SPA', wx + 40, wy + 128, ww - 94, 130,
                 ['одностраничное приложение', '(React 18, Ant Design 5);', 'хранит JWT и передаёт',
                  'его в заголовке Authorization'])
    # --- сервер БД
    dx, dy, dw, dh = 690, 690, 500, 250
    db = node(p, 'Сервер БД', dx, dy, dw, dh)
    pg = node(p, 'PostgreSQL 14', dx + 20, dy + 62, dw - 54, 170, 'executionEnvironment', fill=W, depth=10)
    artifact_box(p, 'база данных ПС TechnoSoft Bonus', dx + 40, dy + 126, dw - 94, 88,
                 ['18 таблиц, ограничения CHECK;', 'схема ведётся системой версионирования схемы БД'])
    # --- внешние системы
    ex, ey, ew, eh = 10, 690, 560, 250
    ext = node(p, 'Внешние системы', ex, ey, ew, eh, 'node')
    xs = [('HRIS', 'кадровые данные'), ('ERP', 'проекты, KPI'), ('Active Directory', 'учётные записи'),
          ('Сервис уведомлений', 'e-mail, push')]
    for k, (n, sname) in enumerate(xs):
        x = ex + 20 + (k % 2) * 265
        y = ey + 66 + (k // 2) * 86
        node(p, n, x, y, 245, 72, 'node', fill=W, depth=8)
        p.t(sname, x + 5, y + 52, fs=12, anchor='cl', bg=None)
    # --- связи
    yl = 160
    p.e(LINE + 'strokeWidth=2;', None, None, (wx + ww, yl), (ax, yl))
    p.t(['«HTTPS» TCP 443', 'REST/JSON,', 'JWT (Bearer)'], (wx + ww + ax) / 2, yl - 6, anchor='bc', align='center',
        bg=None, fs=12)
    xj = dx + dw / 2 + 60
    p.e(LINE + 'strokeWidth=2;', None, None, (xj, ay + 600), (xj, dy))
    p.t(['«JDBC»', 'TCP 5432'], xj + 10, (ay + 600 + dy) / 2, anchor='cl', bg=None, fs=12)
    xe = 530
    p.e(LINE + 'strokeWidth=2;', None, None, (xe, ay + 600), (xe, ey))
    p.t(['«HTTPS» TCP 443', 'REST/JSON'], xe - 10, (ay + 600 + ey) / 2, anchor='cr', align='right', bg=None, fs=12)
    p.note('Проверка соединения с внешними системами в текущей версии имитируется (порт IntegrationProbe). '
           'События для автоматического начисления (UC4) сервер обрабатывает по расписанию из очереди в БД.',
           10, 330, 340, fs=12)
    return p


# Раскладка диаграммы вариантов использования: (колонка, ряд) для каждого UC, ряд и сторона для каждого актора.
# Подобрана перебором так, чтобы ни одна линия не проходила через фигуры и линии не пересекались.
UC_LAYOUT = ({'login': (0, 3), 'uc1': (0, 0), 'uc2': (0, 1), 'uc3': (0, 2), 'uc4': (2, 9), 'uc5': (1, 8), 'uc6': (1, 9), 'uc7': (0, 7), 'uc8': (2, 4), 'uc9': (1, 7), 'uc10': (0, 5), 'uc11': (1, 6), 'uc12': (1, 1), 'uc13': (2, 3), 'approve': (0, 6), 'audit': (2, 1), 'journal': (0, 4)},
             {'E': 2.0, 'M': 7.0, 'H': 5.5, 'F': 4.5, 'A': 2.0, 'S': 8.5})
UC_LEFT = 'EMF'


def build():
    uc, _ = usecase_diagram(UC_LAYOUT)
    s2 = class_sheet2()
    s2 = s2[0] if isinstance(s2, tuple) else s2
    return [uc, class_sheet1(), s2, deployment_diagram()]


PNG = ['uml-usecase.png', 'uml-class-api-1.png', 'uml-class-api-2.png', 'uml-deployment-api.png']


if __name__ == '__main__':
    out = sys.argv[1]
    pages = build()
    open(out, 'w', encoding='utf8').write('<mxfile host="Electron">' + ''.join(pg.xml(k) for k, pg in enumerate(pages)) +
                                          '</mxfile>')
    print('ok', len(pages))
