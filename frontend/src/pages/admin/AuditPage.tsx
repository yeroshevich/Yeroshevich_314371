import { SearchOutlined } from '@ant-design/icons';
import { Card, DatePicker, Empty, Flex, Input, Select, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { api, ROLE_LABELS, type AuditEntry } from '../../api';
import { ErrorState } from '../../components/ErrorState';
import { PageHeader } from '../../components/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { shadowCard } from '../../theme/tokens';
import { formatDateTime } from '../../utils/format';

const columns: ColumnsType<AuditEntry> = [
  {
    title: 'Время',
    dataIndex: 'timestamp',
    width: 150,
    sorter: (a, b) => a.timestamp.localeCompare(b.timestamp),
    defaultSortOrder: 'descend',
    render: (v: string) => <span className="tsb-num">{formatDateTime(v)}</span>,
  },
  {
    title: 'Пользователь',
    dataIndex: 'userName',
    width: 200,
    render: (v: string, e) => (
      <div>
        <div>{v}</div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>{e.role === 'system' ? 'Автоматически' : ROLE_LABELS[e.role]}</Typography.Text>
      </div>
    ),
  },
  { title: 'Действие', dataIndex: 'action', width: 220 },
  {
    title: 'Объект',
    dataIndex: 'objectLabel',
    render: (v: string, e) => (
      <div style={{ minWidth: 200 }}>
        <div>{v}</div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>{e.objectType}</Typography.Text>
      </div>
    ),
  },
  { title: 'IP-адрес', dataIndex: 'ip', width: 120, render: (v: string) => <span className="tsb-num">{v}</span> },
];

/** Журнал аудита (NFR-3, SEC-4): кто, что и когда изменил. Только чтение. */
export default function AuditPage() {
  const audit = useAsync(() => api.getAudit(), []);
  const [query, setQuery] = useState('');
  const [action, setAction] = useState<string>();
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);

  const actions = useMemo(() => Array.from(new Set(audit.data?.map((a) => a.action) ?? [])).sort(), [audit.data]);
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (audit.data ?? []).filter((a) => {
      if (action && a.action !== action) return false;
      if (range && (dayjs(a.timestamp).isBefore(range[0].startOf('day')) || dayjs(a.timestamp).isAfter(range[1].endOf('day')))) return false;
      return !q || `${a.userName} ${a.objectLabel} ${a.ip}`.toLowerCase().includes(q);
    });
  }, [audit.data, query, action, range]);

  return (
    <>
      <PageHeader title="Журнал аудита" description="Все значимые действия в системе: начисления, решения финотдела, изменения правил, ролей и интеграций. Записи не редактируются." />
      {audit.error && <div style={{ marginBottom: 16 }}><ErrorState error={audit.error} onRetry={audit.reload} /></div>}
      <Card style={{ boxShadow: shadowCard }}>
        <Flex gap={12} wrap="wrap" style={{ marginBottom: 16 }}>
          <Input
            allowClear
            prefix={<SearchOutlined aria-hidden />}
            placeholder="Пользователь, объект или IP"
            aria-label="Поиск в журнале"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: 280, maxWidth: '100%' }}
          />
          <Select
            allowClear
            showSearch
            placeholder="Все действия"
            aria-label="Действие"
            value={action}
            onChange={setAction}
            options={actions.map((a) => ({ value: a, label: a }))}
            style={{ width: 240, maxWidth: '100%' }}
          />
          <DatePicker.RangePicker value={range} onChange={(v) => setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)} format="DD.MM.YYYY" placeholder={['Период с', 'по']} />
        </Flex>
        <Table<AuditEntry>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={audit.loading}
          scroll={{ x: 900 }}
          pagination={{ pageSize: 12, showSizeChanger: false, showTotal: (t) => `Записей: ${t}` }}
          locale={{ emptyText: audit.loading ? ' ' : <Empty description="Записей по этим условиям нет" /> }}
        />
      </Card>
    </>
  );
}
