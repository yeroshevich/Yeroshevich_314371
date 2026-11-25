import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Col, Drawer, Flex, Form, Input, InputNumber, Row, Select, Switch, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { api, PERIODICITY_LABELS, type Rule, type RuleInput, type RulePeriodicity } from '../api';
import { ErrorState } from '../components/ErrorState';
import { ToneTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { useAsync } from '../hooks/useAsync';
import { useIsPhone } from '../hooks/useIsMobile';
import { colors, shadowCard } from '../theme/tokens';
import { formatDate, formatPoints } from '../utils/format';

const nextPeriodStart = () => dayjs().add(1, 'month').startOf('month');

/** Правила начисления (UC8, BR-4: бонус = база × вес показателя × уровень достижения). */
export default function RulesPage() {
  const rules = useAsync(() => api.getRules(), []);
  const [editing, setEditing] = useState<Rule | 'new' | null>(null);

  const columns: ColumnsType<Rule> = [
    {
      title: 'Правило',
      dataIndex: 'name',
      render: (v: string, r) => (
        <div style={{ minWidth: 200 }}>
          <div style={{ fontWeight: 500 }}>{v}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.kpi}</Typography.Text>
        </div>
      ),
    },
    { title: 'Вес', dataIndex: 'weight', width: 70, render: (v: number) => <span className="tsb-num">{v.toLocaleString('ru-RU')}</span> },
    { title: 'Формула', dataIndex: 'formula', width: 260 },
    { title: 'Периодичность', dataIndex: 'periodicity', width: 140, render: (v: RulePeriodicity) => PERIODICITY_LABELS[v] },
    { title: 'Порог', dataIndex: 'threshold', width: 80, render: (v: number) => <span className="tsb-num">{v} %</span> },
    {
      title: 'Статус',
      dataIndex: 'active',
      width: 190,
      render: (v: boolean, r) => (
        <Flex vertical gap={4} align="flex-start">
          <ToneTag tone={v ? 'success' : 'default'}>{v ? 'Активно' : 'Отключено'}</ToneTag>
          {r.usedInCurrentPeriod && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>Применялось в этом периоде</Typography.Text>
          )}
        </Flex>
      ),
    },
    {
      title: <span className="tsb-visually-hidden">Действия</span>,
      key: 'actions',
      width: 120,
      render: (_, r) => (
        <Button icon={<EditOutlined />} onClick={() => setEditing(r)}>
          Изменить
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Правила начисления"
        description="По этим правилам система сама начисляет баллы при закрытии периода или наступлении события."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            Новое правило
          </Button>
        }
      />
      {rules.error && <div style={{ marginBottom: 16 }}><ErrorState error={rules.error} onRetry={rules.reload} /></div>}
      <Card style={{ boxShadow: shadowCard }}>
        <Table<Rule>
          rowKey="id"
          columns={columns}
          dataSource={rules.data}
          loading={rules.loading}
          scroll={{ x: 1080 }}
          pagination={false}
        />
      </Card>
      <RuleDrawer
        rule={editing}
        onClose={() => setEditing(null)}
        onSaved={(rule) => {
          rules.setData((prev) => {
            const list = prev ?? [];
            return list.some((r) => r.id === rule.id) ? list.map((r) => (r.id === rule.id ? rule : r)) : [...list, rule];
          });
          setEditing(null);
        }}
      />
    </>
  );
}

const EMPTY_RULE: RuleInput = {
  name: '',
  kpi: '',
  weight: 0.3,
  basePoints: 2000,
  periodicity: 'month',
  threshold: 80,
  active: true,
};

