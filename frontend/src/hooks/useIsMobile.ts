import { Grid } from 'antd';
import { layout } from '../theme/tokens';

/** true на экранах уже контрольной точки lg (992 px) — там меню уходит в Drawer. */
export function useIsCompact(): boolean {
  const screens = Grid.useBreakpoint();
  // До первого измерения useBreakpoint возвращает {} — считаем экран широким.
  if (screens.lg === undefined) return typeof window !== 'undefined' && window.innerWidth < layout.drawerBreakpoint;
  return !screens.lg;
}

/** true на телефонах (< 576 px). */
export function useIsPhone(): boolean {
  const screens = Grid.useBreakpoint();
  if (screens.sm === undefined) return typeof window !== 'undefined' && window.innerWidth < 576;
  return !screens.sm;
}
