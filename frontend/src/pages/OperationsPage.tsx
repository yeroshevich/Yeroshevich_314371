import { EditOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import {
  api,
  BASIS_LABELS,
  OPERATION_STATUS_LABELS,
  OPERATION_TYPE_LABELS,
  type CorrectionRequest,
  type Operation,
  type OperationStatus,
} from '../api';
import { useCurrentUser } from '../auth/AuthContext';
import { Amount } from '../components/Amount';
import { ErrorState } from '../components/ErrorState';
import { OperationFilters, useOperationFilters } from '../components/OperationFilters';
import { OperationStatusTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { useAsync } from '../hooks/useAsync';
import { useIsPhone } from '../hooks/useIsMobile';
import { shadowCard } from '../theme/tokens';
import { formatDateTime, formatPoints, lowerFirst } from '../utils/format';

const correctable = (o: Operation) => o.type !== 'writeoff' && (o.status === 'accrued' || o.status === 'corrected');

/** Журнал всех операций (HR, финотдел) и корректировка начислений (UC7). */
export default function OperationsPage() {
  const user = useCurrentUser();
  const canCorrect = user.role === 'hr';
  const filters = useOperationFilters();
  const [statuses, setStatuses] = useState<OperationStatus[]>([]);
  const [dept, setDept] = useState<string>();
  const [search, setSearch] = useState(filters.query);
  const [editing, setEditing] = useState<Operation | null>(null);

  // Поиск уходит в запрос после паузы в наборе.
  useEffect(() => {
    const t = setTimeout(() => search !== filters.query && filters.setQuery(search), 350);
    return () => clearTimeout(t);
  }, [search]); // eslint-disable-line react-hooks/exhaustive-deps

  const departments = useAsync(() => api.getDepartments(), []);
  const ops = useAsync(
    () =>
      api.getOperations({
        type: filters.type === 'all' ? undefined : filters.type,
        from: filters.from,
        to: filters.to,
        query: filters.query || undefined,
        status: statuses,
        departmentId: dept,
      }),
    [filters.type, filters.from, filters.to, filters.query, statuses.join(), dept],
  );

  const deptName = (id: string) => departments.data?.find((d) => d.id === id)?.name ?? '';

  const columns: ColumnsType<Operation> = [
    {
      title: 'Дата',
      dataIndex: 'createdAt',
      width: 140,
      render: (v: string, o) => (
        <div className="tsb-num">
          <div>{formatDateTime(v)}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{o.number}</Typography.Text>
        </div>
      ),
    },
    {
      title: 'Сотрудник',
      dataIndex: 'employeeName',
      width: 200,
      render: (v: string, o) => (
        <div>
          <div style={{ fontWeight: 500 }}>{v}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{deptName(o.departmentId)}</Typography.Text>
        </div>
      ),
    },
    {
      title: 'Операция',
      dataIndex: 'reason',
      render: (v: string, o) => (
        <div style={{ minWidth: 200 }}>
          <div>{v}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {OPERATION_TYPE_LABELS[o.type]}, {lowerFirst(BASIS_LABELS[o.basis])}. Автор: {o.createdBy}
            {o.comment ? `. ${o.comment}` : ''}
          </Typography.Text>
        </div>
      ),
    },
    { title: 'Статус', dataIndex: 'status', width: 160, render: (s: OperationStatus) => <OperationStatusTag status={s} /> },
    {
      title: 'Сумма',
      dataIndex: 'amount',
      align: 'right',
      width: 130,
      sorter: (a, b) => a.amount - b.amount,
      render: (_, o) => <Amount value={o.amount} original={o.originalAmount} />,
    },
    ...(canCorrect
      ? [
          {
            title: <span className="tsb-visually-hidden">Действия</span>,
            key: 'actions',
            width: 160,
            render: (_: unknown, o: Operation) =>
              correctable(o) ? (
                <Button icon={<EditOutlined />} onClick={() => setEditing(o)}>
                  Корректировать
                </Button>
              ) : null,
          },
        ]
      : []),
  ];

  return (
    <>
      <PageHeader
        title="Операции"
        description={
          canCorrect
            ? 'Журнал начислений и списаний всех сотрудников. Ошибочное начисление можно скорректировать или отменить.'
            : 'Журнал начислений и списаний всех сотрудников. Решения по спорным начислениям — в разделе «Контроль начислений».'
        }
      />
      {ops.error && <div style={{ marginBottom: 16 }}><ErrorState error={ops.error} onRetry={ops.reload} /></div>}
      <Card style={{ boxShadow: shadowCard }}>
        <OperationFilters filters={filters}>
          <Input
            allowClear
            prefix={<SearchOutlined aria-hidden />}
            placeholder="Сотрудник, номер или причина"
            aria-label="Поиск по операциям"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 280, maxWidth: '100%' }}
          />
          <Select
            allowClear
            placeholder="Все подразделения"
            aria-label="Подразделение"
            value={dept}
            onChange={setDept}
            options={departments.data?.map((d) => ({ value: d.id, label: d.name }))}
            style={{ width: 220, maxWidth: '100%' }}
          />
          <Select<OperationStatus[]>
            mode="multiple"
            allowClear
            maxTagCount="responsive"
            placeholder="Все статусы"
            aria-label="Статус"
            value={statuses}
            onChange={setStatuses}
            options={(Object.keys(OPERATION_STATUS_LABELS) as OperationStatus[]).map((s) => ({ value: s, label: OPERATION_STATUS_LABELS[s] }))}
            style={{ width: 220, maxWidth: '100%' }}
          />
        </OperationFilters>
        <Table<Operation>
          rowKey="id"
          columns={columns}
          dataSource={ops.data}
          loading={ops.loading}
          scroll={{ x: 1040 }}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (t) => `Всего ${t}` }}
          locale={{
            emptyText: ops.loading ? ' ' : (
              <Empty description="По этим условиям операций нет">
                <Button onClick={() => { filters.reset(); setStatuses([]); setDept(undefined); setSearch(''); }}>Сбросить фильтры</Button>
              </Empty>
            ),
          }}
        />
      </Card>

      <CorrectionDrawer
        operation={editing}
        onClose={() => setEditing(null)}
        onSaved={(updated) => {
          ops.setData((prev) => (prev ?? []).map((o) => (o.id === updated.id ? updated : o)));
          setEditing(null);
        }}
      />
    </>
  );
}

