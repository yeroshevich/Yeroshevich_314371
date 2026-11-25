# Генератор UML-диаграмм (draw.io) для ЛР8 «Проектирование серверной части»
# Вариант 46, ПС «TechnoSoft Bonus». Источник правды — код backend/src/main/java/by/technosoft/bonus/
# Запуск: python3 uml_diagrams.py uml.drawio
# Экспорт: draw.io -x -f png -s 2 -b 10 -p <N> -o out.png uml.drawio
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


# =====================================================================
# 1. Диаграмма классов, лист 1: Service Layer + Strategy
# =====================================================================
def class_sheet1():
    p = Page('Классы-1 (Service Layer, Strategy)')
    X0, X1 = 30, 1170
    top = 60
    # --- левая колонка
    ctrl = cls(p, X0, top, 'AccrualController', 'REST-контроллер, планируется',
               ['- accrualService: AccrualService'],
               ['+ preview(request: AccrualRequest): AccrualPreview', '+ create(request: AccrualRequest): AccrualResult'],
               dashed=True)
    acc = cls(p, X0, p.bottom(ctrl) + 50, 'AccrualService', 'service',
              ['- employeeRepository: EmployeeRepository', '- accountRepository: BonusAccountRepository',
               '- operationRepository: OperationRepository', '- events: ApplicationEventPublisher'],
              ['+ preview(actorUserId, request): AccrualPreview', '+ create(actorUserId, request): AccrualResult',
               '- validate(request: AccrualRequest)', '- buildPreview(actor, request, today): AccrualPreview'])
    red = cls(p, X0, p.bottom(acc) + 30, 'RedemptionService', 'service',
              ['- programRepository: ProgramRepository', '- accountRepository: BonusAccountRepository',
               '- operationRepository: OperationRepository', '- events: ApplicationEventPublisher'],
              ['+ redeem(employeeId, programId): RedemptionResult'])
    wl = max(p.g[i][2] for i in (ctrl, acc, red))
    for i in (ctrl, acc, red):
        x, y, w, hh = p.g[i]
    # --- средняя колонка
    ap = cls(p, 0, top, 'AccessPolicy', 'component', ['- userRepository: AppUserRepository'],
             ['+ requireActiveUser(userId: Long): AppUser', '+ requireRole(user: AppUser, allowed: RoleCode...)',
              '+ requireCanManage(actor: AppUser,', '        employee: Employee)'])
    ong = cls(p, 0, p.bottom(ap) + 30, 'OperationNumberGenerator', 'component', ['- jdbcTemplate: JdbcTemplate'],
              ['+ next(): OperationNumber', '_+ format(value: long): String'])
    bcs = cls(p, 0, p.bottom(ong) + 30, 'BonusCalculationService', 'service',
              ['- employeeRepository: EmployeeRepository', '- ruleRepository: AccrualRuleRepository',
               '- campaignRepository: CampaignRepository', '- operationRepository: OperationRepository'],
              ['+ calculate(request: CalculationRequest)', '        : CalculationResult',
               '+ calculateManual(employeeId, amount, campaignId,', '        onDate): CalculationResult'])
    # --- правая колонка
    rev = cls(p, 0, top, 'ReviewService', 'service',
              ['- operationRepository: OperationRepository', '- reviewRepository: OperationReviewRepository',
               '- events: ApplicationEventPublisher'],
              ['+ pendingQueue(reviewerUserId): List<OperationView>',
               '+ approve(operationId, reviewerUserId, comment)', '        : OperationView',
               '+ block(operationId, reviewerUserId, comment)', '        : OperationView'])
    cor = cls(p, 0, p.bottom(rev) + 30, 'CorrectionService', 'service',
              ['- operationRepository: OperationRepository', '- accountRepository: BonusAccountRepository',
               '- events: ApplicationEventPublisher'],
              ['+ correct(operationId, request, actorUserId)', '        : CorrectionResult'])
    evp = cls(p, 0, p.bottom(cor) + 30, 'EventProcessingService', 'service',
              ['- eventRepository: BusinessEventRepository', '- ruleRepository: AccrualRuleRepository',
               '- accountRepository: BonusAccountRepository', '- events: ApplicationEventPublisher'],
              ['+ process(eventId: Long): EventProcessingResult', '+ processPending(): BatchProcessingResult'])
    wm = max(p.g[i][2] for i in (ap, ong, bcs))
    wr = max(p.g[i][2] for i in (rev, cor, evp))
    G = (X1 - X0 - wl - wm - wr) / 2
    print('  sheet1 gaps', round(G), wl, wm, wr)
    xm, xr = X0 + wl + G, X1 - wr
    for col, xx, ww in (((ctrl, acc, red), X0, wl), ((ap, ong, bcs), xm, wm), ((rev, cor, evp), xr, wr)):
        for i in col:
            x, y, w, hh = p.g[i]
            p.g[i] = (xx, y, ww, hh)
    # подправить геометрию в XML (ширины/иксы) — пересоберём ячейки
    fix_geometry(p)
    # выровнять AccrualService по вертикали напротив AccessPolicy…BonusCalculationService
    # --- связи слоя сервисов (ассоциации, внедрение через конструктор)
    gl = (X0 + wl, xm)            # левый канал
    gr = (xm + wm, xr)            # правый канал
    c = lambda g, f: g[0] + (g[1] - g[0]) * f
    p.e(ASSOC, ctrl, acc, p.B(ctrl, 0.3), p.T(acc, 0.3))
    ax, ay, aw, ah = p.g[acc]
    hv(p, ASSOC, acc, ap, ya=ay + 20, yb=p.g[ap][1] + p.g[ap][3] - 25, cx=c(gl, 0.25))
    hv(p, ASSOC, acc, ong, ya=ay + ah * 0.5, yb=p.g[ong][1] + p.g[ong][3] * 0.4, cx=c(gl, 0.5))
    hv(p, ASSOC, acc, bcs, ya=ay + ah - 20, yb=p.g[bcs][1] + 30, cx=c(gl, 0.25))
    hv(p, ASSOC, red, ong, ya=p.g[red][1] + 25, yb=p.g[ong][1] + p.g[ong][3] * 0.75, cx=c(gl, 0.75))
    hv(p, ASSOC, rev, ap, ya=p.g[rev][1] + 40, yb=p.g[ap][1] + 40, cx=c(gr, 0.5))
    hv(p, ASSOC, cor, ap, ya=p.g[cor][1] + 25, yb=p.g[ap][1] + p.g[ap][3] - 25, cx=c(gr, 0.3))
    hv(p, ASSOC, cor, ong, ya=p.g[cor][1] + p.g[cor][3] - 25, yb=p.g[ong][1] + p.g[ong][3] * 0.4, cx=c(gr, 0.5))
    hv(p, ASSOC, evp, ong, ya=p.g[evp][1] + 25, yb=p.g[ong][1] + p.g[ong][3] * 0.75, cx=c(gr, 0.7))
    hv(p, ASSOC, evp, bcs, ya=p.g[evp][1] + p.g[evp][3] - 30, yb=p.g[bcs][1] + p.g[bcs][3] - 30, cx=c(gr, 0.5))
    sl_bottom = max(p.bottom(i) for i in (ctrl, acc, red, ap, ong, bcs, rev, cor, evp))
    # примечание Service Layer
    nx = xm
    n1 = p.note('Service Layer: каждый сценарий (UC3–UC7, UC4) — публичный метод @Service-класса '
                'с границей транзакции @Transactional; REST-контроллеры (планируются) вызывают только сервисы. '
                'Зависимости внедряются через конструктор.', X0, max(p.bottom(red), 0) + 25, wl)
    sl_bottom = max(sl_bottom, p.bottom(n1))
    p.frame('Паттерн Service Layer (слой сервисов)', 12, 12, 1176, sl_bottom + 20 - 12)

    # --- Strategy
    fy = sl_bottom + 45
    yA = fy + 50
    reg = cls(p, X0, yA, 'AccrualStrategyRegistry', 'component',
              ['- byPeriodicity: Map<RulePeriodicity, AccrualStrategy>'],
              ['+ AccrualStrategyRegistry(strategies: List<AccrualStrategy>)', '+ forRule(rule: AccrualRule): AccrualStrategy'])
    bx = p.g[bcs][0]
    strat = cls(p, 0, yA, 'AccrualStrategy', 'interface', [],
                ['+ supportedPeriodicities(): Set<RulePeriodicity>',
                 '+ calculate(rule: AccrualRule, kpiValue: BigDecimal): BaseAmount'])
    base = cls(p, 0, yA, 'BaseAmount', 'record', ['+ value: BigDecimal', '+ formula: String'], ['+ isZero(): boolean'])
    sx = bx + 60
    _, _, sw, sh = p.g[strat]
    p.g[strat] = (sx, yA, sw, sh)
    _, _, bw, bh = p.g[base]
    p.g[base] = (X1 - bw, yA, bw, bh)
    fix_geometry(p)
    yB = max(p.bottom(i) for i in (reg, strat, base)) + 70
    rule = cls(p, X0, yB, 'AccrualRule', 'entity',
               ['- basePoints: int', '- weight: BigDecimal', '- periodicity: RulePeriodicity', '- threshold: BigDecimal',
                '- params: String {JSONB}', '- active: boolean'],
               ['+ isApplicableOn(date: LocalDate): boolean'])
    kpi = cls(p, 0, yB, 'KpiAccrualStrategy', 'component', ['_~ MAX_KPI: BigDecimal = 150'],
              ['+ supportedPeriodicities(): Set<RulePeriodicity>', '+ calculate(rule, kpiValue): BaseAmount'])
    fxs = cls(p, 0, yB, 'FixedEventAccrualStrategy', 'component', [],
              ['+ supportedPeriodicities(): Set<RulePeriodicity>', '+ calculate(rule, kpiValue): BaseAmount'])
    _, _, kw, kh = p.g[kpi]
    _, _, fw, fh = p.g[fxs]
    scx = sx + sw / 2
    gap = 40
    p.g[kpi] = (scx - gap / 2 - kw, yB, kw, kh)
    p.g[fxs] = (scx + gap / 2, yB, fw, fh)
    fix_geometry(p)
    per = cls(p, X0, p.bottom(rule) + 50, 'RulePeriodicity', 'enumeration', ['MONTH', 'QUARTER', 'YEAR', 'EVENT'],
              ['+ dbValue(): String'])
    # связи Strategy
    # BonusCalculationService -> реестр (ассоциация), -> стратегия (зависимость «use»)
    rx, ry, rw, rh = p.g[reg]
    vx = bx + 25
    p.e(ASSOC, bcs, reg, (vx, p.bottom(bcs)), (rx + rw, ry + 28), [(vx, ry + 28)])
    ux = min(sx + sw - 30, p.g[bcs][0] + p.g[bcs][2] - 30)
    p.e(DEP, bcs, strat, (ux, p.bottom(bcs)), (ux, yA))
    p.t('«use»', ux + 6, yA - 22, anchor='tl')
    # реестр ◇— стратегии
    yy = yA + sh * 0.7
    p.e(AGGR, reg, strat, (rx + rw, yy), (sx, yy))
    p.t('1..*', sx - 6, yy - 3, anchor='br', bg=None)
    # реализации (дерево)
    busy = yB - 32
    for k in (kpi, fxs):
        x, y, w, hh = p.g[k]
        p.e(REAL, k, strat, (x + w / 2, y), (scx, yA + sh), [(x + w / 2, busy), (scx, busy)])
    # стратегия ..> BaseAmount
    p.e(DEP, strat, base, (sx + sw, yA + sh / 2), (X1 - bw, yA + sh / 2))
    p.t('«create»', (sx + sw + X1 - bw) / 2, yA + sh / 2 - 3, anchor='bc', bg=None)
    # стратегия ..> AccrualRule (параметр)
    rlx, rly, rlw, rlh = p.g[rule]
    yy2 = max(p.bottom(reg), yA + sh) + 22
    p.e(DEP, strat, rule, (sx + 20, yA + sh), (rlx + rlw - 40, rly), [(sx + 20, yy2), (rlx + rlw - 40, yy2)])
    # реестр ..> AccrualRule (forRule)
    p.e(DEP, reg, rule, (rx + 60, ry + rh), (rx + 60, rly))
    p.t('«use»', rx + 66, (ry + rh + rly) / 2, anchor='cl', bg=None)
    # AccrualRule -> RulePeriodicity
    px_, py_, pw_, ph_ = p.g[per]
    p.e(ASSOC, rule, per, (px_ + 50, rly + rlh), (px_ + 50, py_))
    p.t('periodicity\n1', px_ + 56, py_ - 4, anchor='bl', bg=None)
    # примечание Strategy
    nx = p.g[kpi][0]
    n2 = p.note('Strategy: контекст — BonusCalculationService; способ расчёта базовой суммы выбирается по периодичности '
                'правила. KpiAccrualStrategy — MONTH, QUARTER (BR-4: база × вес × KPI / 100; BR-5: KPI ниже порога → 0); '
                'FixedEventAccrualStrategy — EVENT, YEAR (база × вес). Реестр (фабрика) AccrualStrategyRegistry.forRule(rule) '
                'хранит EnumMap и при старте проверяет, что каждую периодичность обслуживает ровно одна стратегия.',
                nx, max(p.bottom(kpi), p.bottom(fxs)) + 30, X1 - nx)
    st_bottom = max(p.bottom(i) for i in (per, n2, rule))
    p.frame('Паттерн Strategy + реестр стратегий (Registry / Factory)', 12, fy, 1176, st_bottom + 20 - fy)
    return p


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