function RuleDrawer({ rule, onClose, onSaved }: { rule: Rule | 'new' | null; onClose: () => void; onSaved: (r: Rule) => void }) {
  const { message } = App.useApp();
  const phone = useIsPhone();
  const [form] = Form.useForm<RuleInput>();
  const [saving, setSaving] = useState(false);
  const existing = rule && rule !== 'new' ? rule : null;
  const base = Form.useWatch('basePoints', form);
  const weight = Form.useWatch('weight', form);

  const save = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return; // ошибки уже показаны у полей
    setSaving(true);
    try {
      const result = await api.saveRule(values, existing?.id);
      message.success(
        result.deferred
          ? `Правило сохранено. Изменения вступят в силу ${formatDate(result.effectiveFrom)}`
          : existing ? 'Правило сохранено' : 'Правило создано и уже применяется',
      );
      onSaved(result.rule);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  // Один экземпляр формы на все правила: при открытии сбрасываем к значениям выбранного.
  useEffect(() => {
    if (rule) form.resetFields();
  }, [rule, form]);

  const example = base && weight ? Math.round(base * weight) : undefined;

  return (
    <Drawer
      title={existing ? `Правило «${existing.name}»` : 'Новое правило'}
      open={Boolean(rule)}
      onClose={onClose}
      width={phone ? '100%' : 520}
      footer={
        <Flex justify="flex-end" gap={8}>
          <Button onClick={onClose}>Закрыть без сохранения</Button>
          <Button type="primary" loading={saving} onClick={save}>
            {existing ? 'Сохранить изменения' : 'Создать правило'}
          </Button>
        </Flex>
      }
    >
      {existing?.usedInCurrentPeriod && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Изменения вступят в силу со следующего периода"
          description={`Правило уже участвовало в расчётах этого месяца. Новые условия начнут действовать с ${nextPeriodStart().format('D MMMM')}, текущие начисления не пересчитаются.`}
        />
      )}
      {rule && (
        <Form<RuleInput>
          key={existing?.id ?? 'new'}
          form={form}
          layout="vertical"
          validateTrigger="onBlur"
          requiredMark={false}
          initialValues={existing ?? EMPTY_RULE}
        >
          <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Назовите правило так, чтобы сотрудник понял, за что баллы' }]}>
            <Input maxLength={80} placeholder="Например: Выполнение плана спринта" />
          </Form.Item>
          <Form.Item name="kpi" label="Показатель KPI" rules={[{ required: true, whitespace: true, message: 'Укажите, что измеряем' }]}>
            <Input maxLength={120} placeholder="Например: Закрытые задачи спринта, %" />
          </Form.Item>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="basePoints" label="База" rules={[{ required: true, message: 'Укажите базу' }]}>
                <InputNumber<number> min={100} max={50000} step={100} precision={0} addonAfter="Б" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="weight" label="Вес показателя" extra="От 0,05 до 1" rules={[{ required: true, message: 'Укажите вес' }]}>
                <InputNumber<number> min={0.05} max={1} step={0.05} precision={2} decimalSeparator="," style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="periodicity" label="Периодичность">
                <Select options={(Object.keys(PERIODICITY_LABELS) as RulePeriodicity[]).map((p) => ({ value: p, label: PERIODICITY_LABELS[p] }))} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="threshold" label="Порог срабатывания" extra="Минимальное выполнение KPI" rules={[{ required: true, message: 'Укажите порог' }]}>
                <InputNumber<number> min={0} max={150} precision={0} addonAfter="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <div style={{ background: colors.colorBgLayout, borderRadius: 8, padding: 16, marginBottom: 24 }}>
            <Typography.Text type="secondary">Формула</Typography.Text>
            <div style={{ fontWeight: 600, margin: '4px 0' }} className="tsb-num">
              {base?.toLocaleString('ru-RU') ?? '…'} × {weight?.toLocaleString('ru-RU') ?? '…'} × уровень достижения
            </div>
            {example !== undefined && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                При выполнении KPI на 100 % сотрудник получит {formatPoints(example)}
              </Typography.Text>
            )}
          </div>
          <Form.Item name="active" label="Правило применяется" valuePropName="checked">
            <Switch checkedChildren="Да" unCheckedChildren="Нет" />
          </Form.Item>
        </Form>
      )}
    </Drawer>
  );
}
