# -*- coding: utf-8 -*-
"""Генератор схем программ по ГОСТ 19.701-90 (ЕСПД) и чертежа формата A3 для ЛР 8.

ПС «TechnoSoft Bonus». Алгоритмы А1–А5 соответствуют коду backend/.../service/:
  А1 BonusCalculationService#calculate (+ KpiAccrualStrategy, FixedEventAccrualStrategy)
  А2 EventProcessingService#process
  А3 AccrualService#create (+ #buildPreview, BonusCalculationService#calculateManual)
  А4 RedemptionService#redeem
  А5 CorrectionService#correct

Запуск:  python3 gost_schemes.py algorithms.drawio
Экспорт: draw.io -x -f png -s 2 -b 10 -p <N> -o <file>.png algorithms.drawio
         (страница 6 — чертёж A3, экспорт с -b 0)

Символы: a = 120 px, b = 0,5a = 60 px (на чертеже 1 мм = 4 px, a×b = 30×15 мм).
Линии потока сверху вниз и слева направо — без стрелок; если на линии есть участок,
идущий вверх или влево, — стрелка на конце. Изломы только под 90°.
"""
import html
import sys

A, B = 120, 60                      # габарит символа (ширина a, высота b = 0,5a)
FONT = 'Arial'
BLACK = '#000000'

SHAPE = {
    'term': 'rounded=1;arcSize=50;',                                   # терминатор
    'proc': 'rounded=0;spacingLeft=4;spacingRight=4;',                                              # процесс
    'pred': 'shape=process;size=0.08;backgroundOutline=1;spacingLeft=10;spacingRight=10;',            # предопределённый процесс
    'data': 'shape=parallelogram;perimeter=parallelogramPerimeter;fixedSize=1;size=12;'
            'spacingLeft=12;spacingRight=12;',                         # данные
    'dec': 'rhombus;spacing=0;',                                       # решение
    'loopS': 'shape=loopLimit;size=12;',                               # граница цикла, начало
    'loopE': 'shape=loopLimit;size=12;flipV=1;',                       # граница цикла, конец
    'conn': 'ellipse;spacing=0;',                                      # соединитель
}


def label_html(text):
    """Текст символа: строки через \\n, спецсимволы экранируются для html=1."""
    return '<br>'.join(html.escape(s, quote=False) for s in text.split('\n'))


def xa(s):
    return html.escape(s, quote=True)