# =====================================================================
# 1б. Диаграмма классов, лист 2: Observer, State, Repository
# =====================================================================
def class_sheet2():
    p = Page('Классы-2 (Observer, State, Repository)')
    X0, X1 = 30, 1170
    # ---------------- Observer
    y0 = 60
    pubs = []
    yy = y0
    for name in ('AccrualService', 'RedemptionService', 'CorrectionService', 'ReviewService', 'EventProcessingService'):
        i = cls(p, X0, yy, name, 'service', w=200)
        pubs.append(i)
        yy = p.bottom(i) + 10
    aep = cls(p, 300, y0 + 90, 'ApplicationEventPublisher', 'interface', [], ['+ publishEvent(event: Object)'])
    ore = cls(p, 300, 0, 'OperationRegisteredEvent', 'record',
              ['+ action: OperationAction', '+ operationId: Long', '+ operationNumber: String', '+ employeeId: Long',
               '+ actorUserId: Long', '+ amount: int', '+ originalAmount: Integer', '+ status: OperationStatus',
               '+ reason: String', '+ comment: String'],
              ['_+ of(action, op, actor, comment): OperationRegisteredEvent'])
    al = cls(p, 0, y0, 'AuditListener', 'component',
             ['- auditLogRepository: AuditLogRepository', '- userRepository: AppUserRepository'],
             ['+ onOperationRegistered(e: OperationRegisteredEvent)', '    {@EventListener — в той же транзакции}',
              '+ onEventProcessingFailed(e: EventProcessingFailedEvent)'])
    nl = cls(p, 0, 0, 'NotificationListener', 'component',
             ['- notificationRepository: NotificationRepository', '- userRepository: AppUserRepository'],
             ['+ onOperationRegistered(e: OperationRegisteredEvent)',
              '    {@TransactionalEventListener(AFTER_COMMIT),', '     @Transactional(REQUIRES_NEW)}'])
    epf = cls(p, 0, 0, 'EventProcessingFailedEvent', 'record',
              ['+ eventId: Long', '+ eventType: String', '+ employeeId: Long', '+ message: String'])
    oa = cls(p, 0, 0, 'OperationAction', 'enumeration',
             ['MANUAL_ACCRUAL, AUTO_ACCRUAL,', 'REDEMPTION, CORRECTION,', 'CANCELLATION, APPROVAL, BLOCKING'],
             ['+ auditLabel(): String'])
    # раскладка
    wl_ = max(p.g[i][2] for i in (al, nl, epf))
    xr = X1 - 25 - wl_
    for i in (al, nl, epf):
        x, y, w, hh = p.g[i]
        p.g[i] = (xr, y, wl_, hh)
    x, y, w, hh = p.g[nl]
    p.g[nl] = (xr, p.bottom(al) + 40, wl_, hh)
    x, y, w, hh = p.g[aep]
    xm = 360
    p.g[aep] = (xm, y0 + 70, w, hh)
    x, y, w, hh = p.g[ore]
    p.g[ore] = (xm, p.bottom(aep) + 60, w, hh)
    x, y, w, hh = p.g[epf]
    p.g[epf] = (xr, p.bottom(nl) + 40, wl_, hh)
    x, y, w, hh = p.g[oa]
    p.g[oa] = (X0, p.bottom(pubs[-1]) + 140, max(w, 200), hh)
    fix_geometry(p)
    # издатели -> ApplicationEventPublisher
    ax, ay, aw, ah = p.g[aep]
    busx = X0 + 200 + 30
    for i in pubs:
        x, y, w, hh = p.g[i]
        yc = y + hh / 2
        tgt = (ax, ay + ah / 2)
        p.e(ASSOC, i, aep, (x + w, yc), tgt, [(busx, yc), (busx, tgt[1])])
    # AEP -> слушатели (уведомление)
    alx, aly, alw, alh = p.g[al]
    nlx, nly, nlw, nlh = p.g[nl]
    ox, oy, ow, oh = p.g[ore]
    cx1 = ox + ow + 12
    p.e(DEP, aep, al, (ax + aw, ay + ah * 0.35), (alx, aly + alh * 0.3), [(cx1, ay + ah * 0.35), (cx1, aly + alh * 0.3)])
    p.e(DEP, aep, nl, (ax + aw, ay + ah * 0.7), (nlx, nly + nlh * 0.3), [(cx1 + 12, ay + ah * 0.7), (cx1 + 12, nly + nlh * 0.3)])
    p.t('«notify»', alx - 6, aly + alh * 0.3 - 3, anchor='br', bg=None)
    p.t('«notify»', nlx - 6, nly + nlh * 0.3 - 3, anchor='br', bg=None)
    # AEP ..> ORE «send»
    ox, oy, ow, oh = p.g[ore]
    p.e(DEP, aep, ore, (ax + 60, ay + ah), (ax + 60, oy))
    p.t('«send»', ax + 66, (ay + ah + oy) / 2, anchor='cl', bg=None)
    # издатели «create» ORE — через примечание; слушатели ..> ORE (параметр)
    cx2 = ox + ow + 36
    p.e(DEP, nl, ore, (nlx, nly + nlh * 0.8), (ox + ow, nly + nlh * 0.8))
    p.e(DEP, al, ore, (alx, aly + alh * 0.85), (ox + ow, oy + 40), [(cx2, aly + alh * 0.85), (cx2, oy + 40)])
    # AuditListener ..> EventProcessingFailedEvent
    ex, ey, ew, eh = p.g[epf]
    rx_ = X1 - 8
    p.e(DEP, al, epf, (alx + alw, aly + alh * 0.75), (ex + ew, ey + eh * 0.4), [(rx_, aly + alh * 0.75), (rx_, ey + eh * 0.4)])
    # ORE -> OperationAction
    oax, oay, oaw, oah = p.g[oa]
    p.e(ASSOC, ore, oa, (ox, oay + 30), (oax + oaw, oay + 30))
    p.t('action\n1', ox - 6, oay + 27, anchor='br', bg=None)
    # примечание
    pb = p.bottom(pubs[-1])
    n1 = p.note('Издатели (субъекты): сервисы создают OperationRegisteredEvent.of(…) или EventProcessingFailedEvent '
                'и вызывают events.publishEvent(e) внутри транзакции.', X0, pb + 20, 250)
    obs_b = max(p.bottom(i) for i in (ore, epf, oa, n1))
    n2 = p.note('Observer: субъект — ApplicationEventPublisher (Spring); наблюдатели не известны издателям. '
                'AuditListener пишет журнал аудита синхронно в той же транзакции (операция и аудит фиксируются вместе); '
                'NotificationListener создаёт уведомление после коммита в новой транзакции — его сбой не откатывает операцию.',
                xr, p.bottom(epf) + 25, wl_)
    obs_b = max(obs_b, p.bottom(n2))
    p.frame('Паттерн Observer (наблюдатель): события операций', 12, 12, 1176, obs_b + 20 - 12)

    # ---------------- State
    fy = obs_b + 45
    ys = fy + 55
    op = cls(p, X0, ys, 'Operation', 'entity',
             ['- id: Long', '- number: String', '- type: OperationType', '- status: OperationStatus',
              '- basis: OperationBasis', '- amount: int', '- originalAmount: Integer', '- reason: String',
              '- createdAt: OffsetDateTime'],
             ['_+ create(number, account, type, status, basis,', '_      amount, reason, createdBy, createdAt): Operation',
              '+ withRule(rule: AccrualRule): Operation', '+ withCampaign(campaign: Campaign): Operation',
              '+ withProgram(program: Program): Operation', '+ correcting(original: Operation): Operation',
              '+ transitionTo(target: OperationStatus)', '+ correctionDelta(): int'])
    ost = cls(p, 0, ys, 'OperationStatus', 'enumeration',
              ['ACCRUED', 'WRITTEN_OFF', 'CORRECTED', 'PENDING', 'BLOCKED', 'CANCELLED'],
              ['+ isSettled(): boolean', '+ canTransitionTo(target: OperationStatus): boolean'])
    ba = cls(p, 0, ys, 'BonusAccount', 'entity',
             ['- id: Long', '- balance: int', '- status: AccountStatus', '- version: int {@Version}'],
             ['+ isActive(): boolean', '+ applyDelta(delta: int, now: OffsetDateTime)'])
    ox, oy, ow, oh = p.g[op]
    _, _, sw, sh = p.g[ost]
    _, _, bw, bh = p.g[ba]
    g3 = (X1 - X0 - ow - sw - bw) / 2
    p.g[ost] = (X0 + ow + g3, ys + 60, sw, sh)
    p.g[ba] = (X1 - bw, ys + 60, bw, bh)
    fix_geometry(p)
    camp = cls(p, 0, 0, 'Campaign', 'entity',
               ['- coefficient: BigDecimal', '- budget: Integer', '- status: CampaignStatus'],
               ['+ isRunningOn(date: LocalDate): boolean'])
    cd = cls(p, 0, 0, 'CampaignDepartment', 'entity', ['- id: CampaignDepartmentId'], [])
    _, _, cw, ch = p.g[camp]
    _, _, dw, dh = p.g[cd]
    yc = max(p.bottom(ost), p.bottom(ba)) + 50
    p.g[camp] = (p.g[ost][0], yc, cw, ch)
    p.g[cd] = (X1 - dw, yc, dw, dh)
    fix_geometry(p)
    sx, sy = p.g[ost][0], p.g[ost][1]
    # Operation -> OperationStatus
    p.e(ASSOC, op, ost, (ox + ow, sy + 40), (sx, sy + 40))
    p.t('status', sx - 6, sy + 37, anchor='br', bg=None)
    p.t('1', sx - 6, sy + 43, anchor='tr', bg=None)
    # Operation -> BonusAccount (через верх)
    bax, bay = p.g[ba][0], p.g[ba][1]
    yt = ys - 22
    p.e(ASSOC, op, ba, (ox + ow - 40, ys), (bax + 60, bay), [(ox + ow - 40, yt), (bax + 60, yt)])
    p.t('*', ox + ow - 34, ys - 4, anchor='bl', bg=None)
    p.t('account   1', bax + 66, bay - 4, anchor='bl', bg=None)
    # Operation -> Campaign
    cx_, cy_ = p.g[camp][0], p.g[camp][1]
    p.e(ASSOC, op, camp, (ox + ow, cy_ + 30), (cx_, cy_ + 30))
    p.t('campaign', cx_ - 6, cy_ + 27, anchor='br', bg=None)
    p.t('0..1', cx_ - 6, cy_ + 33, anchor='tr', bg=None)
    # Campaign ◆— CampaignDepartment
    p.e(COMP, camp, cd, (cx_ + cw, cy_ + 30), (p.g[cd][0], cy_ + 30))
    p.t('1', cx_ + cw + 22, cy_ + 27, anchor='bl', bg=None)
    p.t('targets  0..*', p.g[cd][0] - 6, cy_ + 27, anchor='br', bg=None)
    # Operation -> Operation (corrects)
    yb = oy + oh + 26
    p.e(ASSOC, op, op, (ox + 70, oy + oh), (ox + 200, oy + oh), [(ox + 70, yb), (ox + 200, yb)])
    p.t('corrects  0..1', ox + 206, oy + oh + 4, anchor='tl', bg=None)
    n3 = p.note('State: допустимые переходы задаёт OperationStatus.canTransitionTo(target) '
                '(PENDING → ACCRUED | CORRECTED | CANCELLED | BLOCKED; ACCRUED, CORRECTED → CANCELLED; '
                'WRITTEN_OFF, BLOCKED, CANCELLED — конечные). Operation.transitionTo(target) меняет статус только по этой '
                'таблице, иначе IllegalStateException. Static Factory Method: Operation.create(…).',
                sx, max(p.bottom(camp), p.bottom(cd)) + 30, X1 - sx)
    st_b = max(p.bottom(op) + 50, p.bottom(n3))
    p.frame('Паттерн State (состояние операции)', 12, fy, 1176, st_b + 20 - fy)

    pages = [p]
    # ---------------- Repository (лист 3)
    p = Page('Классы-3 (Repository)')
    pages.append(p)
    X0, X1 = 30, 1170
    fy2 = 12
    yj = fy2 + 50
    jpa = cls(p, 0, yj, 'JpaRepository<T, ID>', 'interface',
              [], ['+ findById(id: ID): Optional<T>', '+ save(entity: S): S', '+ getReferenceById(id: ID): T'])
    _, _, jw, jh = p.g[jpa]
    jx = (X0 + X1) / 2 - jw / 2
    p.g[jpa] = (jx, yj, jw, jh)
    fix_geometry(p)
    repos = [
        ('AppUserRepository', ['+ findWithRoleById(id): Optional<AppUser>', '+ findByEmployeeId(employeeId): Optional<AppUser>']),
        ('OperationRepository', ['+ lockById(id): Optional<Operation>', '+ existsByEventId(eventId): boolean',
                                 '+ sumAmountByCampaign(campaignId, statuses): long']),
        ('BonusAccountRepository', ['+ findByEmployeeId(employeeId): Optional<BonusAccount>',
                                    '+ lockByEmployeeId(employeeId): Optional<BonusAccount>',
                                    '+ lockById(id): Optional<BonusAccount>']),
        ('EmployeeRepository', ['+ findByManagerIdOrderByFullName(managerId)', '        : List<Employee>']),
        ('CampaignRepository', ['+ findRunningForDepartment(departmentId, date)', '        : List<Campaign>']),
        ('CampaignDepartmentRepository', ['+ existsByCampaignIdAndDepartmentId(', '        campaignId, departmentId): boolean']),
        ('ProgramRepository', []),
        ('AuditLogRepository', ['+ findByObjectTypeAndObjectIdOrderByOccurredAtDesc(', '        objectType, objectId): List<AuditLog>']),
        ('NotificationRepository', ['+ findByUserIdOrderByCreatedAtDesc(userId)', '        : List<Notification>']),
    ]
    ids = [cls(p, 0, 0, n, 'repository', [], ops) for n, ops in repos]
    colw = (X1 - X0 - 2 * 40) / 3
    for i in ids:
        x, y, w, hh = p.g[i]
        assert w <= colw + 1, (w, colw)
    yrow = yj + jh + 70
    rowsy = []
    for r in range(3):
        row = ids[r * 3:(r + 1) * 3]
        hmax = max(p.g[i][3] for i in row)
        for k, i in enumerate(row):
            x, y, w, hh = p.g[i]
            p.g[i] = (X0 + k * (colw + 40), yrow, colw, hh)
        rowsy.append(yrow)
        yrow += hmax + 30
    fix_geometry(p)
    # дерево обобщений: каналы между колонками
    ch1 = X0 + colw + 20
    ch2 = X0 + 2 * colw + 40 + 20
    busy = yj + jh + 35
    tx1, tx2 = jx + jw * 0.35, jx + jw * 0.65
    for k, i in enumerate(ids):
        x, y, w, hh = p.g[i]
        col = k % 3
        ym = y + 22
        if col == 0:
            p.e(GEN, i, jpa, (x + w, ym), (tx1, yj + jh), [(ch1, ym), (ch1, busy), (tx1, busy)])
        elif col == 2:
            p.e(GEN, i, jpa, (x, ym), (tx2, yj + jh), [(ch2, ym), (ch2, busy), (tx2, busy)])
        else:
            if k == 1:
                p.e(GEN, i, jpa, (x + w / 2, y), (x + w / 2, yj + jh))
            else:
                p.e(GEN, i, jpa, (x, ym), (tx1, yj + jh), [(ch1, ym), (ch1, busy), (tx1, busy)])
    rb = yrow - 30
    n4 = p.note('Repository: интерфейсы Spring Data JPA, реализации создаются Spring. @Lock(PESSIMISTIC_WRITE) в lockBy… — '
                'SELECT … FOR UPDATE. Ещё 6 интерфейсов того же вида: AccrualRuleRepository, BusinessEventRepository, '
                'KpiResultRepository, OperationReviewRepository, DepartmentRepository, IntegrationRepository.',
                X0, rb + 25, X1 - X0)
    rp_b = p.bottom(n4)
    p.frame('Паттерн Repository (доступ к данным)', 12, fy2, 1176, rp_b + 20 - fy2)
    return pages


