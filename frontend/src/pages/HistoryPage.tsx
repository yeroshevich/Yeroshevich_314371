import { Button, Card, Empty, Flex, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api, BASIS_LABELS, OPERATION_TYPE_LABELS, type Operation } from '../api';
import { Amount } from '../components/Amount';
import { ErrorState } from '../components/ErrorState';
import { OperationFilters, useOperationFilters } from '../components/OperationFilters';
import { OperationStatusTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { useAsync } from '../hooks/useAsync';
import { shadowCard } from '../theme/tokens';
import { formatDateTime, formatPoints, lowerFirst } from '../utils/format';

const columns: ColumnsType<Operation> = [
  {
    title: 'Дата',
    dataIndex: 'createdAt',
    width: 150,
    render: (v: string) => <span className="tsb-num">{formatDateTime(v)}</span>,
    sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
    defaultSortOrder: 'descend',
  },
  {
    title: 'Операция',
    dataIndex: 'reason',
    render: (_, o) => (
      <div style={{ minWidth: 220 }}>
        <div>{o.reason}</div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {o.number}, {lowerFirst(BASIS_LABELS[o.basis])}
          {o.comment ? `. ${o.comment}` : ''}
        </Typography.Text>
      </div>
    ),
  },
  { title: 'Тип', dataIndex: 'type', width: 140, render: (t: Operation['type']) => OPERATION_TYPE_LABELS[t] },
  { title: 'Статус', dataIndex: 'status', width: 160, render: (s: Operation['status']) => <OperationStatusTag status={s} /> },
  {
    title: 'Сумма',
    dataIndex: 'amount',
    align: 'right',
    width: 130,
    render: (_, o) => <Amount value={o.amount} original={o.originalAmount} />,
    sorter: (a, b) => a.amount - b.amount,
  },
];

/** История операций своего счёта (UC2, FR2-1…3). */
export default function HistoryPage() {
  const filters = useOperationFilters();
  const { data, loading, error, reload } = useAsync(
    () => api.getMyOperations({ type: filters.type === 'all' ? undefined : filters.type, from: filters.from, to: filters.to }),
    [filters.type, filters.from, filters.to],
  );

  const settled = (data ?? []).filter((o) => ['accrued', 'written_off', 'corrected'].includes(o.status));
  const plus = settled.filter((o) => o.amount > 0).reduce((s, o) => s + o.amount, 0);
  const minus = settled.filter((o) => o.amount < 0).reduce((s, o) => s + o.amount, 0);

  return (
    <>
      <PageHeader title="История операций" description="Все начисления и списания по вашему счёту с причинами и статусами." />
      {error && <div style={{ marginBottom: 16 }}><ErrorState error={error} onRetry={reload} /></div>}
      <Card style={{ boxShadow: shadowCard }}>
        <OperationFilters filters={filters} />
        {data && data.length > 0 && (
          <Flex gap={24} wrap="wrap" style={{ marginBottom: 12 }}>
            <Typography.Text type="secondary">
              Найдено: <b className="tsb-num">{data.length}</b>
            </Typography.Text>
            <Typography.Text type="secondary">
              Пришло <b className="tsb-num">{formatPoints(plus)}</b>
            </Typography.Text>
            <Typography.Text type="secondary">
              Потрачено <b className="tsb-num">{formatPoints(-minus)}</b>
            </Typography.Text>
          </Flex>
        )}
        <Table<Operation>
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          scroll={{ x: 760 }}
          pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: true }}
          locale={{
            emptyText: loading ? ' ' : (
              <Empty description={filters.isFiltered ? 'За выбранный период операций такого типа нет' : 'Операции отсутствуют'}>
                {filters.isFiltered && <Button onClick={filters.reset}>Показать все операции</Button>}
              </Empty>
            ),
          }}
        />
      </Card>
    </>
  );
}
