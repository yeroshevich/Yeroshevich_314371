import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Divider, Flex, Form, Input, Row, Typography } from 'antd';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import type { Role } from '../api';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '../api/demo';
import { useAuth } from '../auth/AuthContext';
import { PointsMark } from '../components/PointsMark';
import { HOME_BY_ROLE } from '../routes';
import { colors, typeScale } from '../theme/tokens';
import { formatPoints } from '../utils/format';

const DEMO_ROLES: { role: Role; as: string; can: string }[] = [
  { role: 'employee', as: 'сотрудник', can: 'баланс, история, программы' },
  { role: 'manager', as: 'руководитель', can: 'команда, начисления, аналитика' },
  { role: 'hr', as: 'HR', can: 'правила, кампании, отчёты' },
  { role: 'finance', as: 'финотдел', can: 'согласование и блокировка' },
  { role: 'admin', as: 'администратор', can: 'пользователи, интеграции, аудит' },
];

/** Как выглядит движение по счёту — вместо абстрактной картинки на панели входа. */
const SAMPLE_LINES = [
  { amount: 1650, text: 'План спринта выполнен на 106 %' },
  { amount: 800, text: 'Сдача этапа проекта в срок' },
  { amount: -1500, text: 'Сертификат в книжный магазин' },
];

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm<{ email: string; password: string }>();
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState<string | null>(null);

  if (user) return <Navigate to={HOME_BY_ROLE[user.role]} replace />;

  const signIn = async (email: string, password: string, key: string) => {
    setError(undefined);
    setPending(key);
    try {
      const u = await login(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/' ? from : HOME_BY_ROLE[u.role], { replace: true });
    } catch (e) {
      setError((e as Error).message);
      setPending(null);
    }
  };

  return (
    <Row style={{ minHeight: '100vh' }}>
      <Col xs={0} lg={11} style={{ background: colors.siderBg, color: colors.colorBgContainer }}>
        <Flex vertical justify="space-between" style={{ height: '100%', padding: '40px 56px' }}>
          <Flex align="center" gap={10}>
            <PointsMark size={32} onDark />
            <span style={{ fontWeight: 700, fontSize: 18 }}>TechnoSoft Bonus</span>
          </Flex>

          <div style={{ maxWidth: 440 }}>
            <p style={{ fontSize: 40, lineHeight: '48px', fontWeight: 700, margin: '0 0 16px', letterSpacing: '-0.02em' }}>
              Баллы за результат — на одном счёте
            </p>
            <p style={{ margin: '0 0 32px', color: 'rgba(255,255,255,.72)', fontSize: 16, lineHeight: '26px' }}>
              Начисления за KPI, проекты и достижения приходят сюда автоматически. Тратьте их на обучение, отдых и подарки.
            </p>
            <div aria-hidden style={{ borderRadius: 12, background: 'rgba(255,255,255,.06)', padding: '8px 20px' }}>
              {SAMPLE_LINES.map((l) => (
                <Flex key={l.text} justify="space-between" gap={16} style={{ padding: '12px 0', borderBottom: '1px solid rgba(255,255,255,.08)' }}>
                  <span style={{ color: 'rgba(255,255,255,.8)' }}>{l.text}</span>
                  <span className="tsb-num" style={{ fontWeight: 600, color: l.amount > 0 ? colors.colorBonus : 'rgba(255,255,255,.8)' }}>
                    {formatPoints(l.amount, { signed: true })}
                  </span>
                </Flex>
              ))}
              <Flex justify="space-between" style={{ padding: '12px 0 8px' }}>
                <span style={{ color: 'rgba(255,255,255,.72)' }}>Доступно</span>
                <span className="tsb-num" style={{ fontWeight: 700, fontSize: typeScale.h3.fontSize, color: colors.colorBonus }}>
                  {formatPoints(8300)}
                </span>
              </Flex>
            </div>
          </div>

          <span style={{ color: 'rgba(255,255,255,.6)', fontSize: 12 }}>МКПАО «TechnoSoft», Минск</span>
        </Flex>
      </Col>

      <Col xs={24} lg={13} style={{ background: colors.colorBgContainer }}>
        <Flex align="center" justify="center" style={{ minHeight: '100%', padding: '40px 16px' }}>
          <main style={{ width: '100%', maxWidth: 400 }}>
            <Flex align="center" gap={10} style={{ marginBottom: 32 }} className="tsb-login-brand">
              <PointsMark size={28} />
              <span style={{ fontWeight: 700, fontSize: 16 }}>TechnoSoft Bonus</span>
            </Flex>
            <Typography.Title level={1} style={{ marginBottom: 4 }}>Вход</Typography.Title>
            <Typography.Paragraph type="secondary">Корпоративная почта и пароль от учётной записи TechnoSoft.</Typography.Paragraph>

            {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

            <Form
              form={form}
              layout="vertical"
              requiredMark={false}
              validateTrigger="onBlur"
              onFinish={(v) => signIn(v.email, v.password, 'form')}
            >
              <Form.Item
                name="email"
                label="Email"
                rules={[
                  { required: true, message: 'Введите email' },
                  { type: 'email', message: 'Похоже, в адресе опечатка: нужен вид name@technosoft.by' },
                ]}
              >
                <Input size="large" prefix={<MailOutlined aria-hidden />} autoComplete="username" placeholder="name@technosoft.by" />
              </Form.Item>
              <Form.Item name="password" label="Пароль" rules={[{ required: true, message: 'Введите пароль' }]}>
                <Input.Password size="large" prefix={<LockOutlined aria-hidden />} autoComplete="current-password" />
              </Form.Item>
              <Button type="primary" htmlType="submit" size="large" block loading={pending === 'form'} disabled={pending !== null && pending !== 'form'}>
                Войти
              </Button>
            </Form>

            <Divider plain style={{ margin: '32px 0 16px' }}>
              <Typography.Text type="secondary">Демо-доступ, пароль «{DEMO_PASSWORD}»</Typography.Text>
            </Divider>

            <Flex vertical gap={8}>
              {DEMO_ROLES.map(({ role, as, can }) => (
                <Button
                  key={role}
                  block
                  size="large"
                  loading={pending === role}
                  disabled={pending !== null && pending !== role}
                  onClick={() => signIn(DEMO_ACCOUNTS[role], DEMO_PASSWORD, role)}
                  style={{ height: 'auto', minHeight: 48, padding: '8px 16px', justifyContent: 'space-between', textAlign: 'left' }}
                >
                  <span style={{ fontWeight: 600 }}>Войти как {as}</span>
                  <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'normal', textAlign: 'right' }}>
                    {can}
                  </Typography.Text>
                </Button>
              ))}
              <Button
                type="link"
                loading={pending === 'blocked'}
                disabled={pending !== null && pending !== 'blocked'}
                onClick={() => signIn(DEMO_ACCOUNTS.blocked, DEMO_PASSWORD, 'blocked')}
                style={{ alignSelf: 'flex-start', paddingInline: 0, height: 44 }}
              >
                Сотрудник с заблокированным счётом
              </Button>
            </Flex>
          </main>
        </Flex>
      </Col>
    </Row>
  );
}
