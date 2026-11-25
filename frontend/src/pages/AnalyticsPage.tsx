import { ArrowDownOutlined, ArrowUpOutlined, GiftOutlined, PercentageOutlined, RiseOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Card, Col, Flex, Row, Segmented, Skeleton, Typography, theme } from 'antd';
import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api, type AnalyticsPeriod } from '../api';
import { useCurrentUser } from '../auth/AuthContext';
import { ErrorState } from '../components/ErrorState';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { useAsync } from '../hooks/useAsync';
import { useIsPhone } from '../hooks/useIsMobile';
import { colors, shadowCard } from '../theme/tokens';
import { formatNumber, formatPoints } from '../utils/format';

const PERIODS: { value: AnalyticsPeriod; label: string }[] = [
  { value: 'month', label: 'Месяц' },
  { value: 'quarter', label: 'Квартал' },
  { value: 'halfyear', label: 'Полгода' },
];

const axisTick = { fill: colors.colorTextSecondary, fontSize: 12 };

/** Изменение к прошлому периоду: стрелка + процент, цвет — тёмные оттенки статусов. */
function Delta({ current, previous }: { current: number; previous: number }) {
  const { token } = theme.useToken();
  if (!previous) return <span>Нет данных за прошлый период</span>;
  const pct = Math.round(((current - previous) / previous) * 100);
  const up = pct >= 0;
  return (
    <span style={{ color: up ? token.colorSuccessText : token.colorErrorText }}>
      {up ? <ArrowUpOutlined aria-hidden /> : <ArrowDownOutlined aria-hidden />} {up ? '+' : '−'}
      {Math.abs(pct)} % к прошлому периоду
    </span>
  );
}

/** Анализ эффективности бонусной системы (UC11): метрики, отделы, динамика, сравнение периодов. */
export default function AnalyticsPage() {
  const user = useCurrentUser();
  const phone = useIsPhone();
  const [period, setPeriod] = useState<AnalyticsPeriod>('quarter');
  const { data, error, reload } = useAsync(() => api.getAnalytics(period), [period]);

  return (
    <>
      <PageHeader
        title="Аналитика"
        description={
          user.role === 'manager'
            ? 'Как работает бонусная система в вашем подразделении.'
            : 'Насколько бонусная система вовлекает сотрудников: средний бонус, использование программ, отделы.'
        }
      />

      <Flex gap={16} wrap="wrap" align="center" style={{ marginBottom: 24 }}>
        <Segmented<AnalyticsPeriod> options={PERIODS} value={period} onChange={setPeriod} aria-label="Период сравнения" />
        {data && (
          <Typography.Text type="secondary">
            {data.currentLabel} в сравнении с {data.previousLabel}
          </Typography.Text>
        )}
      </Flex>

      {error && <div style={{ marginBottom: 16 }}><ErrorState error={error} onRetry={reload} /></div>}
      {data?.partial && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
          message="Частичная аналитика"
          description="Система ведёт историю меньше, чем длится выбранный прошлый период, поэтому сравнение неполное. Выберите период короче."
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            label="Средний бонус на сотрудника"
            icon={<UserOutlined />}
            iconColor={colors.colorPrimary}
            loading={!data}
            value={data && formatPoints(data.averageBonus.current)}
            footnote={data && <Delta {...data.averageBonus} />}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            label="Конверсия программ"
            icon={<PercentageOutlined />}
            iconColor={colors.colorInfo}
            loading={!data}
            value={data && `${data.programConversion.current} %`}
            footnote={data && <Delta {...data.programConversion} />}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            label="Начислено"
            icon={<RiseOutlined />}
            iconColor={colors.colorSuccess}
            loading={!data}
            value={data && formatPoints(data.totalAccrued.current)}
            footnote={data && <Delta {...data.totalAccrued} />}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            label="Потрачено на программы"
            icon={<GiftOutlined />}
            iconColor={colors.colorBonus}
            loading={!data}
            value={data && formatPoints(data.totalSpent.current)}
            footnote={data && <Delta {...data.totalSpent} />}
          />
        </Col>
      </Row>

      <Row gutter={[24, 24]}>
        <Col xs={24} xl={12}>
          <Card title="Начисления по подразделениям" style={{ boxShadow: shadowCard, height: '100%' }}>
            {!data ? (
              <Skeleton active paragraph={{ rows: 6 }} />
            ) : (
              <figure
                style={{ margin: 0, height: Math.max(220, data.byDepartment.length * 56) }}
                aria-label={`Начисления по подразделениям: ${data.byDepartment.map((d) => `${d.department} ${formatNumber(d.current)} баллов`).join(', ')}`}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={data.byDepartment} layout="vertical" margin={{ left: 0, right: 16, top: 0, bottom: 0 }} barGap={2}>
                    <CartesianGrid stroke={colors.colorBorder} horizontal={false} />
                    <XAxis type="number" tick={axisTick} axisLine={false} tickLine={false} tickFormatter={(v: number) => formatNumber(v)} />
                    <YAxis type="category" dataKey="department" width={phone ? 110 : 170} tick={axisTick} axisLine={false} tickLine={false} />
                    <Tooltip formatter={(v) => formatPoints(Number(v))} cursor={{ fill: colors.colorBgLayout }} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Bar dataKey="current" name="Текущий период" fill={colors.colorPrimary} radius={[0, 4, 4, 0]} isAnimationActive={false} />
                    <Bar dataKey="previous" name="Прошлый период" fill={colors.colorPrimary} fillOpacity={0.35} radius={[0, 4, 4, 0]} isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </figure>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="Динамика за полгода" style={{ boxShadow: shadowCard, height: '100%' }}>
            {!data ? (
              <Skeleton active paragraph={{ rows: 6 }} />
            ) : (
              <figure
                style={{ margin: 0, height: 320 }}
                aria-label={`Динамика начислений: ${data.monthly.map((m) => `${m.month} ${formatNumber(m.accrued)} баллов`).join(', ')}`}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={data.monthly} margin={{ left: 0, right: 16, top: 8, bottom: 0 }}>
                    <CartesianGrid stroke={colors.colorBorder} vertical={false} />
                    <XAxis dataKey="month" tick={axisTick} axisLine={false} tickLine={false} />
                    <YAxis width={64} tick={axisTick} axisLine={false} tickLine={false} tickFormatter={(v: number) => formatNumber(v)} />
                    <Tooltip formatter={(v) => formatPoints(Number(v))} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Line type="monotone" dataKey="accrued" name="Начислено" stroke={colors.colorPrimary} strokeWidth={2.5} dot={{ r: 3 }} isAnimationActive={false} />
                    <Line type="monotone" dataKey="spent" name="Потрачено на программы" stroke={colors.siderBg} strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3 }} isAnimationActive={false} />
                  </LineChart>
                </ResponsiveContainer>
              </figure>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
