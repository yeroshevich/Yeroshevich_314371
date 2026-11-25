import { Tag, theme } from 'antd';
import type { ReactNode } from 'react';
import { OPERATION_STATUS_LABELS, type OperationStatus } from '../api';
import { colors } from '../theme/tokens';

export type Tone = 'success' | 'error' | 'warning' | 'processing' | 'dark' | 'default';

/**
 * Тег статуса с контрастными цветами. Берёт производные токены темы (…Bg / …Border / …Text),
 * а не пресеты Tag: у пресетов оранжевый и красный текст на светлом фоне не проходит WCAG AA.
 */
export function ToneTag({ tone, children }: { tone: Tone; children: ReactNode }) {
  const { token } = theme.useToken();
  const palette: Record<Tone, { bg: string; border: string; text: string }> = {
    success: { bg: token.colorSuccessBg, border: token.colorSuccessBorder, text: token.colorSuccessText },
    error: { bg: token.colorErrorBg, border: token.colorErrorBorder, text: token.colorErrorText },
    processing: { bg: token.colorPrimaryBg, border: token.colorPrimaryBorder, text: token.colorPrimaryText },
    warning: { bg: token.colorWarningBg, border: token.colorWarningBorder, text: token.colorWarningText },
    dark: { bg: colors.siderBg, border: colors.siderBg, text: colors.colorBgContainer },
    default: { bg: colors.colorBgLayout, border: colors.colorBorder, text: colors.colorTextSecondary },
  };
  const p = palette[tone];
  return (
    <Tag style={{ background: p.bg, borderColor: p.border, color: p.text, marginInlineEnd: 0, fontWeight: 500 }}>
      {children}
    </Tag>
  );
}

/** Статус операции → тон (design-system.md, разд. 5, таблица статусов). */
export const STATUS_TONE: Record<OperationStatus, Tone> = {
  accrued: 'success',
  written_off: 'error',
  corrected: 'processing',
  pending: 'warning',
  blocked: 'dark',
  cancelled: 'default',
};

export function OperationStatusTag({ status }: { status: OperationStatus }) {
  return <ToneTag tone={STATUS_TONE[status]}>{OPERATION_STATUS_LABELS[status]}</ToneTag>;
}
