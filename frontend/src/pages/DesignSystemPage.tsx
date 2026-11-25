import { ClockCircleOutlined, FallOutlined, PlusOutlined, RiseOutlined } from '@ant-design/icons';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Flex,
  Input,
  InputNumber,
  Row,
  Segmented,
  Select,
  Space,
  Steps,
  Switch,
  Table,
  Typography,
} from 'antd';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { OPERATION_STATUS_LABELS, type OperationStatus } from '../api';
import { Amount } from '../components/Amount';
import { BalanceCard } from '../components/BalanceCard';
import { OperationStatusTag } from '../components/OperationStatusTag';
import { PointsMark } from '../components/PointsMark';
import { StatCard } from '../components/StatCard';
import {
  breakpoints,
  colorPurpose,
  colors,
  controlHeight,
  fontFamily,
  layout,
  radius,
  shadowCard,
  space,
  typeScale,
  type ColorToken,
} from '../theme/tokens';
import { formatPoints } from '../utils/format';

const SECTIONS = [
  { id: 'colors', title: 'Цвета' },
  { id: 'type', title: 'Типографика' },
  { id: 'space', title: 'Отступы и формы' },
  { id: 'grid', title: 'Сетка' },
  { id: 'controls', title: 'Элементы управления' },
  { id: 'status', title: 'Статусы' },
  { id: 'cards', title: 'Карточки' },
  { id: 'table', title: 'Таблица' },
  { id: 'feedback', title: 'Обратная связь' },
];

function Section({ id, title, lead, children }: { id: string; title: string; lead?: string; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} style={{ padding: '40px 0', borderTop: `1px solid ${colors.colorBorder}` }}>
      <Typography.Title level={2} id={`${id}-title`} style={{ marginTop: 0 }}>
        {title}
      </Typography.Title>
      {lead && (
        <Typography.Paragraph type="secondary" style={{ maxWidth: 680, marginBottom: 24 }}>
          {lead}
        </Typography.Paragraph>
      )}
      {children}
    </section>
  );
}

const Spec = ({ children }: { children: ReactNode }) => (
  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
    {children}
  </Typography.Text>
);

const SAMPLE_ROWS = [
  { key: '1', date: '24.11.2026', reason: 'План спринта за октябрь, ×1,5', status: 'accrued' as OperationStatus, amount: 1650 },
  { key: '2', date: '18.11.2026', reason: 'Сертификат в книжный магазин', status: 'written_off' as OperationStatus, amount: -1500 },
  { key: '3', date: '21.11.2026', reason: 'Доклад на внутреннем митапе', status: 'pending' as OperationStatus, amount: 1500 },
];

