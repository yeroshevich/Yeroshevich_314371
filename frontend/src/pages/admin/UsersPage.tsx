import { EditOutlined, LockOutlined, PlusOutlined, SearchOutlined, UnlockOutlined } from '@ant-design/icons';
import { App, Button, Card, Drawer, Empty, Flex, Form, Input, Popconfirm, Select, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { api, ROLE_LABELS, type Role, type User, type UserInput } from '../../api';
import { useCurrentUser } from '../../auth/AuthContext';
import { ErrorState } from '../../components/ErrorState';
import { ToneTag } from '../../components/OperationStatusTag';
import { PageHeader } from '../../components/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { useIsPhone } from '../../hooks/useIsMobile';
import { shadowCard } from '../../theme/tokens';
import { formatDateTime } from '../../utils/format';

const ROLE_OPTIONS = (Object.keys(ROLE_LABELS) as Role[]).map((r) => ({ value: r, label: ROLE_LABELS[r] }));

/** Управление пользователями (UC12): создание, изменение, роли, блокировка. */
export default function UsersPage() {
  const me = useCurrentUser();
  const { message } = App.useApp();
  const users = useAsync(() => api.getUsers(), []);
  const departments = useAsync(() => api.getDepartments(), []);
  const [query, setQuery] = useState('');
  const [role, setRole] = useState<Role>();
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const deptName = (id: string) => departments.data?.find((d) => d.id === id)?.name ?? '';

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (users.data ?? []).filter(
      (u) => (!role || u.role === role) && (!q || `${u.fullName} ${u.email} ${u.position}`.toLowerCase().includes(q)),
    );
  }, [users.data, query, role]);

  const upsert = (u: User) =>
    users.setData((prev) => {
      const list = prev ?? [];
      return list.some((x) => x.id === u.id) ? list.map((x) => (x.id === u.id ? u : x)) : [u, ...list];
    });

  const toggleStatus = async (u: User) => {
    setBusy(u.id);
    try {
      const next = await api.setUserStatus(u.id, u.status === 'active' ? 'blocked' : 'active');
      upsert(next);
      message.success(next.status === 'blocked' ? `${u.fullName}: вход заблокирован` : `${u.fullName}: вход снова открыт`);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const columns: ColumnsType<User> = [
    {
      title: 'Пользователь',
      dataIndex: 'fullName',
      sorter: (a, b) => a.fullName.localeCompare(b.fullName, 'ru'),
      render: (v: string, u) => (
        <div style={{ minWidth: 200 }}>
          <div style={{ fontWeight: 500 }}>{v}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{u.email}</Typography.Text>
        </div>
      ),
    },
    {
      title: 'Подразделение и должность',
      dataIndex: 'departmentId',
      render: (v: string, u) => (
        <div style={{ minWidth: 180 }}>
          <div>{deptName(v)}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{u.position}</Typography.Text>
        </div>
      ),
    },
    { title: 'Роль', dataIndex: 'role', width: 130, render: (r: Role) => ROLE_LABELS[r] },
    {
      title: 'Статус',
      dataIndex: 'status',
      width: 130,
      render: (s: User['status']) => <ToneTag tone={s === 'active' ? 'success' : 'dark'}>{s === 'active' ? 'Активен' : 'Заблокирован'}</ToneTag>,
    },
    {
      title: 'Последний вход',
      dataIndex: 'lastLoginAt',
      width: 140,
      responsive: ['xl'],
      render: (v?: string) => (v ? <span className="tsb-num">{formatDateTime(v)}</span> : '—'),
    },
    {
      title: <span className="tsb-visually-hidden">Действия</span>,
      key: 'actions',
      width: 300,
      render: (_, u) => (
        <Flex gap={8}>
          <Button icon={<EditOutlined />} onClick={() => setEditing(u)}>
            Изменить
          </Button>
          {u.id !== me.id && (
            <Popconfirm
              title={u.status === 'active' ? `Заблокировать ${u.fullName}?` : `Разблокировать ${u.fullName}?`}
              description={u.status === 'active' ? 'Пользователь не сможет войти. Баллы на счёте сохранятся.' : 'Пользователь снова сможет войти.'}
              okText={u.status === 'active' ? 'Заблокировать' : 'Разблокировать'}
              okButtonProps={{ danger: u.status === 'active' }}
              cancelText="Отмена"
              onConfirm={() => toggleStatus(u)}
            >
              <Button danger={u.status === 'active'} loading={busy === u.id} icon={u.status === 'active' ? <LockOutlined /> : <UnlockOutlined />}>
                {u.status === 'active' ? 'Заблокировать' : 'Разблокировать'}
              </Button>
            </Popconfirm>
          )}
        </Flex>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Пользователи"
        description="Учётные записи сотрудников и их роли. Роль определяет, какие разделы открыты человеку."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            Добавить пользователя
          </Button>
        }
      />
      {users.error && <div style={{ marginBottom: 16 }}><ErrorState error={users.error} onRetry={users.reload} /></div>}
      <Card style={{ boxShadow: shadowCard }}>
        <Flex gap={12} wrap="wrap" style={{ marginBottom: 16 }}>
          <Input
            allowClear
            prefix={<SearchOutlined aria-hidden />}
            placeholder="Имя, email или должность"
            aria-label="Поиск пользователя"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: 300, maxWidth: '100%' }}
          />
          <Select allowClear placeholder="Все роли" aria-label="Роль" value={role} onChange={setRole} options={ROLE_OPTIONS} style={{ width: 200, maxWidth: '100%' }} />
        </Flex>
        <Table<User>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={users.loading}
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (t) => `Всего ${t}` }}
          locale={{ emptyText: users.loading ? ' ' : <Empty description="Никого не нашли. Проверьте написание или сбросьте фильтр роли." /> }}
        />
      </Card>
      <UserDrawer
        user={editing}
        departments={departments.data?.map((d) => ({ value: d.id, label: d.name })) ?? []}
        onClose={() => setEditing(null)}
        onSaved={(u) => {
          upsert(u);
          setEditing(null);
        }}
      />
    </>
  );
}

