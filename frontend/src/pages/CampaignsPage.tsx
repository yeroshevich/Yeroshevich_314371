import { PlusOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Progress,
  Row,
  Select,
  Skeleton,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { api, CAMPAIGN_STATUS_LABELS, type Campaign, type CampaignStatus } from '../api';
import { ErrorState } from '../components/ErrorState';
import { ToneTag, type Tone } from '../components/OperationStatusTag';
import { PageHeader } from '../components/PageHeader';
import { useAsync } from '../hooks/useAsync';
import { useIsPhone } from '../hooks/useIsMobile';
import { colors, shadowCard } from '../theme/tokens';
import { formatPoints } from '../utils/format';

const CAMPAIGN_TONE: Record<CampaignStatus, Tone> = {
  active: 'success',
  planned: 'processing',
  needs_approval: 'warning',
  finished: 'default',
};

const ORDER: CampaignStatus[] = ['active', 'needs_approval', 'planned', 'finished'];

const period = (c: Pick<Campaign, 'startDate' | 'endDate'>) =>
  `${dayjs(c.startDate).format('D MMM')} – ${dayjs(c.endDate).format('D MMM YYYY')}`;

/** Бонусные кампании (UC9): временные коэффициенты для выбранных отделов. */
export default function CampaignsPage() {
  const campaigns = useAsync(() => api.getCampaigns(), []);
  const departments = useAsync(() => api.getDepartments(), []);
  const [creating, setCreating] = useState(false);

  const deptNames = (ids: string[]) =>
    ids.length === departments.data?.length
      ? 'Все подразделения'
      : ids.map((id) => departments.data?.find((d) => d.id === id)?.name ?? id).join(', ');

  const sorted = [...(campaigns.data ?? [])].sort((a, b) => ORDER.indexOf(a.status) - ORDER.indexOf(b.status));

  return (
    <>
      <PageHeader
        title="Бонусные кампании"
        description="Временные условия начисления: на период кампании баллы выбранных отделов умножаются на коэффициент."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
            Новая кампания
          </Button>
        }
      />
      {campaigns.error && <div style={{ marginBottom: 16 }}><ErrorState error={campaigns.error} onRetry={campaigns.reload} /></div>}

      <Row gutter={[24, 24]}>
        {!campaigns.data &&
          Array.from({ length: 3 }, (_, i) => (
            <Col key={i} xs={24} md={12} xl={8}>
              <Card><Skeleton active /></Card>
            </Col>
          ))}
        {campaigns.data?.length === 0 && (
          <Col span={24}>
            <Card>
              <Empty description="Кампаний пока нет. Запустите первую — например, двойные баллы за релиз.">
                <Button type="primary" onClick={() => setCreating(true)}>Создать кампанию</Button>
              </Empty>
            </Card>
          </Col>
        )}
        {sorted.map((c) => {
          const used = c.budget ? Math.min(100, Math.round((c.spent / c.budget) * 100)) : 0;
          return (
            <Col key={c.id} xs={24} md={12} xl={8}>
              <Card style={{ height: '100%', boxShadow: shadowCard }} styles={{ body: { display: 'flex', flexDirection: 'column', gap: 12, height: '100%' } }}>
                <Flex justify="space-between" align="flex-start" gap={12}>
                  <Typography.Title level={3} style={{ margin: 0 }}>{c.name}</Typography.Title>
                  <ToneTag tone={CAMPAIGN_TONE[c.status]}>{CAMPAIGN_STATUS_LABELS[c.status]}</ToneTag>
                </Flex>
                <Typography.Paragraph type="secondary" style={{ margin: 0 }}>{c.description}</Typography.Paragraph>
                <Descriptions column={1} size="small" colon={false} styles={{ label: { width: 110, color: colors.colorTextSecondary } }}>
                  <Descriptions.Item label="Сроки">{period(c)}</Descriptions.Item>
                  <Descriptions.Item label="Отделы">{deptNames(c.departmentIds)}</Descriptions.Item>
                  <Descriptions.Item label="Коэффициент">
                    <b className="tsb-num">×{c.coefficient.toLocaleString('ru-RU')}</b>
                  </Descriptions.Item>
                </Descriptions>
                <div style={{ marginTop: 'auto' }}>
                  {c.budget === null ? (
                    <Alert type="warning" showIcon message="Бюджет не выделен" description="Кампания заработает после утверждения бюджета финансовым директором." />
                  ) : (
                    <>
                      <Flex justify="space-between" style={{ fontSize: 12 }}>
                        <Typography.Text type="secondary">Израсходовано</Typography.Text>
                        <span className="tsb-num">
                          {formatPoints(c.spent)} из {formatPoints(c.budget)}
                        </span>
                      </Flex>
                      <Progress
                        percent={used}
                        showInfo={false}
                        strokeColor={used >= 90 ? colors.colorWarning : colors.colorPrimary}
                        aria-label={`Бюджет израсходован на ${used}%`}
                      />
                    </>
                  )}
                </div>
              </Card>
            </Col>
          );
        })}
      </Row>

      <CampaignDrawer
        open={creating}
        departments={departments.data?.map((d) => ({ value: d.id, label: d.name })) ?? []}
        onClose={() => setCreating(false)}
        onCreated={(c) => {
          campaigns.setData((prev) => [c, ...(prev ?? [])]);
          setCreating(false);
        }}
      />
    </>
  );
}

