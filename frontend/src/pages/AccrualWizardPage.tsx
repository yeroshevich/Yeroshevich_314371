import { SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Flex,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Radio,
  Result,
  Row,
  Select,
  Skeleton,
  Steps,
  Table,
  Typography,
  theme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  api,
  BASIS_LABELS,
  type AccrualPreview,
  type AccrualRequest,
  type AccrualResult,
  type TeamMember,
} from '../api';
import { ACCRUAL_LIMIT } from '../api/demo';
import { useCurrentUser } from '../auth/AuthContext';
import { Amount } from '../components/Amount';
import { ToneTag } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { useAsync } from '../hooks/useAsync';
import { useIsPhone } from '../hooks/useIsMobile';
import { shadowCard } from '../theme/tokens';
import { formatPoints } from '../utils/format';

type Basis = AccrualRequest['basis'];

interface DetailsForm {
  amount: number;
  basis: Basis;
  ruleId?: string;
  campaignId?: string;
  reason: string;
}

const STEPS = [
  { title: 'Сотрудники', description: 'Кому начислить' },
  { title: 'Сумма и основание', description: 'Расчёт и лимит' },
  { title: 'Подтверждение', description: 'Проверка перед начислением' },
];

const cardStyle = { boxShadow: shadowCard };

/**
 * Ручное начисление (UC5) с расчётом по правилам и кампаниям (UC6).
 * Три шага: выбор сотрудников → сумма, основание и предпросмотр расчёта → подтверждение.
 * Сумма выше лимита не блокирует начисление, а отправляет его на согласование финотделу.
 */