function UserDrawer({
  user,
  departments,
  onClose,
  onSaved,
}: {
  user: User | 'new' | null;
  departments: { value: string; label: string }[];
  onClose: () => void;
  onSaved: (u: User) => void;
}) {
  const { message, modal } = App.useApp();
  const phone = useIsPhone();
  const [form] = Form.useForm<UserInput>();
  const [saving, setSaving] = useState(false);
  const existing = user && user !== 'new' ? user : null;

  useEffect(() => {
    if (user) form.resetFields();
  }, [user, form]);

  const persist = async (input: UserInput, id?: string) => {
    setSaving(true);
    try {
      const saved = await api.saveUser(input, id);
      message.success(id ? 'Изменения сохранены' : `Пользователь добавлен. Данные для входа отправлены на ${saved.email}`);
      onSaved(saved);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const save = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return; // ошибки уже показаны у полей
    if (existing) return persist(values, existing.id);
    setSaving(true);
    const duplicate = await api.findUserByEmail(values.email).finally(() => setSaving(false));
    if (!duplicate) return persist(values);
    // Альтернативный поток UC12: такой email уже есть — спрашиваем, обновить ли запись.
    modal.confirm({
      title: 'Пользователь с таким email уже есть',
      content: `${duplicate.fullName}, ${duplicate.position} (${ROLE_LABELS[duplicate.role]}). Обновить его данные тем, что вы ввели?`,
      okText: 'Обновить данные',
      cancelText: 'Вернуться к форме',
      onOk: () => persist(values, duplicate.id),
    });
  };

  return (
    <Drawer
      title={existing ? existing.fullName : 'Новый пользователь'}
      open={Boolean(user)}
      onClose={onClose}
      width={phone ? '100%' : 480}
      footer={
        <Flex justify="flex-end" gap={8}>
          <Button onClick={onClose}>Закрыть без сохранения</Button>
          <Button type="primary" loading={saving} onClick={save}>
            {existing ? 'Сохранить изменения' : 'Добавить пользователя'}
          </Button>
        </Flex>
      }
    >
      {user && (
        <Form<UserInput>
          key={existing?.id ?? 'new'}
          form={form}
          layout="vertical"
          validateTrigger="onBlur"
          requiredMark={false}
          initialValues={existing ?? { role: 'employee' }}
        >
          <Form.Item name="fullName" label="ФИО" rules={[{ required: true, whitespace: true, message: 'Введите имя и фамилию' }]}>
            <Input maxLength={80} placeholder="Например: Анна Ковалёва" />
          </Form.Item>
          <Form.Item
            name="email"
            label="Корпоративный email"
            rules={[
              { required: true, message: 'Введите email' },
              { type: 'email', message: 'Похоже, в адресе опечатка: нужен вид name@technosoft.by' },
            ]}
          >
            <Input placeholder="name@technosoft.by" autoComplete="off" />
          </Form.Item>
          <Form.Item name="departmentId" label="Подразделение" rules={[{ required: true, message: 'Выберите подразделение' }]}>
            <Select options={departments} placeholder="Выберите подразделение" />
          </Form.Item>
          <Form.Item name="position" label="Должность" rules={[{ required: true, whitespace: true, message: 'Введите должность' }]}>
            <Input maxLength={80} />
          </Form.Item>
          <Form.Item name="role" label="Роль" extra="Определяет доступные разделы. Изменение роли попадёт в журнал аудита.">
            <Select options={ROLE_OPTIONS} />
          </Form.Item>
        </Form>
      )}
    </Drawer>
  );
}
