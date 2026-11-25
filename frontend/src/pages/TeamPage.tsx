import { PlusOutlined, SearchOutlined, TeamOutlined, TrophyOutlined, WalletOutlined } from '@ant-design/icons';
import { Avatar, Button, Card, Col, Empty, Flex, Input, Progress, Row, Select, Table, Typography, theme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type TeamMember } from '../api';
import { useCurrentUser } from '../auth/AuthContext';
import { Amount } from '../components/Amount';
import { ErrorState } from '../components/ErrorState';
import { ToneTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { useAsync } from '../hooks/useAsync';
import { colors, shadowCard } from '../theme/tokens';
import { formatPoints, initials } from '../utils/format';

/** «Моя команда» руководителя и «Сотрудники» HR: балансы и выполнение KPI. */
export default function TeamPage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const isHr = user.role === 'hr';
  const team = useAsync(() => api.getTeam(), []);
  const departments = useAsync(() => api.getDepartments(), []);
  const [query, setQuery] = useState('');
  const [dept, setDept] = useState<string>();

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (team.data ?? []).filter(
      (m) => (!dept || m.departmentId === dept) && (!q || `${m.fullName} ${m.position}`.toLowerCase().includes(q)),
    );
  }, [team.data, query, dept]);

  const totalBalance = rows.reduce((s, m) => s + m.balance, 0);
  const avgKpi = rows.length ? Math.round(rows.reduce((s, m) => s + m.kpiCompletion, 0) / rows.length) : 0;

  const kpiColor = (v: number) => (v >= 100 ? token.colorSuccess : v >= 80 ? colors.colorPrimary : token.colorWarning);

  const columns: ColumnsType<TeamMember> = [
    {
      title: 'Сотрудник',
      dataIndex: 'fullName',
      sorter: (a, b) => a.fullName.localeCompare(b.fullName, 'ru'),
      render: (_, m) => (
        <Flex gap={12} align="center" style={{ minWidth: 220 }}>
          <Avatar style={{ background: colors.colorBgLayout, color: colors.colorText, flex: 'none' }}>{initials(m.fullName)}</Avatar>
          <div>
            <div style={{ fontWeight: 500 }}>{m.fullName}</div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>{m.position}</Typography.Text>
          </div>
        </Flex>
      ),
    },
    ...(isHr ? [{ title: 'Подразделение', dataIndex: 'departmentName', width: 200 } as const] : []),
    {
      title: 'Баланс',
      dataIndex: 'balance',
      align: 'right',
      width: 130,
      sorter: (a, b) => a.balance - b.balance,
      render: (v: number, m) => (m.accountStatus === 'blocked' ? <ToneTag tone="dark">Счёт заблокирован</ToneTag> : <Amount value={v} signed={false} strong={false} />),
    },
    {
      title: 'За месяц',
      dataIndex: 'accruedThisMonth',
      align: 'right',
      width: 120,
      sorter: (a, b) => a.accruedThisMonth - b.accruedThisMonth,
      render: (v: number) => <Amount value={v} strong={false} />,
    },
    {
      title: 'KPI',
      dataIndex: 'kpiCompletion',
      width: 180,
      sorter: (a, b) => a.kpiCompletion - b.kpiCompletion,
      render: (v: number) => (
        <Flex align="center" gap={8}>
          <Progress percent={Math.min(v, 100)} showInfo={false} strokeColor={kpiColor(v)} size="small" style={{ width: 90, margin: 0 }} aria-label={`KPI ${v}%`} />
          <span className="tsb-num">{v} %</span>
        </Flex>
      ),
    },
    {
      title: <span className="tsb-visually-hidden">Действия</span>,
      key: 'actions',
      width: 140,
      render: (_, m) => (
        <Button icon={<PlusOutlined />} disabled={m.accountStatus === 'blocked'} onClick={() => navigate(`/accruals/new?employees=${m.id}`)}>
          Начислить
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title={isHr ? 'Сотрудники' : 'Моя команда'}
        description={isHr ? 'Балансы и выполнение KPI по всем подразделениям.' : 'Балансы подчинённых и выполнение KPI за текущий месяц.'}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/accruals/new')}>
            Начислить баллы
          </Button>
        }
      />
      {team.error && <ErrorState error={team.error} onRetry={team.reload} />}

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <StatCard label={isHr ? 'Сотрудников' : 'В команде'} icon={<TeamOutlined />} iconColor={colors.colorPrimary} loading={!team.data} value={rows.length} />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard label="Баллов на счетах" icon={<WalletOutlined />} iconColor={colors.colorBonus} loading={!team.data} value={formatPoints(totalBalance)} />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard label="Средний KPI" icon={<TrophyOutlined />} iconColor={colors.colorSuccess} loading={!team.data} value={`${avgKpi} %`} footnote="Порог правил — от 80 %" />
        </Col>
      </Row>

      <Card style={{ boxShadow: shadowCard }}>
        <Flex gap={12} wrap="wrap" style={{ marginBottom: 16 }}>
          <Input
            allowClear
            prefix={<SearchOutlined aria-hidden />}
            placeholder="Поиск по имени или должности"
            aria-label="Поиск сотрудника"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: 320, maxWidth: '100%' }}
          />
          {isHr && (
            <Select
              allowClear
              placeholder="Все подразделения"
              aria-label="Подразделение"
              value={dept}
              onChange={setDept}
              options={departments.data?.map((d) => ({ value: d.id, label: d.name }))}
              style={{ width: 240, maxWidth: '100%' }}
            />
          )}
        </Flex>
        <Table<TeamMember>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={team.loading}
          scroll={{ x: 820 }}
          pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
          locale={{ emptyText: team.loading ? ' ' : <Empty description="Никого не нашли. Проверьте написание или сбросьте фильтр." /> }}
        />
      </Card>
    </>
  );
}