class Page:
    """Страница draw.io: символы, линии потока, надписи."""

    def __init__(self, name, fs=12, sw=1):
        self.name, self.fs, self.sw = name, fs, sw
        self.cells, self.nodes, self.n = [], {}, 0

    def _id(self, p):
        self.n += 1
        return f'{p}{self.n}'

    # --- символы -------------------------------------------------------------
    def node(self, key, kind, text, x, y, w=A, h=B, fs=None):
        style = (SHAPE[kind] + f'whiteSpace=wrap;html=1;fontFamily={FONT};fontSize={fs or self.fs};'
                 f'fontColor={BLACK};strokeColor={BLACK};fillColor=#FFFFFF;strokeWidth={self.sw};')
        self.cells.append(f'<mxCell id="{key}" value="{xa(label_html(text))}" style="{style}" vertex="1" '
                          f'parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
        self.nodes[key] = (kind, x, y, w, h)
        return key

    def conn(self, key, text, cx, cy, d=30, fs=None):
        return self.node(key, 'conn', text, cx - d / 2, cy - d / 2, d, d, fs)

    def text(self, text, x, y, w, h, align='left', fs=None, extra=''):
        style = (f'text;html=1;whiteSpace=wrap;align={align};verticalAlign=middle;spacing=0;'
                 f'fontFamily={FONT};fontSize={fs or self.fs};fontColor={BLACK};{extra}')
        self.cells.append(f'<mxCell id="{self._id("t")}" value="{xa(label_html(text))}" style="{style}" '
                          f'vertex="1" parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" '
                          f'as="geometry"/></mxCell>')

    def rect(self, x, y, w, h, sw=1, stroke=BLACK, fill='none'):
        style = f'rounded=0;html=1;fillColor={fill};strokeColor={stroke};strokeWidth={sw};'
        self.cells.append(f'<mxCell id="{self._id("r")}" value="" style="{style}" vertex="1" parent="1">'
                          f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')

    # --- точки привязки ------------------------------------------------------
    def geo(self, k):
        return self.nodes[k][1:]

    def T(self, k):
        _, x, y, w, h = self.nodes[k]
        return (x + w / 2, y)

    def Bm(self, k):
        _, x, y, w, h = self.nodes[k]
        return (x + w / 2, y + h)

    def L(self, k):
        kind, x, y, w, h = self.nodes[k]
        return (x + (6 if kind == 'data' else 0), y + h / 2)

    def R(self, k):
        kind, x, y, w, h = self.nodes[k]
        return (x + w - (6 if kind == 'data' else 0), y + h / 2)

    def cx(self, k):
        _, x, y, w, h = self.nodes[k]
        return x + w / 2

    def cy(self, k):
        _, x, y, w, h = self.nodes[k]
        return y + h / 2

    # --- линии потока ---------------------------------------------------------
    def line(self, pts, label=None, sw=None, arrow=None):
        """Ломаная по точкам (только горизонтальные/вертикальные участки)."""
        pts = [(float(a), float(b)) for a, b in pts]
        back = False
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            assert x1 == x2 or y1 == y2, f'{self.name}: излом не 90°: {pts}'
            if y2 < y1 or x2 < x1:
                back = True
        if arrow is None:
            arrow = back
        end = 'endArrow=block;endFill=1;endSize=7;' if arrow else 'endArrow=none;'
        style = (f'edgeStyle=none;rounded=0;html=1;strokeColor={BLACK};strokeWidth={sw or self.sw};'
                 f'startArrow=none;{end}')
        (sx, sy), (tx, ty) = pts[0], pts[-1]
        mids = ''.join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in pts[1:-1])
        arr = f'<Array as="points">{mids}</Array>' if mids else ''
        self.cells.append(f'<mxCell id="{self._id("e")}" value="" style="{style}" edge="1" parent="1">'
                          f'<mxGeometry relative="1" as="geometry"><mxPoint x="{sx}" y="{sy}" as="sourcePoint"/>'
                          f'<mxPoint x="{tx}" y="{ty}" as="targetPoint"/>{arr}</mxGeometry></mxCell>')
        if label:
            (x1, y1), (x2, y2) = pts[0], pts[1]
            fs = self.fs
            if x2 > x1:      # выход вправо — подпись над линией
                self.text(label, x1 + 3, y1 - fs - 4, 3 * fs, fs + 2)
            elif x2 < x1:    # выход влево
                self.text(label, x1 - 3 * fs - 3, y1 - fs - 4, 3 * fs, fs + 2, align='right')
            else:            # выход вниз — подпись справа от линии
                self.text(label, x1 + 4, y1 + 1, 3 * fs, fs + 2)

    def ylab(self, dec, label):
        """Подпись выхода решения вниз (линия уже проведена)."""
        x, y = self.Bm(dec)
        self.text(label, x + 4, y + 1, 3 * self.fs, self.fs + 2)

    def down(self, a, b, label=None):
        self.line([self.Bm(a), self.T(b)], label)

    def xml(self, i):
        return (f'<diagram id="p{i}" name="{xa(self.name)}"><mxGraphModel dx="1200" dy="800" grid="0" '
                f'gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="0" pageScale="1" '
                f'pageWidth="827" pageHeight="1169" math="0" shadow="0"><root><mxCell id="0"/>'
                f'<mxCell id="1" parent="0"/>' + ''.join(self.cells) + '</root></mxGraphModel></diagram>')


# ============================================================================
# Схемы А1–А5 (лист A4, книжная ориентация)
# ============================================================================
RP = 90                       # шаг строк: символ 60 + промежуток 30
Y0 = 20
M1, S1, LANE1 = 30, 220, 380  # колонка 1: основной поток, боковая ветвь, обходная линия
M2, S2, LANE2 = 430, 620, 770 # колонка 2


def ry(r):
    return Y0 + r * RP


class Scheme(Page):
    """Схема программы с раскладкой по колонкам и строкам."""

    def put(self, key, kind, text, col, r):
        return self.node(key, kind, text, col, ry(r))

    def chain(self, *keys):
        for a, b in zip(keys, keys[1:]):
            self.down(a, b)

    def stub(self, dec, num, label='нет'):
        """Выход решения вправо на соединитель (разрыв линии) — переход к выводу ошибки."""
        x, y = self.R(dec)
        k = self.conn(self._id('c'), num, x + 45, y)
        self.line([(x, y), (x + 30, y)], label)
        return k

    def side(self, dec, key, kind, text, col, label):
        """Решение → вправо → символ боковой ветви в той же строке."""
        _, _, y, _, _ = self.nodes[dec]
        self.node(key, kind, text, col, y)
        self.line([self.R(dec), self.L(key)], label)
        return key

    def merge_from_side(self, side_key, main_x, gap_y):
        """Слияние боковой ветви: вниз до промежутка, затем влево на линию основного потока."""
        x, y = self.Bm(side_key)
        self.line([(x, y), (x, gap_y), (main_x, gap_y)])

    def conn_out(self, prev, num):
        x, y = self.Bm(prev)
        _, _, py, _, _ = self.nodes[prev]
        k = self.conn(self._id('c'), num, x, py + B + 30 + 15)
        self.line([(x, y), (x, py + B + 30)])
        return k

    def conn_in(self, num, col, nxt_key):
        """Входящий соединитель над первым символом колонки (строка 0)."""
        cxx = col + A / 2
        k = self.conn(self._id('c'), num, cxx, ry(0) + 30)
        self.line([(cxx, ry(0) + 45), self.T(nxt_key)])
        return k

    def error_tail(self, end_key, num='1', text='Вывод: сообщение об ошибке'):
        """Вход соединителя ошибок → вывод сообщения → «Конец» (вывод справа от «Конец»)."""
        _, ex, ey, _, _ = self.nodes[end_key]
        col = ex + A + 70
        out = self.node(self._id('err'), 'data', text, col, ey)
        c = self.conn(self._id('c'), num, col + A + 30, ey + B / 2)
        self.line([(col + A + 15, ey + B / 2), self.R(out)])
        self.line([self.L(out), self.R(end_key)])


def gap(r):
    """Середина промежутка под строкой r."""
    return ry(r) + B + 15


def alg1():
    s = Scheme('А1 Расчёт бонуса')
    # колонка 1
    s.put('st', 'term', 'Начало', M1, 0)
    s.put('in', 'data', 'Ввод: сотрудник, правило, KPI, дата', M1, 1)
    s.put('d1', 'dec', 'Данные\nзаданы?', M1, 2)
    s.put('d2', 'dec', 'Сотрудник\nнайден?', M1, 3)
    s.put('d3', 'dec', 'Правило\nдействует?', M1, 4)
    s.put('p1', 'proc', 'Выбрать стратегию по периодичности правила', M1, 5)
    s.put('d4', 'dec', 'Стратегия\nKPI?', M1, 6)
    s.put('d5', 'dec', 'KPI в 0–150 %?', M1, 7)
    s.put('d6', 'dec', 'KPI ≥ порога?', M1, 8)
    s.put('p0', 'proc', 'База := 0', M1, 9)
    s.chain('st', 'in', 'd1', 'd2', 'd3', 'p1', 'd4', 'd5', 'd6', 'p0')
    for d in ('d1', 'd2', 'd3', 'd5'):
        s.stub(d, '1')
    for d, lab in (('d1', 'да'), ('d2', 'да'), ('d3', 'да'), ('d4', 'да'), ('d5', 'да'), ('d6', 'нет')):
        s.ylab(d, lab)
    s.side('d4', 'pf', 'proc', 'База := баллы × вес', S1, 'нет')
    s.side('d6', 'pk', 'proc', 'База := баллы × вес × KPI / 100', S1, 'да')
    g = gap(9)
    mx = s.cx('p0')
    s.merge_from_side('pk', mx, g)
    x, y = s.R('pf')
    s.line([(x, y), (LANE1, y), (LANE1, g), (mx, g)])
    c2 = s.conn_out('p0', '2')
    # колонка 2
    s.put('d7', 'dec', 'База = 0?', M2, 1)
    s.conn_in('2', M2, 'd7')
    s.put('p2', 'proc', 'Шаг округления := roundTo (по умолчанию 50)', M2, 2)
    s.put('p3', 'proc', 'Найти активные кампании отдела (по убыв. коэфф.)', M2, 3)
    s.put('l1', 'loopS', 'Цикл по кампаниям', M2, 4)
    s.put('p4', 'proc', 'Итог := округл(база × коэфф.)', M2, 5)
    s.put('d8', 'dec', 'Бюджет\nпозволяет?', M2, 6)
    s.put('l2', 'loopE', 'Конец цикла по кампаниям', M2, 7)
    s.put('p5', 'proc', 'Итог := округл(база); коэфф. := 1', M2, 8)
    s.put('out', 'data', 'Вывод: итог, коэфф., кампания, формула', M2, 9)
    s.put('end', 'term', 'Конец', M2, 10)
    s.line([s.Bm('d7'), s.T('p2')], 'нет')
    s.chain('p2', 'p3', 'l1', 'p4')
    s.down('p4', 'd8')
    s.line([s.Bm('d8'), s.T('l2')], 'нет')
    s.chain('l2', 'p5', 'out', 'end')
    s.side('d7', 'z0', 'proc', 'Итог := 0', S2, 'да')
    s.side('d8', 'pc', 'proc', 'Кампания := текущая; выход из цикла', S2, 'да')
    g = gap(8)
    mx = s.cx('out')
    s.merge_from_side('pc', mx, g)
    x, y = s.R('z0')
    s.line([(x, y), (LANE2, y), (LANE2, g), (mx, g)])
    s.error_tail('end', '1', 'Вывод: ошибка «Некорректные данные»')
    return s


def alg2():
    s = Scheme('А2 Автоначисление по событию')
    s.put('st', 'term', 'Начало', M1, 0)
    s.put('in', 'data', 'Ввод: идентификатор события', M1, 1)
    s.put('p1', 'proc', 'Заблокировать событие (FOR UPDATE)', M1, 2)
    s.put('d1', 'dec', 'Событие\nнайдено?', M1, 3)
    s.put('d2', 'dec', 'Уже\nобработано?', M1, 4)
    s.put('p2', 'proc', 'Разобрать данные события: код KPI, значение', M1, 5)
    s.put('d3', 'dec', 'Данные\nкорректны?', M1, 6)
    s.put('p3', 'proc', 'Найти правило по коду KPI на дату получения', M1, 7)
    s.put('d4', 'dec', 'Правило\nнайдено?', M1, 8)
    s.put('pr', 'pred', 'Расчёт бонуса (А1)', M1, 9)
    s.put('d5', 'dec', 'Расчёт без\nошибок?', M1, 10)
    s.chain('st', 'in', 'p1', 'd1')
    s.line([s.Bm('d1'), s.T('d2')], 'да')
    s.line([s.Bm('d2'), s.T('p2')], 'нет')
    s.down('p2', 'd3')
    s.line([s.Bm('d3'), s.T('p3')], 'да')
    s.down('p3', 'd4')
    s.line([s.Bm('d4'), s.T('pr')], 'да')
    s.down('pr', 'd5')
    s.stub('d1', '1')
    for d in ('d3', 'd4'):
        s.stub(d, '2')
    s.side('d2', 'sk', 'proc', 'Исход := SKIPPED', S1, 'да')
    x, y = s.R('sk')
    s.conn(s._id('c'), '3', x + 45, y)
    s.line([(x, y), (x + 30, y)])
    # выход «да» последнего решения колонки — на соединитель 4
    x, y = s.Bm('d5')
    _, _, py, _, _ = s.nodes['d5']
    s.conn(s._id('c'), '4', x, py + B + 45)
    s.line([(x, y), (x, py + B + 30)], 'да')
    s.stub('d5', '2')
    # колонка 2
    s.put('d6', 'dec', 'Итог = 0?', M2, 1)
    s.conn_in('4', M2, 'd6')
    s.put('p4', 'proc', 'Заблокировать бонусный счёт сотрудника', M2, 2)
    s.put('d7', 'dec', 'Счёт\nактивен?', M2, 3)
    s.put('p5', 'proc', 'Создать операцию начисления (accrued, kpi)', M2, 4)
    s.put('p6', 'proc', 'Баланс += итог; событие := processed', M2, 5)
    s.put('p7', 'proc', 'Опубликовать событие операции; исход ACCRUED', M2, 6)
    s.put('out', 'data', 'Вывод: исход обработки', M2, 7)
    s.put('end', 'term', 'Конец', M2, 8)
    s.line([s.Bm('d6'), s.T('p4')], 'нет')
    s.down('p4', 'd7')
    s.line([s.Bm('d7'), s.T('p5')], 'да')
    s.chain('p5', 'p6', 'p7', 'out', 'end')
    s.side('d6', 'nb', 'proc', 'Событие := processed; исход NO_BONUS', S2, 'да')
    x, y = s.R('nb')
    s.conn(s._id('c'), '3', x + 45, y)
    s.line([(x, y), (x + 30, y)])
    # обработчик сбоя (catch BonusDomainException)
    s.put('f1', 'proc', 'Событие := failed, текст ошибки', S2, 4)
    s.put('f2', 'proc', 'Опубликовать событие сбоя; исход FAILED', S2, 5)
    fx = s.cx('f1')
    s.conn(s._id('c'), '2', fx, s.cy('p4'))
    s.line([(fx, s.cy('p4') + 15), s.T('f1')])
    s.line([s.R('d7'), (fx, s.cy('d7'))], 'нет')
    s.down('f1', 'f2')
    g = gap(6)
    mx = s.cx('out')
    s.merge_from_side('f2', mx, g)
    c3x = S2 + A + 30
    s.conn(s._id('c'), '3', c3x, g)
    s.line([(c3x - 15, g), (mx, g)])
    s.error_tail('end', '1', 'Вывод: ошибка «Событие не найдено»')
    return s


def alg3():
    s = Scheme('А3 Ручное начисление')
    s.put('st', 'term', 'Начало', M1, 0)
    s.put('in', 'data', 'Ввод: инициатор, запрос начисления', M1, 1)
    s.put('d1', 'dec', 'Запрос\nкорректен?', M1, 2)
    s.put('d2', 'dec', 'Инициатор\nактивен?', M1, 3)
    s.put('d3', 'dec', 'Роль\nруков./HR?', M1, 4)
    s.put('d4', 'dec', 'Основание\nдопустимо?', M1, 5)
    s.put('l1', 'loopS', 'Цикл расчёта по сотрудникам', M1, 6)
    s.put('d5', 'dec', 'Сотрудник\nнайден?', M1, 7)
    s.put('d6', 'dec', 'Права на\nсотрудника?', M1, 8)
    s.put('pr', 'pred', 'Расчёт итога с коэфф. кампании', M1, 9)
    s.put('p1', 'proc', 'Признак проверки := итог > лимита', M1, 10)
    s.put('l2', 'loopE', 'Конец цикла расчёта', M1, 11)
    s.chain('st', 'in', 'd1')
    for a, b in (('d1', 'd2'), ('d2', 'd3'), ('d3', 'd4'), ('d4', 'l1'), ('d5', 'd6'), ('d6', 'pr')):
        s.line([s.Bm(a), s.T(b)], 'да')
    s.chain('l1', 'd5')
    s.chain('pr', 'p1', 'l2')
    for d in ('d1', 'd2', 'd3', 'd4', 'd5', 'd6'):
        s.stub(d, '1')
    s.conn_out('l2', '2')
    # колонка 2
    s.put('l3', 'loopS', 'Цикл блокировки счетов', M2, 1)
    s.conn_in('2', M2, 'l3')
    s.put('p2', 'proc', 'Заблокировать счёт (FOR UPDATE)', M2, 2)
    s.put('d7', 'dec', 'Счёт\nактивен?', M2, 3)
    s.put('l4', 'loopE', 'Конец цикла блокировки', M2, 4)
    s.put('p3', 'proc', 'Найти правило (если указано)', M2, 5)
    s.put('l5', 'loopS', 'Цикл создания операций', M2, 6)
    s.put('d8', 'dec', 'Нужна\nпроверка?', M2, 7)
    s.put('p4', 'proc', 'Создать операцию accrued; баланс += итог', M2, 8)
    s.put('p5', 'proc', 'Сохранить операцию, опубликовать событие', M2, 9)
    s.put('l6', 'loopE', 'Конец цикла создания операций', M2, 10)
    s.put('out', 'data', 'Вывод: операции и их число по статусам', M2, 11)
    s.put('end', 'term', 'Конец', M2, 12)
    s.chain('l3', 'p2', 'd7')
    s.line([s.Bm('d7'), s.T('l4')], 'да')
    s.chain('l4', 'p3', 'l5', 'd8')
    s.line([s.Bm('d8'), s.T('p4')], 'нет')
    s.chain('p4', 'p5', 'l6', 'out', 'end')
    s.stub('d7', '1')
    s.side('d8', 'pp', 'proc', 'Создать операцию pending (баланс не меняется)', S2, 'да')
    s.merge_from_side('pp', s.cx('p5'), gap(8))
    s.error_tail('end', '1')
    return s


def alg4():
    s = Scheme('А4 Списание на программу')
    s.put('st', 'term', 'Начало', M1, 0)
    s.put('in', 'data', 'Ввод: сотрудник, программа', M1, 1)
    s.put('p1', 'proc', 'Найти программу мотивации', M1, 2)
    s.put('d1', 'dec', 'Программа\nнайдена?', M1, 3)
    s.put('d2', 'dec', 'Программа\nактивна?', M1, 4)
    s.put('p2', 'proc', 'Заблокировать бонусный счёт (FOR UPDATE)', M1, 5)
    s.put('d3', 'dec', 'Счёт\nактивен?', M1, 6)
    s.put('d4', 'dec', 'Баланс ≥\nстоимости?', M1, 7)
    s.put('p3', 'proc', 'Создать операцию списания (written_off, −стоимость)', M1, 8)
    s.put('p4', 'proc', 'Баланс −= стоимость; сохранить операцию', M1, 9)
    s.put('p5', 'proc', 'Опубликовать событие операции', M1, 10)
    s.put('out', 'data', 'Вывод: операция, новый баланс', M1, 11)
    s.put('end', 'term', 'Конец', M1, 12)
    s.chain('st', 'in', 'p1', 'd1')
    s.line([s.Bm('d1'), s.T('d2')], 'да')
    s.line([s.Bm('d2'), s.T('p2')], 'да')
    s.down('p2', 'd3')
    s.line([s.Bm('d3'), s.T('d4')], 'да')
    s.line([s.Bm('d4'), s.T('p3')], 'да')
    s.chain('p3', 'p4', 'p5', 'out', 'end')
    for d in ('d1', 'd2', 'd3'):
        s.stub(d, '1')
    s.side('d4', 'ns', 'proc', 'Недостаёт := стоимость − баланс', S1, 'нет')
    x, y = s.R('ns')
    s.conn(s._id('c'), '1', x + 45, y)
    s.line([(x, y), (x + 30, y)])
    s.error_tail('end', '1')
    return s


def alg5():
    s = Scheme('А5 Корректировка начисления')
    s.put('st', 'term', 'Начало', M1, 0)
    s.put('in', 'data', 'Ввод: операция, действие, новая сумма, причина, инициатор', M1, 1)
    s.put('d1', 'dec', 'Инициатор\nактивен?', M1, 2)
    s.put('d2', 'dec', 'Роль\nHR/руков.?', M1, 3)
    s.put('p1', 'proc', 'Заблокировать исходную операцию', M1, 4)
    s.put('d3', 'dec', 'Операция\nнайдена?', M1, 5)
    s.put('d4', 'dec', 'Права на\nсотрудника?', M1, 6)
    s.put('d5', 'dec', 'Статус\nдопустим?', M1, 7)
    s.put('p2', 'proc', 'Найти корректировку операции на согласовании', M1, 8)
    s.put('d6', 'dec', 'Найдена?', M1, 9)
    s.put('d7', 'dec', 'Отмена?', M1, 10)
    s.put('d8', 'dec', 'Сумма\nдопустима?', M1, 11)
    s.chain('st', 'in', 'd1')
    s.line([s.Bm('d1'), s.T('d2')], 'да')
    s.line([s.Bm('d2'), s.T('p1')], 'да')
    s.down('p1', 'd3')
    s.line([s.Bm('d3'), s.T('d4')], 'да')
    s.line([s.Bm('d4'), s.T('d5')], 'да')
    s.line([s.Bm('d5'), s.T('p2')], 'да')
    s.down('p2', 'd6')
    s.line([s.Bm('d6'), s.T('d7')], 'нет')
    s.line([s.Bm('d7'), s.T('d8')], 'нет')
    for d in ('d1', 'd2', 'd3', 'd4', 'd5', 'd8'):
        s.stub(d, '1')
    s.stub('d6', '1', 'да')
    s.side('d7', 'p0', 'proc', 'Новая сумма := 0', S1, 'да')
    x, y = s.Bm('d8')
    _, _, py, _, _ = s.nodes['d8']
    s.conn(s._id('c'), '2', x, py + B + 45)
    s.line([(x, y), (x, py + B + 30)])
    s.text('да', x - 40, y + 1, 36, 14, align='right')
    s.merge_from_side('p0', x, gap(11))
    # колонка 2
    s.put('p3', 'proc', 'Δ := новая сумма − исходная', M2, 1)
    s.conn_in('2', M2, 'p3')
    s.put('p4', 'proc', 'Заблокировать бонусный счёт', M2, 2)
    s.put('d9', 'dec', 'Счёт\nактивен?', M2, 3)
    s.put('d10', 'dec', 'Баланс + Δ\n< 0?', M2, 4)
    s.put('p5', 'proc', 'Корректировка: corrected (при отмене cancelled)', M2, 5)
    s.put('p6', 'proc', 'Исходная := cancelled; баланс += Δ', M2, 6)
    s.put('p7', 'proc', 'Сохранить корректировку, опубликовать событие', M2, 7)
    s.put('out', 'data', 'Вывод: операции и признак согласования', M2, 8)
    s.put('end', 'term', 'Конец', M2, 9)
    s.chain('p3', 'p4', 'd9')
    s.line([s.Bm('d9'), s.T('d10')], 'да')
    s.line([s.Bm('d10'), s.T('p5')], 'нет')
    s.chain('p5', 'p6', 'p7', 'out', 'end')
    s.stub('d9', '1')
    s.side('d10', 'pp', 'proc', 'Корректировка: pending; баланс не меняется', S2, 'да')
    s.merge_from_side('pp', s.cx('p7'), gap(6))
    s.error_tail('end', '1')
    return s


# ============================================================================
# Чертёж A3: схема алгоритма А3 «Ручное начисление бонусов»
# 1 мм = 4 px; A3 420×297 мм = 1680×1188 px; рамка: слева 20 мм, остальные 5 мм.
# Сетка зон (ГОСТ 19.002-80): ячейка = символ = 30×15 мм (120×60 px),
# столбцы 1…13 (цифры), строки A…T (латинские буквы без I и O).
# ============================================================================
MM = 4
SHEET_W, SHEET_H = 420 * MM, 297 * MM
FR_X, FR_Y = 20 * MM, 5 * MM
FR_W, FR_H = SHEET_W - 25 * MM, SHEET_H - 10 * MM
BAND = 5 * MM                                  # полоса обозначений зон
GX, GY = FR_X + BAND, FR_Y + BAND              # начало сетки
COLS, ROWS = 13, 18
ROW_NAMES = 'ABCDEFGHJKLMNPQRST'
TB_W, TB_H = 185 * MM, 55 * MM                 # основная надпись, форма 1
TB_X, TB_Y = FR_X + FR_W - TB_W, FR_Y + FR_H - TB_H

TITLE = {
    'name': 'Алгоритм ручного начисления бонусов\nСхема программы',
    'code': 'БГУИР 6-05-0611-01 046 ПД',
    'dev': 'Ерошевич', 'chk': 'Алексеев',
    'lit': 'У', 'mass': '', 'scale': '1:1', 'sheet': '1', 'sheets': '1',
    'org': 'БГУИР, гр. 314371',
}
DFS = 10            # шрифт 2,5 мм для надписей в символах и обозначений зон
SW_MAIN = 2.8       # сплошная основная ≈ 0,7 мм
SW_THIN = 1.2       # сплошная тонкая ≈ 0,3 мм
SW_SYM = 2          # контуры символов и линии потока 0,5 мм


def zone(rowname, col):
    return GX + (col - 1) * A, GY + ROW_NAMES.index(rowname) * B


class Drawing(Scheme):
    def sym(self, key, kind, text, z):
        r, c = z[0], int(z[1:])
        x, y = zone(r, c)
        self.node(key, kind, text, x, y)
        if kind == 'dec':   # текст ромба смещён вправо, чтобы не задевать координату зоны
            i = next(j for j, c_ in enumerate(self.cells) if f'id="{key}"' in c_)
            self.cells[i] = self.cells[i].replace('rhombus;spacing=0;', 'rhombus;spacing=0;spacingLeft=16;')
        self.zlabel(key, z)
        return key

    def zlabel(self, key, z):
        """Координата зоны в левом верхнем углу с разрывом контура, отступ 5 мм от левого края."""
        kind, x, y, w, h = self.nodes[key]
        lx = x + 5 * MM
        if kind == 'dec':
            ly = y + h / 2 - (h / 2) * (5 * MM) / (w / 2)   # точка контура ромба при x + 5 мм
        elif kind == 'term':
            ly = y + 2
        else:
            ly = y
        tw = 7 * len(z) + 4
        self.text(z, lx - 2, ly - 7, tw, 14, align='center', fs=DFS,
                  extra='fillColor=#FFFFFF;strokeColor=none;labelBackgroundColor=#FFFFFF;')

    def zconn(self, key, label, z):
        r, c = z[0], int(z[1:])
        x, y = zone(r, c)
        return self.conn(key, label, x + A / 2, y + B / 2, d=B, fs=DFS)

    def zstub(self, dec, z, label='нет'):
        """Выход решения вправо на соединитель в соседней зоне (метка = зона символа-приёмника)."""
        x, y = self.R(dec)
        k = self.conn(self._id('c'), z, x + A / 2, y, d=B, fs=DFS)
        self.line([(x, y), (x + A / 2 - B / 2, y)], label)
        return k


def build_drawing():
    d = Drawing('Чертёж A3 — схема А3', fs=DFS, sw=SW_SYM)
    # лист, рамка
    d.rect(0, 0, SHEET_W, SHEET_H, sw=0, stroke='none', fill='#FFFFFF')
    d.rect(FR_X, FR_Y, FR_W, FR_H, sw=SW_MAIN)
    # сетка зон (тонкие вспомогательные линии) и полосы обозначений
    grey = '#B0B0B0'
    gx2, gy2 = GX + COLS * A, GY + ROWS * B
    for i in range(COLS + 1):
        x = GX + i * A
        ybot = gy2 if x <= TB_X else TB_Y
        d.line([(x, GY), (x, ybot)], sw=0.6, arrow=False)
        d.cells[-1] = d.cells[-1].replace(f'strokeColor={BLACK}', f'strokeColor={grey}')
    for j in range(ROWS + 1):
        y = GY + j * B
        xr = gx2 if y <= TB_Y else TB_X
        d.line([(GX, y), (xr, y)], sw=0.6, arrow=False)
        d.cells[-1] = d.cells[-1].replace(f'strokeColor={BLACK}', f'strokeColor={grey}')
    d.rect(GX, FR_Y, COLS * A, BAND, sw=SW_THIN)
    d.rect(FR_X, GY, BAND, ROWS * B, sw=SW_THIN)
    for i in range(COLS):
        x = GX + i * A
        d.rect(x, FR_Y, A, BAND, sw=SW_THIN)
        d.text(str(i + 1), x, FR_Y, A, BAND, align='center', fs=14)
    for j, rn in enumerate(ROW_NAMES):
        y = GY + j * B
        d.rect(FR_X, y, BAND, B, sw=SW_THIN)
        d.text(rn, FR_X, y, BAND, B, align='center', fs=14)

    # --- блок 1: столбец 1 ---
    d.sym('st', 'term', 'Начало', 'A1')
    d.sym('in', 'data', 'Ввод: инициатор и запрос начисления', 'C1')
    d.sym('d1', 'dec', 'Запрос\nкорректен?', 'E1')
    d.sym('d2', 'dec', 'Инициатор\nактивен?', 'G1')
    d.sym('d3', 'dec', 'Роль\nруков./HR?', 'J1')
    d.sym('d4', 'dec', 'Основание\nдопустимо?', 'L1')
    d.sym('l1', 'loopS', 'Цикл 1: по сотрудникам без повторов (по возрастанию id)', 'N1')
    d.sym('d5', 'dec', 'Сотрудник\nнайден?', 'Q1')
    d.zconn('c1o', 'C4', 'S1')
    d.chain('st', 'in', 'd1')
    for a, b in (('d1', 'd2'), ('d2', 'd3'), ('d3', 'd4'), ('d4', 'l1')):
        d.line([d.Bm(a), d.T(b)], 'да')
    d.down('l1', 'd5')
    d.line([d.Bm('d5'), d.T('c1o')], 'да')
    d.zstub('d1', 'A12'); d.zstub('d2', 'C12'); d.zstub('d3', 'C12')
    d.zstub('d4', 'A12'); d.zstub('d5', 'A12')
    # --- блок 2: столбец 4 ---
    d.zconn('c1i', 'C4', 'A4')
    d.sym('d6', 'dec', 'Права на\nсотрудника?', 'C4')
    d.sym('pr', 'pred', 'Расчёт итога: сумма × коэфф. кампании', 'E4')
    d.sym('p1', 'proc', 'Признак проверки := итог > лимита (1000)', 'G4')
    d.sym('l2', 'loopE', 'Конец цикла 1', 'J4')
    d.sym('l3', 'loopS', 'Цикл 2: по строкам расчёта', 'L4')
    d.sym('p2', 'proc', 'Заблокировать счёт сотрудника (SELECT … FOR UPDATE)', 'N4')
    d.sym('d7', 'dec', 'Счёт есть\nи активен?', 'Q4')
    d.zconn('c2o', 'C7', 'S4')
    d.down('c1i', 'd6')
    d.line([d.Bm('d6'), d.T('pr')], 'да')
    d.chain('pr', 'p1', 'l2', 'l3', 'p2', 'd7')
    d.line([d.Bm('d7'), d.T('c2o')], 'да')
    d.zstub('d6', 'C12'); d.zstub('d7', 'E12')
    # --- блок 3: столбец 7 ---
    d.zconn('c2i', 'C7', 'A7')
    d.sym('l4', 'loopE', 'Конец цикла 2', 'C7')
    d.sym('p3', 'proc', 'Найти правило начисления (если указано)', 'E7')
    d.sym('l5', 'loopS', 'Цикл 3: по строкам расчёта', 'G7')
    d.sym('d8', 'dec', 'Нужна\nпроверка?', 'J7')
    d.sym('p4', 'proc', 'Создать операцию accrued; баланс += итог', 'L7')
    d.sym('p5', 'proc', 'Сохранить операцию; опубликовать событие (аудит, уведомление)', 'N7')
    d.zconn('c3o', 'C10', 'Q7')
    d.sym('pp', 'proc', 'Создать операцию pending (баланс не меняется)', 'J9')
    d.chain('c2i', 'l4', 'p3', 'l5', 'd8')
    d.line([d.Bm('d8'), d.T('p4')], 'нет')
    d.chain('p4', 'p5', 'c3o')
    d.line([d.R('d8'), d.L('pp')], 'да')
    _, my = zone('M', 7)
    d.merge_from_side('pp', d.cx('p4'), my + B / 2)
    # --- блок 4: столбец 10, ошибки — столбцы 12–13 ---
    d.zconn('c3i', 'C10', 'A10')
    d.sym('l6', 'loopE', 'Конец цикла 3', 'C10')
    d.sym('out', 'data', 'Вывод: операции, число начисленных и ожидающих', 'E10')
    d.sym('end', 'term', 'Конец', 'G10')
    d.chain('c3i', 'l6', 'out', 'end')
    errs = [('A', 'Вывод: ошибка «Некорректный запрос»'),
            ('C', 'Вывод: ошибка «Доступ запрещён»'),
            ('E', 'Вывод: ошибка «Счёт заблокирован»')]
    lane = GX + 10 * A + A / 2          # середина столбца 11
    for r, t in errs:
        k = d.sym('e' + r, 'data', t, f'{r}12')
        ci = d.zconn('ci' + r, f'{r}12', f'{r}13')
        d.line([d.L(ci), d.R(k)])
        x, y = d.L(k)
        d.line([(x, y), (lane, y), (lane, d.cy('end')), d.R('end')])
    title_block(d)
    return d


def title_block(d):
    """Основная надпись по ГОСТ 2.104, форма 1 (185×55 мм)."""
    x0, y0 = TB_X, TB_Y
    X = lambda mm: x0 + mm * MM
    Y = lambda mm: y0 + mm * MM

    def hl(y_mm, xa_mm, xb_mm, sw=SW_THIN):
        d.line([(X(xa_mm), Y(y_mm)), (X(xb_mm), Y(y_mm))], sw=sw, arrow=False)

    def vl(x_mm, ya_mm, yb_mm, sw=SW_MAIN):
        d.line([(X(x_mm), Y(ya_mm)), (X(x_mm), Y(yb_mm))], sw=sw, arrow=False)

    def cell(text, xa_mm, ya_mm, xb_mm, yb_mm, fs=10, align='center', extra=''):
        pad = 1 * MM if align == 'left' else 0
        d.text(text, X(xa_mm) + pad, Y(ya_mm), (xb_mm - xa_mm) * MM - pad, (yb_mm - ya_mm) * MM,
               align=align, fs=fs, extra=extra)

    d.rect(x0, y0, TB_W, TB_H, sw=SW_MAIN, fill='#FFFFFF')
    # левая часть: графы 7/10/23/15/10 мм, 11 строк по 5 мм
    for i in range(1, 11):
        hl(i * 5, 0, 65, SW_MAIN if i in (5,) else SW_THIN)
    hl(4 * 5, 0, 65, SW_MAIN)
    for xm in (7, 17, 40, 55):
        if xm == 7:
            vl(xm, 0, 25)            # в строках Разраб.… графы «Изм.» и «Лист» объединены
        else:
            vl(xm, 0, 55)
    vl(65, 0, 55)
    # правая часть
    hl(15, 65, 185, SW_MAIN)
    hl(40, 65, 185, SW_MAIN)
    vl(135, 15, 55)
    hl(20, 135, 185, SW_THIN)
    hl(35, 135, 185, SW_THIN)
    vl(140, 20, 35, SW_THIN); vl(145, 20, 35, SW_THIN)
    vl(150, 15, 35); vl(167, 15, 35)
    vl(155, 35, 40, SW_THIN)
    # подписи граф
    for txt, xa_, xb_ in (('Изм.', 0, 7), ('Лист', 7, 17), ('№ докум.', 17, 40), ('Подп.', 40, 55), ('Дата', 55, 65)):
        cell(txt, xa_, 20, xb_, 25, fs=10)
    roles = [('Разраб.', TITLE['dev']), ('Пров.', TITLE['chk']), ('Т.контр.', ''), ('', ''),
             ('Н.контр.', ''), ('Утв.', '')]
    for i, (role, who) in enumerate(roles):
        ya_ = 25 + i * 5
        cell(role, 0, ya_, 17, ya_ + 5, fs=10, align='left')
        cell(who, 17, ya_, 40, ya_ + 5, fs=10, align='left')
    cell(TITLE['code'], 65, 0, 185, 15, fs=20)
    cell(TITLE['name'], 65, 15, 135, 40, fs=14)
    cell('Лит.', 135, 15, 150, 20); cell('Масса', 150, 15, 167, 20); cell('Масштаб', 167, 15, 185, 20)
    cell(TITLE['lit'], 135, 20, 140, 35, fs=14)
    cell(TITLE['mass'], 150, 20, 167, 35, fs=14)
    cell(TITLE['scale'], 167, 20, 185, 35, fs=14)
    cell('Лист ' + TITLE['sheet'], 135, 35, 155, 40, align='left')
    cell('Листов ' + TITLE['sheets'], 155, 35, 185, 40, align='left')
    cell(TITLE['org'], 135, 40, 185, 55, fs=14)


def main(out):
    pages = [alg1(), alg2(), alg3(), alg4(), alg5(), build_drawing()]
    with open(out, 'w', encoding='utf-8') as f:
        f.write('<mxfile host="Electron">' + ''.join(p.xml(i) for i, p in enumerate(pages)) + '</mxfile>')
    print('ok', len(pages), 'pages ->', out)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'algorithms.drawio')
