import { ApiOutlined, BellOutlined, ClusterOutlined, IdcardOutlined, SafetyOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Col, Flex, Form, Input, Row, Skeleton, Switch, Typography } from 'antd';
import { useState, type ReactNode } from 'react';
import { api, type Integration, type IntegrationCheck, type IntegrationKind } from '../../api';
import { ErrorState } from '../../components/ErrorState';
import { ToneTag } from '../../components/OperationStatusTag';
import { PageHeader } from '../../components/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { colors, shadowCard } from '../../theme/tokens';
import { formatAgo, formatDateTime } from '../../utils/format';

const ICONS: Record<IntegrationKind, ReactNode> = {
  hris: <IdcardOutlined />,
  erp: <ClusterOutlined />,
  ad: <SafetyOutlined />,
  notifications: <BellOutlined />,
};

/** Интеграции с корпоративными системами (UC13). */
export default function IntegrationsPage() {
  const list = useAsync(() => api.getIntegrations(), []);
  return (
    <>
      <PageHeader
        title="Интеграции"
        description="Подключения к HRIS, ERP, Active Directory и сервису уведомлений. Сохранить параметры можно только после успешной проверки соединения."
      />
      {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
      <Row gutter={[24, 24]}>
        {!list.data
          ? Array.from({ length: 4 }, (_, i) => (
              <Col key={i} xs={24} lg={12}>
                <Card><Skeleton active paragraph={{ rows: 5 }} /></Card>
              </Col>
            ))
          : list.data.map((i) => (
              <Col key={i.id} xs={24} lg={12}>
                <IntegrationCard
                  integration={i}
                  onChange={(next) => list.setData((prev) => (prev ?? []).map((x) => (x.id === next.id ? next : x)))}
                />
              </Col>
            ))}
      </Row>
    </>
  );
}

function IntegrationCard({ integration, onChange }: { integration: Integration; onChange: (i: Integration) => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ url: string; apiKey: string }>();
  const [check, setCheck] = useState<IntegrationCheck | undefined>(integration.lastCheck);
  const [testedWith, setTestedWith] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toggling, setToggling] = useState(false);
  const url = Form.useWatch('url', form) ?? integration.url;
  const apiKey = Form.useWatch('apiKey', form) ?? integration.apiKey;

  const params = `${url}\n${apiKey}`;
  const dirty = url !== integration.url || apiKey !== integration.apiKey;
  const canSave = dirty && check?.ok === true && testedWith === params;

  const test = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return; // ошибки уже показаны у полей
    setTesting(true);
    try {
      const result = await api.testIntegration(integration.id, values);
      setCheck(result);
      setTestedWith(`${values.url}\n${values.apiKey}`);
      onChange({ ...integration, lastCheck: result });
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setTesting(false);
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      const next = await api.saveIntegration(integration.id, { url, apiKey });
      onChange(next);
      message.success(`${integration.name}: параметры сохранены`);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (enabled: boolean) => {
    setToggling(true);
    try {
      const next = await api.saveIntegration(integration.id, { enabled });
      onChange(next);
      message.success(`${integration.name}: ${enabled ? 'синхронизация включена' : 'синхронизация выключена'}`);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setToggling(false);
    }
  };

  const switchId = `switch-${integration.id}`;
  const canEnable = integration.enabled || integration.lastCheck?.ok === true;

  return (
    <Card style={{ boxShadow: shadowCard, height: '100%' }}>
      <Flex justify="space-between" align="flex-start" gap={12} style={{ marginBottom: 12 }}>
        <Flex gap={12} align="flex-start">
          <span
            aria-hidden
            style={{ fontSize: 20, width: 40, height: 40, borderRadius: 8, display: 'grid', placeItems: 'center', background: colors.colorBgLayout, color: colors.siderBg, flex: 'none' }}
          >
            {ICONS[integration.kind]}
          </span>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>{integration.name}</Typography.Title>
            <Typography.Text type="secondary">{integration.description}</Typography.Text>
          </div>
        </Flex>
        <ToneTag tone={integration.enabled ? 'success' : 'default'}>{integration.enabled ? 'Включена' : 'Выключена'}</ToneTag>
      </Flex>

      <Form name={integration.id} form={form} layout="vertical" requiredMark={false} validateTrigger="onBlur" initialValues={{ url: integration.url, apiKey: integration.apiKey }}>
        <Form.Item name="url" label="Адрес API" rules={[{ required: true, message: 'Введите адрес API' }, { type: 'url', message: 'Нужен полный адрес, например https://hris.technosoft.by/api' }]}>
          <Input prefix={<ApiOutlined aria-hidden />} />
        </Form.Item>
        <Form.Item name="apiKey" label="Ключ API" rules={[{ required: true, message: 'Введите ключ API' }]}>
          <Input.Password autoComplete="off" />
        </Form.Item>
      </Form>

      {check && (
        <Alert
          type={check.ok ? 'success' : 'error'}
          showIcon
          style={{ marginBottom: 16 }}
          message={check.message}
          description={
            check.ok
              ? `Проверено ${formatDateTime(check.checkedAt)}${check.latencyMs ? `, ответ за ${check.latencyMs} мс` : ''}`
              : 'Исправьте адрес или ключ и проверьте снова. Пока проверка не пройдёт, настройки не сохранятся.'
          }
        />
      )}

      <Flex gap={8} wrap="wrap" align="center">
        <Button loading={testing} onClick={test}>
          Проверить соединение
        </Button>
        <Button type="primary" disabled={!canSave} loading={saving} onClick={save}>
          Сохранить параметры
        </Button>
        {dirty && !canSave && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>Сначала проверьте новые параметры</Typography.Text>
        )}
      </Flex>

      <Flex justify="space-between" align="center" gap={12} wrap="wrap" style={{ marginTop: 16, paddingTop: 16, borderTop: `1px solid ${colors.colorBorder}` }}>
        <Flex align="center" gap={12}>
          <Switch id={switchId} checked={integration.enabled} loading={toggling} disabled={!canEnable} onChange={toggle} />
          <label htmlFor={switchId}>Автоматическая синхронизация</label>
        </Flex>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {!canEnable
            ? 'Включится после успешной проверки'
            : integration.lastSyncAt
              ? `Последняя синхронизация ${formatAgo(integration.lastSyncAt)}`
              : 'Синхронизаций ещё не было'}
        </Typography.Text>
      </Flex>
    </Card>
  );
}