# =====================================================================
# 2. Диаграмма пакетов
# =====================================================================
def pkg(p, label, x, y, w, hh, container=False, sub=None, fill=None):
    fs = 12
    if container:
        tabw = tw(label, 13, True) + 24
        st = (f'shape=folder;html=1;tabWidth={tabw:.0f};tabHeight=26;tabPosition=left;fillColor={fill or "#FFFFFF"};'
              f'strokeColor={CS};strokeWidth=1.5;verticalAlign=top;align=left;spacingLeft=10;spacingTop=4;fontSize=13;'
              f'fontStyle=1;fontColor={INK};{FONT}')
        i = p.v(h(label), x, y, w, hh, st)
        p.g[i] = (x, y, w, hh)
        return i
    st = (f'shape=folder;html=1;tabWidth=46;tabHeight=14;tabPosition=left;fillColor={fill or CF};strokeColor={CS};'
          f'strokeWidth=1.5;verticalAlign=middle;align=center;spacingTop=14;fontSize={fs};fontColor={INK};{FONT}')
    val = f'<b>{h(label)}</b>' + (f'<br>{hl(sub if isinstance(sub, list) else [sub])}' if sub else '')
    return p.v(val, x, y, w, hh, st)


def package_diagram():
    p = Page('Пакеты')
    D = DEP
    be = pkg(p, 'by.technosoft.bonus — серверная часть (Spring Boot 3.3, Java 21; 107 классов)', 20, 20, 1160, 680, True)
    sv = pkg(p, 'service — сервисный слой', 40, 70, 900, 390, True, fill='#F8F9FF')
    root = p.v(f'<b>Классы пакета service (9)</b><br>' + hl(['AccrualService, BonusCalculationService, RedemptionService,',
                                                          'CorrectionService, ReviewService, EventProcessingService,',
                                                          'AccessPolicy, OperationNumberGenerator, PointsFormat']),
               60, 115, 540, 90, f'rounded=0;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;'
                                 f'fontSize=12;fontColor={INK};align=center;verticalAlign=middle;{FONT}')
    row = 300
    W5 = [(60, 160), (240, 160), (420, 160), (600, 160), (780, 150)]
    names = [('strategy', 'стратегии расчёта (5)'), ('dto', 'запросы и результаты (14)'), ('event', 'события (3)'),
             ('exception', 'исключения (9)'), ('listener', 'слушатели (2)')]
    sub = {}
    for (x, w), (n, d) in zip(W5, names):
        sub[n] = pkg(p, n, x, row, w, 70, sub=d)
    cfg = pkg(p, 'config', 980, 115, 180, 70, sub='конфигурация (4)')
    rep = pkg(p, 'repository', 980, 300, 180, 70, sub='репозитории JPA (15)')
    dm = pkg(p, 'domain — предметная область (33 класса)', 40, 540, 1120, 150, True, fill='#F8F9FF')
    p.t(['Сущности JPA (18): Operation, BonusAccount, Employee, AppUser, AccrualRule,',
         'Campaign, Program, BusinessEvent, AuditLog, Notification, …',
         'Перечисления (13): OperationStatus, OperationType, RulePeriodicity, …',
         'Значения: OperationNumber, CampaignDepartmentId; интерфейс PersistentEnum'], 60, 580, fs=12, bg=None)
    conv = pkg(p, 'converter', 900, 590, 230, 76, sub=['PersistentEnumConverter<E>', '+ 11 конвертеров'])
    dtop = 540
    dtab = 40 + tw('domain — предметная область (33 класса)', 13, True) + 24

    def dy(x):
        return dtop if x <= dtab else dtop + 26
    # зависимости «import»
    p.e(D, root, cfg, (600, 135), (980, 135))
    p.e(D, cfg, root, (980, 165), (600, 165))
    p.e(D, root, sub['strategy'], (140, 205), (140, row))
    p.e(D, root, sub['dto'], (320, 205), (320, row))
    p.e(D, root, sub['event'], (500, 205), (500, row))
    p.e(D, root, sub['exception'], (590, 205), (680, row), [(590, 250), (680, 250)])
    p.e(D, sub['listener'], root, (850, row), (560, 205), [(850, 230), (560, 230)])
    p.e(D, root, rep, (600, 190), (980, 320), [(955, 190), (955, 320)])
    p.e(D, sub['listener'], rep, (930, 345), (980, 345))
    p.e(D, root, dm, (230, 205), (230, dy(230)))
    p.e(D, sub['strategy'], dm, (120, row + 70), (120, dy(120)))
    p.e(D, sub['strategy'], sub['exception'], (190, row + 70), (700, row + 70), [(190, 420), (700, 420)])
    p.e(D, sub['dto'], dm, (320, row + 70), (320, dy(320)))
    p.e(D, sub['event'], dm, (500, row + 70), (500, dy(500)))
    p.e(D, sub['listener'], sub['event'], (820, row + 70), (540, row + 70), [(820, 400), (540, 400)])
    p.e(D, sub['listener'], dm, (900, row + 70), (900, dy(900)))
    p.e(D, rep, dm, (1070, 370), (1070, dy(1070)))
    p.t('«import»', 610, 131, anchor='bl', bg=None)

    # ---- клиентская часть
    fe = pkg(p, 'frontend/src — клиентская часть (SPA: React 18, TypeScript, Ant Design)', 20, 760, 1160, 520, True)
    r1, r2, r3 = 820, 970, 1120
    lay = pkg(p, 'layout', 60, r1, 240, 70, sub='каркас, меню, уведомления')
    pg = pkg(p, 'pages (+ admin)', 640, r1, 360, 70, sub='экраны UC1–UC13 (20)')
    au = pkg(p, 'auth', 60, r2, 240, 70, sub='вход, роль, RequireAuth')
    cm = pkg(p, 'components', 400, r2, 200, 70, sub='общие UI-компоненты')
    hk = pkg(p, 'hooks', 700, r2, 200, 70, sub='useAsync, useIsMobile')
    api = pkg(p, 'api (+ mock)', 60, r3, 340, 70, sub='BonusApi, типы, mockApi')
    th = pkg(p, 'theme', 500, r3, 200, 70, sub='токены, тема antd')
    ut = pkg(p, 'utils', 800, r3, 200, 70, sub='форматирование, CSV')
    p.t(['Корень src: main.tsx,', 'App.tsx, routes.tsx'], 985, r2 + 20, fs=12, bg=None)
    p.e(D, lay, pg, (300, r1 + 35), (640, r1 + 35))
    p.t('«маршруты»', 470, r1 + 32, anchor='bc', bg=None)
    p.e(D, lay, au, (180, r1 + 70), (180, r2))
    p.e(D, pg, au, (680, r1 + 70), (260, r2), [(680, 910), (260, 910)])
    p.e(D, pg, cm, (720, r1 + 70), (520, r2), [(720, 935), (520, 935)])
    p.e(D, pg, hk, (800, r1 + 70), (800, r2))
    p.e(D, pg, api, (950, r1 + 70), (340, r3), [(950, 1080), (340, 1080)])
    p.e(D, hk, api, (740, r2 + 70), (300, r3), [(740, 1060), (300, 1060)])
    p.e(D, au, api, (180, r2 + 70), (180, r3))
    p.e(D, cm, th, (560, r2 + 70), (560, r3))
    p.e(D, cm, ut, (590, r2 + 70), (850, r3), [(590, 1095), (850, 1095)])
    n = p.note('Связи pages, layout и hooks с пакетами theme и utils не показаны, чтобы не загромождать схему.',
               60, 1215, 700)
    # клиент -> сервер
    p.e(D, api, be, (60, r3 + 50), (38, 700), [(38, r3 + 50)])
    p.t('«use»  HTTPS, REST/JSON — через REST-контроллеры (планируются); сейчас данные отдаёт mockApi', 50, 730,
        anchor='cl', bg=None)
    return p


