/**
 * Токены системы дизайна «TechnoSoft Bonus» (design-system.md, разделы 1–4).
 * Единственный источник цветов, шрифтов, отступов и размеров в клиенте:
 * компоненты берут значения отсюда или из темы Ant Design, собранной из этих же токенов.
 */

/** Раздел 1 — цвета. */
export const colors = {
  colorPrimary: '#3B5BDB',
  colorPrimaryHover: '#4C6EF5',
  colorBonus: '#F59F00',
  colorSuccess: '#2F9E44',
  colorError: '#E03131',
  colorWarning: '#F08C00',
  colorInfo: '#1C7ED6',
  colorText: '#1F2430',
  colorTextSecondary: '#5C6370',
  colorBorder: '#E3E6EC',
  colorBgLayout: '#F5F7FA',
  colorBgContainer: '#FFFFFF',
  siderBg: '#14213D',
} as const;

export type ColorToken = keyof typeof colors;

/** Назначение цветов — для витрины /design-system и отчёта. */
export const colorPurpose: Record<ColorToken, string> = {
  colorPrimary: 'Основной: кнопки, активный пункт меню, ссылки',
  colorPrimaryHover: 'Наведение на основные элементы',
  colorBonus: 'Бонусные баллы: баланс, значок балла',
  colorSuccess: 'Начисление, «активно», успешные операции',
  colorError: 'Списание, ошибки, блокировка',
  colorWarning: '«На согласовании», предупреждения',
  colorInfo: 'Информационные сообщения',
  colorText: 'Основной текст',
  colorTextSecondary: 'Вторичный текст, подписи',
  colorBorder: 'Границы, разделители',
  colorBgLayout: 'Фон приложения',
  colorBgContainer: 'Фон карточек и таблиц',
  siderBg: 'Фон боковой панели навигации',
};

/** Раздел 2 — типографика. */
export const fontFamily =
  "Inter, 'Segoe UI', Roboto, Arial, sans-serif";

export interface TypeLevel {
  name: string;
  role: string;
  fontSize: number;
  lineHeight: number;
  fontWeight: 400 | 500 | 600 | 700;
}

export const typeScale = {
  display: { name: 'Display', role: 'Баланс', fontSize: 40, lineHeight: 48, fontWeight: 700 },
  h1: { name: 'H1', role: 'Заголовок страницы', fontSize: 28, lineHeight: 36, fontWeight: 600 },
  h2: { name: 'H2', role: 'Заголовок блока', fontSize: 22, lineHeight: 30, fontWeight: 600 },
  h3: { name: 'H3', role: 'Заголовок карточки', fontSize: 18, lineHeight: 26, fontWeight: 600 },
  body: { name: 'Body', role: 'Основной текст', fontSize: 14, lineHeight: 22, fontWeight: 400 },
  small: { name: 'Small', role: 'Подписи', fontSize: 12, lineHeight: 20, fontWeight: 400 },
} as const satisfies Record<string, TypeLevel>;

/** Раздел 3 — отступы (кратны 4), скругления, тень, высоты контролов. */
export const space = { xxs: 4, xs: 8, sm: 12, md: 16, lg: 24, xl: 32, xxl: 48 } as const;

export const radius = { control: 8, card: 12 } as const;

export const shadowCard =
  '0 1px 2px rgba(16,24,40,.06), 0 1px 3px rgba(16,24,40,.10)';

export const controlHeight = { lg: 40, md: 32, sm: 24 } as const;

/** Раздел 4 — сетка и макет. */
export const layout = {
  siderWidth: 240,
  siderCollapsedWidth: 80,
  headerHeight: 64,
  contentMaxWidth: 1440,
  pagePadding: 24,
  pagePaddingMobile: 16,
  gutter: 24,
  gutterMobile: 16,
  /** Ниже этой ширины боковая панель заменяется выдвижным меню (Drawer). */
  drawerBreakpoint: 992,
} as const;

export const breakpoints = { xs: 0, sm: 576, md: 768, lg: 992, xl: 1200, xxl: 1600 } as const;