export default function AccrualWizardPage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const phone = useIsPhone();
  const [params] = useSearchParams();
  const [form] = Form.useForm<DetailsForm>();

  const team = useAsync(() => api.getTeam(), []);
  const rules = useAsync(() => api.getRules(), []);
  const campaigns = useAsync(() => api.getCampaigns(), []);

  const [step, setStep] = useState(0);
  const [selected, setSelected] = useState<string[]>(() => params.get('employees')?.split(',').filter(Boolean) ?? []);
  const [query, setQuery] = useState('');
  const [details, setDetails] = useState<DetailsForm>();
  const [preview, setPreview] = useState<AccrualPreview>();
  const [previewing, setPreviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<AccrualResult>();

  const amount = Form.useWatch('amount', form);
  const basis = Form.useWatch('basis', form);
  const campaignId = Form.useWatch('campaignId', form);

  // Предпросмотр расчёта (UC6) — пересчитывается при изменении суммы, кампании или списка.
  useEffect(() => {
    if (step !== 1) return;
    if (!amount || amount <= 0 || !selected.length) {
      setPreview(undefined);
      return;
    }
    setPreviewing(true);
    const timer = setTimeout(() => {
      api
        .previewAccrual({ employeeIds: selected, amount, basis: basis ?? 'kpi', reason: '', campaignId })
        .then(setPreview)
        .catch((e: Error) => message.error(e.message))
        .finally(() => setPreviewing(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [step, amount, campaignId, basis, selected, message]);

  const members = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (team.data ?? []).filter((m) => !q || `${m.fullName} ${m.position} ${m.departmentName}`.toLowerCase().includes(q));
  }, [team.data, query]);

  const selectedMembers = (team.data ?? []).filter((m) => selected.includes(m.id));
  const activeCampaigns = (campaigns.data ?? []).filter((c) => c.status === 'active');

  const goToDetails = () => setStep(1);
  const goToConfirm = async () => {
    try {
      const values = await form.validateFields().catch(() => null);
    if (!values) return; // ошибки уже показаны у полей
      setDetails(values);
      setPreview(await api.previewAccrual({ employeeIds: selected, ...values }));
      setStep(2);
    } catch {
      /* ошибки показаны у полей формы */
    }
  };

  const submit = async () => {
    if (!details) return;
    setSubmitting(true);
    try {
      const r = await api.createAccrual({ employeeIds: selected, ...details, reason: details.reason.trim() });
      setResult(r);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const restart = () => {
    setResult(undefined);
    setSelected([]);
    setDetails(undefined);
    setPreview(undefined);
    form.resetFields();
    setStep(0);
  };

  const cancel = (
    <Popconfirm
      title="Отменить начисление?"
      description="Введённые данные не сохранятся."
      okText="Да, отменить"
      cancelText="Продолжить"
      onConfirm={() => navigate('/team')}
    >
      <Button type="text">Отменить</Button>
    </Popconfirm>
  );

  if (result) {
    const allAccrued = result.pendingCount === 0;
    return (
      <Card style={cardStyle}>
        <Result
          status={allAccrued ? 'success' : 'info'}
          title={allAccrued ? 'Баллы начислены' : 'Начисление создано'}
          subTitle={
            allAccrued
              ? `${result.accruedCount} сотр. получили баллы и уведомление.`
              : `Сразу начислено: ${result.accruedCount}. Ждут согласования финотдела: ${result.pendingCount} — сотрудники увидят их со статусом «На согласовании».`
          }
          extra={[
            <Button type="primary" key="new" onClick={restart}>
              Новое начисление
            </Button>,
            <Button key="team" onClick={() => navigate('/team')}>
              {user.role === 'hr' ? 'К списку сотрудников' : 'К команде'}
            </Button>,
          ]}
        />
      </Card>
    );
  }

  const memberColumns: ColumnsType<TeamMember> = [
    {
      title: 'Сотрудник',
      dataIndex: 'fullName',
      render: (_, m) => (
        <div>
          <div style={{ fontWeight: 500 }}>{m.fullName}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{m.position}</Typography.Text>
        </div>
      ),
    },
    ...(user.role === 'hr' ? [{ title: 'Подразделение', dataIndex: 'departmentName', responsive: ['md'] } as ColumnsType<TeamMember>[number]] : []),
    { title: 'KPI', dataIndex: 'kpiCompletion', width: 80, render: (v: number) => <span className="tsb-num">{v} %</span> },
    {
      title: 'Баланс',
      dataIndex: 'balance',
      align: 'right',
      width: 120,
      render: (v: number, m) => (m.accountStatus === 'blocked' ? <ToneTag tone="dark">Счёт заблокирован</ToneTag> : <Amount value={v} signed={false} strong={false} />),
    },
  ];

  return (
    <>
      <PageHeader
        title="Начисление баллов"
        description={user.role === 'manager' ? 'Ручное начисление сотрудникам вашей команды за результат, который не учли правила.' : 'Ручное начисление одному или нескольким сотрудникам.'}
      />

      <Card style={{ ...cardStyle, marginBottom: 24 }}>
        <Steps current={step} items={STEPS} size={phone ? 'small' : 'default'} direction={phone ? 'vertical' : 'horizontal'} />
      </Card>

      {step === 0 && (
        <Card style={cardStyle} title="Кому начислить">
          <Flex gap={12} wrap="wrap" align="center" style={{ marginBottom: 16 }}>
            <Input
              allowClear
              prefix={<SearchOutlined aria-hidden />}
              placeholder="Поиск по имени, должности, отделу"
              aria-label="Поиск сотрудника"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              style={{ width: 340, maxWidth: '100%' }}
            />
            <Typography.Text type="secondary">
              Выбрано: <b className="tsb-num">{selected.length}</b>
            </Typography.Text>
            {selected.length > 0 && (
              <Button type="link" onClick={() => setSelected([])}>
                Снять выбор
              </Button>
            )}
          </Flex>
          <Table<TeamMember>
            rowKey="id"
            size="middle"
            columns={memberColumns}
            dataSource={members}
            loading={team.loading}
            scroll={{ x: 560 }}
            pagination={{ pageSize: 8, hideOnSinglePage: true, showSizeChanger: false }}
            onRow={(m) => ({
              onClick: () => {
                if (m.accountStatus === 'blocked') return;
                setSelected((s) => (s.includes(m.id) ? s.filter((x) => x !== m.id) : [...s, m.id]));
              },
              style: { cursor: m.accountStatus === 'blocked' ? 'not-allowed' : 'pointer' },
            })}
            rowSelection={{
              selectedRowKeys: selected,
              preserveSelectedRowKeys: true,
              onChange: (keys) => setSelected(keys as string[]),
              getCheckboxProps: (m) => ({ disabled: m.accountStatus === 'blocked', 'aria-label': `Выбрать: ${m.fullName}` }),
            }}
          />
          <Flex justify="space-between" align="center" gap={12} wrap="wrap" style={{ marginTop: 16 }}>
            {cancel}
            <Flex align="center" gap={12} wrap="wrap">
              {!selected.length && <Typography.Text type="secondary">Выберите хотя бы одного сотрудника</Typography.Text>}
              <Button type="primary" disabled={!selected.length} onClick={goToDetails}>
                Далее: сумма
              </Button>
            </Flex>
          </Flex>
        </Card>
      )}

      {step === 1 && (
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={12}>
            <Card style={cardStyle} title="Сумма и основание">
              <Form<DetailsForm>
                form={form}
                layout="vertical"
                validateTrigger="onBlur"
                initialValues={details ?? { basis: 'kpi' }}
                requiredMark={(label, { required }) => (
                  <>
                    {label}
                    {!required && <Typography.Text type="secondary" style={{ marginInlineStart: 4 }}>(необязательно)</Typography.Text>}
                  </>
                )}
              >
                <Form.Item
                  name="amount"
                  label="Сумма на одного сотрудника"
                  extra={`Без согласования — до ${formatPoints(ACCRUAL_LIMIT)} на сотрудника`}
                  rules={[
                    { required: true, message: 'Введите сумму' },
                    { type: 'number', min: 1, message: 'Сумма должна быть больше нуля' },
                    { type: 'number', max: 50000, message: 'Больше 50 000 Б за раз начислить нельзя — разбейте начисление' },
                  ]}
                >
                  <InputNumber<number>
                    min={1}
                    step={50}
                    precision={0}
                    addonAfter="Б"
                    style={{ width: '100%' }}
                    formatter={(v) => (v ? Number(v).toLocaleString('ru-RU') : '')}
                    parser={(v) => Number((v ?? '').replace(/\D/g, ''))}
                    placeholder="Например, 800"
                  />
                </Form.Item>
                <Form.Item name="basis" label="Основание" rules={[{ required: true }]}>
                  <Radio.Group optionType="button" buttonStyle="solid" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {(['kpi', 'project', 'achievement'] as Basis[]).map((b) => (
                      <Radio.Button key={b} value={b} style={{ borderRadius: 8, borderInlineStartWidth: 1 }}>
                        {b === 'kpi' ? 'KPI' : b === 'project' ? 'Проект' : 'Особое достижение'}
                      </Radio.Button>
                    ))}
                  </Radio.Group>
                </Form.Item>
                {basis === 'kpi' && (
                  <Form.Item name="ruleId" label="Правило начисления" required={false}>
                    <Select
                      allowClear
                      placeholder="Выберите правило KPI"
                      loading={rules.loading}
                      options={rules.data?.filter((r) => r.active).map((r) => ({ value: r.id, label: `${r.name} — ${r.kpi}` }))}
                    />
                  </Form.Item>
                )}
                <Form.Item
                  name="campaignId"
                  label="Бонусная кампания"
                  required={false}
                  extra="Коэффициент кампании применяется к сотрудникам её отделов"
                >
                  <Select
                    allowClear
                    placeholder="Без кампании"
                    loading={campaigns.loading}
                    options={activeCampaigns.map((c) => ({ value: c.id, label: `${c.name}, ×${c.coefficient.toLocaleString('ru-RU')}` }))}
                  />
                </Form.Item>
                <Form.Item
                  name="reason"
                  label="Причина"
                  rules={[
                    { required: true, whitespace: true, message: 'Напишите, за что начисляете — сотрудник увидит это в истории' },
                    { min: 10, message: 'Опишите причину чуть подробнее, от 10 символов' },
                  ]}
                >
                  <Input.TextArea rows={3} maxLength={300} showCount placeholder="Например: доклад на внутреннем митапе фронтенда" />
                </Form.Item>
              </Form>
              <Flex justify="space-between" gap={12} wrap="wrap">
                <Flex gap={8}>
                  <Button onClick={() => setStep(0)}>Назад</Button>
                  {cancel}
                </Flex>
                <Button type="primary" onClick={goToConfirm}>
                  Далее: проверить
                </Button>
              </Flex>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <PreviewCard preview={preview} loading={previewing} />
          </Col>
        </Row>
      )}

      {step === 2 && details && preview && (
        <Card style={cardStyle} title="Проверьте перед начислением">
          {preview.approvalCount > 0 && <ApprovalAlert count={preview.approvalCount} />}
          <Descriptions column={{ xs: 1, md: 2 }} bordered size="middle" style={{ marginBottom: 24 }}>
            <Descriptions.Item label="Получатели" span={2}>
              {selectedMembers.map((m) => m.fullName).join(', ')}
            </Descriptions.Item>
            <Descriptions.Item label="Основание">{BASIS_LABELS[details.basis]}</Descriptions.Item>
            <Descriptions.Item label="Кампания">
              {activeCampaigns.find((c) => c.id === details.campaignId)?.name ?? 'Без кампании'}
            </Descriptions.Item>
            <Descriptions.Item label="Причина" span={2}>{details.reason}</Descriptions.Item>
            <Descriptions.Item label="На сотрудника">
              <span className="tsb-num">{formatPoints(details.amount)}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Итого">
              <b className="tsb-num">{formatPoints(preview.total)}</b>
            </Descriptions.Item>
          </Descriptions>
          <Flex justify="space-between" gap={12} wrap="wrap">
            <Flex gap={8}>
              <Button onClick={() => setStep(1)}>Назад</Button>
              {cancel}
            </Flex>
            <Button type="primary" loading={submitting} onClick={submit}>
              Начислить {formatPoints(preview.total)}
            </Button>
          </Flex>
        </Card>
      )}
    </>
  );
}

function ApprovalAlert({ count }: { count: number }) {
  return (
    <Alert
      type="warning"
      showIcon
      style={{ marginBottom: 16 }}
      message="Потребуется согласование"
      description={`${count} сотр. получат больше ${formatPoints(ACCRUAL_LIMIT)}. Эти начисления уйдут в финотдел со статусом «На согласовании» и попадут на счёт после подтверждения.`}
    />
  );
}

/** Предпросмотр расчёта: база × коэффициент кампании, отметка о лимите. */
function PreviewCard({ preview, loading }: { preview?: AccrualPreview; loading: boolean }) {
  const { token } = theme.useToken();
  return (
    <Card style={{ ...cardStyle, height: '100%' }} title="Расчёт" extra={loading && <Typography.Text type="secondary">Пересчитываем…</Typography.Text>}>
      {!preview ? (
        loading ? (
          <Skeleton active />
        ) : (
          <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
            Введите сумму — здесь появится расчёт по каждому сотруднику с учётом коэффициента кампании и лимита.
          </Typography.Paragraph>
        )
      ) : (
        <>
          {preview.approvalCount > 0 && <ApprovalAlert count={preview.approvalCount} />}
          <Table
            rowKey="employeeId"
            size="small"
            pagination={false}
            dataSource={preview.lines}
            scroll={{ x: 420 }}
            columns={[
              { title: 'Сотрудник', dataIndex: 'employeeName' },
              {
                title: 'Коэфф.',
                dataIndex: 'coefficient',
                width: 70,
                render: (v: number) => <span className="tsb-num">×{v.toLocaleString('ru-RU')}</span>,
              },
              {
                title: 'Итого',
                dataIndex: 'total',
                align: 'right',
                width: 170,
                render: (v: number, l) => (
                  <Flex vertical align="flex-end" gap={2}>
                    <span className="tsb-num" style={{ fontWeight: 600 }}>{formatPoints(v)}</span>
                    {l.needsApproval && <span style={{ fontSize: 12, color: token.colorWarningText }}>на согласование</span>}
                  </Flex>
                ),
              },
            ]}
            summary={() => (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={2}>
                  <b>Всего</b>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <b className="tsb-num">{formatPoints(preview.total)}</b>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            )}
          />
        </>
      )}
    </Card>
  );
}