interface CorrectionForm {
  action: CorrectionRequest['action'];
  newAmount?: number;
  reason: string;
}

/** Форма корректировки (UC7): новая сумма или отмена, причина обязательна. */
function CorrectionDrawer({
  operation,
  onClose,
  onSaved,
}: {
  operation: Operation | null;
  onClose: () => void;
  onSaved: (op: Operation) => void;
}) {
  const { message } = App.useApp();
  const phone = useIsPhone();
  const [form] = Form.useForm<CorrectionForm>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const action = Form.useWatch('action', form);

  useEffect(() => {
    setError(undefined);
    // Один экземпляр формы на все операции: сбрасываем к initialValues новой записи.
    if (operation) form.resetFields();
  }, [operation, form]);

  const save = async () => {
    if (!operation) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return; // ошибки уже показаны у полей
    setSaving(true);
    setError(undefined);
    try {
      const updated = await api.correctOperation(operation.id, values);
      if (updated.status === 'pending') {
        message.warning('Корректировка отправлена на согласование: баланс сотрудника стал бы отрицательным.');
      } else {
        message.success(values.action === 'cancel' ? 'Начисление отменено' : 'Корректировка сохранена');
      }
      onSaved(updated);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title="Корректировка начисления"
      open={Boolean(operation)}
      onClose={onClose}
      width={phone ? '100%' : 480}
      footer={
        <Flex justify="flex-end" gap={8}>
          <Button onClick={onClose}>Закрыть без изменений</Button>
          <Button type="primary" danger={action === 'cancel'} loading={saving} onClick={save}>
            {action === 'cancel' ? 'Отменить начисление' : 'Сохранить корректировку'}
          </Button>
        </Flex>
      }
    >
      {operation && (
        <>
          <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Операция">{operation.number}</Descriptions.Item>
            <Descriptions.Item label="Сотрудник">{operation.employeeName}</Descriptions.Item>
            <Descriptions.Item label="Причина">{operation.reason}</Descriptions.Item>
            <Descriptions.Item label="Текущая сумма">
              <span className="tsb-num">{formatPoints(operation.amount)}</span>
            </Descriptions.Item>
          </Descriptions>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Если после корректировки баланс сотрудника станет отрицательным, операция уйдёт на согласование финотделу."
          />
          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
          <Form<CorrectionForm>
            key={operation.id}
            form={form}
            layout="vertical"
            validateTrigger="onBlur"
            requiredMark={false}
            initialValues={{ action: 'adjust', newAmount: operation.amount, reason: '' }}
          >
            <Form.Item name="action" label="Что сделать">
              <Radio.Group>
                <Radio value="adjust">Изменить сумму</Radio>
                <Radio value="cancel">Отменить начисление</Radio>
              </Radio.Group>
            </Form.Item>
            {action !== 'cancel' && (
              <Form.Item
                name="newAmount"
                label="Новая сумма"
                rules={[
                  { required: true, message: 'Введите новую сумму' },
                  { type: 'number', min: 0, message: 'Сумма не может быть отрицательной' },
                ]}
              >
                <InputNumber<number> min={0} step={50} precision={0} addonAfter="Б" style={{ width: '100%' }} />
              </Form.Item>
            )}
            <Form.Item
              name="reason"
              label="Причина корректировки"
              extra="Сотрудник получит уведомление с этим текстом"
              rules={[{ required: true, whitespace: true, message: 'Напишите причину — без неё корректировку не сохранить' }]}
            >
              <Input.TextArea rows={3} maxLength={300} showCount placeholder="Например: KPI учтён дважды" />
            </Form.Item>
          </Form>
        </>
      )}
    </Drawer>
  );
}