/** Служебная витрина системы дизайна (ЛР 6, п. 1). Открыта без входа. */
export default function DesignSystemPage() {
  const navigate = useNavigate();
  return (
    <div style={{ background: colors.colorBgContainer, minHeight: '100vh' }}>
      <header style={{ background: colors.siderBg, color: colors.colorBgContainer }}>
        <div style={{ maxWidth: 1200, margin: '0 auto', padding: '32px 24px 40px' }}>
          <Flex justify="space-between" align="center" wrap="wrap" gap={16} style={{ marginBottom: 32 }}>
            <Flex align="center" gap={10}>
              <PointsMark size={28} onDark />
              <span style={{ fontWeight: 700, fontSize: 16 }}>TechnoSoft Bonus</span>
            </Flex>
            <Button onClick={() => navigate('/login')}>Войти в приложение</Button>
          </Flex>
          <h1 style={{ fontSize: 40, lineHeight: '48px', fontWeight: 700, margin: '0 0 12px', letterSpacing: '-0.02em' }}>Система дизайна</h1>
          <p style={{ margin: 0, maxWidth: 640, color: 'rgba(255,255,255,.72)', fontSize: 16, lineHeight: '26px' }}>
            Токены, шкалы и компоненты клиента TechnoSoft Bonus. Все экраны собираются только из этих значений — через тему Ant Design 5.
          </p>
          <nav aria-label="Разделы системы дизайна" style={{ marginTop: 24 }}>
            <Flex gap={8} wrap="wrap">
              {SECTIONS.map((s) => (
                <a key={s.id} href={`#${s.id}`} style={{ color: colors.colorBgContainer, padding: '6px 12px', borderRadius: 8, background: 'rgba(255,255,255,.08)' }}>
                  {s.title}
                </a>
              ))}
            </Flex>
          </nav>
        </div>
      </header>

      <main style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px 64px' }}>
        <Section id="colors" title="Цвета" lead="Жёлто-оранжевый colorBonus закреплён только за баллами. Статусные цвета означают одно и то же на всех экранах.">
          <Row gutter={[16, 24]}>
            {(Object.keys(colors) as ColorToken[]).map((name) => (
              <Col key={name} xs={12} sm={8} lg={6} xl={4}>
                <div
                  style={{ height: 72, borderRadius: radius.card, background: colors[name], border: `1px solid ${colors.colorBorder}`, marginBottom: 8 }}
                  aria-hidden
                />
                <div style={{ fontWeight: 600 }}>{name}</div>
                <div className="tsb-num">{colors[name]}</div>
                <Spec>{colorPurpose[name]}</Spec>
              </Col>
            ))}
          </Row>
        </Section>

        <Section id="type" title="Типографика" lead={`Гарнитура Inter, запасные — ${fontFamily.split(',').slice(1).join(',').trim()}. Цифры сумм — tabular-nums, чтобы разряды стояли ровно.`}>
          <Flex vertical gap={20}>
            {Object.values(typeScale).map((t) => (
              <Flex key={t.name} gap={24} align="baseline" wrap="wrap">
                <div style={{ width: 180, flex: 'none' }}>
                  <div style={{ fontWeight: 600 }}>{t.name}</div>
                  <Spec>
                    {t.role}, {t.fontSize}/{t.lineHeight} px, {t.fontWeight}
                  </Spec>
                </div>
                <div
                  className={t.name === 'Display' ? 'tsb-num' : undefined}
                  style={{ fontSize: t.fontSize, lineHeight: `${t.lineHeight}px`, fontWeight: t.fontWeight, minWidth: 0 }}
                >
                  {t.name === 'Display' ? formatPoints(8300) : t.name === 'Small' ? 'Обновлено 24.11.2026 в 14:32' : 'Начисление за выполнение плана спринта'}
                </div>
              </Flex>
            ))}
          </Flex>
        </Section>

        <Section id="space" title="Отступы и формы" lead={`Шкала кратна 4. Отступ страницы ${layout.pagePadding} px, на телефоне ${layout.pagePaddingMobile} px.`}>
          <Row gutter={[48, 32]}>
            <Col xs={24} md={12}>
              <Flex vertical gap={10}>
                {Object.values(space).map((v) => (
                  <Flex key={v} align="center" gap={16}>
                    <span className="tsb-num" style={{ width: 48 }}>{v} px</span>
                    <span style={{ height: 16, width: v * 4, background: colors.colorPrimary, borderRadius: 4 }} aria-hidden />
                  </Flex>
                ))}
              </Flex>
            </Col>
            <Col xs={24} md={12}>
              <Flex gap={24} wrap="wrap">
                {[
                  { label: `Элемент управления, ${radius.control} px`, r: radius.control, shadow: false },
                  { label: `Карточка, ${radius.card} px + тень`, r: radius.card, shadow: true },
                ].map((s) => (
                  <div key={s.label}>
                    <div
                      style={{ width: 140, height: 96, borderRadius: s.r, border: `1px solid ${colors.colorBorder}`, boxShadow: s.shadow ? shadowCard : undefined, background: colors.colorBgContainer, marginBottom: 8 }}
                      aria-hidden
                    />
                    <Spec>{s.label}</Spec>
                  </div>
                ))}
              </Flex>
              <div style={{ marginTop: 24 }}>
                <Spec>
                  Высота элементов: {controlHeight.lg} px крупные, {controlHeight.md} px стандарт, {controlHeight.sm} px малые. На тач-экранах кнопки не ниже 44 px.
                </Spec>
              </div>
            </Col>
          </Row>
        </Section>

        <Section
          id="grid"
          title="Сетка"
          lead={`12 колонок, интервал ${layout.gutter} px (${layout.gutterMobile} px на телефоне). Боковая панель ${layout.siderWidth} px, свёрнутая ${layout.siderCollapsedWidth} px, шапка ${layout.headerHeight} px, контент до ${layout.contentMaxWidth} px. Ниже ${layout.drawerBreakpoint} px меню уходит в выдвижную панель.`}
        >
          <Flex vertical gap={12}>
            {[Array(12).fill(1), [3, 9], [4, 4, 4], [6, 6], [3, 3, 3, 3]].map((spans, i) => (
              <Row key={i} gutter={[layout.gutter, 0]}>
                {spans.map((s: number, j: number) => (
                  <Col key={j} span={s * 2}>
                    <div
                      style={{
                        height: 40,
                        borderRadius: 6,
                        background: i === 0 ? 'rgba(59,91,219,.12)' : 'rgba(59,91,219,.22)',
                        display: 'grid',
                        placeItems: 'center',
                        fontSize: 12,
                        color: colors.colorPrimary,
                        fontWeight: 600,
                      }}
                    >
                      {s}
                    </div>
                  </Col>
                ))}
              </Row>
            ))}
          </Flex>
          <Flex gap={24} wrap="wrap" style={{ marginTop: 20 }}>
            {Object.entries(breakpoints).map(([k, v]) => (
              <Spec key={k}>
                {k}: {v === 0 ? '< 576' : `≥ ${v}`} px
              </Spec>
            ))}
          </Flex>
        </Section>

        <Section id="controls" title="Элементы управления">
          <Flex vertical gap={24}>
            <Flex gap={12} wrap="wrap" align="center">
              <Button type="primary" icon={<PlusOutlined />}>Начислить баллы</Button>
              <Button>Сформировать</Button>
              <Button type="dashed">Добавить условие</Button>
              <Button danger>Заблокировать</Button>
              <Button type="link">Вся история</Button>
              <Button type="primary" disabled>Использовать</Button>
            </Flex>
            <Flex gap={12} wrap="wrap" align="center">
              <Button type="primary" size="large">Крупная 40</Button>
              <Button type="primary">Стандарт 32</Button>
              <Button type="primary" size="small">Малая 24</Button>
            </Flex>
            <Row gutter={[24, 16]}>
              <Col xs={24} md={8}>
                <Spec>Поле ввода</Spec>
                <Input placeholder="Поиск по имени или должности" style={{ marginTop: 4 }} aria-label="Пример поля ввода" />
              </Col>
              <Col xs={24} md={8}>
                <Spec>Сумма (InputNumber)</Spec>
                <InputNumber addonAfter="Б" defaultValue={800} style={{ width: '100%', marginTop: 4 }} aria-label="Пример суммы" />
              </Col>
              <Col xs={24} md={8}>
                <Spec>Выбор (Select)</Spec>
                <Select
                  defaultValue="d-dev"
                  style={{ width: '100%', marginTop: 4 }}
                  aria-label="Пример выбора"
                  options={[{ value: 'd-dev', label: 'Разработка ПО' }, { value: 'd-qa', label: 'Обеспечение качества' }]}
                />
              </Col>
              <Col xs={24} md={8}>
                <Spec>Период (RangePicker)</Spec>
                <DatePicker.RangePicker style={{ width: '100%', marginTop: 4 }} format="DD.MM.YYYY" placeholder={['Период с', 'по']} />
              </Col>
              <Col xs={24} md={8}>
                <Spec>Переключатель сегментов</Spec>
                <div style={{ marginTop: 4 }}>
                  <Segmented options={['Месяц', 'Квартал', 'Полгода']} defaultValue="Квартал" />
                </div>
              </Col>
              <Col xs={24} md={8}>
                <Spec>Switch, Badge, Avatar</Spec>
                <Space size={16} style={{ marginTop: 8, display: 'flex' }}>
                  <Switch defaultChecked aria-label="Пример переключателя" />
                  <Badge count={3}>
                    <Avatar style={{ background: colors.colorPrimary }}>АК</Avatar>
                  </Badge>
                </Space>
              </Col>
            </Row>
            <Steps current={1} items={[{ title: 'Сотрудники' }, { title: 'Сумма и основание' }, { title: 'Подтверждение' }]} />
          </Flex>
        </Section>

        <Section id="status" title="Статусы операций" lead="Цвет текста тегов взят из тёмных производных токенов, поэтому все теги проходят контраст 4,5:1.">
          <Flex gap={12} wrap="wrap">
            {(Object.keys(OPERATION_STATUS_LABELS) as OperationStatus[]).map((s) => (
              <OperationStatusTag key={s} status={s} />
            ))}
          </Flex>
          <Flex gap={24} wrap="wrap" style={{ marginTop: 16 }}>
            <Amount value={1650} />
            <Amount value={-1500} />
            <Amount value={800} original={1200} />
          </Flex>
        </Section>

        <Section id="cards" title="Карточки" lead="Карточка баланса — единственный тёмный блок в контенте: цифра colorBonus читается только на тёмном фоне.">
          <Row gutter={[24, 24]}>
            <Col xs={24} lg={10}>
              <BalanceCard
                account={{ employeeId: 'e01', status: 'active', balance: 8300, pending: 1500, updatedAt: '2026-11-24T14:32:00' }}
                nextProgram={{ id: 'p7', name: 'Наушники с шумоподавлением', category: 'gift', description: '', cost: 9500, active: true }}
                affordable={[
                  { id: 'p5', name: 'Курс английского языка', category: 'education', description: '', cost: 6000, active: true },
                  { id: 'p4', name: 'Дополнительный выходной', category: 'dayoff', description: '', cost: 4000, active: true },
                ]}
              />
            </Col>
            <Col xs={24} lg={14}>
              <Row gutter={[16, 16]}>
                <Col xs={24} sm={12}>
                  <StatCard label="Начислено за месяц" icon={<RiseOutlined />} iconColor={colors.colorSuccess} value={formatPoints(3950)} />
                </Col>
                <Col xs={24} sm={12}>
                  <StatCard label="Потрачено за месяц" icon={<FallOutlined />} iconColor={colors.colorError} value={formatPoints(1500)} />
                </Col>
                <Col xs={24} sm={12}>
                  <StatCard label="На согласовании" icon={<ClockCircleOutlined />} iconColor={colors.colorWarning} value={formatPoints(1500)} footnote="Ждут подтверждения финотдела" />
                </Col>
                <Col xs={24} sm={12}>
                  <Card style={{ boxShadow: shadowCard, height: '100%' }}>
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Операций пока нет" />
                  </Card>
                </Col>
              </Row>
            </Col>
          </Row>
        </Section>

        <Section id="table" title="Таблица">
          <Table
            pagination={false}
            dataSource={SAMPLE_ROWS}
            scroll={{ x: 560 }}
            columns={[
              { title: 'Дата', dataIndex: 'date', width: 120 },
              { title: 'Операция', dataIndex: 'reason' },
              { title: 'Статус', dataIndex: 'status', width: 170, render: (s: OperationStatus) => <OperationStatusTag status={s} /> },
              { title: 'Сумма', dataIndex: 'amount', width: 120, align: 'right', render: (v: number) => <Amount value={v} /> },
            ]}
          />
        </Section>

        <Section id="feedback" title="Обратная связь">
          <Flex vertical gap={12}>
            <Alert type="success" showIcon message="Списано 1 500 Б. Подтверждение участия придёт на почту." />
            <Alert type="info" showIcon message="Если после корректировки баланс станет отрицательным, операция уйдёт на согласование финотделу." />
            <Alert type="warning" showIcon message="Потребуется согласование" description="2 сотр. получат больше 1 000 Б. Эти начисления уйдут в финотдел со статусом «На согласовании»." />
            <Alert type="error" showIcon message="Баланс недоступен, обратитесь к HR" />
          </Flex>
        </Section>
      </main>
    </div>
  );
}