# =====================================================================
# 3. Диаграмма компонентов
# =====================================================================
def comp(p, name, x, y, w, hh, sub=None, stereo='component', dashed=False, fill=None, valign='middle'):
    lines = f'«{h(stereo)}»<br><b>{h(name)}</b>' + (f'<br>{hl(sub if isinstance(sub, list) else [sub])}' if sub else '')
    st = (f'rounded=0;html=1;whiteSpace=nowrap;fillColor={fill or CF};strokeColor={CS};strokeWidth=1.5;fontSize=12;'
          f'fontColor={INK};align=center;verticalAlign={valign};spacingTop=4;{FONT}' + ('dashed=1;dashPattern=8 4;' if dashed else ''))
    i = p.v(lines, x, y, w, hh, st)
    p.v('', x + w - 24, y + 7, 16, 18, f'shape=module;jettyWidth=7;jettyHeight=3;html=1;fillColor=#FFFFFF;strokeColor={CS};')
    return i


def subsystem(p, label, x, y, w, hh):
    st = (f'rounded=0;html=1;whiteSpace=nowrap;fillColor=#F8F9FF;strokeColor={CS};strokeWidth=2;verticalAlign=top;'
          f'align=left;spacingLeft=12;spacingTop=6;fontSize=13;fontColor={INK};{FONT}')
    i = p.v(f'«subsystem» <b>{h(label)}</b>', x, y, w, hh, st, back=True)
    p.v('', x + w - 28, y + 9, 18, 20, f'shape=module;jettyWidth=8;jettyHeight=3;html=1;fillColor=#FFFFFF;strokeColor={CS};')
    return i


