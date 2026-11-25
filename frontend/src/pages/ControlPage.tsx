import { CheckOutlined, ClockCircleOutlined, ExclamationCircleOutlined, StopOutlined, WalletOutlined } from '@ant-design/icons';
import { App, Button, Card, Col, Flex, Form, Input, Modal, Result, Row, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { api, BASIS_LABELS, type Operation } from '../api';
import { Amount } from '../components/Amount';
import { ErrorState } from '../components/ErrorState';
import { OperationStatusTag, ToneTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { useAsync } from '../hooks/useAsync';
import { colors, shadowCard } from '../theme/tokens';
import { formatDateTime, formatPoints } from '../utils/format';

/** Контроль начислений финотделом: подтверждение или блокировка с причиной. */
export default function ControlPage() {
  const { message } = App.useApp();
  const queue = useAsync(() => api.getControlQueue(), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [blocking, setBlocking] = useState<Operation | null>(null);
  const [reasonForm] = Form.useForm<{ reason: string }>();

  const list = queue.data ?? [];
  const pending = list.filter((o) => o.status === 'pending');
  const disputed = list.filter((o) => o.disputed);
  const pendingSum = pending.reduce((s, o) => s + Math.max(o.amount, 0), 0);

  const remove = (id: string) => queue.setData((prev) => (prev ?? []).filter((o) => o.id !== id));

  const approve = async (o: Operation) => {
    setBusy(o.id);
    try {
      await api.approveOperation(o.id);
      remove(o.id);
      message.success(`Подтверждено: ${o.employeeName}, ${formatPoints(o.amount)}`);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const block = async () => {
    if (!blocking) return;
    const values = await reasonForm.validateFields().catch(() => null);
    if (!values) return; // ошибки уже показаны у полей
    const { reason } = values;
    setBusy(blocking.id);
    try {
      await api.blockOperation(blocking.id, reason);
      remove(blocking.id);
      message.success(`Заблокировано: ${blocking.employeeName}, ${formatPoints(blocking.amount)}`);
      setBlocking(null);
      reasonForm.resetFields();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

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
      title: 'Сотрудник и основание',
      dataIndex: 'employeeName',
      render: (v: string, o) => (
        <div style={{ minWidth: 240 }}>
          <div style={{ fontWeight: 500 }}>{v}</div>
          <div>{o.reason}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {BASIS_LABELS[o.basis]}. Автор: {o.createdBy}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: 'Почему здесь',
      key: 'why',
      width: 240,
      render: (_, o) => (
        <Flex vertical gap={4} align="flex-start">
          {o.disputed ? <ToneTag tone="error">Спорное</ToneTag> : <OperationStatusTag status={o.status} />}
          {o.comment && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{o.comment}</Typography.Text>}
        </Flex>
      ),
    },
    {
      title: 'Сумма',
      dataIndex: 'amount',
      align: 'right',
      width: 130,
      render: (_, o) => <Amount value={o.amount} original={o.originalAmount} />,
    },
    {
      title: <span className="tsb-visually-hidden">Решение</span>,
      key: 'actions',
      width: 280,
      render: (_, o) => (
        <Flex gap={8} wrap="wrap">
          <Button type="primary" icon={<CheckOutlined />} loading={busy === o.id && !blocking} disabled={busy !== null} onClick={() => approve(o)}>
            Подтвердить
          </Button>
          <Button danger icon={<StopOutlined />} disabled={busy !== null} onClick={() => setBlocking(o)}>
            Заблокировать
          </Button>
        </Flex>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Контроль начислений"
        description="Начисления выше лимита, отмены с отрицательным балансом и спорные операции. Подтвердите или заблокируйте каждую."
      />
      {queue.error && <div style={{ marginBottom: 16 }}><ErrorState error={queue.error} onRetry={queue.reload} /></div>}

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <StatCard label="На согласовании" icon={<ClockCircleOutlined />} iconColor={colors.colorWarning} loading={!queue.data} value={pending.length} />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard label="Сумма к согласованию" icon={<WalletOutlined />} iconColor={colors.colorBonus} loading={!queue.data} value={formatPoints(pendingSum)} />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard label="Спорные" icon={<ExclamationCircleOutlined />} iconColor={colors.colorError} loading={!queue.data} value={disputed.length} footnote="Уже начислены, но под сомнением" />
        </Col>
      </Row>

      <Card style={{ boxShadow: shadowCard }}>
        {queue.data && list.length === 0 ? (
          <Result
            status="success"
            title="Очередь пуста"
            subTitle="Все начисления разобраны. Новые операции выше лимита появятся здесь автоматически."
          />
        ) : (
          <Table<Operation>
            rowKey="id"
            columns={columns}
            dataSource={list}
            loading={queue.loading}
            scroll={{ x: 1000 }}
            pagination={{ pageSize: 10, hideOnSinglePage: true }}
          />
        )}
      </Card>

      <Modal
        open={Boolean(blocking)}
        title="Заблокировать начисление?"
        okText="Заблокировать"
        okButtonProps={{ danger: true, loading: busy !== null }}
        cancelText="Не блокировать"
        onOk={block}
        onCancel={() => {
          setBlocking(null);
          reasonForm.resetFields();
        }}
      >
        {blocking && (
          <Typography.Paragraph>
            {blocking.employeeName}, <span className="tsb-num">{formatPoints(blocking.amount)}</span>: {blocking.reason}. Баллы не попадут на счёт, сотрудник и автор получат уведомление с причиной.
          </Typography.Paragraph>
        )}
        <Form form={reasonForm} layout="vertical" requiredMark={false} validateTrigger="onBlur">
          <Form.Item
            name="reason"
            label="Причина блокировки"
            rules={[{ required: true, whitespace: true, message: 'Укажите причину — её увидят сотрудник и автор начисления' }]}
          >
            <Input.TextArea rows={3} maxLength={300} showCount placeholder="Например: нет подтверждённого результата (BR-5)" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
