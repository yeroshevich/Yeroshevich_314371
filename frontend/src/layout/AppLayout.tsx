import { CloseOutlined, LogoutOutlined, MenuFoldOutlined, MenuOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Avatar, Button, Drawer, Dropdown, Flex, Layout, Menu, Tooltip, Typography } from 'antd';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ROLE_LABELS } from '../api';
import { useCurrentUser, useAuth } from '../auth/AuthContext';
import { PageSkeleton } from '../components/PageSkeleton';
import { PointsMark } from '../components/PointsMark';
import { useIsCompact } from '../hooks/useIsMobile';
import { appRoutes, HOME_BY_ROLE, menuFor, WORKSPACE_BY_ROLE } from '../routes';
import { colors, layout } from '../theme/tokens';
import { initials } from '../utils/format';
import { NotificationBell } from './NotificationBell';

const { Sider, Header, Content } = Layout;
const COLLAPSE_KEY = 'tsb.sider.collapsed';

function Brand({ compact = false, onDark = true }: { compact?: boolean; onDark?: boolean }) {
  return (
    <Flex align="center" gap={10} style={{ minWidth: 0 }}>
      <PointsMark size={28} onDark />
      {!compact && (
        <span
          style={{
            fontWeight: 700,
            fontSize: 16,
            letterSpacing: '-0.01em',
            color: onDark ? colors.colorBgContainer : colors.colorText,
            whiteSpace: 'nowrap',
          }}
        >
          TechnoSoft Bonus
        </span>
      )}
    </Flex>
  );
}

/**
 * Каркас приложения (design-system.md, разд. 4): Sider 240/80 px на тёмном siderBg,
 * Header 64 px, контент до 1440 px. Ниже 992 px меню переезжает в Drawer.
 */
export default function AppLayout() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const compact = useIsCompact();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSE_KEY) === '1');
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => setDrawerOpen(false), [pathname]);

  const items = useMemo(
    () => menuFor(user.role).map((m) => ({ key: m.path, icon: m.icon, label: m.label })),
    [user.role],
  );
  // «Вы здесь»: подсвечиваем текущий пункт; экраны вне меню роли — её главную.
  const selected = items.some((i) => i.key === pathname) ? pathname : HOME_BY_ROLE[user.role];
  const pageTitle = appRoutes.find((r) => r.path === pathname)?.title;

  useEffect(() => {
    document.title = pageTitle ? `${pageTitle} — TechnoSoft Bonus` : 'TechnoSoft Bonus';
  }, [pageTitle]);

  const toggleCollapsed = () => {
    setCollapsed((c) => {
      localStorage.setItem(COLLAPSE_KEY, c ? '0' : '1');
      return !c;
    });
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const menu = (
    <Menu
      theme="dark"
      mode="inline"
      inlineIndent={16}
      selectedKeys={[selected]}
      items={items}
      onClick={({ key }) => navigate(key)}
      style={{ borderInlineEnd: 0, padding: '8px 4px 0' }}
      aria-label="Разделы"
    />
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <a href="#main" className="tsb-skip-link">
        Перейти к содержимому
      </a>

      {!compact && (
        <Sider
          width={layout.siderWidth}
          collapsedWidth={layout.siderCollapsedWidth}
          collapsed={collapsed}
          trigger={null}
          style={{ position: 'sticky', top: 0, height: '100vh', overflow: 'auto' }}
        >
          <Flex vertical style={{ height: '100%' }}>
            <div style={{ height: layout.headerHeight, display: 'flex', alignItems: 'center', padding: collapsed ? '0 26px' : '0 24px' }}>
              <Brand compact={collapsed} />
            </div>
            <div style={{ flex: 1 }}>{menu}</div>
            <div style={{ padding: 8, borderTop: '1px solid rgba(255,255,255,.08)' }}>
              <Tooltip title={collapsed ? 'Развернуть меню' : undefined} placement="right">
                <Button
                  type="text"
                  block
                  icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                  onClick={toggleCollapsed}
                  aria-label={collapsed ? 'Развернуть меню' : 'Свернуть меню'}
                  style={{ color: 'rgba(255,255,255,.72)', height: 44, justifyContent: collapsed ? 'center' : 'flex-start' }}
                >
                  {!collapsed && 'Свернуть меню'}
                </Button>
              </Tooltip>
            </div>
          </Flex>
        </Sider>
      )}

      <Drawer
        open={compact && drawerOpen}
        onClose={() => setDrawerOpen(false)}
        placement="left"
        width={layout.siderWidth + 40}
        title={<Brand />}
        closeIcon={<CloseOutlined style={{ color: colors.colorBgContainer }} aria-label="Закрыть меню" />}
        styles={{
          header: { background: colors.siderBg, borderBottom: '1px solid rgba(255,255,255,.08)' },
          body: { background: colors.siderBg, padding: 0 },
        }}
      >
        {menu}
      </Drawer>

      <Layout>
        <Header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 10,
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: compact ? '0 12px 0 8px' : '0 24px',
            borderBottom: `1px solid ${colors.colorBorder}`,
          }}
          className="tsb-no-print"
        >
          {compact && (
            <Button
              type="text"
              icon={<MenuOutlined />}
              onClick={() => setDrawerOpen(true)}
              aria-label="Открыть меню"
              style={{ width: 44, height: 44 }}
            />
          )}
          <div style={{ minWidth: 0, flex: 1 }}>
            {compact ? (
              <Brand onDark={false} />
            ) : (
              <Typography.Text type="secondary">{WORKSPACE_BY_ROLE[user.role]}</Typography.Text>
            )}
          </div>

          <NotificationBell />

          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'who',
                  disabled: true,
                  label: (
                    <div style={{ lineHeight: 1.4, color: colors.colorText }}>
                      <div style={{ fontWeight: 600 }}>{user.fullName}</div>
                      <div style={{ color: colors.colorTextSecondary, fontSize: 12 }}>{user.email}</div>
                    </div>
                  ),
                },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: 'Выйти', onClick: handleLogout },
              ],
            }}
          >
            <Button type="text" style={{ height: 48, padding: '0 8px' }} aria-label={`Профиль: ${user.fullName}, ${ROLE_LABELS[user.role]}`}>
              <Flex align="center" gap={10}>
                <Avatar style={{ background: colors.colorPrimary, flex: 'none' }}>{initials(user.fullName)}</Avatar>
                {!compact && (
                  <span style={{ textAlign: 'left', lineHeight: '18px' }}>
                    <span style={{ display: 'block', fontWeight: 600 }}>{user.fullName}</span>
                    <span style={{ display: 'block', fontSize: 12, color: colors.colorTextSecondary }}>{ROLE_LABELS[user.role]}</span>
                  </span>
                )}
              </Flex>
            </Button>
          </Dropdown>

          {!compact && (
            <Button icon={<LogoutOutlined />} onClick={handleLogout}>
              Выйти
            </Button>
          )}
        </Header>

        <Content id="main" tabIndex={-1} style={{ outline: 'none' }}>
          <div
            className="tsb-content"
            style={{
              maxWidth: layout.contentMaxWidth,
              margin: '0 auto',
              padding: compact ? layout.pagePaddingMobile : layout.pagePadding,
            }}
          >
            <Suspense fallback={<PageSkeleton />}>
              <Outlet />
            </Suspense>
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
