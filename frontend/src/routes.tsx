import {
  ApiOutlined,
  AuditOutlined,
  BarChartOutlined,
  ControlOutlined,
  FileTextOutlined,
  FlagOutlined,
  GiftOutlined,
  HistoryOutlined,
  PlusCircleOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { lazy, type ComponentType, type LazyExoticComponent, type ReactNode } from 'react';
import type { Role } from './api';

/**
 * Реестр маршрутов (design-system.md, разд. 6–7) — единый источник для
 * <Routes>, бокового меню и проверки доступа (SEC-3).
 * `roles` — кто может открыть экран; `menu` — у кого он виден в меню и под каким именем.
 */
export interface AppRoute {
  path: string;
  title: string;
  icon: ReactNode;
  roles: Role[];
  /** Подпись пункта меню по ролям; роли без подписи не видят пункт в меню. */
  menu: Partial<Record<Role, string>>;
  page: LazyExoticComponent<ComponentType>;
}

const page = (load: () => Promise<{ default: ComponentType }>) => lazy(load);

export const appRoutes: AppRoute[] = [
  {
    path: '/me', title: 'Мой баланс', icon: <WalletOutlined />,
    roles: ['employee', 'manager', 'hr'],
    menu: { employee: 'Мой баланс', manager: 'Мой баланс', hr: 'Главная' },
    page: page(() => import('./pages/MyBalancePage')),
  },
  {
    path: '/history', title: 'История операций', icon: <HistoryOutlined />,
    roles: ['employee', 'manager', 'hr'],
    menu: { employee: 'История операций', manager: 'История операций' },
    page: page(() => import('./pages/HistoryPage')),
  },
  {
    path: '/programs', title: 'Программы мотивации', icon: <GiftOutlined />,
    roles: ['employee', 'manager', 'hr'],
    menu: { employee: 'Программы мотивации', manager: 'Программы мотивации' },
    page: page(() => import('./pages/ProgramsPage')),
  },
  {
    path: '/team', title: 'Моя команда', icon: <TeamOutlined />,
    roles: ['manager', 'hr'],
    menu: { manager: 'Моя команда', hr: 'Сотрудники' },
    page: page(() => import('./pages/TeamPage')),
  },
  {
    path: '/accruals/new', title: 'Начисление баллов', icon: <PlusCircleOutlined />,
    roles: ['manager', 'hr'],
    menu: { manager: 'Начисление', hr: 'Начисление' },
    page: page(() => import('./pages/AccrualWizardPage')),
  },
  {
    path: '/control', title: 'Контроль начислений', icon: <SafetyCertificateOutlined />,
    roles: ['finance'],
    menu: { finance: 'Контроль начислений' },
    page: page(() => import('./pages/ControlPage')),
  },
  {
    path: '/operations', title: 'Операции', icon: <UnorderedListOutlined />,
    roles: ['hr', 'finance'],
    menu: { hr: 'Операции', finance: 'Операции' },
    page: page(() => import('./pages/OperationsPage')),
  },
  {
    path: '/rules', title: 'Правила начисления', icon: <ControlOutlined />,
    roles: ['hr'],
    menu: { hr: 'Правила начисления' },
    page: page(() => import('./pages/RulesPage')),
  },
  {
    path: '/campaigns', title: 'Бонусные кампании', icon: <FlagOutlined />,
    roles: ['hr'],
    menu: { hr: 'Кампании' },
    page: page(() => import('./pages/CampaignsPage')),
  },
  {
    path: '/reports', title: 'Отчёты', icon: <FileTextOutlined />,
    roles: ['hr', 'finance'],
    menu: { hr: 'Отчёты', finance: 'Отчёты' },
    page: page(() => import('./pages/ReportsPage')),
  },
  {
    path: '/analytics', title: 'Аналитика', icon: <BarChartOutlined />,
    roles: ['manager', 'hr'],
    menu: { manager: 'Аналитика', hr: 'Аналитика' },
    page: page(() => import('./pages/AnalyticsPage')),
  },
  {
    path: '/admin/users', title: 'Пользователи', icon: <UserOutlined />,
    roles: ['admin'],
    menu: { admin: 'Пользователи' },
    page: page(() => import('./pages/admin/UsersPage')),
  },
  {
    path: '/admin/integrations', title: 'Интеграции', icon: <ApiOutlined />,
    roles: ['admin'],
    menu: { admin: 'Интеграции' },
    page: page(() => import('./pages/admin/IntegrationsPage')),
  },
  {
    path: '/admin/audit', title: 'Журнал аудита', icon: <AuditOutlined />,
    roles: ['admin'],
    menu: { admin: 'Журнал аудита' },
    page: page(() => import('./pages/admin/AuditPage')),
  },
];

/** Стартовый экран после входа — первый пункт меню роли. */
export const HOME_BY_ROLE: Record<Role, string> = {
  employee: '/me',
  manager: '/me',
  hr: '/me',
  finance: '/control',
  admin: '/admin/users',
};

/** Пункты меню роли в порядке реестра. */
export function menuFor(role: Role) {
  return appRoutes
    .filter((r) => r.menu[role])
    .map((r) => ({ path: r.path, label: r.menu[role]!, icon: r.icon }));
}

/** Подпись рабочего места в шапке — подсказывает, «где я». */
export const WORKSPACE_BY_ROLE: Record<Role, string> = {
  employee: 'Кабинет сотрудника',
  manager: 'Кабинет руководителя',
  hr: 'Рабочее место HR',
  finance: 'Финансовый контроль',
  admin: 'Администрирование',
};