def ball_socket(p, x, y, label, lx=None, ly=None, anchor='cl'):
    """Сборочный соединитель «шар–гнездо» с вертикальной осью (шар снизу — предоставляет нижний компонент)."""
    p.v('', x - 11, y - 11, 22, 22, f'ellipse;html=1;fillColor=#FFFFFF;strokeColor={INK};strokeWidth=1.5;')
    p.v('', x - 17, y - 21, 34, 22, f'shape=requiredInterface;html=1;direction=north;fillColor=none;strokeColor={INK};strokeWidth=1.5;')
    p.t(label, lx if lx is not None else x + 24, ly if ly is not None else y, anchor=anchor, fs=12, bold=True, bg=None)


def component_diagram():
    p = Page('Компоненты')
    D = DEP
    # ---- клиент
    subsystem(p, 'Клиентская часть — SPA (React 18, TypeScript, Ant Design)', 20, 20, 1160, 290)
    c0, c1, c2, c3 = 50, 330, 610, 890
    W, H = 250, 70
    y1, y2 = 90, 210
    lay = comp(p, 'Макет и маршрутизация', c0, y1, W, H, 'layout, routes')
    pgs = comp(p, 'Страницы', c1, y1, W, H, 'pages: экраны UC1–UC13')
    aut = comp(p, 'Аутентификация', c2, y1, W, H, 'auth: вход, роль')
    thm = comp(p, 'Тема оформления', c0, y2, W, H, 'theme: токены, antd')
    ui = comp(p, 'Общие UI-компоненты', c1, y2, W, H, 'components, hooks')
    dat = comp(p, 'Слой данных', c2, y2, W, H, 'api: интерфейс BonusApi')
    p.note('Сейчас BonusApi реализован mockApi (демо-данные); после появления REST-контроллеров '
           'слой данных обращается к серверу по HTTPS.', c3, y2, 250)
    p.e(D, lay, pgs, (c0 + W, y1 + 35), (c1, y1 + 35))
    p.e(D, pgs, aut, (c1 + W, y1 + 35), (c2, y1 + 35))
    p.e(D, lay, aut, (c0 + 125, y1), (c2 + 125, y1), [(c0 + 125, y1 - 18), (c2 + 125, y1 - 18)])
    p.e(D, lay, thm, (c0 + 125, y1 + H), (c0 + 125, y2))
    p.e(D, pgs, ui, (c1 + 100, y1 + H), (c1 + 100, y2))
    p.e(D, pgs, dat, (c1 + 200, y1 + H), (c2 + 60, y2), [(c1 + 200, y1 + H + 25), (c2 + 60, y1 + H + 25)])
    p.e(D, aut, dat, (c2 + 180, y1 + H), (c2 + 180, y2))
    p.e(D, ui, thm, (c1, y2 + 35), (c0 + W, y2 + 35))
    # ---- сервер
    subsystem(p, 'Серверная часть — монолит Spring Boot 3.3 (Java 21)', 20, 380, 840, 640)
    ry = 440
    cfg = comp(p, 'Конфигурация', 50, ry, 250, 70, 'config: BonusProperties, Clock')
    rest = comp(p, 'REST API', c2, ry, 230, 70, 'контроллеры (планируются)', dashed=True)
    bl = p.v('«component» <b>Бизнес-логика</b> (service)', 40, 560, 800, 300,
             f'rounded=0;html=1;whiteSpace=nowrap;fillColor=#FFFFFF;strokeColor={CS};strokeWidth=1.5;verticalAlign=top;'
             f'align=left;spacingLeft=10;spacingTop=6;fontSize=13;fontColor={INK};{FONT}')
    p.v('', 40 + 800 - 26, 560 + 8, 16, 18, f'shape=module;jettyWidth=7;jettyHeight=3;html=1;fillColor=#FFFFFF;strokeColor={CS};')
    ix = [60, 330, 600]
    iy = [605, 690, 775]
    IW, IH = 225, 62
    inner = [('Начисления', 'AccrualService'), ('Списания', 'RedemptionService'), ('Обработка событий', 'EventProcessingService'),
             ('Корректировки', 'CorrectionService'), ('Согласование', 'ReviewService'), ('Политика доступа', 'AccessPolicy'),
             ('Расчёт бонусов', 'BonusCalculationService + Strategy'), ('Нумерация операций', 'OperationNumberGenerator')]
    ids = []
    for k, (n, s) in enumerate(inner):
        ids.append(comp(p, n, ix[k % 3], iy[k // 3], IW if k != 6 else 245, IH, s))
    lst = comp(p, 'Слушатели событий', 560, 910, 280, 70, 'listener: аудит, уведомления')
    dao = comp(p, 'Доступ к данным', 40, 910, 330, 70, 'repository: Spring Data JPA')
    p.e(D, rest, bl, (c2 + 115, ry + 70), (c2 + 115, 560))
    p.e(D, bl, cfg, (175, 560), (175, ry + 70))
    p.e(D, bl, dao, (205, 860), (205, 910))
    p.e(D, bl, lst, (700, 860), (700, 910))
    p.t('«events»', 708, 885, anchor='cl', bg=None)
    p.e(D, lst, dao, (560, 945), (370, 945))
    # клиент — REST API
    ax = c2 + 115
    p.e(LINE, dat, None, (ax, y2 + H), (ax, 343 - 21))
    p.e(LINE, rest, None, (ax, ry), (ax, 343 + 11))
    ball_socket(p, ax, 343, 'REST API (HTTPS, JSON)')
    # БД
    db = p.v('«database»<br><b>PostgreSQL 14</b><br>БД tpsis_bonus_app', 40, 1090, 330, 86,
             f'shape=cylinder3;html=1;boundedLbl=1;size=12;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize=12;'
             f'fontColor={INK};{FONT}')
    p.e(LINE, dao, None, (205, 980), (205, 1046 - 21))
    p.e(LINE, db, None, (205, 1090), (205, 1046 + 11))
    ball_socket(p, 205, 1046, 'JDBC (TCP 5432)')
    # внешние системы
    ex = 960
    EW = 200
    hr = comp(p, 'HRIS', ex, 590, EW, 56, 'кадровые данные', stereo='external system', fill='#FFFFFF')
    erp = comp(p, 'ERP', ex, 670, EW, 56, 'проекты, KPI', stereo='external system', fill='#FFFFFF')
    ad = comp(p, 'Active Directory', ex, 760, EW, 56, 'учётные записи', stereo='external system', fill='#FFFFFF')
    ns = comp(p, 'Сервис уведомлений', ex, 905, EW, 70, 'e-mail, push', stereo='external system', fill='#FFFFFF')
    ev = ids[2]
    p.e(D, ev, hr, (825, 622), (ex, 618), [(880, 622), (880, 618)])
    p.e(D, ev, erp, (825, 648), (ex, 698), [(900, 648), (900, 698)])
    p.e(D, ids[5], ad, (825, 721), (ex, 788), [(920, 721), (920, 788)])
    p.e(D, lst, ns, (840, 945), (ex, 945))
    p.t('HTTPS, REST/JSON', ex + EW / 2, 575, anchor='bc', bg=None)
    p.t('HTTPS', 900, 940, anchor='bc', bg=None)
    p.note('Подключения к внешним системам настраиваются в разделе «Интеграции» (UC13); события HRIS/ERP '
           'сохраняются в business_event и обрабатываются компонентом «Обработка событий» (UC4).', ex - 20, 400, 240)
    return p


# =====================================================================
# 4. Диаграмма размещения
# =====================================================================
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


def deployment_diagram():
    p = Page('Размещение')
    # рабочая станция
    ws = node(p, 'Рабочая станция пользователя (ПК, планшет, смартфон)', 20, 20, 500, 330)
    os_ = node(p, 'ОС Windows 10/11, Android или iOS', 40, 85, 460, 250, 'executionEnvironment', depth=10)
    br = node(p, 'Веб-браузер (Chrome, Edge, Safari, Firefox)', 60, 150, 420, 170, 'executionEnvironment', fill='#F8F9FF', depth=10)
    artifact(p, 'SPA TechnoSoft Bonus', 80, 215, 380, 88, ['index.html, assets/*.js, *.css', '(сборка Vite: React 18, Ant Design)'])
    # сервер приложений
    ap = node(p, 'Сервер приложений', 680, 20, 500, 330)
    jvm = node(p, 'JVM 21 (Spring Boot 3.3, встроенный Tomcat)', 700, 85, 460, 250, 'executionEnvironment', fill='#F8F9FF', depth=10)
    artifact(p, 'bonus.jar', 720, 150, 420, 92, ['REST API, сервисы, слушатели событий,', 'репозитории Spring Data JPA, Flyway'])
    artifact(p, 'application.yml', 720, 258, 420, 62, ['лимит начисления, часовой пояс, подключение к БД'])
    # сервер БД
    dbs = node(p, 'Сервер БД', 760, 460, 420, 260)
    pg = node(p, 'PostgreSQL 14', 780, 525, 380, 180, 'executionEnvironment', fill='#F8F9FF', depth=10)
    artifact(p, 'БД tpsis_bonus_app', 800, 590, 340, 92, ['схема V1__schema.sql (Flyway):', '18 таблиц, ограничения CHECK'])
    # внешние системы
    ext = node(p, 'Внешние системы', 20, 460, 560, 260, 'node')
    xs = [('HRIS', 'кадровые данные'), ('ERP', 'проекты, KPI'), ('Active Directory', 'учётные записи'),
          ('Сервис уведомлений', 'e-mail, push')]
    for k, (n, s) in enumerate(xs):
        x = 40 + (k % 2) * 270
        y = 530 + (k // 2) * 90
        i = node(p, n, x, y, 250, 74, 'node', fill='#F8F9FF', depth=8)
        p.t(s, x + 5, y + 52, fs=12, anchor='cl', bg=None)
    # связи
    p.e(LINE + 'strokeWidth=2;', None, None, (520, 190), (680, 190))
    p.t(['«HTTPS»', 'REST/JSON,', 'TCP 443'], 600, 185, anchor='bc', bg=None, fs=12)
    p.e(LINE + 'strokeWidth=2;', None, None, (970, 350), (970, 460))
    p.t(['«JDBC»', 'TCP 5432'], 980, 405, anchor='cl', bg=None, fs=12)
    p.e(LINE + 'strokeWidth=2;', None, None, (710, 350), (580, 600), [(710, 600)])
    p.t(['«HTTPS»', 'REST/JSON'], 700, 405, anchor='cr', align='right', bg=None, fs=12)
    return p


# =====================================================================
# 5. Диаграмма состояний объекта «Операция»
# =====================================================================
def state_diagram():
    p = Page('Состояния операции')
    S = (f'rounded=1;arcSize=35;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize=13;'
         f'fontColor={INK};{FONT}')
    SW, SH = 240, 58
    C0, C1, C2 = 30, 470, 920          # левые границы колонок
    R0, R1, R2 = 230, 640, 900         # верх строк
    st = {}
    for key, name, val, x, y in [('A', 'Начислено', 'accrued', C1, R0), ('X', 'Отменено', 'cancelled', C2, R0),
                                 ('P', 'На согласовании', 'pending', C1, R1), ('C', 'Скорректировано', 'corrected', C2, R1),
                                 ('W', 'Списано', 'written_off', C0, R2), ('B', 'Заблокировано', 'blocked', C1, R2)]:
        st[key] = p.v(f'<b>{h(name)}</b><br>({val})', x, y, SW, SH, S)
    IX, IY = 150, R1 + SH / 2           # центр начального состояния
    ini = p.v('', IX - 13, IY - 13, 26, 26, f'ellipse;html=1;fillColor={INK};strokeColor={INK};')
    TR = EB + 'endArrow=open;endFill=0;endSize=12;'
    fs = 11

    def lab(text, x, y, width, anchor='tl', align='left'):
        import re
        text = re.sub(r' (?=[+−=≥≤<>Δ0])|(?<=[+−=≥≤<>]) ', '\u00a0', text)
        p.t(wrap(text, width, fs), x, y, fs=fs, anchor=anchor, align=align, bg='#FFFFFF')
    # ---- переходы из начального состояния (событие «создание операции»)
    # -> Отменено: вверх, по верхней кромке, вниз
    ytop = 120
    p.e(TR, ini, st['X'], (IX, IY - 13), (C2 + SW / 2, R0), [(IX, ytop), (C2 + SW / 2, ytop)])
    lab('создание [отмена начисления, баланс − сумма исходной ≥ 0] / исходная → Отменено, баланс −= сумма исходной',
        IX + 330, ytop - 4, 800, anchor='bl')
    # -> Начислено: диагональ
    p.e(TR, ini, st['A'], (IX + 9, IY - 9), (C1, R0 + SH * 0.65))
    lab('создание [автоначисление по событию или ручное начисление ≤ 1000] / баланс += сумма',
        C1 - 10, R0 + SH * 0.65 - 14, 250, anchor='br', align='right')
    # -> На согласовании: горизонталь
    p.e(TR, ini, st['P'], (IX + 13, IY), (C1, IY))
    lab('создание [ручное начисление > 1000 или корректировка, после которой баланс < 0]',
        C1 - 10, IY - 4, 250, anchor='br', align='right')
    # -> Списано: вниз
    p.e(TR, ini, st['W'], (IX, IY + 13), (IX, R2))
    lab('создание [списание на программу, баланс ≥ стоимости] / баланс −= стоимость', IX + 10, IY + 90, 250)
    # -> Скорректировано: влево, вниз, по нижней кромке, вверх
    ybot = R2 + SH + 60
    xl = 14
    p.e(TR, ini, st['C'], (IX - 13, IY), (C2 + SW / 2, R1 + SH), [(xl, IY), (xl, ybot), (C2 + SW / 2, ybot)])
    lab('создание [корректировка суммы, баланс + Δ ≥ 0] / исходная → Отменено, баланс += Δ',
        C0 + 280, ybot + 4, 600)
    # ---- из «На согласовании»
    xa = C1 + 70
    p.e(TR, st['P'], st['A'], (xa, R1), (xa, R0 + SH))
    lab('подтверждение финотделом [начисление, счёт активен] / баланс += сумма', xa + 8, R0 + SH + 40, 175)
    p.e(TR, st['P'], st['X'], (C1 + SW, R1 + 10), (C2, R0 + SH - 10), [(C1 + SW + 40, R1 + 10), (C1 + SW + 40, R0 + SH - 10)])
    lab('подтверждение финотделом [отмена, баланс + Δ ≥ 0] / исходная → Отменено, баланс += Δ',
        C1 + SW + 50, R0 + SH + 40, 150)
    p.e(TR, st['P'], st['C'], (C1 + SW, R1 + SH * 0.6), (C2, R1 + SH * 0.6))
    lab('подтверждение финотделом [корректировка суммы, баланс + Δ ≥ 0] / исходная → Отменено, баланс += Δ',
        C1 + SW + 12, R1 + SH * 0.6 + 6, 190)
    xb = C1 + 120
    p.e(TR, st['P'], st['B'], (xb, R1 + SH), (xb, R2))
    lab('блокировка финотделом [причина указана] / баланс не меняется', xb + 8, R1 + SH + 60, 220)
    # ---- в «Отменено»
    p.e(TR, st['A'], st['X'], (C1 + SW, R0 + 20), (C2, R0 + 20))
    lab('проведена корректировка или отмена этой операции [нет корректировки на согласовании] / баланс += Δ',
        C1 + SW + 10, R0 + 16, 190, anchor='bl')
    xc = C2 + SW - 50
    p.e(TR, st['C'], st['X'], (xc, R1), (xc, R0 + SH))
    lab('повторная корректировка или отмена [нет корректировки на согласовании] / баланс += Δ',
        xc - 8, (R0 + SH + R1) / 2 + 20, 150, anchor='cr', align='right')
    p.note('Конечные состояния: Списано, Заблокировано, Отменено — переходов из них нет '
           '(OperationStatus.canTransitionTo(…) = false). Переход выполняет Operation.transitionTo(target). '
           'Корректируется и отменяется только операция в состоянии accrued или corrected. '
           'Инвариант: баланс счёта = сумма операций в состояниях accrued, written_off, corrected.',
           C0, ybot + 60, 1150)
    return p


# =====================================================================
# 6. Диаграмма последовательности UC5 «Ручное начисление»
# =====================================================================
SEQ_MSGS = []   # (номер, отправитель, получатель, сообщение) — для отчёта


def sequence_diagram():
    p = Page('Последовательность UC5')
    fs = 11
    heads = [('A', None), ('C', ['«REST-контроллер»', ':AccrualController']), ('S', [':AccrualService']),
             ('P', [':AccessPolicy']), ('E', [':Employee', 'Repository']), ('B', [':Bonus', 'Calculation', 'Service']),
             ('R', [':BonusAccount', 'Repository']), ('N', [':Operation', 'Number', 'Generator']), ('O', [':Operation']),
             ('BA', [':BonusAccount']), ('OR', [':Operation', 'Repository']), ('EP', [':Application', 'Event', 'Publisher']),
             ('AL', [':Audit', 'Listener']), ('NL', [':Notification', 'Listener'])]
    names = {'A': 'Руководитель / HR', 'C': 'AccrualController', 'S': 'AccrualService', 'P': 'AccessPolicy',
             'E': 'EmployeeRepository', 'B': 'BonusCalculationService', 'R': 'BonusAccountRepository',
             'N': 'OperationNumberGenerator', 'O': 'Operation', 'BA': 'BonusAccount', 'OR': 'OperationRepository',
             'EP': 'ApplicationEventPublisher', 'AL': 'AuditListener', 'NL': 'NotificationListener'}
    # ширины заголовков
    hw = {}
    for k, lines in heads:
        hw[k] = 70 if lines is None else max(tw(l, fs) for l in lines) + 14
    gap = {'C': 135}          # расстояние между центрами соседних линий жизни (минимум)
    X = {}
    x = 50
    prev = None
    for k, _ in heads:
        if prev is None:
            X[k] = x
        else:
            d = max(gap.get(k, 0), (hw[prev] + hw[k]) / 2 + 6)
            X[k] = X[prev] + d
        prev = k
    print('  seq width', X['NL'] + hw['NL'] / 2)
    HT, HH = 20, 58
    yend = None
    # заголовки
    for k, lines in heads:
        if lines is None:
            p.v('', X[k] - 13, HT, 26, 44, f'shape=umlActor;html=1;verticalLabelPosition=bottom;verticalAlign=top;'
                                           f'fillColor=#FFFFFF;strokeColor={INK};strokeWidth=1.5;')
            p.t(['Руководитель', '/ HR'], X[k], HT + 46, fs=fs, anchor='tc', align='center', bg=None)
        else:
            p.v(hl(lines), X[k] - hw[k] / 2, HT, hw[k], HH,
                f'rounded=0;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize={fs};'
                f'fontColor={INK};{FONT}' + ('dashed=1;dashPattern=8 4;' if k == 'C' else ''))
    y = HT + HH + 40
    rows = []          # события в порядке
    ACT = {}           # интервалы активации
    BW = 12
    SYNC = EB + 'endArrow=block;endFill=1;endSize=9;strokeWidth=1.2;'
    RET = EB + 'endArrow=open;endFill=0;endSize=9;dashed=1;dashPattern=6 4;strokeWidth=1.2;'
    CRE = RET
    msgs = []
    num = [0]
    labels = []
    frames = []

    def off(k, side):
        # смещение конца стрелки к краю полосы активации
        return X[k] + (BW / 2 if side > 0 else -BW / 2) if k != 'A' else X[k]

    def msg(a, b, text, kind='sync', lines=None, dy=None):
        nonlocal y
        num[0] += 1
        n = num[0]
        lab = (lines or [text])
        lab = [f'{n}: {lab[0]}'] + lab[1:]
        lh = len(lab) * fs * 1.25 + 4
        y += max(lh, 16) + 8
        if a == b:
            x0 = X[a] + BW / 2
            p.e(SYNC, None, None, (x0, y), (x0, y + 18), [(x0 + 34, y), (x0 + 34, y + 18)])
            p.t(lab, x0 + 40, y + 9, fs=fs, anchor='cl', bg='#FFFFFF')
            y += 18
        else:
            d = 1 if X[b] > X[a] else -1
            sx, tx = off(a, d), off(b, -d)
            p.e({'sync': SYNC, 'ret': RET, 'create': CRE}[kind], None, None, (sx, y), (tx, y))
            if d > 0:
                p.t(lab, sx + 6, y - 2, fs=fs, anchor='bl', bg='#FFFFFF')
            else:
                p.t(lab, tx + 6, y - 2, fs=fs, anchor='bl', bg='#FFFFFF')
        SEQ_MSGS.append((n, names[a], names[b], ' '.join(lines) if lines else text))
        return y

    def act(k, y0, y1):
        ACT.setdefault(k, []).append((y0, y1))

    def frame(kind, guard, x0, x1, y0, y1):
        frames.append((kind, guard, x0, x1, y0, y1))

    t0 = y + 8
    m1 = msg('A', 'C', '', lines=['начислить(employeeIds,', 'amount, basis, reason)'])
    m2 = msg('C', 'S', '', lines=['create(actorUserId,', 'request)'])
    m3 = msg('S', 'P', '', lines=['requireActiveUser(', 'actorUserId)'])
    m4 = msg('P', 'S', 'actor', 'ret')
    act('P', m3, m4)
    m5 = msg('S', 'P', '', lines=['requireRole(actor,', 'MANAGER, HR)'])
    act('P', m5, m5 + 14)
    m6 = msg('S', 'S', 'validate(request)')
    m7 = msg('S', 'S', 'buildPreview(actor, request, today)')
    y += 30
    l1 = y - 6
    y += 22
    m8 = msg('S', 'E', 'findById(employeeId)')
    m9 = msg('E', 'S', 'employee', 'ret')
    act('E', m8, m9)
    m10 = msg('S', 'P', '', lines=['requireCanManage(', 'actor, employee)'])
    act('P', m10, m10 + 14)
    m11 = msg('S', 'B', '', lines=['calculateManual(employeeId,', 'amount, campaignId, today)'])
    m12 = msg('B', 'S', 'CalculationResult', 'ret')
    act('B', m11, m12)
    y += 16
    frame('loop', '[для каждого сотрудника из employeeIds]', X['S'] - 80, X['B'] + 70, l1, y)
    y += 34
    l2 = y - 6
    y += 22
    m13 = msg('S', 'R', 'lockByEmployeeId(employeeId)')
    m14 = msg('R', 'S', 'account', 'ret')
    act('R', m13, m14)
    y += 16
    frame('loop', '[для каждой строки предпросмотра: блокировка счёта (FOR UPDATE)]', X['S'] - 80, X['R'] + 70, l2, y)
    y += 34
    l3 = y - 6
    y += 22
    m15 = msg('S', 'N', 'next()')
    m16 = msg('N', 'S', 'OperationNumber', 'ret')
    act('N', m15, m16)
    y += 30
    a1 = y - 6
    y += 22
    m17 = msg('S', 'O', '', 'create', lines=['«create» create(number, account, ACCRUAL,', 'PENDING, …).withRule().withCampaign()'])
    act('O', m17, m17 + 14)
    y += 18
    asep = y
    y += 26
    m18 = msg('S', 'O', '', 'create', lines=['«create» create(number, account, ACCRUAL,', 'ACCRUED, …).withRule().withCampaign()'])
    act('O', m18, m18 + 14)
    m19 = msg('S', 'BA', 'applyDelta(total, now)')
    act('BA', m19, m19 + 14)
    y += 16
    a2 = y
    frame('alt', '[total > лимита (1000): требуется проверка финотдела]', X['S'] - 68, X['BA'] + 60, a1, a2)
    m20 = msg('S', 'OR', 'save(op)')
    act('OR', m20, m20 + 14)
    m21 = msg('S', 'EP', 'publishEvent(OperationRegisteredEvent)')
    m22 = msg('EP', 'AL', 'onOperationRegistered(e)')
    act('AL', m22, m22 + 16)
    act('EP', m21, m22 + 16)
    y += 18
    frame('loop', '[для каждой строки предпросмотра: создание операции]', X['S'] - 80, X['NL'] + 52, l3, y)
    y += 34
    o1 = y - 6
    y += 22
    m23 = msg('EP', 'NL', 'onOperationRegistered(e)')
    act('EP', m23, m23 + 16)
    act('NL', m23, m23 + 16)
    y += 18
    frame('opt', '[после фиксации транзакции (AFTER_COMMIT)]', X['OR'] - 50, X['NL'] + 52, o1, y)
    m24 = msg('S', 'C', '', 'ret', lines=['AccrualResult(operations,', 'accruedCount, pendingCount)'])
    act('S', m2, m24)
    m25 = msg('C', 'A', 'результат', 'ret')
    act('C', m1, m25)
    yend = y + 30
    # линии жизни
    for k, lines in heads:
        top = HT + (HH if lines else 82)
        p.e(EB + f'endArrow=none;dashed=1;dashPattern=5 4;strokeColor={GR};strokeWidth=1;', None, None, (X[k], top), (X[k], yend), back=True)
    # рамки фрагментов
    for kind, guard, x0, x1, y0, y1 in frames:
        st = (f'shape=umlFrame;html=1;width={tw(kind, 12, True) + 26:.0f};height=22;verticalAlign=top;align=left;'
              f'spacingLeft=6;spacingTop=1;fontSize=12;fontStyle=1;fontColor={INK};strokeColor={INK};fillColor=none;{FONT}')
        p.v(kind, x0, y0, x1 - x0, y1 - y0, st, back=True)
        p.t(guard, max(x0 + tw(kind, 12, True) + 34, X['S'] + 12), y0 + 12, fs=fs, anchor='cl', bg='#FFFFFF')
    # разделитель alt
    fa = [f for f in frames if f[0] == 'alt'][0]
    p.e(EB + 'endArrow=none;dashed=1;dashPattern=6 4;', None, None, (fa[2], asep), (fa[3], asep))
    p.t('[иначе]', X['S'] + 12, asep + 12, fs=fs, anchor='cl', bg='#FFFFFF')
    # полосы активации
    for k, ivs in ACT.items():
        for y0, y1 in ivs:
            p.v('', X[k] - BW / 2, y0 - 4, BW, (y1 - y0) + 8, f'rounded=0;html=1;fillColor=#FFFFFF;strokeColor={INK};strokeWidth=1.2;',
                back=True)
    # примечание о вложенных вызовах
    p.note('Вложенные вызовы (на диаграмме не показаны): 3 — AppUserRepository.findWithRoleById(id); '
           '11 — CampaignRepository.findById(campaignId), CampaignDepartmentRepository.existsByCampaignIdAndDepartmentId(…), '
           'OperationRepository.sumAmountByCampaign(…); 22 — AuditLogRepository.save(AuditLog) в той же транзакции; '
           '23 — AppUserRepository.findByEmployeeId(…), NotificationRepository.save(Notification) в новой транзакции '
           '(REQUIRES_NEW). Если счёт заблокирован (после 14), AccountBlockedException откатывает всё начисление.',
           20, yend + 20, X['NL'] + hw['NL'] / 2 - 20)
    return p


# =====================================================================
# 7. Диаграмма деятельности UC3 «Использование бонусов»
# =====================================================================
def activity_diagram():
    p = Page('Деятельность UC3')
    LW = 290
    LX = [20 + LW * i for i in range(4)]
    CX = [x + LW / 2 for x in LX]
    TOP, HDR = 20, 36
    P0, PITCH = 100, 66
    ry = lambda r: P0 + r * PITCH
    AW, AH = 236, 46
    ACT = (f'rounded=1;arcSize=50;html=1;whiteSpace=nowrap;fillColor={CF};strokeColor={CS};strokeWidth=1.5;fontSize=12;'
           f'fontColor={INK};{FONT}')
    ERRA = ACT.replace(CF, '#FFF0F0')
    FL = EB + 'endArrow=open;endFill=0;endSize=10;'
    nodes = {}

    def a(key, lane, r, text, w=AW, dx=0, err=False):
        lines = wrap(text, w - 18, 12)
        hh = max(AH, len(lines) * 15 + 14)
        i = p.v(hl(lines), CX[lane] - w / 2 + dx, ry(r) - hh / 2, w, hh, ERRA if err else ACT)
        nodes[key] = i
        return i

    def dia(key, lane, r, dx=0):
        i = p.v('', CX[lane] - 20 + dx, ry(r) - 20, 40, 40, f'rhombus;html=1;fillColor=#FFFFFF;strokeColor={INK};strokeWidth=1.5;')
        nodes[key] = i
        return i

    def bar(key, x0, x1, r):
        i = p.v('', x0, ry(r) - 4, x1 - x0, 8, f'rounded=0;html=1;fillColor={INK};strokeColor={INK};')
        nodes[key] = i
        return i
    rows_total = 22.6
    H = ry(rows_total) + 10
    for k, name in enumerate(['Сотрудник', 'Клиентская часть (SPA)', 'Серверная часть', 'База данных (PostgreSQL)']):
        p.v(h(name), LX[k], TOP, LW, H - TOP,
            f'swimlane;html=1;startSize={HDR};horizontal=1;collapsible=0;fillColor={CF};strokeColor={CS};strokeWidth=1.5;'
            f'swimlaneFillColor=#FFFFFF;fontSize=13;fontStyle=1;fontColor={INK};{FONT}', back=True)
    st = p.v('', CX[0] - 13, ry(0) - 13, 26, 26, f'ellipse;html=1;fillColor={INK};strokeColor={INK};')
    a('a1', 0, 1, 'Открыть раздел «Программы мотивации»')
    a('a2', 1, 2, 'Загрузить каталог программ и баланс счёта')
    a('a3', 0, 3, 'Выбрать программу, нажать «Использовать»')
    a('a4', 1, 4, 'Показать окно подтверждения: стоимость и баланс после списания')
    a('a5', 0, 5, 'Подтвердить списание')
    a('a6', 1, 6, 'Отправить запрос redeem(employeeId, programId)')
    a('a7', 2, 7, 'Начать транзакцию, найти программу по programId')
    a('d7', 3, 7, 'SELECT program')
    dia('q1', 2, 8)
    a('a8', 2, 9, 'Заблокировать бонусный счёт сотрудника')
    a('d8', 3, 9, 'SELECT … FOR UPDATE bonus_account')
    dia('q2', 2, 10)
    dia('q3', 2, 11)
    a('a9', 2, 12, 'Создать операцию writeoff, статус written_off, сумма −стоимость')
    a('a10', 2, 13, 'Уменьшить баланс: applyDelta(−стоимость)')
    a('a11', 2, 14, 'Сохранить операцию и счёт')
    a('d11', 3, 14, 'INSERT operation; UPDATE bonus_account')
    a('a12', 2, 15, 'Опубликовать OperationRegisteredEvent')
    fx0, fx1 = LX[2] + 20, LX[3] + LW - 20
    bar('f', LX[2] + 20, LX[2] + LW - 20, 16)
    SW2 = 138
    a('a13', 2, 17, 'Записать аудит (AuditListener)', w=SW2, dx=-72)
    a('a14', 2, 17, 'Уведомить после коммита (NotificationListener)', w=SW2, dx=72)
    a('d13', 3, 18, 'INSERT audit_log; COMMIT', w=SW2, dx=-72)
    a('d14', 3, 17, 'INSERT notification', w=SW2, dx=72)
    bar('j', LX[2] + 20, LX[3] + LW - 20, 19)
    a('a15', 1, 20, 'Показать результат: операция и новый баланс')
    a('a16', 0, 21, 'Увидеть обновлённый баланс и уведомление')
    en = p.v('', CX[0] - 14, ry(22) - 14, 28, 28, f'ellipse;shape=endState;html=1;fillColor={INK};strokeColor={INK};')
    # ветвь ошибок
    mg = dia('m', 1, 11, dx=-40)
    a('e1', 1, 12.6, 'Показать сообщение об ошибке (причина; при нехватке — недостающая сумма)', err=True)
    a('e2', 0, 13.8, 'Ознакомиться с сообщением', err=True)
    en2 = p.v('', CX[0] - 14, ry(15) - 14, 28, 28, f'ellipse;shape=endState;html=1;fillColor={INK};strokeColor={INK};')
    G = p.g
    def vflow(a_, b_, label=None, lx=None):
        ax, ay, aw, ah = G[a_]
        bx, by, bw, bh = G[b_]
        x1 = ax + aw / 2
        x2 = bx + bw / 2
        if abs(x1 - x2) < 1:
            p.e(FL, a_, b_, (x1, ay + ah), (x2, by))
        else:
            ym = (ay + ah + by) / 2
            p.e(FL, a_, b_, (x1, ay + ah), (x2, by), [(x1, ym), (x2, ym)])
        if label:
            p.t(label, x1 + 8, (ay + ah + by) / 2, fs=11, anchor='cl', bg='#FFFFFF')

    def hflow(a_, b_, back=False):
        ax, ay, aw, ah = G[a_]
        bx, by, bw, bh = G[b_]
        yy = ay + ah / 2
        if bx > ax:
            p.e(FL, a_, b_, (ax + aw, yy), (bx, yy))
        else:
            p.e(FL, a_, b_, (ax, yy), (bx + bw, yy))
    vflow(st, nodes['a1'])
    for s_, t_ in [('a1', 'a2'), ('a2', 'a3'), ('a3', 'a4'), ('a4', 'a5'), ('a5', 'a6'), ('a6', 'a7')]:
        vflow(nodes[s_], nodes[t_])
    hflow(nodes['a7'], nodes['d7'])
    # d7 -> q1 (снизу DB, влево в ромб)
    dx_, dy_, dw_, dh_ = G[nodes['d7']]
    qx, qy, qw, qh = G[nodes['q1']]
    p.e(FL, nodes['d7'], nodes['q1'], (dx_ + dw_ / 2, dy_ + dh_), (qx + qw, qy + qh / 2), [(dx_ + dw_ / 2, qy + qh / 2)])
    vflow(nodes['q1'], nodes['a8'], '[программа найдена и активна]')
    hflow(nodes['a8'], nodes['d8'])
    dx_, dy_, dw_, dh_ = G[nodes['d8']]
    qx, qy, qw, qh = G[nodes['q2']]
    p.e(FL, nodes['d8'], nodes['q2'], (dx_ + dw_ / 2, dy_ + dh_), (qx + qw, qy + qh / 2), [(dx_ + dw_ / 2, qy + qh / 2)])
    vflow(nodes['q2'], nodes['q3'], '[счёт активен]')
    vflow(nodes['q3'], nodes['a9'], '[баланс ≥ стоимости]')
    vflow(nodes['a9'], nodes['a10'])
    vflow(nodes['a10'], nodes['a11'])
    hflow(nodes['a11'], nodes['d11'])
    dx_, dy_, dw_, dh_ = G[nodes['d11']]
    ax_, ay_, aw_, ah_ = G[nodes['a12']]
    p.e(FL, nodes['d11'], nodes['a12'], (dx_ + dw_ / 2, dy_ + dh_), (ax_ + aw_, ay_ + ah_ / 2), [(dx_ + dw_ / 2, ay_ + ah_ / 2)])
    vflow(nodes['a12'], nodes['f'])
    fxx, fyy, fww, fhh = G[nodes['f']]
    for k in ('a13', 'a14'):
        bx, by, bw, bh = G[nodes[k]]
        p.e(FL, nodes['f'], nodes[k], (bx + bw / 2, fyy + fhh), (bx + bw / 2, by))
    # a13 -> d13 (вниз, затем вправо)
    ax_, ay_, aw_, ah_ = G[nodes['a13']]
    dx_, dy_, dw_, dh_ = G[nodes['d13']]
    p.e(FL, nodes['a13'], nodes['d13'], (ax_ + aw_ / 2, ay_ + ah_), (dx_, dy_ + dh_ / 2), [(ax_ + aw_ / 2, dy_ + dh_ / 2)])
    hflow(nodes['a14'], nodes['d14'])
    jx, jy, jw, jh = G[nodes['j']]
    for k in ('d13', 'd14'):
        bx, by, bw, bh = G[nodes[k]]
        p.e(FL, nodes[k], nodes['j'], (bx + bw / 2, by + bh), (bx + bw / 2, jy))
    ax_, ay_, aw_, ah_ = G[nodes['a15']]
    p.e(FL, nodes['j'], nodes['a15'], (jx + 60, jy + jh), (ax_ + aw_, ay_ + ah_ / 2), [(jx + 60, ay_ + ah_ / 2)])
    vflow(nodes['a15'], nodes['a16'])
    vflow(nodes['a16'], en)
    # ошибки
    mx, my, mw, mh = G[mg]
    q1x, q1y, q1w, q1h = G[nodes['q1']]
    q2x, q2y, q2w, q2h = G[nodes['q2']]
    q3x, q3y, q3w, q3h = G[nodes['q3']]
    lx = mx - 60
    p.e(FL, nodes['q1'], mg, (q1x, q1y + q1h / 2), (mx, my + mh / 2), [(lx, q1y + q1h / 2), (lx, my + mh / 2)])
    p.t(['[программа не найдена', 'или неактивна]'], LX[2] - 8, q1y + q1h / 2 - 2, anchor='br', align='right')
    p.e(FL, nodes['q2'], mg, (q2x, q2y + q2h / 2), (mx + mw / 2, my), [(mx + mw / 2, q2y + q2h / 2)])
    p.t('[счёт заблокирован]', LX[2] - 8, q2y + q2h / 2 - 2, anchor='br', align='right')
    p.e(FL, nodes['q3'], mg, (q3x, q3y + q3h / 2), (mx + mw, my + mh / 2))
    p.t('[баланс < стоимости]', LX[2] - 8, q3y + q3h / 2 + 2, anchor='tr', align='right')
    vflow(mg, nodes['e1'])
    ex_, ey_, ew_, eh_ = G[nodes['e1']]
    e2x, e2y, e2w, e2h = G[nodes['e2']]
    p.e(FL, nodes['e1'], nodes['e2'], (ex_, ey_ + eh_ / 2), (e2x + e2w / 2, e2y), [(e2x + e2w / 2, ey_ + eh_ / 2)])
    vflow(nodes['e2'], en2)
    p.note('Отказы сервера: ProgramUnavailableException, AccountBlockedException, InsufficientBalanceException '
           '(с недостающей суммой); транзакция откатывается, баланс не меняется.', LX[0] + 15, ry(7.6), LW - 30)
    return p


def build():
    return [class_sheet1(), *class_sheet2(), package_diagram(), component_diagram(), deployment_diagram(),
            state_diagram(), sequence_diagram(), activity_diagram()]


PNG = ['uml-class-patterns-1.png', 'uml-class-patterns-2.png', 'uml-class-patterns-3.png', 'uml-package.png',
       'uml-component.png', 'uml-deployment.png', 'uml-state-operation.png', 'uml-sequence-uc5.png', 'uml-activity-uc3.png']


if __name__ == '__main__':
    out = sys.argv[1]
    pages = build()
    open(out, 'w', encoding='utf8').write('<mxfile host="Electron">' + ''.join(pg.xml(k) for k, pg in enumerate(pages)) + '</mxfile>')
    print('ok', len(pages))
    if '--msgs' in sys.argv:
        for m in SEQ_MSGS:
            print(*m, sep=' | ')