interface CampaignForm {
  name: string;
  description: string;
  dates: [Dayjs, Dayjs];
  departmentIds: string[];
  coefficient: number;
  budget?: number | null;
}

function CampaignDrawer({
  open,
  departments,
  onClose,
  onCreated,
}: {
  open: boolean;
  departments: { value: string; label: string }[];
  onClose: () => void;
  onCreated: (c: Campaign) => void;
}) {
  const { message } = App.useApp();
  const phone = useIsPhone();
  const [form] = Form.useForm<CampaignForm>();
  const [saving, setSaving] = useState(false);
  const budget = Form.useWatch('budget', form);

  const save = async () => {
    const v = await form.validateFields().catch(() => null);
    if (!v) return; // ошибки уже показаны у полей
    setSaving(true);
    try {
      const c = await api.createCampaign({
        name: v.name.trim(),
        description: v.description?.trim() ?? '',
        startDate: v.dates[0].startOf('day').toISOString(),
        endDate: v.dates[1].endOf('day').toISOString(),
        departmentIds: v.departmentIds,
        coefficient: v.coefficient,
        budget: v.budget ?? null,
      });
      message.success(c.status === 'needs_approval' ? 'Кампания создана и отправлена на утверждение бюджета' : 'Кампания создана');
      form.resetFields();
      onCreated(c);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title="Новая кампания"
      open={open}
      onClose={onClose}
      width={phone ? '100%' : 520}
      footer={
        <Flex justify="flex-end" gap={8}>
          <Button onClick={onClose}>Закрыть без сохранения</Button>
          <Button type="primary" loading={saving} onClick={save}>
            Создать кампанию
          </Button>
        </Flex>
      }
    >
      <Form<CampaignForm>
        form={form}
        layout="vertical"
        validateTrigger="onBlur"
        initialValues={{ coefficient: 1.5, departmentIds: [] }}
        requiredMark={(label, { required }) => (
          <>
            {label}
            {!required && <Typography.Text type="secondary" style={{ marginInlineStart: 4 }}>(необязательно)</Typography.Text>}
          </>
        )}
      >
        <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Назовите кампанию' }]}>
          <Input maxLength={80} placeholder="Например: Двойные баллы за релиз" />
        </Form.Item>
        <Form.Item name="description" label="Условия для сотрудников" required={false}>
          <Input.TextArea rows={2} maxLength={240} showCount placeholder="За что и как начисляются баллы" />
        </Form.Item>
        <Form.Item name="dates" label="Сроки" rules={[{ required: true, message: 'Выберите даты начала и окончания' }]}>
          <DatePicker.RangePicker format="DD.MM.YYYY" style={{ width: '100%' }} placeholder={['Начало', 'Окончание']} />
        </Form.Item>
        <Form.Item name="departmentIds" label="Отделы" rules={[{ required: true, type: 'array', min: 1, message: 'Выберите хотя бы один отдел' }]}>
          <Select mode="multiple" options={departments} placeholder="Кого касается кампания" />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="coefficient" label="Коэффициент" extra="От 1,1 до 3" rules={[{ required: true, message: 'Укажите коэффициент' }]}>
              <InputNumber<number> min={1.1} max={3} step={0.1} precision={1} decimalSeparator="," prefix="×" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="budget" label="Бюджет" required={false}>
              <InputNumber<number> min={1000} step={1000} precision={0} addonAfter="Б" style={{ width: '100%' }} placeholder="Не выделен" />
            </Form.Item>
          </Col>
        </Row>
        {!budget && (
          <Alert
            type="warning"
            showIcon
            message="Кампания без бюджета получит статус «Требуется утверждение»"
            description="Начисления по ней начнутся после утверждения бюджета финансовым директором."
          />
        )}
      </Form>
    </Drawer>
  );
}
