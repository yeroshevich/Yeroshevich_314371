import { Card, Flex, Skeleton, Typography } from 'antd';
import type { ReactNode } from 'react';
import { shadowCard } from '../theme/tokens';

interface StatCardProps {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  /** Цвет иконки — один из токенов статуса. */
  iconColor?: string;
  footnote?: ReactNode;
  loading?: boolean;
}

/** KPI-карточка: подпись, крупное значение (tabular-nums), пояснение. */
export function StatCard({ label, value, icon, iconColor, footnote, loading }: StatCardProps) {
  return (
    <Card style={{ height: '100%', boxShadow: shadowCard }} styles={{ body: { padding: 20 } }}>
      <Flex align="center" gap={8} style={{ marginBottom: 8 }}>
        {icon && <span style={{ color: iconColor, fontSize: 16, display: 'inline-flex' }} aria-hidden>{icon}</span>}
        <Typography.Text type="secondary">{label}</Typography.Text>
      </Flex>
      {loading ? (
        <Skeleton.Input active size="small" style={{ width: 120 }} />
      ) : (
        <div className="tsb-num" style={{ fontSize: 24, lineHeight: '32px', fontWeight: 600 }}>
          {value}
        </div>
      )}
      {footnote && (
        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
          {footnote}
        </Typography.Text>
      )}
    </Card>
  );
}
