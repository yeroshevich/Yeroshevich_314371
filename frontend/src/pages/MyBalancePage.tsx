import { ClockCircleOutlined, FallOutlined, MailOutlined, RiseOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Empty, Row, Skeleton } from 'antd';
import { useNavigate } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, ApiError } from '../api';
import { useCurrentUser } from '../auth/AuthContext';
import { BalanceCard } from '../components/BalanceCard';
import { ErrorState } from '../components/ErrorState';
import { OperationList } from '../components/OperationList';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { useAsync } from '../hooks/useAsync';
import { colors, shadowCard } from '../theme/tokens';
import { formatMonth, formatNumber, formatPoints } from '../utils/format';

const cardStyle = { boxShadow: shadowCard, height: '100%' };

/** «Мой баланс» — главная сотрудника (UC1, FR1-1…3). */
export default function MyBalancePage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const summary = useAsync(() => api.getMyAccount(), []);
  const programs = useAsync(() => api.getPrograms(), []);
  const firstName = user.fullName.split(' ')[0];

  if (summary.error instanceof ApiError && summary.error.code === 'ACCOUNT_BLOCKED') {
    return (
      <>
        <PageHeader title="Мой баланс" />
        <Alert
          type="error"
          showIcon
          message="Баланс недоступен, обратитесь к HR"
          description="Бонусный счёт заблокирован отделом персонала. Пока счёт заблокирован, баллы не начисляются и не списываются. Напишите HR-бизнес-партнёру — он объяснит причину и сроки."
          action={
            <Button icon={<MailOutlined />} href="mailto:olga.melnik@technosoft.by?subject=Заблокирован бонусный счёт">
              Написать HR
            </Button>
          }
        />
      </>
    );
  }

  const data = summary.data;
  const account = data?.account;
  const nextProgram = account
    ? programs.data?.filter((p) => p.active && p.cost > account.balance).sort((a, b) => a.cost - b.cost)[0]
    : undefined;
  const affordable = account
    ? programs.data?.filter((p) => p.active && p.cost <= account.balance).sort((a, b) => b.cost - a.cost).slice(0, 3)
    : undefined;
  const chartData = data?.history.map((h) => ({ month: formatMonth(h.month), balance: h.balance })) ?? [];

  return (
    <>
      <PageHeader title={`Здравствуйте, ${firstName}`} description="Ваш бонусный счёт: сколько баллов доступно, что пришло за месяц и на что их потратить." />

      {summary.error && <div style={{ marginBottom: 24 }}><ErrorState error={summary.error} onRetry={summary.reload} /></div>}

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={10} xl={9}>
          {account ? (
            <BalanceCard account={account} nextProgram={nextProgram} affordable={affordable} onRefresh={summary.reload} refreshing={summary.loading} />
          ) : (
            <Card style={cardStyle}><Skeleton active paragraph={{ rows: 5 }} /></Card>
          )}
        </Col>

        <Col xs={24} lg={14} xl={15}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={8}>
              <StatCard
                label="Начислено за месяц"
                icon={<RiseOutlined />}
                iconColor={colors.colorSuccess}
                loading={!data}
                value={data && formatPoints(data.accruedThisMonth)}
              />
            </Col>
            <Col xs={24} sm={8}>
              <StatCard
                label="Потрачено за месяц"
                icon={<FallOutlined />}
                iconColor={colors.colorError}
                loading={!data}
                value={data && formatPoints(data.spentThisMonth)}
              />
            </Col>
            <Col xs={24} sm={8}>
              <StatCard
                label="На согласовании"
                icon={<ClockCircleOutlined />}
                iconColor={colors.colorWarning}
                loading={!data}
                value={account && formatPoints(account.pending)}
                footnote="Ждут подтверждения финотдела"
              />
            </Col>
            <Col span={24}>
              <Card title="Баланс за полгода" style={cardStyle} styles={{ body: { paddingTop: 8 } }}>
                {data ? (
                  <figure
                    style={{ margin: 0, height: 220 }}
                    aria-label={`График баланса: ${chartData.map((d) => `${d.month} ${formatNumber(d.balance)} баллов`).join(', ')}`}
                  >
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                        <defs>
                          <linearGradient id="balanceFill" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor={colors.colorBonus} stopOpacity={0.35} />
                            <stop offset="100%" stopColor={colors.colorBonus} stopOpacity={0.04} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid stroke={colors.colorBorder} vertical={false} />
                        <XAxis dataKey="month" tick={{ fill: colors.colorTextSecondary, fontSize: 12 }} axisLine={false} tickLine={false} />
                        <YAxis
                          width={56}
                          tick={{ fill: colors.colorTextSecondary, fontSize: 12 }}
                          axisLine={false}
                          tickLine={false}
                          tickFormatter={(v: number) => formatNumber(v)}
                        />
                        <Tooltip formatter={(v: number) => [formatPoints(v), 'Баланс']} />
                        <Area type="monotone" dataKey="balance" stroke={colors.siderBg} strokeWidth={2} fill="url(#balanceFill)" isAnimationActive={false} />
                      </AreaChart>
                    </ResponsiveContainer>
                  </figure>
                ) : (
                  <Skeleton active paragraph={{ rows: 5 }} />
                )}
              </Card>
            </Col>
          </Row>
        </Col>

        <Col span={24}>
          <Card
            title="Последние операции"
            style={cardStyle}
            extra={<Button type="link" onClick={() => navigate('/history')}>Вся история</Button>}
          >
            {!data ? (
              <Skeleton active />
            ) : data.recent.length ? (
              <OperationList operations={data.recent} />
            ) : (
              <Empty description="Операций пока нет. Первые баллы придут после закрытия месяца по KPI." />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
