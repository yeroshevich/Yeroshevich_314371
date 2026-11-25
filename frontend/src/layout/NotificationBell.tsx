import { BellOutlined } from '@ant-design/icons';
import { Badge, Button, Empty, Flex, Popover, Spin, Typography } from 'antd';
import { useState } from 'react';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { colors } from '../theme/tokens';
import { formatAgo } from '../utils/format';

/** Колокольчик уведомлений в шапке: счётчик непрочитанных и список последних событий. */
export function NotificationBell() {
  const { data, loading, setData } = useAsync(() => api.getNotifications(), []);
  const [open, setOpen] = useState(false);
  const unread = data?.filter((n) => !n.read).length ?? 0;

  const markRead = async () => {
    await api.markNotificationsRead();
    setData((prev) => (prev ?? []).map((n) => ({ ...n, read: true })));
  };

  const content = (
    <div style={{ width: 320, maxWidth: 'calc(100vw - 48px)' }}>
      <Flex justify="space-between" align="center" style={{ marginBottom: 8 }}>
        <Typography.Text strong>Уведомления</Typography.Text>
        {unread > 0 && (
          <Button type="link" size="small" onClick={markRead} style={{ paddingInline: 0 }}>
            Отметить прочитанными
          </Button>
        )}
      </Flex>
      {loading && !data ? (
        <Flex justify="center" style={{ padding: 24 }}><Spin /></Flex>
      ) : data && data.length ? (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, maxHeight: 360, overflow: 'auto' }}>
          {data.map((n) => (
            <li key={n.id} style={{ padding: '10px 0', borderTop: `1px solid ${colors.colorBorder}` }}>
              <Flex gap={8} align="baseline">
                <span
                  aria-label={n.read ? undefined : 'Новое'}
                  style={{ width: 8, height: 8, borderRadius: 4, flex: 'none', background: n.read ? 'transparent' : colors.colorPrimary }}
                />
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: n.read ? 400 : 600 }}>{n.title}</div>
                  <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block' }}>{n.text}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>{formatAgo(n.createdAt)}</Typography.Text>
                </div>
              </Flex>
            </li>
          ))}
        </ul>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Новых событий нет. Здесь появятся начисления и решения по ним." />
      )}
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomRight" open={open} onOpenChange={setOpen}>
      <Button
        type="text"
        aria-label={unread ? `Уведомления: ${unread} новых` : 'Уведомления'}
        style={{ width: 44, height: 44 }}
        icon={
          <Badge count={unread} size="small" offset={[2, -2]}>
            <BellOutlined style={{ fontSize: 18 }} />
          </Badge>
        }
      />
    </Popover>
  );
}
