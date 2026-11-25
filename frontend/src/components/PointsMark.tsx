import { colors } from '../theme/tokens';

/** Значок балла — монета цвета colorBonus. Декоративный: сумма всегда подписана текстом «Б». */
export function PointsMark({ size = '1em', onDark = false }: { size?: number | string; onDark?: boolean }) {
  const ink = onDark ? colors.siderBg : colors.colorText;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false" style={{ flex: 'none' }}>
      <circle cx="12" cy="12" r="11" fill={colors.colorBonus} />
      <circle cx="12" cy="12" r="8" fill="none" stroke={ink} strokeOpacity=".18" strokeWidth="1.5" />
      <path d="M9 8h6M12 8v8" stroke={ink} strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
