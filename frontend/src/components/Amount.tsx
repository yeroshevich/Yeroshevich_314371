import { theme, Typography } from 'antd';
import { formatPoints } from '../utils/format';

interface AmountProps {
  value: number;
  /** Показать «+» у положительных сумм. */
  signed?: boolean;
  /** Сумма до корректировки — показывается мелко под основной. */
  original?: number;
  strong?: boolean;
}

/**
 * Сумма в баллах. Зелёный/красный — тёмные оттенки colorSuccess/colorError из темы,
 * чтобы текст проходил контраст 4,5:1 на белом.
 */
export function Amount({ value, signed = true, original, strong = true }: AmountProps) {
  const { token } = theme.useToken();
  const color = !signed || value === 0 ? token.colorText : value > 0 ? token.colorSuccessText : token.colorErrorText;
  return (
    <span className="tsb-num" style={{ display: 'inline-flex', flexDirection: 'column', lineHeight: 1.3 }}>
      <span style={{ color, fontWeight: strong ? 600 : 400 }}>{formatPoints(value, { signed })}</span>
      {original !== undefined && original !== value && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          было {formatPoints(original, { signed })}
        </Typography.Text>
      )}
    </span>
  );
}
